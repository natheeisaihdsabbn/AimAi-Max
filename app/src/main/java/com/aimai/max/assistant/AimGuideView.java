package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class AimGuideView extends View {

    // Colours are chosen so the board analyzer (which sees our own overlay in the capture)
    // never classifies them as white / black / red / wood pieces.
    private static final int ROUTE_YELLOW = Color.rgb(255, 214, 0);
    private static final int ROUTE_GREEN = Color.rgb(0, 255, 110);
    private static final int START_MARK = Color.rgb(255, 0, 200);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];
    private float startX = 0.5f;
    private float startY = 0.85f;
    private float endX = 0.5f;
    private float endY = 0.25f;
    private float thickness = 5f;
    private int accentColor = Color.CYAN;
    private boolean guideEnabled = true;
    private String guideStyle = "FOCUS";
    private int screenW = 0, screenH = 0;

    private float ghostX = -1f, ghostY = -1f, pocketX = -1f, pocketY = -1f;
    private boolean shotRouteEnabled = false;

    public AimGuideView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    /** Real physical screen size; normalized coordinates refer to this area, not to the view. */
    public void setScreenSize(int w, int h) {
        screenW = w;
        screenH = h;
        invalidate();
    }

    public void setShotRoute(float gx, float gy, float px, float py, boolean enabled) {
        ghostX = clamp(gx);
        ghostY = clamp(gy);
        pocketX = clamp(px);
        pocketY = clamp(py);
        shotRouteEnabled = enabled;
        invalidate();
    }

    public void setGuide(float sx, float sy, float ex, float ey) {
        startX = clamp(sx);
        startY = clamp(sy);
        endX = clamp(ex);
        endY = clamp(ey);
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

        getLocationOnScreen(loc);
        float sw = screenW > 0 ? screenW : getWidth();
        float sh = screenH > 0 ? screenH : getHeight();
        float ox = loc[0];
        float oy = loc[1];

        float sx = startX * sw - ox;
        float sy = startY * sh - oy;
        float ex = endX * sw - ox;
        float ey = endY * sh - oy;
        float gx = ghostX * sw - ox;
        float gy = ghostY * sh - oy;
        float px = pocketX * sw - ox;
        float py = pocketY * sh - oy;

        paint.setPathEffect(null);
        paint.setColor(accentColor);
        if ("RAINBOW".equals(guideStyle)) {
            paint.setShader(new android.graphics.LinearGradient(
                sx, sy, ex, ey,
                new int[]{Color.rgb(255, 120, 0), Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA},
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

        if (shotRouteEnabled) {
            paint.setShadowLayer(0f, 0f, 0f, 0);
            paint.setStrokeWidth(thickness);
            paint.setColor(ROUTE_GREEN);
            canvas.drawLine(gx, gy, px, py, paint);
            paint.setColor(ROUTE_YELLOW);
            canvas.drawCircle(px, py, thickness * 4f, paint);
        }
        paint.clearShadowLayer();

        paint.setColor(accentColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(ex, ey, thickness * 2.5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        canvas.drawCircle(ex, ey, thickness * 5f, paint);

        paint.setColor(START_MARK);
        paint.setStrokeWidth(Math.max(1f, thickness / 2f));
        canvas.drawCircle(sx, sy, thickness * 2f, paint);
    }
}
