package com.aimai.max.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private long lastAnalysisTime = 0L;
    private String previousSignature = "";
    private int stableFrameCount = 0;

    @SuppressWarnings("deprecation")
    private static Intent readResultData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2001, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(2001, buildNotification());
        }

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = readResultData(intent);

        if (resultData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            projection = manager.getMediaProjection(resultCode, resultData);

            if (projection == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            // Real physical size, the same coordinate space the overlay and gestures use.
            int[] size = ScreenMetrics.realSize(this);
            int width = size[0];
            int height = size[1];
            int density = getResources().getDisplayMetrics().densityDpi;

            imageReader = ImageReader.newInstance(
                    width, height, PixelFormat.RGBA_8888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                Image frame = null;
                try {
                    frame = reader.acquireLatestImage();
                    if (frame == null) return;

                    // Scanning is fully off whenever Carrom Disc Pool is not in the foreground.
                    if (!AimAiGestureService.isGameForeground()) {
                        stableFrameCount = 0;
                        previousSignature = "";
                        return;
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastAnalysisTime < 500) return;
                    lastAnalysisTime = now;

                    Image.Plane[] planes = frame.getPlanes();
                    if (planes.length == 0) return;
                    Image.Plane plane = planes[0];
                    int fw = frame.getWidth();
                    int fh = frame.getHeight();
                    Bitmap raw = Bitmap.createBitmap(
                            fw + (plane.getRowStride() - plane.getPixelStride() * fw)
                                    / plane.getPixelStride(),
                            fh, Bitmap.Config.ARGB_8888);
                    raw.copyPixelsFromBuffer(plane.getBuffer());
                    Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, fw, fh);
                    if (cropped != raw) raw.recycle();

                    try {
                        publishAnalysis(cropped);
                    } finally {
                        cropped.recycle();
                    }
                } catch (Exception e) {
                    android.util.Log.e("AimAiMax", "Frame capture failed", e);
                } finally {
                    if (frame != null) {
                        frame.close();
                    }
                }
            }, null);

            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopCapture();
                    stopSelf();
                }
            }, null);

            virtualDisplay = projection.createVirtualDisplay(
                    "AimAiMaxCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    null
            );

        } catch (Exception e) {
            android.util.Log.e("AimAiMax", "Capture start failed", e);
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void publishAnalysis(Bitmap cropped) {
        CarromBoardAnalyzer.Analysis result = CarromBoardAnalyzer.analyze(cropped);
        float frameW = cropped.getWidth();
        float frameH = cropped.getHeight();

        int strikerCount = result.striker != null ? 1 : 0;
        int coinCount = 0;
        for (CarromBoardAnalyzer.Piece p : result.pieces) {
            if (p != result.striker) coinCount++;
        }
        android.util.Log.d("AimAiMax", "Pockets: " + result.pockets.size()
                + ", pieces: " + result.pieces.size()
                + ", strikers: " + strikerCount + ", guti: " + coinCount);

        StringBuilder sig = new StringBuilder();
        for (CarromBoardAnalyzer.Piece p : result.pieces) {
            sig.append(Math.round(p.x / 12f)).append(",").append(Math.round(p.y / 12f)).append(";");
        }
        String currentSignature = sig.toString();
        if (currentSignature.equals(previousSignature)) {
            stableFrameCount++;
        } else {
            stableFrameCount = 0;
            previousSignature = currentSignature;
        }

        Intent guideIntent = new Intent("com.aimai.max.GUTI_GUIDE_DATA");
        guideIntent.setPackage(getPackageName());
        guideIntent.putExtra("boardStable", stableFrameCount >= 4);
        guideIntent.putExtra("strikerCount", strikerCount);
        guideIntent.putExtra("gutiCount", coinCount);

        CarromBoardAnalyzer.Piece striker = result.striker;
        if (striker != null && result.board.detected) {
            CarromShotPlanner.Shot shot = CarromShotPlanner.plan(result, null);
            guideIntent.putExtra("isStriker", true);
            guideIntent.putExtra("pieceX", striker.x / frameW);
            guideIntent.putExtra("pieceY", striker.y / frameH);
            guideIntent.putExtra("strikerRadius", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusX", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusY", striker.radius / frameH);
            guideIntent.putExtra("shotFound", shot.valid);
            if (shot.valid) {
                guideIntent.putExtra("targetPieceX", shot.target.x / frameW);
                guideIntent.putExtra("targetPieceY", shot.target.y / frameH);
                guideIntent.putExtra("targetPieceRadiusX", shot.target.radius / frameW);
                guideIntent.putExtra("targetPieceRadiusY", shot.target.radius / frameH);
                guideIntent.putExtra("ghostX", shot.ghostX / frameW);
                guideIntent.putExtra("ghostY", shot.ghostY / frameH);
                guideIntent.putExtra("shotPower", shot.power);
                guideIntent.putExtra("pocketX", shot.pocket.x / frameW);
                guideIntent.putExtra("pocketY", shot.pocket.y / frameH);
                guideIntent.putExtra("cutAngle", shot.cutAngleDeg);
                guideIntent.putExtra("targetFound", true);
            } else {
                guideIntent.putExtra("targetFound", false);
            }
        } else {
            guideIntent.putExtra("isStriker", false);
            guideIntent.putExtra("targetFound", false);
            guideIntent.putExtra("shotFound", false);
        }
        sendBroadcast(guideIntent);

        Intent analysisIntent = new Intent("com.aimai.max.BOARD_ANALYSIS");
        analysisIntent.setPackage(getPackageName());
        analysisIntent.putExtra("found", result.board.detected);
        analysisIntent.putExtra("centerX", (result.board.left + result.board.right) / 2f / frameW);
        analysisIntent.putExtra("centerY", (result.board.top + result.board.bottom) / 2f / frameH);
        analysisIntent.putExtra("boardWidth", result.board.width() / frameW);
        analysisIntent.putExtra("boardHeight", result.board.height() / frameH);
        sendBroadcast(analysisIntent);
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "aimai_capture",
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "aimai_capture")
                : new Notification.Builder(this);

        return builder
                .setContentTitle("AimAi Max Screen Capture")
                .setContentText("Screen analysis is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    private void stopCapture() {
        VirtualDisplay vd = virtualDisplay;
        virtualDisplay = null;
        if (vd != null) vd.release();
        ImageReader ir = imageReader;
        imageReader = null;
        if (ir != null) ir.close();
        MediaProjection mp = projection;
        projection = null;
        if (mp != null) {
            try { mp.stop(); } catch (RuntimeException ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
