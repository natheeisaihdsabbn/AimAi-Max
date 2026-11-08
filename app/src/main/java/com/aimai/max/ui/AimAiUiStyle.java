package com.aimai.max.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.view.Gravity;

/** Shared dark-glass control styling used by the AimAi screens. */
public final class AimAiUiStyle {
    private static final int PANEL = Color.rgb(25, 28, 38);
    private static final int SELECTED = Color.rgb(70, 68, 108);
    private static final int BORDER = Color.rgb(48, 52, 66);
    private static final int ACCENT = Color.rgb(170, 166, 235);

    private AimAiUiStyle() {}

    private static GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(12f * 1.0f);
        d.setStroke(1, stroke);
        return d;
    }

    public static void button(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(238, 238, 246));
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(44);
        button.setPadding(14, 8, 14, 8);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(35, 190, 185, 255)),
                shape(PANEL, BORDER), null));
        button.setElevation(0f);
        button.setStateListAnimator(null);
    }

    public static void chip(TextView view, boolean selected) {
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.rgb(238, 238, 246));
        view.setTextSize(12);
        view.setPadding(10, 8, 10, 8);
        view.setMinHeight(40);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(35, 190, 185, 255)),
                shape(selected ? SELECTED : PANEL, selected ? ACCENT : BORDER), null));
        view.setClickable(true);
        view.setFocusable(true);
    }

    public static void toggle(Switch control) {
        control.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.WHITE, Color.rgb(170, 174, 186)}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.rgb(96, 104, 150), Color.rgb(60, 64, 78)}));
    }
}
