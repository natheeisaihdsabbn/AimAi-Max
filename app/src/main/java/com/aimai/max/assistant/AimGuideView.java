package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class AimGuideView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float startX = 0.5f;
    private float startY = 0.85f;
    private float endX = 0.5f;
    private float endY = 0.25f;
    private float thickness = 5f;
    private int accentColor = Color.CYAN;
    private boolean guideEnabled = true;
    private String guideStyle = "FOCUS";

    public AimGuideView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setGuide(float sx, float sy, float ex, float ey) {
        startX = sx;
        startY = sy;
        endX = ex;
        endY = ey;
        invalidate();
    }

    public void setThickness(float value) {
        thickness = Math.max(1f, value);
        invalidate();
    }

    public void setAccentColor(int color) {
        accentColor = color;
        invalidate();
    }

    public void setGuideStyle(String style) {
        guideStyle = style == null ? "FOCUS" : style.toUpperCase();
        invalidate();
    }

    public void setGuideEnabled(boolean enabled) {
        guideEnabled = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!guideEnabled) return;

        float sx = startX * getWidth();
        float sy = startY * getHeight();
        float ex = endX * getWidth();
        float ey = endY * getHeight();

        paint.setColor(accentColor);
        if ("RAINBOW".equals(guideStyle)) {
            paint.setShader(new android.graphics.LinearGradient(
                sx, sy, ex, ey,
                new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA},
                null, android.graphics.Shader.TileMode.CLAMP));
        } else {
            paint.setShader(null);
        }
        paint.setStrokeWidth(thickness);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShadowLayer(12f, 0f, 0f, accentColor);

        if ("GLOW".equals(guideStyle) || "AURORA".equals(guideStyle)) {
            paint.setShadowLayer(24f, 0f, 0f, accentColor);
        } else if ("PULSE".equals(guideStyle)) {
            paint.setStrokeWidth(thickness * 1.5f);
        } else if ("FLOW".equals(guideStyle)) {
            paint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{18f, 10f}, 0f));
        }
        canvas.drawLine(sx, sy, ex, ey, paint);
        paint.setPathEffect(null);
        paint.setShader(null);
        paint.clearShadowLayer();

        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(ex, ey, thickness * 2.5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(ex, ey, thickness * 5f, paint);

        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(Math.max(1f, thickness / 2f));
        canvas.drawCircle(sx, sy, thickness * 2f, paint);
    }
}
