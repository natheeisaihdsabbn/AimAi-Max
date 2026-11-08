package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** A small card with a title and a mini graph, used for the Fast and Human play styles. */
public class PlayStyleCardView extends View {

    private final String title;
    private final boolean fast;
    private boolean selected = false;
    private final float density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PlayStyleCardView(Context context, String title, boolean fast, int accent) {
        super(context);
        this.title = title;
        this.fast = fast;
        density = context.getResources().getDisplayMetrics().density;

        fill.setStyle(Paint.Style.FILL);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(11f * density);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(1.2f * density);
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(1.2f * density);
    }

    public void setSelectedState(boolean value) {
        selected = value;
        invalidate();
    }

    private void drawPoly(Canvas canvas, float[] pts, float gx, float gy, float gw, float gh, Paint paint) {
        Path path = new Path();
        path.moveTo(gx + pts[0] * gw, gy + pts[1] * gh);
        for (int i = 2; i + 1 < pts.length; i += 2) {
            path.lineTo(gx + pts[i] * gw, gy + pts[i + 1] * gh);
        }
        canvas.drawPath(path, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = 10f * density;
        float pad = 1f * density;
        RectF box = new RectF(pad, pad, w - pad, h - pad);

        fill.setColor(selected ? Color.rgb(24, 28, 40) : Color.rgb(15, 17, 24));
        canvas.drawRoundRect(box, r, r, fill);
        border.setColor(selected ? Color.argb(210, 175, 180, 205) : Color.argb(60, 150, 154, 170));
        canvas.drawRoundRect(box, r, r, border);

        text.setColor(selected ? Color.WHITE : Color.rgb(165, 170, 188));
        canvas.drawText(title, w / 2f, 17f * density, text);

        float gx = 10f * density;
        float gy = 24f * density;
        float gw = w - 20f * density;
        float gh = h - gy - 9f * density;
        int bright = selected ? Color.argb(230, 190, 194, 220) : Color.argb(110, 150, 154, 175);
        int faint = selected ? Color.argb(110, 150, 154, 180) : Color.argb(60, 130, 134, 155);
        float dotR = 3f * density;

        if (fast) {
            float[] main = {0.02f, 0.95f, 0.45f, 0.20f, 0.92f, 0.28f};
            float[] low = {0.02f, 0.95f, 0.45f, 0.55f, 0.92f, 0.85f};
            line.setColor(faint);
            drawPoly(canvas, low, gx, gy, gw, gh, line);
            line.setColor(bright);
            drawPoly(canvas, main, gx, gy, gw, gh, line);
            dot.setColor(bright);
            canvas.drawCircle(gx + 0.92f * gw, gy + 0.28f * gh, dotR, dot);
            dot.setColor(faint);
            canvas.drawCircle(gx + 0.92f * gw, gy + 0.85f * gh, dotR, dot);
        } else {
            float[] main = {0.02f, 0.80f, 0.50f, 0.80f, 0.95f, 0.25f};
            line.setColor(bright);
            drawPoly(canvas, main, gx, gy, gw, gh, line);
            dot.setColor(bright);
            canvas.drawCircle(gx + 0.50f * gw, gy + 0.80f * gh, dotR, dot);
            canvas.drawCircle(gx + 0.95f * gw, gy + 0.25f * gh, dotR, dot);
        }
    }
}
