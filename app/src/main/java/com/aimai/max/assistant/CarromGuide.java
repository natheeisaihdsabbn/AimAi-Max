package com.aimai.max.assistant;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;

public final class CarromGuide {

    public boolean valid;
    public boolean hasAfter;
    public float strikerX;
    public float strikerY;
    public float strikerR;
    public float ghostX;
    public float ghostY;
    public float targetX;
    public float targetY;
    public float targetR;
    public float pocketX;
    public float pocketY;
    public float pocketR;
    public float afterX;
    public float afterY;

    public static CarromGuide build(Analysis a, CarromShotPlanner.Shot shot) {
        CarromGuide g = new CarromGuide();
        if (a == null || shot == null || !shot.valid || a.striker == null
                || shot.target == null || shot.pocket == null) {
            return g;
        }
        g.valid = true;
        g.strikerX = a.striker.x;
        g.strikerY = a.striker.y;
        g.strikerR = a.striker.radius;
        g.ghostX = shot.ghostX;
        g.ghostY = shot.ghostY;
        g.targetX = shot.target.x;
        g.targetY = shot.target.y;
        g.targetR = shot.target.radius;
        g.pocketX = shot.pocket.x;
        g.pocketY = shot.pocket.y;
        g.pocketR = shot.pocket.radius;

        g.hasAfter = shot.afterDx != 0f || shot.afterDy != 0f;
        float len = a.board.width() * 0.22f;
        g.afterX = shot.ghostX + shot.afterDx * len;
        g.afterY = shot.ghostY + shot.afterDy * len;
        return g;
    }

    public void draw(Canvas canvas, int color, float thickness) {
        if (!valid || canvas == null) {
            return;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(thickness);

        p.setColor(color);
        canvas.drawLine(strikerX, strikerY, ghostX, ghostY, p);
        canvas.drawCircle(ghostX, ghostY, strikerR, p);

        p.setColor(Color.rgb(255, 210, 60));
        canvas.drawLine(targetX, targetY, pocketX, pocketY, p);
        canvas.drawCircle(targetX, targetY, targetR, p);

        p.setColor(Color.rgb(80, 255, 120));
        canvas.drawCircle(pocketX, pocketY, pocketR, p);

        if (hasAfter) {
            p.setColor(Color.argb(200, 255, 255, 255));
            p.setPathEffect(new DashPathEffect(new float[]{18f, 14f}, 0f));
            canvas.drawLine(ghostX, ghostY, afterX, afterY, p);
        }
    }
}
