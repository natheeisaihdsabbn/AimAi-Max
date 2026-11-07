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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final long ANALYSIS_GAP_MS = 450L;
    private static final long HOLD_LAST_MS = 1500L;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread workerThread;
    private MediaProjection.Callback projectionCallback;
    private Handler worker;

    // Everything below is touched only on the worker thread.
    private long lastAnalysisTime = 0L;
    private int stableFrameCount = 0;
    private List<float[]> previousPieces = new ArrayList<>();
    private CarromBoardAnalyzer.Analysis lastGood;
    private long lastGoodAt = 0L;
    private Intent lastGuideIntent;
    private Intent lastBoardIntent;
    private long lastBroadcastAt = 0L;

    /**
     * A phone screen that is not changing produces no new frames. Re-send the last result so the
     * overlay knows scanning is alive, and count each quiet tick as one more "steady board" frame.
     */
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            Handler w = worker;
            if (w == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (AimAiGestureService.isGameForeground() && lastGuideIntent != null
                    && now - lastBroadcastAt >= 600L) {
                stableFrameCount++;
                lastGuideIntent.putExtra("boardStable", stableFrameCount >= 3);
                sendBroadcast(new Intent(lastGuideIntent));
                if (lastBoardIntent != null) sendBroadcast(new Intent(lastBoardIntent));
                lastBroadcastAt = now;
            }
            w.postDelayed(this, 700L);
        }
    };

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
            stopCapture();
            // Scanning runs on its own thread so the phone, the overlay and the game never stall.
            workerThread = new HandlerThread("AimAiScan", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            workerThread.start();
            worker = new Handler(workerThread.getLooper());
            worker.postDelayed(heartbeat, 700L);

            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            projection = manager.getMediaProjection(resultCode, resultData);

            if (projection == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            int[] size = ScreenMetrics.realSize(this);
            int width = size[0];
            int height = size[1];
            int density = getResources().getDisplayMetrics().densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(this::onFrame, worker);

            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopCapture();
                    stopSelf();
                }
            };
            projection.registerCallback(projectionCallback, worker);

            virtualDisplay = projection.createVirtualDisplay(
                    "AimAiMaxCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    worker
            );

        } catch (Exception e) {
            android.util.Log.e("AimAiMax", "Capture start failed", e);
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void onFrame(ImageReader reader) {
        Image frame = null;
        try {
            frame = reader.acquireLatestImage();
            if (frame == null) return;

            // Scanning is fully off whenever Carrom Disc Pool is not in the foreground.
            if (!AimAiGestureService.isGameForeground()) {
                stableFrameCount = 0;
                previousPieces = new ArrayList<>();
                lastGood = null;
                lastGuideIntent = null;
                lastBoardIntent = null;
                return;
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAnalysisTime < ANALYSIS_GAP_MS) return;
            lastAnalysisTime = now;

            Image.Plane[] planes = frame.getPlanes();
            if (planes.length == 0) return;
            Image.Plane plane = planes[0];
            int fw = frame.getWidth();
            int fh = frame.getHeight();
            Bitmap raw = Bitmap.createBitmap(
                    fw + (plane.getRowStride() - plane.getPixelStride() * fw) / plane.getPixelStride(),
                    fh, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(plane.getBuffer());
            Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, fw, fh);
            if (cropped != raw) raw.recycle();

            // The Image buffer is no longer needed once copied; free it before the heavy work.
            frame.close();
            frame = null;

            try {
                publishAnalysis(cropped);
            } finally {
                cropped.recycle();
            }
        } catch (Throwable e) {
            android.util.Log.e("AimAiMax", "Frame capture failed", e);
        } finally {
            if (frame != null) {
                frame.close();
            }
        }
    }

    private boolean samePositions(List<float[]> a, List<CarromBoardAnalyzer.Piece> b, float tol) {
        if (a.size() != b.size()) return false;
        for (CarromBoardAnalyzer.Piece p : b) {
            boolean found = false;
            for (float[] q : a) {
                float dx = q[0] - p.x, dy = q[1] - p.y;
                if (dx * dx + dy * dy <= tol * tol) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private void publishAnalysis(Bitmap cropped) {
        long started = android.os.SystemClock.elapsedRealtime();
        CarromBoardAnalyzer.Analysis result = CarromBoardAnalyzer.analyze(cropped);
        long now = android.os.SystemClock.elapsedRealtime();
        float frameW = cropped.getWidth();
        float frameH = cropped.getHeight();

        // Our own guide drawing can hide the striker for one frame. Keep the last good scan briefly.
        if (result.board.detected && result.striker != null) {
            lastGood = result;
            lastGoodAt = now;
        } else if (lastGood != null && now - lastGoodAt < HOLD_LAST_MS) {
            result = lastGood;
        } else {
            lastGood = null;
        }

        int strikerCount = result.striker != null ? 1 : 0;
        int coinCount = 0;
        for (CarromBoardAnalyzer.Piece p : result.pieces) {
            if (p != result.striker) coinCount++;
        }
        android.util.Log.d("AimAiMax", "scan " + (now - started) + "ms pockets=" + result.pockets.size()
                + " pieces=" + result.pieces.size() + " striker=" + strikerCount + " coins=" + coinCount);

        float tol = Math.max(6f, 0.012f * Math.max(1f, result.board.width()));
        if (samePositions(previousPieces, result.pieces, tol)) {
            stableFrameCount++;
        } else {
            stableFrameCount = 0;
            List<float[]> copy = new ArrayList<>();
            for (CarromBoardAnalyzer.Piece p : result.pieces) copy.add(new float[]{p.x, p.y});
            previousPieces = copy;
        }

        Intent guideIntent = new Intent("com.aimai.max.GUTI_GUIDE_DATA");
        guideIntent.setPackage(getPackageName());
        guideIntent.putExtra("boardStable", stableFrameCount >= 3);
        guideIntent.putExtra("strikerCount", strikerCount);
        guideIntent.putExtra("gutiCount", coinCount);

        CarromBoardAnalyzer.Piece striker = result.striker;
        if (striker != null && result.board.detected) {
            String mine = getSharedPreferences("aimai_settings", MODE_PRIVATE).getString("my_coins", "BLACK");
            CarromBoardAnalyzer.PieceType wanted = "WHITE".equals(mine)
                    ? CarromBoardAnalyzer.PieceType.WHITE
                    : "ANY".equals(mine) ? null : CarromBoardAnalyzer.PieceType.BLACK;
            CarromShotPlanner.Shot shot = CarromShotPlanner.plan(result, wanted);
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
                guideIntent.putExtra("pocketRadius", shot.pocket.radius / frameW);
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
        lastGuideIntent = new Intent(guideIntent);
        lastBroadcastAt = android.os.SystemClock.elapsedRealtime();

        Intent analysisIntent = new Intent("com.aimai.max.BOARD_ANALYSIS");
        analysisIntent.setPackage(getPackageName());
        analysisIntent.putExtra("found", result.board.detected);
        analysisIntent.putExtra("centerX", (result.board.left + result.board.right) / 2f / frameW);
        analysisIntent.putExtra("centerY", (result.board.top + result.board.bottom) / 2f / frameH);
        analysisIntent.putExtra("boardWidth", result.board.width() / frameW);
        analysisIntent.putExtra("boardHeight", result.board.height() / frameH);
        sendBroadcast(analysisIntent);
        lastBoardIntent = new Intent(analysisIntent);
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
        MediaProjection.Callback cb = projectionCallback;
        projectionCallback = null;
        if (mp != null) {
            try {
                if (cb != null) mp.unregisterCallback(cb);
                mp.stop();
            } catch (RuntimeException ignored) {}
        }
        HandlerThread t = workerThread;
        Handler w = worker;
        workerThread = null;
        worker = null;
        if (w != null) w.removeCallbacks(heartbeat);
        if (t != null) t.quitSafely();
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
