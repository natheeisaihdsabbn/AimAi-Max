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

/**
 * Fallback for Android 10 and older, where the accessibility screenshot does not exist.
 * It needs the "start recording or casting" permission. On Android 11+ this service is not used.
 */
public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final long ANALYSIS_GAP_MS = 450L;

    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread workerThread;
    private Handler worker;
    private final ScanPipeline pipeline = new ScanPipeline();
    private long lastAnalysisTime = 0L;

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
            workerThread = new HandlerThread("AimAiScan", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            workerThread.start();
            worker = new Handler(workerThread.getLooper());

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

            virtualDisplay = projection.createVirtualDisplay("AimAiMaxCapture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, worker);
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
            if (!AimAiGestureService.isGameForeground()) {
                pipeline.reset();
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
            frame.close();
            frame = null;
            try {
                pipeline.process(this, cropped);
            } finally {
                cropped.recycle();
            }
        } catch (Throwable e) {
            android.util.Log.e("AimAiMax", "Frame capture failed", e);
        } finally {
            if (frame != null) frame.close();
        }
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "aimai_capture", "Screen Capture", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
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
            } catch (RuntimeException ignored) {
            }
        }
        HandlerThread t = workerThread;
        workerThread = null;
        worker = null;
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
