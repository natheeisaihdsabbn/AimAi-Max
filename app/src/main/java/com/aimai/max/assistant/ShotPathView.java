package com.aimai.max.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Draws what the normal guide line cannot: the bent route of a cushion shot (striker in blue, coin in green)
 * and faint lines for the other coin to pocket routes the planner found. Coordinates are 0..1 screen fractions.
 */
public class ShotPathView extends View {

    private float screenW = 1f;
    private float screenH = 1f;
    private boolean bank = false;
    private float[] strikerPath = new float[0];
    private float[] coinPath = new float[0];
    private float[] alts = new float[0];

    private final Paint strikerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint altPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint altDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bounceFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public ShotPathView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        strikerPaint.setStyle(Paint.Style.STROKE);
        strikerPaint.setStrokeCap(Paint.Cap.ROUND);
        strikerPaint.setStrokeJoin(Paint.Join.ROUND);
        strikerPaint.setStrokeWidth(2.5f * density);
        strikerPaint.setColor(Color.rgb(88, 166, 255));

        coinPaint.setStyle(Paint.Style.STROKE);
        coinPaint.setStrokeCap(Paint.Cap.ROUND);
        coinPaint.setStrokeJoin(Paint.Join.ROUND);
        coinPaint.setStrokeWidth(2.5f * density);
        coinPaint.setColor(Color.rgb(46, 230, 110));

        altPaint.setStyle(Paint.Style.STROKE);
        altPaint.setStrokeCap(Paint.Cap.ROUND);
        altPaint.setStrokeWidth(1.2f * density);
        altPaint.setColor(Color.argb(110, 255, 255, 255));

        altDot.setStyle(Paint.Style.STROKE);
        altDot.setStrokeWidth(1.2f * density);
        altDot.setColor(Color.argb(140, 255, 255, 255));

        bounceFill.setStyle(Paint.Style.FILL);
        bounceFill.setColor(Color.rgb(255, 214, 64));
    }

    public void setScreenSize(int w, int h) {
        screenW = Math.max(1, w);
        screenH = Math.max(1, h);
        invalidate();
    }

    public void setShot(boolean isBank, float[] striker, float[] coin, float[] others) {
        bank = isBank;
        strikerPath = striker == null ? new float[0] : striker;
        coinPath = coin == null ? new float[0] : coin;
        alts = others == null ? new float[0] : others;
        invalidate();
    }

    public void clear() {
        bank = false;
        strikerPath = new float[0];
        coinPath = new float[0];
        alts = new float[0];
        invalidate();
    }

    private void drawPoly(Canvas canvas, float[] pts, Paint paint) {
        if (pts.length < 4) {
            return;
        }
        Path path = new Path();
        path.moveTo(pts[0] * screenW, pts[1] * screenH);
        for (int i = 2; i + 1 < pts.length; i += 2) {
            path.lineTo(pts[i] * screenW, pts[i + 1] * screenH);
        }
        canvas.drawPath(path, paint);
        if (pts.length >= 6) {
            // The middle point is where the route bounces off the cushion.
            canvas.drawCircle(pts[2] * screenW, pts[3] * screenH, 5f * density, bounceFill);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i + 3 < alts.length; i += 4) {
            float cx = alts[i] * screenW;
            float cy = alts[i + 1] * screenH;
            canvas.drawLine(cx, cy, alts[i + 2] * screenW, alts[i + 3] * screenH, altPaint);
            canvas.drawCircle(cx, cy, 9f * density, altDot);
        }
        if (bank) {
            drawPoly(canvas, coinPath, coinPaint);
            drawPoly(canvas, strikerPath, strikerPaint);
        }
    }
}
