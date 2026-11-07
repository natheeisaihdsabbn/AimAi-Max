package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;
import com.aimai.max.assistant.CarromBoardAnalyzer.Piece;
import com.aimai.max.assistant.CarromBoardAnalyzer.PieceType;
import com.aimai.max.assistant.CarromBoardAnalyzer.Pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class CarromShotPlanner {

    private CarromShotPlanner() {}

    public static class Shot {
        public boolean valid;
        public Piece target;
        public Pocket pocket;
        /** Where the striker must stand on the baseline for this shot (screen pixels). */
        public float strikerX;
        public float strikerY;
        public float ghostX;
        public float ghostY;
        public float contactX;
        public float contactY;
        public float cutAngleDeg;
        public float afterDx;
        public float afterDy;
        /** Planner cost in pixels (lower is better). Not a power value. */
        public float score;
        /** 0..1 shot strength derived from the travel distance. */
        public float power;
        /** How many other coins sit on the striker path or on the coin's path to the pocket. */
        public int blockers;
        /** True when the shot uses a bounce off a cushion (striker or coin). */
        public boolean bank;
        /** The point the finger must aim at: the ghost position, or the cushion point for a striker bank. */
        public float aimX;
        public float aimY;
        /** Striker route as x,y pairs: start, [cushion point], ghost. */
        public float[] strikerPath;
        /** Coin route as x,y pairs: coin, [cushion point], pocket. */
        public float[] coinPath;
        /** Other good coin to pocket routes, 4 numbers each: coinX, coinY, pocketX, pocketY. */
        public final List<float[]> alts = new ArrayList<>();
    }

    /** Best shot from the place where the striker stands right now. */
    public static Shot plan(Analysis a, PieceType wanted) {
        if (a == null || a.striker == null || !a.board.detected) {
            return new Shot();
        }
        return planFrom(a, wanted, a.striker.x, a.striker.y);
    }

    /**
     * Tries many places along the striker baseline (the striker can slide left and right) and keeps
     * the cheapest shot. Places where a coin already sits are skipped.
     */
    public static Shot planBest(Analysis a, PieceType wanted) {
        Shot best = plan(a, wanted);
        if (a == null || a.striker == null || !a.board.detected) {
            return best;
        }
        Piece s = a.striker;
        float bw = a.board.width();
        float minX = a.board.left + 0.2265f * bw;
        float maxX = a.board.left + 0.772f * bw;
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            float x = minX + (maxX - minX) * i / steps;
            boolean free = true;
            for (Piece o : a.pieces) {
                if (o == s) {
                    continue;
                }
                float dx = o.x - x;
                float dy = o.y - s.y;
                if (Math.sqrt(dx * dx + dy * dy) < o.radius + s.radius) {
                    free = false;
                    break;
                }
            }
            if (!free) {
                continue;
            }
            Shot c = planFrom(a, wanted, x, s.y);
            if (c.valid && (!best.valid || c.score < best.score)) {
                best = c;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- planning

    /**
     * Looks at every wanted coin and every pocket and tries three kinds of shot: a straight shot,
     * a shot where the coin bounces off a cushion on its way to the pocket, and a shot where the striker
     * bounces off a cushion before it hits the coin. The cheapest shot that fits wins. If nothing fits,
     * the nearest wanted coin is aimed at directly.
     */
    private static Shot planFrom(Analysis a, PieceType wanted, float stX, float stY) {
        Shot best = new Shot();
        if (a == null || a.striker == null || !a.board.detected) {
            return best;
        }

        Piece real = a.striker;
        float sr = real.radius;
        float bw = a.board.width();
        float[] walls = wallEdges(a);
        List<Shot> directs = new ArrayList<>();

        for (Piece t : a.pieces) {
            if (!isWanted(t, real, wanted)) {
                continue;
            }
            for (Pocket p : a.pockets) {
                Shot d = direct(a, real, t, p, stX, stY, sr, bw);
                if (d != null) {
                    directs.add(d);
                    best = cheaper(best, d);
                }
                for (int w = 0; w < 4; w++) {
                    best = cheaper(best, coinBank(a, real, t, p, w, walls, stX, stY, sr, bw));
                }
                for (int w = 0; w < 3; w++) {
                    best = cheaper(best, strikerBank(a, real, t, p, w, walls, stX, stY, sr, bw));
                }
            }
        }

        if (!best.valid) {
            aimNearest(best, a, stX, stY, sr, wanted, bw);
            return best;
        }

        Collections.sort(directs, new Comparator<Shot>() {
            @Override
            public int compare(Shot x, Shot y) {
                return Float.compare(x.score, y.score);
            }
        });
        for (Shot d : directs) {
            if (best.alts.size() >= 4) {
                break;
            }
            if (d.target == best.target && d.pocket == best.pocket) {
                continue;
            }
            best.alts.add(new float[]{d.target.x, d.target.y, d.pocket.x, d.pocket.y});
        }
        return best;
    }

    private static Shot cheaper(Shot best, Shot c) {
        if (c != null && c.valid && (!best.valid || c.score < best.score)) {
            return c;
        }
        return best;
    }

    private static boolean isWanted(Piece t, Piece real, PieceType wanted) {
        if (t == real || t.type == PieceType.STRIKER || t.type == PieceType.UNKNOWN) {
            return false;
        }
        return wanted == null || t.type == wanted || t.type == PieceType.QUEEN;
    }

    /** Inner edges of the four cushions: left, right, top, bottom, and the average pocket radius. */
    private static float[] wallEdges(Analysis a) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float pr = 0f;
        int n = 0;
        for (Pocket p : a.pockets) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
            pr += p.radius;
            n++;
        }
        float bw = a.board.width();
        if (n < 4) {
            float inset = 0.035f * bw;
            return new float[]{a.board.left + inset, a.board.right - inset,
                    a.board.top + inset, a.board.bottom - inset, 0.045f * bw};
        }
        pr /= n;
        return new float[]{minX - pr, maxX + pr, minY - pr, maxY + pr, pr};
    }

    private static boolean nearPocket(Analysis a, float x, float y, float r) {
        for (Pocket p : a.pockets) {
            float dx = p.x - x;
            float dy = p.y - y;
            if (Math.sqrt(dx * dx + dy * dy) < r) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideBoard(Analysis a, float gx, float gy, float r) {
        return !(gx < a.board.left + r || gx > a.board.right - r
                || gy < a.board.top + r || gy > a.board.bottom - r);
    }

    /** How many coins (other than the striker and the ignored one) touch the moving disc's path. */
    private static int blockSeg(Analysis a, Piece real, Piece ignore,
                                float ax, float ay, float bx, float by, float rad) {
        int n = 0;
        for (Piece o : a.pieces) {
            if (o == real || o == ignore) {
                continue;
            }
            if (segDist(o.x, o.y, ax, ay, bx, by) < o.radius + rad) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ shot kinds

    private static Shot direct(Analysis a, Piece real, Piece t, Pocket p,
                               float stX, float stY, float sr, float bw) {
        float tx = p.x - t.x;
        float ty = p.y - t.y;
        float tLen = (float) Math.sqrt(tx * tx + ty * ty);
        if (tLen < 1f) {
            return null;
        }
        float ux = tx / tLen;
        float uy = ty / tLen;

        float gx = t.x - ux * (t.radius + sr);
        float gy = t.y - uy * (t.radius + sr);
        if (!insideBoard(a, gx, gy, sr)) {
            return null;
        }

        float sx = gx - stX;
        float sy = gy - stY;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy);
        if (sLen < 1f) {
            return null;
        }
        float vx = sx / sLen;
        float vy = sy / sLen;
        float cos = vx * ux + vy * uy;
        if (cos < 0.2588f) {
            return null;
        }

        int blockers = blockSeg(a, real, t, stX, stY, gx, gy, sr * 0.95f)
                + blockSeg(a, real, t, t.x, t.y, p.x, p.y, t.radius * 0.95f);
        float score = sLen + tLen * 1.5f + (1f - cos) * bw * 1.5f + blockers * bw;

        Shot s = new Shot();
        fill(s, sr, t, p, gx, gy, ux, uy, vx, vy, cos, sLen, tLen, bw);
        s.strikerX = stX;
        s.strikerY = stY;
        s.score = score;
        s.blockers = blockers;
        s.aimX = gx;
        s.aimY = gy;
        s.strikerPath = new float[]{stX, stY, gx, gy};
        s.coinPath = new float[]{t.x, t.y, p.x, p.y};
        return s;
    }

    /** The coin bounces off one cushion (0 left, 1 right, 2 top, 3 bottom) and then drops in the pocket. */
    private static Shot coinBank(Analysis a, Piece real, Piece t, Pocket p, int wall, float[] walls,
                                 float stX, float stY, float sr, float bw) {
        float c;
        float rx;
        float ry;
        if (wall == 0) {
            c = walls[0] + t.radius;
            rx = 2f * c - p.x;
            ry = p.y;
        } else if (wall == 1) {
            c = walls[1] - t.radius;
            rx = 2f * c - p.x;
            ry = p.y;
        } else if (wall == 2) {
            c = walls[2] + t.radius;
            rx = p.x;
            ry = 2f * c - p.y;
        } else {
            c = walls[3] - t.radius;
            rx = p.x;
            ry = 2f * c - p.y;
        }
        float dx = rx - t.x;
        float dy = ry - t.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) {
            return null;
        }
        float k;
        if (wall < 2) {
            if (Math.abs(dx) < 1e-3f) {
                return null;
            }
            k = (c - t.x) / dx;
        } else {
            if (Math.abs(dy) < 1e-3f) {
                return null;
            }
            k = (c - t.y) / dy;
        }
        if (k <= 0.02f || k >= 0.98f) {
            return null;
        }
        float bx = t.x + dx * k;
        float by = t.y + dy * k;
        if (!onWall(wall, bx, by, walls) || nearPocket(a, bx, by, walls[4] * 1.8f)) {
            return null;
        }

        float ux = dx / len;
        float uy = dy / len;
        float gx = t.x - ux * (t.radius + sr);
        float gy = t.y - uy * (t.radius + sr);
        if (!insideBoard(a, gx, gy, sr)) {
            return null;
        }
        float sx = gx - stX;
        float sy = gy - stY;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy);
        if (sLen < 1f) {
            return null;
        }
        float vx = sx / sLen;
        float vy = sy / sLen;
        float cos = vx * ux + vy * uy;
        if (cos < 0.2588f) {
            return null;
        }

        float leg1 = (float) Math.sqrt((bx - t.x) * (bx - t.x) + (by - t.y) * (by - t.y));
        float leg2 = (float) Math.sqrt((p.x - bx) * (p.x - bx) + (p.y - by) * (p.y - by));
        float tLen = leg1 + leg2;
        int blockers = blockSeg(a, real, t, stX, stY, gx, gy, sr * 0.95f)
                + blockSeg(a, real, t, t.x, t.y, bx, by, t.radius * 0.95f)
                + blockSeg(a, real, t, bx, by, p.x, p.y, t.radius * 0.95f);
        float score = sLen + tLen * 1.5f + (1f - cos) * bw * 1.5f + blockers * bw + 0.35f * bw;

        Shot s = new Shot();
        fill(s, sr, t, p, gx, gy, ux, uy, vx, vy, cos, sLen, tLen, bw);
        s.power = Math.min(1f, s.power + 0.1f);
        s.strikerX = stX;
        s.strikerY = stY;
        s.score = score;
        s.blockers = blockers;
        s.bank = true;
        s.aimX = gx;
        s.aimY = gy;
        s.strikerPath = new float[]{stX, stY, gx, gy};
        s.coinPath = new float[]{t.x, t.y, bx, by, p.x, p.y};
        return s;
    }

    /** The striker bounces off one cushion (0 left, 1 right, 2 top) and then hits the coin towards the pocket. */
    private static Shot strikerBank(Analysis a, Piece real, Piece t, Pocket p, int wall, float[] walls,
                                    float stX, float stY, float sr, float bw) {
        float tx = p.x - t.x;
        float ty = p.y - t.y;
        float tLen = (float) Math.sqrt(tx * tx + ty * ty);
        if (tLen < 1f) {
            return null;
        }
        float ux = tx / tLen;
        float uy = ty / tLen;
        float gx = t.x - ux * (t.radius + sr);
        float gy = t.y - uy * (t.radius + sr);
        if (!insideBoard(a, gx, gy, sr)) {
            return null;
        }

        float c;
        float rx;
        float ry;
        if (wall == 0) {
            c = walls[0] + sr;
            rx = 2f * c - gx;
            ry = gy;
        } else if (wall == 1) {
            c = walls[1] - sr;
            rx = 2f * c - gx;
            ry = gy;
        } else {
            c = walls[2] + sr;
            rx = gx;
            ry = 2f * c - gy;
        }
        float dx = rx - stX;
        float dy = ry - stY;
        float k;
        if (wall < 2) {
            if (Math.abs(dx) < 1e-3f) {
                return null;
            }
            k = (c - stX) / dx;
        } else {
            if (Math.abs(dy) < 1e-3f) {
                return null;
            }
            k = (c - stY) / dy;
        }
        if (k <= 0.02f || k >= 0.98f) {
            return null;
        }
        float bx = stX + dx * k;
        float by = stY + dy * k;
        if (!onWall(wall, bx, by, walls) || nearPocket(a, bx, by, walls[4] * 1.8f)) {
            return null;
        }

        float ex = gx - bx;
        float ey = gy - by;
        float eLen = (float) Math.sqrt(ex * ex + ey * ey);
        if (eLen < 1f) {
            return null;
        }
        float vx = ex / eLen;
        float vy = ey / eLen;
        float cos = vx * ux + vy * uy;
        if (cos < 0.2588f) {
            return null;
        }

        float sLen = (float) Math.sqrt((bx - stX) * (bx - stX) + (by - stY) * (by - stY)) + eLen;
        int blockers = blockSeg(a, real, t, stX, stY, bx, by, sr * 0.95f)
                + blockSeg(a, real, t, bx, by, gx, gy, sr * 0.95f)
                + blockSeg(a, real, t, t.x, t.y, p.x, p.y, t.radius * 0.95f);
        float score = sLen + tLen * 1.5f + (1f - cos) * bw * 1.5f + blockers * bw + 0.35f * bw;

        Shot s = new Shot();
        fill(s, sr, t, p, gx, gy, ux, uy, vx, vy, cos, sLen, tLen, bw);
        s.power = Math.min(1f, s.power + 0.1f);
        s.strikerX = stX;
        s.strikerY = stY;
        s.score = score;
        s.blockers = blockers;
        s.bank = true;
        s.aimX = bx;
        s.aimY = by;
        s.strikerPath = new float[]{stX, stY, bx, by, gx, gy};
        s.coinPath = new float[]{t.x, t.y, p.x, p.y};
        return s;
    }

    /** Is the cushion point inside the stretch of that cushion (not beyond the corners)? */
    private static boolean onWall(int wall, float bx, float by, float[] walls) {
        if (wall < 2) {
            return by > walls[2] && by < walls[3];
        }
        return bx > walls[0] && bx < walls[1];
    }

    private static void fill(Shot best, float sr, Piece t, Pocket p, float gx, float gy,
                             float ux, float uy, float vx, float vy, float cos,
                             float sLen, float tLen, float boardW) {
        best.valid = true;
        best.target = t;
        best.pocket = p;
        best.ghostX = gx;
        best.ghostY = gy;
        best.contactX = gx + ux * sr;
        best.contactY = gy + uy * sr;
        float c = Math.max(-1f, Math.min(1f, cos));
        best.cutAngleDeg = (float) Math.toDegrees(Math.acos(c));
        float rx = vx - c * ux;
        float ry = vy - c * uy;
        float rLen = (float) Math.sqrt(rx * rx + ry * ry);
        if (rLen > 1e-4f) {
            best.afterDx = rx / rLen;
            best.afterDy = ry / rLen;
        } else {
            best.afterDx = 0f;
            best.afterDy = 0f;
        }
        float travel = (sLen + tLen) / Math.max(1f, boardW * 1.6f);
        float cutPenalty = best.cutAngleDeg / 75f * 0.15f;
        best.power = Math.max(0.3f, Math.min(1f, 0.3f + 0.7f * travel + cutPenalty));
    }

    /** Last resort: hit the nearest wanted coin head-on and let it go towards its closest pocket. */
    private static void aimNearest(Shot best, Analysis a, float stX, float stY, float sr,
                                   PieceType wanted, float boardW) {
        Piece real = a.striker;
        Piece near = null;
        float nearD = Float.MAX_VALUE;
        for (Piece t : a.pieces) {
            if (!isWanted(t, real, wanted)) {
                continue;
            }
            float dx = t.x - stX, dy = t.y - stY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < nearD && d > 1f) {
                nearD = d;
                near = t;
            }
        }
        if (near == null) {
            return;
        }
        Pocket pk = null;
        float pd = Float.MAX_VALUE;
        for (Pocket p : a.pockets) {
            float dx = p.x - near.x, dy = p.y - near.y;
            float d = dx * dx + dy * dy;
            if (d < pd) {
                pd = d;
                pk = p;
            }
        }
        if (pk == null) {
            return;
        }
        float vx = (near.x - stX) / nearD;
        float vy = (near.y - stY) / nearD;
        float gx = near.x - vx * (near.radius + sr);
        float gy = near.y - vy * (near.radius + sr);
        float ux = pk.x - near.x, uy = pk.y - near.y;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy);
        if (uLen < 1f) {
            return;
        }
        ux /= uLen;
        uy /= uLen;
        float cos = vx * ux + vy * uy;
        fill(best, sr, near, pk, gx, gy, ux, uy, vx, vy, cos, nearD, uLen, boardW);
        best.strikerX = stX;
        best.strikerY = stY;
        // Always worse than any real planned shot, so sliding never prefers a head-on fallback.
        best.score = nearD + boardW * 100f;
        best.blockers = 99;
        best.aimX = gx;
        best.aimY = gy;
        best.strikerPath = new float[]{stX, stY, gx, gy};
        best.coinPath = new float[]{near.x, near.y, pk.x, pk.y};
    }

    private static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 1e-6f ? 0f : ((px - ax) * abx + (py - ay) * aby) / len2;
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        float cx = ax + abx * t;
        float cy = ay + aby * t;
        float dx = px - cx;
        float dy = py - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
