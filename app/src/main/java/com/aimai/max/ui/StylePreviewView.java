package com.aimai.max.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Selectable card with a small preview of a guide style (FOCUS...) or collision mode (SHARP/SMOOTH). */
public class StylePreviewView extends View {
    private final String mode;
    private final String label;
    private boolean selected;
    private int accent;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public StylePreviewView(Context c, String mode, String label, int accent) {
        super(c);
        this.mode = mode;
        this.label = label;
        this.accent = accent;
    }

    public void setSelectedState(boolean value) {
        selected = value;
        invalidate();
    }

    public void setAccent(int value) {
        accent = value;
        invalidate();
    }

    private void line(Canvas c, float x0, float yb, float x1, float yt) {
        path.reset();
        path.moveTo(x0, yb);
        path.lineTo(x0 + (x1 - x0) * 0.62f, yb);
        path.lineTo(x1, yt);
        c.drawPath(path, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float r = 12 * d;
        p.setPathEffect(null);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(17, 20, 28));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth((selected ? 1.6f : 1f) * d);
        p.setColor(selected ? Color.rgb(205, 210, 225) : Color.rgb(40, 44, 56));
        rect.set(d, d, w - d, h - d);
        c.drawRoundRect(rect, r, r, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(222, 226, 236));
        p.setTextSize(11 * d);
        c.drawText(label, 10 * d, 20 * d, p);

        float x0 = 10 * d, x1 = w - 10 * d, yb = h - 14 * d, yt = h * 0.50f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(accent);
        p.setStrokeWidth(2f * d);

        switch (mode) {
            case "GLOW":
                p.setStrokeWidth(7f * d);
                p.setColor(Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)));
                line(c, x0, yb, x1, yt);
                p.setStrokeWidth(2f * d);
                p.setColor(accent);
                line(c, x0, yb, x1, yt);
                break;
            case "FLOW":
                p.setPathEffect(new DashPathEffect(new float[]{6 * d, 5 * d}, 0));
                line(c, x0, yb, x1, yt);
                p.setPathEffect(null);
                break;
            case "RAINBOW":
                p.setShader(new LinearGradient(x0, 0, x1, 0, new int[]{
                        Color.rgb(255, 120, 0), Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA},
                        null, Shader.TileMode.CLAMP));
                line(c, x0, yb, x1, yt);
                p.setShader(null);
                break;
            case "PULSE":
                p.setShader(new LinearGradient(x0, 0, x1, 0, Color.argb(40, Color.red(accent),
                        Color.green(accent), Color.blue(accent)), accent, Shader.TileMode.CLAMP));
                p.setStrokeWidth(3.2f * d);
                line(c, x0, yb, x1, yt);
                p.setShader(null);
                break;
            case "AURORA":
                p.setShader(new LinearGradient(x0, 0, x1, 0, accent, Color.rgb(170, 120, 255),
                        Shader.TileMode.CLAMP));
                p.setStrokeWidth(2.4f * d);
                line(c, x0, yb, x1, yt);
                p.setShader(null);
                break;
            case "SHARP":
                path.reset();
                path.moveTo(x0, yb - 4 * d);
                path.lineTo((x0 + x1) / 2f, yt - 2 * d);
                path.lineTo(x1, yb - 4 * d);
                c.drawPath(path, p);
                break;
            case "SMOOTH":
                path.reset();
                path.moveTo(x0, yb - 4 * d);
                path.quadTo((x0 + x1) / 2f, yt - 12 * d, x1, yb - 4 * d);
                c.drawPath(path, p);
                break;
            default: // FOCUS
                line(c, x0, yb, x1, yt);
                break;
        }
        if (!"SHARP".equals(mode) && !"SMOOTH".equals(mode)) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(accent);
            c.drawCircle(x1, yt, 3 * d, p);
        }
    }
}
