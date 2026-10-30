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
    private final CarromBoardAnalyzer boardAnalyzer = new CarromBoardAnalyzer();
    private long lastAnalysisTime = 0L;

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
                        if (now - lastAnalysisTime < 500) return;
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

                            CarromBoardAnalyzer.AnalysisResult result =
                                    boardAnalyzer.analyze(cropped);
                            android.content.Intent analysisIntent =
                                    new android.content.Intent("com.aimai.max.BOARD_ANALYSIS");
                            analysisIntent.setPackage(getPackageName());
                            analysisIntent.putExtra("found", result.boardCandidateFound);
                            analysisIntent.putExtra("centerX", result.centerX);
                            analysisIntent.putExtra("centerY", result.centerY);
                            analysisIntent.putExtra("boardWidth", result.boardWidth);
                            analysisIntent.putExtra("boardHeight", result.boardHeight);
                            sendBroadcast(analysisIntent);


                            android.util.Log.d(
                                    "AimAiMax",
                                    "Frame analyzed. Board candidate: "
                                            + result.boardCandidateFound
                            );
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
