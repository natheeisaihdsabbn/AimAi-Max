package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/** Real physical screen size (including status/navigation bars) shared by capture, overlay and gestures. */
public final class ScreenMetrics {
    private ScreenMetrics() {}

    public static int[] realSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30 && wm != null) {
            android.graphics.Rect b = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{b.width(), b.height()};
        }
        Point p = new Point();
        if (wm != null) {
            Display d = wm.getDefaultDisplay();
            d.getRealSize(p);
        }
        if (p.x <= 0 || p.y <= 0) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            return new int[]{dm.widthPixels, dm.heightPixels};
        }
        return new int[]{p.x, p.y};
    }
}
