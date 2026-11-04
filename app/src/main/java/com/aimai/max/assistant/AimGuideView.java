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
    private boolean trajectoryEnabled;
    private float ghostX, ghostY, targetX, targetY, pocketX, pocketY;

    public AimGuideView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setGuide(float sx, float sy, float ex, float ey) {
        startX = clamp(sx);
        startY = clamp(sy);
        endX = clamp(ex);
        endY = clamp(ey);
        invalidate();
    }

    public void setTrajectory(float sx, float sy, float gx, float gy,
                              float tx, float ty, float px, float py) {
        setGuide(sx, sy, gx, gy);
        ghostX=clamp(gx); ghostY=clamp(gy);
        targetX=clamp(tx); targetY=clamp(ty);
        pocketX=clamp(px); pocketY=clamp(py);
        trajectoryEnabled=true;
        invalidate();
    }

    public void clearTrajectory() {
        trajectoryEnabled=false;
        invalidate();
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public void setThickness(float value) {
        thickness = Math.max(1f, value);
        invalidate();
    }

    public void setAccentColor(int color) {
        accentColor = color;
        invalidate();
    }

    public void setGuideThickness(float value) {
        thickness = Math.max(1f, Math.min(20f, value));
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
        if (trajectoryEnabled) {
            float gx=ghostX*getWidth(), gy=ghostY*getHeight();
            float tx=targetX*getWidth(), ty=targetY*getHeight();
            float px=pocketX*getWidth(), py=pocketY*getHeight();
            paint.setShader(null);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{12f,8f},0f));
            paint.setStrokeWidth(Math.max(1f, thickness*0.75f));
            canvas.drawLine(gx,gy,tx,ty,paint);
            canvas.drawLine(tx,ty,px,py,paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.YELLOW);
            canvas.drawCircle(gx,gy,thickness*2f,paint);
            paint.setColor(Color.GREEN);
            canvas.drawCircle(px,py,thickness*2.5f,paint);
            paint.setColor(accentColor);
            paint.setStyle(Paint.Style.STROKE);
        }
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
