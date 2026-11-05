package com.aimai.max.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AimAiGestureService extends AccessibilityService {
    private static final String TAG = "AimAiAutoPlay";
    private static final String GAME_PACKAGE = "com.miniclip.carrom";

    private static volatile AimAiGestureService instance;
    private static volatile boolean gameForeground = false;

    public static boolean isConnected() { return instance != null; }
    public static boolean isGameForeground() { return gameForeground; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // A stale "true" from a previous process must never survive a reconnect.
        gameForeground = false;
        saveForeground(false);
        Log.i(TAG, "Accessibility connected");
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
        if (cls != null) {
            String c = cls.toString();
            if (c.startsWith("android.widget.Toast") || c.contains("InputMethod")) return true;
        }
        return false;
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
        if (isTransientWindow(pkg, e.getClassName())) return;
        setForeground(GAME_PACKAGE.contentEquals(pkg), pkg);
    }

    /**
     * Drag-to-shoot: the finger goes down ON the striker and is pulled backwards,
     * opposite to the shot direction. (x,y) and (gx,gy) are real-screen pixels.
     */
    public static boolean swipeShot(float x, float y, float gx, float gy, int w, int h, float power) {
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
        float pull = minSide * (0.07f + 0.13f * strength);

        float sx = clamp(x, 2, w - 2), sy = clamp(y, 2, h - 2);          // exact striker position
        float ex = clamp(x - ux * pull, 2, w - 2), ey = clamp(y - uy * pull, 2, h - 2);
        if (Math.hypot(ex - sx, ey - sy) < 8f) return false;

        Path path = new Path();
        path.moveTo(sx, sy);
        path.lineTo(ex, ey);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 260)).build();
        boolean ok = s.dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) {
                Log.i(TAG, "Gesture completed from striker " + sx + "," + sy);
            }
            @Override public void onCancelled(GestureDescription d) {
                Log.e(TAG, "Gesture cancelled");
            }
        }, null);
        Log.i(TAG, "dispatchGesture accepted=" + ok + " start=" + sx + "," + sy
                + " pullTo=" + ex + "," + ey + " power=" + strength);
        return ok;
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    @Override public void onInterrupt() { Log.w(TAG, "Accessibility interrupted"); }

    @Override
    public boolean onUnbind(Intent intent) {
        gameForeground = false;
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
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
