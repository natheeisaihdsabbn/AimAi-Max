package com.aimai.max.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.MotionEvent;
import android.os.Handler;
import android.os.Looper;

/** Top banner of the Games page: dark card, headline text and a drawn carrom shot. */
public class BannerView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int page = 0;
    private float downX;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotate = new Runnable() { public void run() { page = (page + 1) % 3; invalidate(); handler.postDelayed(this, 4200); } };

    public BannerView(Context c) {
        super(c);
        setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downX = e.getX(); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (Math.abs(e.getX() - downX) > 28) page = (page + (e.getX() < downX ? 1 : 2)) % 3;
                else page = (page + 1) % 3;
                invalidate(); handler.removeCallbacks(rotate); handler.postDelayed(rotate, 4200); return true;
            }
            return true;
        });
        handler.postDelayed(rotate, 4200);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float r = 22 * d;

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, w, h, Color.rgb(14, 15, 22),
                Color.rgb(30, 31, 44), Shader.TileMode.CLAMP));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, p);
        p.setShader(null);

        // faint board corner and shot path
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(Color.argb(90, 150, 140, 220));
        path.reset();
        path.moveTo(w * 0.60f, h * 0.88f);
        path.quadTo(w * 0.66f, h * 0.30f, w * 0.86f, h * 0.12f);
        c.drawPath(path, p);
        p.setColor(Color.argb(70, 150, 140, 220));
        c.drawCircle(w * 0.90f, h * 0.10f, 14 * d, p);

        // coins
        p.setStyle(Paint.Style.FILL);
        float[][] coins = {{0.80f, 0.46f, 1}, {0.86f, 0.46f, 0}, {0.92f, 0.46f, 1},
                {0.77f, 0.58f, 0}, {0.83f, 0.58f, 1}, {0.89f, 0.58f, 0}, {0.95f, 0.58f, 1},
                {0.80f, 0.70f, 1}, {0.86f, 0.70f, 0}, {0.92f, 0.70f, 1}};
        for (float[] k : coins) {
            p.setColor(k[2] == 1 ? Color.rgb(205, 205, 215) : Color.rgb(55, 55, 70));
            c.drawCircle(w * k[0], h * k[1], 9 * d, p);
        }
        p.setColor(Color.rgb(226, 214, 190));
        c.drawCircle(w * 0.60f, h * 0.88f, 12 * d, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(Color.rgb(160, 150, 230));
        p.setTextSize(10 * d);
        p.setLetterSpacing(0.18f);
        String[] eyebrow = {"AIM.AI ASSISTANT", "STAY IN FLOW", "BUILT FOR CARROM"};
        String[] headline = {"See the shot.", "Stay in flow.", "Play with focus."};
        String[] subline = {"Plan a smarter line.", "Keep your rhythm going.", "Your game, your pace."};
        c.drawText(eyebrow[page], 18 * d, 32 * d, p);
        p.setLetterSpacing(0f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.WHITE);
        p.setTextSize(27 * d);
        c.drawText(headline[page], 18 * d, 74 * d, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(13 * d); p.setColor(Color.rgb(205, 202, 220));
        c.drawText(subline[page], 18 * d, 98 * d, p);
        p.setTypeface(Typeface.DEFAULT);

        for (int i = 0; i < 3; i++) {
            p.setColor(i == page ? Color.rgb(207, 116, 255) : Color.argb(120, 200, 200, 230));
            c.drawCircle(w / 2f + (i - 1) * 12 * d, h - 12 * d, 2 * d, p);
        }
    }
}
