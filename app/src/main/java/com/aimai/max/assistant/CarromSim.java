package com.aimai.max.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * Small carrom physics: discs collide with each other and bounce off the four walls, slow down by
 * friction and drop into a pocket when their centre gets close enough. It is used to try a shot on
 * the phone before it is played, so hard shots (several coins, wall bounces) can be found.
 * Units: pixels and "steps". The numbers are a close approximation of a real board, not exact.
 */
public final class CarromSim {

    private CarromSim() {}

    public static final int STRIKER = 0, MINE = 1, OTHER = 2, QUEEN = 3;

    public static final class Disc {
        public float x, y, vx, vy, r, mass;
        public int owner;
        public boolean potted;
        public float startX, startY;
        public int pathCount;
        public float[] path = new float[PATH_MAX * 2];
    }

    public static final class Result {
        public int minePotted, otherPotted, queenPotted;
        public boolean strikerPotted;
        public float strikerMinPocket = 1e9f;
        public List<Disc> discs;
        public int steps;
    }

    private static final int SUB = 3;
    private static final float DECEL = 0.18f;          // speed lost per step
    private static final float REST_DISC = 0.90f;      // disc against disc
    private static final float REST_WALL = 0.74f;      // disc against wall
    private static final float STOP_SPEED = 0.15f;
    private static final int PATH_MAX = 72;
    private static final int MAX_STEPS = 260;

    public static Disc copy(Disc d) {
        Disc c = new Disc();
        c.x = d.x; c.y = d.y; c.vx = d.vx; c.vy = d.vy; c.r = d.r; c.mass = d.mass;
        c.owner = d.owner; c.potted = d.potted; c.startX = d.x; c.startY = d.y;
        return c;
    }

    /**
     * Plays one shot. discs[strikerIndex] gets the velocity (dirX,dirY unit vector) * speed.
     * The discs passed in are not changed: the result holds the final copies.
     */
    public static Result run(List<Disc> input, int strikerIndex, float dirX, float dirY, float speed,
                             float left, float top, float right, float bottom,
                             float[] pocketX, float[] pocketY, float pocketR, boolean record) {
        int n = input.size();
        Disc[] d = new Disc[n];
        for (int i = 0; i < n; i++) d[i] = copy(input.get(i));
        d[strikerIndex].vx = dirX * speed;
        d[strikerIndex].vy = dirY * speed;

        float dt = 1f / SUB;
        float decelSub = DECEL / SUB;
        float potR2 = (pocketR * 0.92f) * (pocketR * 0.92f);
        float strikerMinD2 = 1e18f;
        int steps = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            boolean moving = false;
            for (int sub = 0; sub < SUB; sub++) {
                for (int i = 0; i < n; i++) {
                    Disc a = d[i];
                    if (a.potted) continue;
                    float sp = (float) Math.sqrt(a.vx * a.vx + a.vy * a.vy);
                    if (sp > STOP_SPEED) {
                        a.x += a.vx * dt;
                        a.y += a.vy * dt;
                        float ns = Math.max(0f, sp - decelSub);
                        float k = ns / sp;
                        a.vx *= k;
                        a.vy *= k;
                        moving = true;
                    } else {
                        a.vx = 0f;
                        a.vy = 0f;
                    }
                    // walls
                    if (a.x - a.r < left) { a.x = left + a.r; a.vx = Math.abs(a.vx) * REST_WALL; }
                    else if (a.x + a.r > right) { a.x = right - a.r; a.vx = -Math.abs(a.vx) * REST_WALL; }
                    if (a.y - a.r < top) { a.y = top + a.r; a.vy = Math.abs(a.vy) * REST_WALL; }
                    else if (a.y + a.r > bottom) { a.y = bottom - a.r; a.vy = -Math.abs(a.vy) * REST_WALL; }
                    // pockets
                    for (int p = 0; p < pocketX.length; p++) {
                        float dx = a.x - pocketX[p], dy = a.y - pocketY[p];
                        if (a.owner == STRIKER && dx * dx + dy * dy < strikerMinD2) strikerMinD2 = dx * dx + dy * dy;
                        if (dx * dx + dy * dy < potR2) {
                            a.potted = true;
                            a.vx = 0f;
                            a.vy = 0f;
                            if (a.owner == MINE) {
                                // counted once at the end
                            }
                            break;
                        }
                    }
                }
                // disc against disc
                for (int i = 0; i < n; i++) {
                    Disc a = d[i];
                    if (a.potted) continue;
                    for (int j = i + 1; j < n; j++) {
                        Disc b = d[j];
                        if (b.potted) continue;
                        float dx = b.x - a.x, dy = b.y - a.y;
                        float rr = a.r + b.r;
                        float dist2 = dx * dx + dy * dy;
                        if (dist2 >= rr * rr || dist2 < 1e-6f) continue;
                        float dist = (float) Math.sqrt(dist2);
                        float nx = dx / dist, ny = dy / dist;
                        float u = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
                        float invA = 1f / a.mass, invB = 1f / b.mass;
                        if (u < 0f) {
                            float jimp = -(1f + REST_DISC) * u / (invA + invB);
                            a.vx -= jimp * invA * nx;
                            a.vy -= jimp * invA * ny;
                            b.vx += jimp * invB * nx;
                            b.vy += jimp * invB * ny;
                        }
                        float overlap = rr - dist;
                        float push = overlap / (invA + invB);
                        a.x -= nx * push * invA;
                        a.y -= ny * push * invA;
                        b.x += nx * push * invB;
                        b.y += ny * push * invB;
                    }
                }
            }
            steps = step + 1;
            if (record && (step % 3 == 0)) {
                for (int i = 0; i < n; i++) {
                    Disc a = d[i];
                    if (a.pathCount < PATH_MAX) {
                        if (a.pathCount == 0) {
                            a.path[0] = a.startX;
                            a.path[1] = a.startY;
                            a.pathCount = 1;
                        }
                        float lastX = a.path[(a.pathCount - 1) * 2], lastY = a.path[(a.pathCount - 1) * 2 + 1];
                        float ddx = a.x - lastX, ddy = a.y - lastY;
                        if (ddx * ddx + ddy * ddy > 9f && a.pathCount < PATH_MAX) {
                            a.path[a.pathCount * 2] = a.x;
                            a.path[a.pathCount * 2 + 1] = a.y;
                            a.pathCount++;
                        }
                    }
                }
            }
            if (!moving) break;
        }

        Result res = new Result();
        res.steps = steps;
        res.strikerMinPocket = (float) Math.sqrt(strikerMinD2);
        res.discs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Disc a = d[i];
            res.discs.add(a);
            if (!a.potted) continue;
            if (a.owner == STRIKER) res.strikerPotted = true;
            else if (a.owner == MINE) res.minePotted++;
            else if (a.owner == QUEEN) res.queenPotted++;
            else res.otherPotted++;
        }
        return res;
    }
}
