package com.aimai.max.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small line icons drawn on a 24x24 grid, so no image assets are needed. */
public class IconView extends View {
    public static final int HOME = 0, CROWN = 1, TROPHY = 2, GEAR = 3, CHAT = 4,
            INFO = 5, CROSSHAIR = 6, PLAY = 7, SLIDERS = 8, SHARE = 9, TRASH = 10,
            DOC = 11, CHIP = 12, ACCESS = 13, LAYERS = 14;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int color = Color.GRAY;

    public IconView(Context c, int type) {
        super(c);
        this.type = type;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        float u = s / 24f;
        canvas.save();
        canvas.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        canvas.scale(u, u);
        p.setColor(color);
        p.setStrokeWidth(1.7f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (type) {
            case HOME:
                path.moveTo(3, 11); path.lineTo(12, 3); path.lineTo(21, 11);
                path.moveTo(5, 10); path.lineTo(5, 20); path.lineTo(19, 20); path.lineTo(19, 10);
                path.moveTo(10, 20); path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 20);
                canvas.drawPath(path, p);
                break;
            case CROWN:
                path.moveTo(3, 18); path.lineTo(5, 8); path.lineTo(9, 13); path.lineTo(12, 6);
                path.lineTo(15, 13); path.lineTo(19, 8); path.lineTo(21, 18); path.close();
                path.moveTo(4, 21); path.lineTo(20, 21);
                canvas.drawPath(path, p);
                break;
            case TROPHY:
                path.moveTo(7, 4); path.lineTo(17, 4); path.lineTo(17, 9);
                path.quadTo(17, 15, 12, 15); path.quadTo(7, 15, 7, 9); path.close();
                path.moveTo(7, 6); path.lineTo(4, 6); path.lineTo(4, 8); path.quadTo(4, 11, 7, 11);
                path.moveTo(17, 6); path.lineTo(20, 6); path.lineTo(20, 8); path.quadTo(20, 11, 17, 11);
                path.moveTo(12, 15); path.lineTo(12, 19); path.moveTo(8, 20.5f); path.lineTo(16, 20.5f);
                canvas.drawPath(path, p);
                break;
            case GEAR:
                canvas.drawCircle(12, 12, 3f, p);
                canvas.drawCircle(12, 12, 6.2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    canvas.drawLine(12 + cs * 6.2f, 12 + sn * 6.2f, 12 + cs * 9.2f, 12 + sn * 9.2f, p);
                }
                break;
            case CHAT:
                path.moveTo(4, 5); path.lineTo(20, 5); path.lineTo(20, 16); path.lineTo(11, 16);
                path.lineTo(7, 20); path.lineTo(7, 16); path.lineTo(4, 16); path.close();
                path.moveTo(8, 9); path.lineTo(16, 9); path.moveTo(8, 12); path.lineTo(13, 12);
                canvas.drawPath(path, p);
                break;
            case INFO:
                canvas.drawCircle(12, 12, 9, p);
                canvas.drawLine(12, 11, 12, 16.5f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 7.8f, 1.1f, p);
                break;
            case CROSSHAIR:
                canvas.drawCircle(12, 12, 6, p);
                canvas.drawLine(12, 2, 12, 7, p); canvas.drawLine(12, 17, 12, 22, p);
                canvas.drawLine(2, 12, 7, 12, p); canvas.drawLine(17, 12, 22, 12, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 12, 1.3f, p);
                break;
            case PLAY:
                canvas.drawCircle(12, 12, 9, p);
                path.moveTo(10, 8); path.lineTo(16, 12); path.lineTo(10, 16); path.close();
                canvas.drawPath(path, p);
                break;
            case SLIDERS:
                canvas.drawLine(3, 7, 21, 7, p); canvas.drawLine(3, 12, 21, 12, p);
                canvas.drawLine(3, 17, 21, 17, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(255, 12, 14, 20));
                canvas.drawCircle(8, 7, 2.2f, p); canvas.drawCircle(16, 12, 2.2f, p);
                canvas.drawCircle(9, 17, 2.2f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(color);
                canvas.drawCircle(8, 7, 2.2f, p); canvas.drawCircle(16, 12, 2.2f, p);
                canvas.drawCircle(9, 17, 2.2f, p);
                break;
            case SHARE:
                canvas.drawCircle(6, 12, 2.4f, p); canvas.drawCircle(18, 6, 2.4f, p);
                canvas.drawCircle(18, 18, 2.4f, p);
                canvas.drawLine(8.2f, 10.8f, 15.8f, 7.2f, p);
                canvas.drawLine(8.2f, 13.2f, 15.8f, 16.8f, p);
                break;
            case TRASH:
                path.moveTo(4, 7); path.lineTo(20, 7); path.moveTo(9, 7); path.lineTo(9, 4);
                path.lineTo(15, 4); path.lineTo(15, 7);
                path.moveTo(6, 7); path.lineTo(7, 20); path.lineTo(17, 20); path.lineTo(18, 7);
                path.moveTo(10, 11); path.lineTo(10, 16); path.moveTo(14, 11); path.lineTo(14, 16);
                canvas.drawPath(path, p);
                break;
            case DOC:
                path.moveTo(6, 3); path.lineTo(14, 3); path.lineTo(19, 8); path.lineTo(19, 21);
                path.lineTo(6, 21); path.close();
                path.moveTo(14, 3); path.lineTo(14, 8); path.lineTo(19, 8);
                path.moveTo(9, 13); path.lineTo(16, 13); path.moveTo(9, 17); path.lineTo(16, 17);
                canvas.drawPath(path, p);
                break;
            case CHIP:
                canvas.drawRect(7, 7, 17, 17, p);
                canvas.drawRect(10, 10, 14, 14, p);
                for (int i = 0; i < 3; i++) {
                    float v = 9.5f + i * 2.5f;
                    canvas.drawLine(v, 3.5f, v, 7, p); canvas.drawLine(v, 17, v, 20.5f, p);
                    canvas.drawLine(3.5f, v, 7, v, p); canvas.drawLine(17, v, 20.5f, v, p);
                }
                break;
            case ACCESS:
                canvas.drawCircle(12, 5, 2, p);
                canvas.drawLine(4, 9, 20, 9, p);
                canvas.drawLine(12, 9, 12, 15, p);
                canvas.drawLine(12, 15, 8, 21, p);
                canvas.drawLine(12, 15, 16, 21, p);
                break;
            case LAYERS:
                path.moveTo(12, 3); path.lineTo(21, 8); path.lineTo(12, 13); path.lineTo(3, 8); path.close();
                path.moveTo(3, 12.5f); path.lineTo(12, 17.5f); path.lineTo(21, 12.5f);
                canvas.drawPath(path, p);
                break;
            default:
                break;
        }
        canvas.restore();
    }
}
