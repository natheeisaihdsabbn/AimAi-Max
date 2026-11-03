package com.aimai.max.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Bitmap;
import android.media.Image;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
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
    private long lastAnalysisTime = 0L;\n    private String previousSignature = "";\n    private int stableFrameCount = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        startForeground(2001, buildNotification());

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

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

            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int density = getResources().getDisplayMetrics().densityDpi;

            imageReader = ImageReader.newInstance(
                    width, height, PixelFormat.RGBA_8888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                android.media.Image frame = null;
                try {
                    frame = reader.acquireLatestImage();
                    if (frame != null) {
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - lastAnalysisTime < 500) return;\n                        if (!getSharedPreferences("aimai_settings", MODE_PRIVATE).getBoolean("game_foreground", false)) return;
                        lastAnalysisTime = now;
                        Image.Plane[] planes = frame.getPlanes();
                        if (planes.length > 0) {
                            Image.Plane plane = planes[0];
                            android.graphics.Bitmap raw = Bitmap.createBitmap(
                                    frame.getWidth() + (plane.getRowStride()
                                            - plane.getPixelStride() * frame.getWidth())
                                            / plane.getPixelStride(),
                                    frame.getHeight(),
                                    Bitmap.Config.ARGB_8888
                            );
                            raw.copyPixelsFromBuffer(plane.getBuffer());

                            Bitmap cropped = Bitmap.createBitmap(
                                    raw, 0, 0, frame.getWidth(), frame.getHeight()
                            );
                            raw.recycle();

                            CarromBoardAnalyzer.Analysis result = CarromBoardAnalyzer.analyze(cropped);
                            float frameW = cropped.getWidth();
                            float frameH = cropped.getHeight();

                            int strikerCount = result.striker != null ? 1 : 0;
                            int coinCount = 0;
                            for (CarromBoardAnalyzer.Piece p : result.pieces) {
                                if (p != result.striker) coinCount++;
                            }
                            android.util.Log.d("AimAiMax", "Detected pockets: " + result.pockets.size());
                            android.util.Log.d("AimAiMax", "Piece scan: " + result.pieces.size()
                                    + ", strikers: " + strikerCount + ", guti: " + coinCount);

                            StringBuilder sig = new StringBuilder();\n                            for (CarromBoardAnalyzer.Piece p : result.pieces) sig.append(Math.round(p.x / 12f)).append(",").append(Math.round(p.y / 12f)).append(";");\n                            String currentSignature = sig.toString();\n                            if (currentSignature.equals(previousSignature)) stableFrameCount++; else { stableFrameCount = 0; previousSignature = currentSignature; }\n                            Intent guideIntent = new Intent("com.aimai.max.GUTI_GUIDE_DATA");\n                            guideIntent.putExtra("boardStable", stableFrameCount >= 4);
                            guideIntent.setPackage(getPackageName());
                            guideIntent.putExtra("strikerCount", strikerCount);
                            guideIntent.putExtra("gutiCount", coinCount);

                            CarromBoardAnalyzer.Piece firstPiece = result.striker;
                            if (firstPiece != null && result.board.detected) {
                                CarromShotPlanner.Shot shot = CarromShotPlanner.plan(
                                        result, null);
                                guideIntent.putExtra("isStriker", true);
                                guideIntent.putExtra("pieceX", firstPiece.x / frameW);
                                guideIntent.putExtra("pieceY", firstPiece.y / frameH);
                                guideIntent.putExtra("strikerRadius", firstPiece.radius / frameW);
                                guideIntent.putExtra("strikerRadiusX", firstPiece.radius / frameW);
                                guideIntent.putExtra("strikerRadiusY", firstPiece.radius / frameH);
                                guideIntent.putExtra("shotFound", shot.valid);
                                if (shot.valid) {
                                    guideIntent.putExtra("targetPieceX", shot.target.x / frameW);
                                    guideIntent.putExtra("targetPieceY", shot.target.y / frameH);
                                    guideIntent.putExtra("targetPieceRadiusX", shot.target.radius / frameW);
                                    guideIntent.putExtra("targetPieceRadiusY", shot.target.radius / frameH);
                                    guideIntent.putExtra("ghostX", shot.ghostX / frameW);
                                    guideIntent.putExtra("ghostY", shot.ghostY / frameH);\n                                    guideIntent.putExtra("shotPower", Math.max(0f, Math.min(1f, shot.score / 100f)));
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

                            android.util.Log.d("AimAiMax", "Frame analyzed. Board candidate: " + result.board.detected);
                            cropped.recycle();
                        }
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
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
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
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
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
