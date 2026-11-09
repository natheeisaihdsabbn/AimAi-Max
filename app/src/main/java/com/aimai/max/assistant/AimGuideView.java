package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * Draws the aim guide on top of the game. It is deliberately thin and starts/stops at the edge of the
 * striker and the coin, so it never covers their centres (the scanner can still see them through it).
 */
public class AimGuideView extends View {

    // Colours chosen so the board analyzer (which also sees this overlay) never mistakes
    // them for white / black / red / orange game pieces.
    private static final int ROUTE_YELLOW = Color.rgb(255, 214, 0);
    private static final int ROUTE_GREEN = Color.rgb(0, 255, 110);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];
    private float startX = 0.5f;
    private float startY = 0.85f;
    private float endX = 0.5f;
    private float endY = 0.25f;
    private float thickness = 5f;
    private int accentColor = Color.CYAN;
    private boolean guideEnabled = true;
    private boolean smooth = false;
    private String guideStyle = "FOCUS";
    private int screenW = 0, screenH = 0;
    private float bodyRadius = 0f;      // striker radius as a fraction of the screen width
    private float pocketRadius = 0f;    // pocket radius as a fraction of the screen width

    private float ghostX = -1f, ghostY = -1f, pocketX = -1f, pocketY = -1f;
    private boolean shotRouteEnabled = false;

    // simulated shot paths: [kind, n, x0, y0, ...] repeated, all normalized to the screen
    private float[] searchMain = null;
    private float[] searchAlts = null;
    private float searchGhostX = -1f, searchGhostY = -1f;

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

    public void setBodyRadius(float normalizedToWidth) {
        bodyRadius = Math.max(0f, normalizedToWidth);
    }

    public void setPocketRadius(float normalizedToWidth) {
        pocketRadius = Math.max(0f, normalizedToWidth);
    }

    public void setShotRoute(float gx, float gy, float px, float py, boolean enabled) {
        ghostX = clamp(gx);
        ghostY = clamp(gy);
        pocketX = clamp(px);
        pocketY = clamp(py);
        shotRouteEnabled = enabled;
        invalidate();
    }

    /** Shows the simulated path of the chosen shot (with every wall bounce) and, thin, the other shots tried. */
    public void setSearch(float[] main, float[] alts, float ghostX, float ghostY) {
        searchMain = (main != null && main.length > 0) ? main : null;
        searchAlts = (alts != null && alts.length > 0) ? alts : null;
        searchGhostX = ghostX;
        searchGhostY = ghostY;
        invalidate();
    }

    public void clearSearch() {
        searchMain = null;
        searchAlts = null;
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

    /** Smooth = rounded, softly glowing line. Sharp = crisp, flat-ended line with no glow. */
    public void setSmooth(boolean value) {
        smooth = value;
        invalidate();
    }

    public void setGuideEnabled(boolean enabled) {
        guideEnabled = enabled;
        invalidate();
    }

    private void drawPaths(Canvas canvas, float[] data, float sw, float sh, float ox, float oy,
                           float stroke, int alpha) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(null);
        paint.setShader(null);
        paint.clearShadowLayer();
        paint.setStrokeWidth(stroke);
        paint.setPathEffect(smooth ? new android.graphics.CornerPathEffect(46f) : null);
        android.graphics.Path path = new android.graphics.Path();
        int i = 0;
        while (i + 1 < data.length) {
            int kind = (int) data[i];
            int n = (int) data[i + 1];
            int p = i + 2;
            if (n < 2 || p + n * 2 > data.length) break;
            int color = kind == 0 ? accentColor : (kind == 1 ? ROUTE_GREEN : Color.rgb(255, 95, 120));
            paint.setColor(color);
            paint.setAlpha(alpha);
            path.reset();
            for (int k = 0; k < n; k++) {
                float x = data[p + k * 2] * sw - ox;
                float y = data[p + k * 2 + 1] * sh - oy;
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
            i = p + n * 2;
        }
        paint.setAlpha(255);
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

        float r = bodyRadius > 0f ? bodyRadius * sw : 0.032f * sw;   // striker radius in pixels

        if (searchMain != null) {
            float stroke0 = Math.max(2f, thickness * 0.6f);
            if (searchAlts != null) drawPaths(canvas, searchAlts, sw, sh, ox, oy, Math.max(1.5f, stroke0 * 0.45f), 105);
            drawPaths(canvas, searchMain, sw, sh, ox, oy, stroke0, 255);
            if (searchGhostX >= 0f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setPathEffect(null);
                paint.setShader(null);
                paint.clearShadowLayer();
                paint.setStrokeWidth(stroke0);
                paint.setColor(accentColor);
                canvas.drawCircle(searchGhostX * sw - ox, searchGhostY * sh - oy, r, paint);
            }
            return;
        }
        float coinR = r * 0.75f;
        float stroke = Math.max(2f, thickness * 0.6f);

        float dx = ex - sx, dy = ey - sy;
        float len = (float) Math.hypot(dx, dy);

        // ---- striker -> ghost striker (the line the striker travels)
        if (len > r * 2.3f) {
            float ux = dx / len, uy = dy / len;
            float x0 = sx + ux * r * 1.1f, y0 = sy + uy * r * 1.1f;
            float x1 = ex - ux * r, y1 = ey - uy * r;

            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(smooth ? Paint.Cap.ROUND : Paint.Cap.BUTT);
            paint.setStrokeWidth(stroke);
            paint.setColor(accentColor);
            if ("RAINBOW".equals(guideStyle)) {
                paint.setShader(new LinearGradient(x0, y0, x1, y1,
                        new int[]{Color.rgb(255, 120, 0), Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA},
                        null, Shader.TileMode.CLAMP));
            } else {
                paint.setShader(null);
            }
            if (smooth) {
                paint.setShadowLayer(10f, 0f, 0f, accentColor);
            } else {
                paint.clearShadowLayer();
            }
            if ("GLOW".equals(guideStyle) || "AURORA".equals(guideStyle)) {
                paint.setShadowLayer(18f, 0f, 0f, accentColor);
            } else if ("PULSE".equals(guideStyle)) {
                paint.setStrokeWidth(stroke * 1.4f);
            } else if ("FLOW".equals(guideStyle)) {
                paint.setPathEffect(new DashPathEffect(new float[]{18f, 10f}, 0f));
            }
            canvas.drawLine(x0, y0, x1, y1, paint);
            paint.setPathEffect(null);
            paint.setShader(null);
            paint.clearShadowLayer();
        }

        // ---- ghost striker: a thin ring exactly the size of the striker
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(accentColor);
        canvas.drawCircle(ex, ey, r, paint);

        // ---- coin -> pocket
        if (shotRouteEnabled) {
            float rx = px - gx, ry = py - gy;
            float rl = (float) Math.hypot(rx, ry);
            if (rl > 1f) {
                float ux = rx / rl, uy = ry / rl;
                float tcx = gx + ux * (r + coinR);           // centre of the coin being hit
                float tcy = gy + uy * (r + coinR);
                float pr = pocketRadius > 0f ? pocketRadius * sw : r * 1.1f;
                float x0 = tcx + ux * coinR * 1.05f, y0 = tcy + uy * coinR * 1.05f;
                float x1 = px - ux * pr, y1 = py - uy * pr;

                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStrokeWidth(stroke);
                paint.setColor(ROUTE_GREEN);
                if ((x1 - x0) * ux + (y1 - y0) * uy > 4f) {
                    canvas.drawLine(x0, y0, x1, y1, paint);
                }
                paint.setStrokeWidth(stroke);
                canvas.drawCircle(tcx, tcy, coinR + 3f, paint);   // marks the coin that will be hit
                paint.setColor(ROUTE_YELLOW);
                canvas.drawCircle(px, py, pr * 0.9f, paint);
            }
        }
    }
}
