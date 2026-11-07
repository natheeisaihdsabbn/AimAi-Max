package com.aimai.max.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

public class AimAiGestureService extends AccessibilityService {
    private static final String TAG = "AimAiAutoPlay";
    private static final String GAME_PACKAGE = "com.miniclip.carrom";
    private static final long SCAN_INTERVAL_MS = 600L;

    private static volatile AimAiGestureService instance;
    private static volatile boolean gameForeground = false;
    private static volatile boolean scanEnabled = false;
    /** Last real screenshot error (0 = none). The overlay shows a hint when this is set. */
    public static volatile int lastScanError = 0;

    private HandlerThread scanThread;
    private Handler scanHandler;
    private boolean scanBusy = false;
    private final ScanPipeline pipeline = new ScanPipeline();

    public static boolean isConnected() { return instance != null; }
    public static boolean isGameForeground() { return gameForeground; }

    /**
     * Turns the screenshot scanner on or off. It uses the accessibility screenshot, so Android never
     * asks for the "start recording or casting" permission (needs Android 11 or newer).
     */
    public static void setScanEnabled(boolean enabled) {
        scanEnabled = enabled;
        if (!enabled) lastScanError = 0;
    }

    public static boolean isScanEnabled() { return scanEnabled; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // A stale "true" from a previous process must never survive a reconnect.
        gameForeground = false;
        saveForeground(false);
        startScanLoop();
        Log.i(TAG, "Accessibility connected");
    }

    private void startScanLoop() {
        stopScanLoop();
        scanThread = new HandlerThread("AimAiScreenshot", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        scanThread.start();
        scanHandler = new Handler(scanThread.getLooper());
        scanHandler.postDelayed(scanTick, SCAN_INTERVAL_MS);
    }

    private void stopScanLoop() {
        Handler h = scanHandler;
        HandlerThread t = scanThread;
        scanHandler = null;
        scanThread = null;
        if (h != null) h.removeCallbacksAndMessages(null);
        if (t != null) t.quitSafely();
    }

    private final Runnable scanTick = new Runnable() {
        @Override
        public void run() {
            Handler h = scanHandler;
            if (h == null) return;
            if (scanEnabled && gameForeground && Build.VERSION.SDK_INT >= 30) {
                if (!scanBusy) requestScreenshot();
            } else {
                pipeline.reset();
            }
            h.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    private void requestScreenshot() {
        final Handler h = scanHandler;
        if (h == null) return;
        scanBusy = true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, r -> h.post(r), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    try {
                        HardwareBuffer buffer = result.getHardwareBuffer();
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware != null) {
                            Bitmap soft = hardware.copy(Bitmap.Config.ARGB_8888, false);
                            hardware.recycle();
                            buffer.close();
                            if (soft != null) {
                                lastScanError = 0;
                                try {
                                    pipeline.process(AimAiGestureService.this, soft);
                                } finally {
                                    soft.recycle();
                                }
                            }
                        } else {
                            buffer.close();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Screenshot processing failed", t);
                    } finally {
                        scanBusy = false;
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    scanBusy = false;
                    // 3 = asked again too soon, harmless.
                    if (errorCode != 3) {
                        lastScanError = errorCode;
                        Log.w(TAG, "Screenshot failed, code " + errorCode);
                    }
                }
            });
        } catch (Throwable t) {
            scanBusy = false;
            lastScanError = 1;
            Log.e(TAG, "takeScreenshot threw", t);
        }
    }

    private void saveForeground(boolean value) {
        SharedPreferences p = getSharedPreferences("aimai_settings", MODE_PRIVATE);
        p.edit().putBoolean("game_foreground", value).apply();
    }

    /** Windows that appear on top of the game without the game actually leaving the screen. */
    private boolean isTransientWindow(CharSequence pkg, CharSequence cls) {
        if (pkg == null) return true;
        String p = pkg.toString();
        if (p.equals(getPackageName())) return true;                 // our own overlay / activity
        if (p.equals("com.android.systemui")) return true;           // shade, volume, recents hints
        String lp = p.toLowerCase();
        if (lp.contains("inputmethod") || lp.contains("keyboard")) return true;
        // Screen recorders, screenshot tools and game-booster bars float over the game while it keeps running.
        if (lp.contains("record") || lp.contains("screencap") || lp.contains("screenshot")
                || lp.contains("capture") || lp.contains("gamebooster") || lp.contains("gametools")
                || lp.contains("gamelauncher")) return true;
        if (cls != null) {
            String c = cls.toString();
            if (c.startsWith("android.widget.Toast") || c.contains("InputMethod")) return true;
        }
        return false;
    }

    /**
     * A small floating window (recorder button, chat bubble) reports a plain view class, not an Activity.
     * Only a real screen change (an Activity or the home launcher) means the game was left.
     */
    private boolean looksLikeAppSwitch(CharSequence pkg, CharSequence cls) {
        String p = pkg == null ? "" : pkg.toString().toLowerCase();
        String c = cls == null ? "" : cls.toString().toLowerCase();
        if (p.contains("launcher") || p.endsWith(".home") || c.contains("launcher")) return true;
        return c.contains("activity");
    }

    private void setForeground(boolean active, CharSequence pkg) {
        if (active == gameForeground) return;
        gameForeground = active;
        saveForeground(active);
        Intent i = new Intent("com.aimai.max.GAME_FOREGROUND");
        i.setPackage(getPackageName());
        i.putExtra("foreground", active);
        sendBroadcast(i);
        Log.i(TAG, "Foreground=" + active + " package=" + pkg);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        int t = e.getEventType();
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        CharSequence cls = e.getClassName();
        if (isTransientWindow(pkg, cls)) return;
        boolean isGame = GAME_PACKAGE.contentEquals(pkg);
        if (!isGame && !looksLikeAppSwitch(pkg, cls)) return;
        setForeground(isGame, pkg);
    }

    /**
     * Drag-to-shoot: the finger goes down ON the striker and is dragged. In Carrom Disc Pool the shot
     * goes in the direction of the drag, so by default the finger moves towards the target; "pull back"
     * is the opposite (slingshot) style. Longer drag = more power. (x,y) and (gx,gy) are real-screen pixels.
     */
    public static boolean swipeShot(float x, float y, float gx, float gy, int w, int h, float power,
                                    boolean pullBack, float powerMul) {
        AimAiGestureService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24 || !gameForeground) {
            Log.w(TAG, "Gesture rejected: game not foreground or service unavailable");
            return false;
        }
        float dx = gx - x, dy = gy - y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 2f) return false;
        float ux = dx / len, uy = dy / len;

        float strength = Math.max(0f, Math.min(1f, power));
        float minSide = Math.min(w, h);
        float pull = minSide * (0.20f + 0.30f * strength) * Math.max(0.2f, powerMul);

        float sx = clamp(x, 2, w - 2), sy = clamp(y, 2, h - 2);          // exact striker position
        float dir = pullBack ? -1f : 1f;
        float ex = clamp(x + dir * ux * pull, 2, w - 2), ey = clamp(y + dir * uy * pull, 2, h - 2);
        if (Math.hypot(ex - sx, ey - sy) < 8f) return false;

        Path path = new Path();
        path.moveTo(sx, sy);
        path.lineTo(ex, ey);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 300)).build();
        boolean ok = s.dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) {
                Log.i(TAG, "Gesture completed from striker " + sx + "," + sy);
            }
            @Override public void onCancelled(GestureDescription d) {
                Log.e(TAG, "Gesture cancelled");
            }
        }, null);
        Log.i(TAG, "dispatchGesture accepted=" + ok + " start=" + sx + "," + sy
                + " to=" + ex + "," + ey + " power=" + strength + " mul=" + powerMul);
        return ok;
    }

    /**
     * Slides the striker along the baseline: the finger goes down on the slider knob (its x is the same
     * as the striker's x) and is dragged sideways to the wanted x. All values are real-screen pixels.
     */
    public static boolean dragSlider(float fromX, float toX, float y, int w, int h) {
        AimAiGestureService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24 || !gameForeground) {
            Log.w(TAG, "Slider drag rejected: game not foreground or service unavailable");
            return false;
        }
        float fx = clamp(fromX, 2, w - 2), tx = clamp(toX, 2, w - 2), fy = clamp(y, 2, h - 2);
        if (Math.abs(tx - fx) < 6f) return false;
        Path path = new Path();
        path.moveTo(fx, fy);
        path.lineTo(tx, fy);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 450)).build();
        boolean ok = s.dispatchGesture(g, null, null);
        Log.i(TAG, "dragSlider accepted=" + ok + " from=" + fx + " to=" + tx + " y=" + fy);
        return ok;
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    @Override public void onInterrupt() { Log.w(TAG, "Accessibility interrupted"); }

    @Override
    public boolean onUnbind(Intent intent) {
        gameForeground = false;
        scanEnabled = false;
        stopScanLoop();
        if (instance == this) instance = null;
        saveForeground(false);
        Intent i = new Intent("com.aimai.max.GAME_FOREGROUND");
        i.setPackage(getPackageName());
        i.putExtra("foreground", false);
        sendBroadcast(i);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        gameForeground = false;
        stopScanLoop();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
