package com.aimai.max.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Tiny carrom-board picture used as the game icon. */
public class BoardIconView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BoardIconView(Context c) {
        super(c);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) * 0.22f;
        p.setShader(new LinearGradient(0, 0, w, h, Color.rgb(222, 150, 96),
                Color.rgb(150, 86, 44), Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(0, 0, w, h, r, r, p);
        p.setShader(null);
        p.setColor(Color.rgb(246, 214, 170));
        float in = Math.min(w, h) * 0.14f;
        c.drawRoundRect(in, in, w - in, h - in, r * 0.6f, r * 0.6f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.rgb(120, 70, 40));
        p.setStrokeWidth(Math.max(1f, w * 0.02f));
        c.drawCircle(w / 2f, h / 2f, w * 0.20f, p);
        p.setStyle(Paint.Style.FILL);
        float cr = w * 0.045f;
        float[][] dots = {{0, 0, 0}, {0.1f, 0, 1}, {-0.1f, 0, 1}, {0, 0.1f, 1}, {0, -0.1f, 1},
                {0.07f, 0.07f, 0}, {-0.07f, -0.07f, 0}, {0.07f, -0.07f, 0}, {-0.07f, 0.07f, 0}};
        for (float[] d : dots) {
            p.setColor(d[2] == 1 ? Color.rgb(250, 250, 250) : Color.rgb(35, 28, 26));
            c.drawCircle(w / 2f + d[0] * w, h / 2f + d[1] * h, cr, p);
        }
        p.setColor(Color.rgb(35, 28, 26));
        float pr = w * 0.06f;
        c.drawCircle(in + pr, in + pr, pr, p);
        c.drawCircle(w - in - pr, in + pr, pr, p);
        c.drawCircle(in + pr, h - in - pr, pr, p);
        c.drawCircle(w - in - pr, h - in - pr, pr, p);
    }
}
