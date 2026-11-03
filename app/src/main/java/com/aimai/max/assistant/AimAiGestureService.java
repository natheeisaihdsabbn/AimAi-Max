package com.aimai.max.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class AimAiGestureService extends AccessibilityService {
    private static volatile AimAiGestureService instance;
    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }
    public static boolean isConnected() { return instance != null; }

    public static boolean swipeShot(float strikerX, float strikerY, float ghostX, float ghostY,
                                    int screenWidth, int screenHeight) {
        AimAiGestureService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) return false;
        float dx = strikerX - ghostX, dy = strikerY - ghostY;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) return false;
        float pull = Math.min(Math.min(screenWidth, screenHeight) * 0.22f,
                Math.max(65f, length * 0.75f));
        float sx = clamp(strikerX, 2f, screenWidth - 2f);
        float sy = clamp(strikerY, 2f, screenHeight - 2f);
        float ex = clamp(sx + dx / length * pull, 2f, screenWidth - 2f);
        float ey = clamp(sy + dy / length * pull, 2f, screenHeight - 2f);
        Path path = new Path(); path.moveTo(sx, sy); path.lineTo(ex, ey);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 260)).build();
        return service.dispatchGesture(gesture, null, null);
    }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
}