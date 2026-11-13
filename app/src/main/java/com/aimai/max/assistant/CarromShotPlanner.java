package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;
import com.aimai.max.assistant.CarromBoardAnalyzer.Piece;
import com.aimai.max.assistant.CarromBoardAnalyzer.PieceType;
import com.aimai.max.assistant.CarromBoardAnalyzer.Pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shot planner. For every striker position on the baseline (the striker can be slid with the slider),
 * every wanted coin and every pocket it scores three kinds of shot:
 *   0 = direct: striker hits the coin, the coin goes to the pocket;
 *   1 = coin bank: the coin first bounces off a wall, then goes to the pocket;
 *   2 = striker bank: the striker bounces off a wall, then hits the coin.
 * Coins in the way cost extra, so the cleanest shot wins, but a blocked shot is still allowed
 * when nothing better exists.
 */
public final class CarromShotPlanner {

    private CarromShotPlanner() {}

    /** A shot needs at least this many striker radii between the striker and the point it aims at.
     *  Shorter than that, one or two pixels of error in the striker position turn the swipe by many degrees. */
    private static final float MIN_SHOT_RS = 3f;

    public static class Shot {
        public boolean valid;
        public int kind;
        public Piece target;
        public Pocket pocket;
        /** Where the striker stands on the baseline for this shot. */
        public float strikerX;
        public float strikerY;
        /** The point the finger must aim at (the ghost striker, or the wall for a striker bank). */
        public float aimX;
        public float aimY;
        public float ghostX;
        public float ghostY;
        /** Wall point of a bank shot, -1 when there is none. */
        public float bankX = -1f;
        public float bankY = -1f;
        public float cutAngleDeg;
        /** Direction the striker travels after hitting the coin (kept for the old guide helper). */
        public float afterDx;
        public float afterDy;
        /** Planner cost in pixels (lower is better). */
        public float score;
        /** 0..1 shot strength derived from the travel distance. */
        public float power;
        /** How many other coins sit on the paths of this shot. */
        public int blockers;
    }

    public static List<Shot> planAll(Analysis a, boolean anyCoin, boolean slide) {
        List<Shot> out = new ArrayList<>();
        if (a == null || a.striker == null || !a.board.detected) {
            return out;
        }
        Piece s0 = a.striker;
        float L = a.board.left, T = a.board.top, R = a.board.right, B = a.board.bottom;
        float W = a.board.width();
        float rc = 0f;
        for (Piece p : a.pieces) {
            if (p != s0 && p.type != PieceType.STRIKER) { rc = p.radius; break; }
        }
        if (rc <= 0f) rc = 0.75f * s0.radius;
        float rs = s0.radius;
        float sy = s0.y;

        float xMin = L + 0.215f * W;
        float xMax = R - 0.215f * W;
        int positions = slide ? 25 : 1;
        // Use the detected centre of the dark pocket opening. Offsetting this point toward
        // the corner moves the aim toward the wooden jaw on diagonal approaches.
        List<Pocket> aimPockets = new ArrayList<>();
        for (Pocket pk : a.pockets) {
            aimPockets.add(new Pocket(pk.x, pk.y, pk.radius));
        }

        for (int i = 0; i < positions; i++) {
            float sx;
            if (!slide || i == 0) {
                sx = s0.x;
            } else {
                sx = xMin + (xMax - xMin) * (i - 1) / (float) (positions - 2);
            }
            // the striker cannot be put on top of another coin
            boolean overlap = false;
            for (Piece o : a.pieces) {
                if (o == s0 || o.type == PieceType.STRIKER) continue;
                float dx = o.x - sx, dy = o.y - sy;
                if (Math.sqrt(dx * dx + dy * dy) < rs + o.radius * 0.95f) { overlap = true; break; }
            }
            if (overlap) continue;
            float move = Math.abs(sx - s0.x);

            for (Piece t : a.pieces) {
                if (t == s0 || t.type == PieceType.STRIKER || t.type == PieceType.UNKNOWN) continue;
                boolean isQueen = t.type == PieceType.QUEEN;
                if (!t.mine && !isQueen) continue;
                float queenCost = (isQueen && !anyCoin) ? W * 0.4f : 0f;
                float rt = t.radius;

                for (Pocket p : aimPockets) {
                    float tx = p.x - t.x, ty = p.y - t.y;
                    float tl = (float) Math.sqrt(tx * tx + ty * ty);
                    if (tl < 1f) continue;
                    float ux = tx / tl, uy = ty / tl;
                    float gx = t.x - ux * (rt + rs);
                    float gy = t.y - uy * (rt + rs);
                    boolean ghostInside = gx >= L + rs && gx <= R - rs && gy >= T + rs && gy <= B - rs;

                    int coinLegBlockers = 0;
                    for (Piece o : a.pieces) {
                        if (o == t || o == s0 || o.type == PieceType.STRIKER) continue;
                        if (segDist(o.x, o.y, t.x, t.y, p.x, p.y) < o.radius + rt * 0.95f) coinLegBlockers++;
                    }

                    if (ghostInside) {
                        // ---- 0: direct shot
                        float vx0 = gx - sx, vy0 = gy - sy;
                        float sl = (float) Math.sqrt(vx0 * vx0 + vy0 * vy0);
                        if (sl > MIN_SHOT_RS * rs) {
                            float vx = vx0 / sl, vy = vy0 / sl;
                            float cos = vx * ux + vy * uy;
                            boolean awayFromBaseline = !(sy > T + 0.5f * a.board.height()) || vy <= -0.10f;
                            if (cos >= 0.10f && awayFromBaseline) {
                                int blk = coinLegBlockers;
                                for (Piece o : a.pieces) {
                                    if (o == t || o == s0 || o.type == PieceType.STRIKER) continue;
                                    if (segDist(o.x, o.y, sx, sy, gx, gy) < o.radius + rs * 0.95f) blk++;
                                }
                                float score = sl + tl * 1.5f + (1f - cos) * W * 1.5f + blk * W + move + queenCost;
                                out.add(make(0, t, p, sx, sy, gx, gy, gx, gy, -1f, -1f, cos, sl + tl, blk, score, W));
                            }
                        }

                        // ---- 2: striker bank off each wall, then the coin
                        for (int wall = 0; wall < 4; wall++) {
                            float wv, rgx, rgy;
                            if (wall == 0) { wv = L + rs; rgx = 2 * wv - gx; rgy = gy; }
                            else if (wall == 1) { wv = R - rs; rgx = 2 * wv - gx; rgy = gy; }
                            else if (wall == 2) { wv = T + rs; rgx = gx; rgy = 2 * wv - gy; }
                            else { wv = B - rs; rgx = gx; rgy = 2 * wv - gy; }
                            float dx = rgx - sx, dy = rgy - sy;
                            float hx, hy;
                            if (wall < 2) {
                                if (Math.abs(dx) < 1e-6f) continue;
                                float lam = (wv - sx) / dx;
                                if (lam < 0.02f || lam > 0.98f) continue;
                                hx = wv;
                                hy = sy + lam * dy;
                                if (hy < T + rs || hy > B - rs) continue;
                            } else {
                                if (Math.abs(dy) < 1e-6f) continue;
                                float lam = (wv - sy) / dy;
                                if (lam < 0.02f || lam > 0.98f) continue;
                                hx = sx + lam * dx;
                                hy = wv;
                                if (hx < L + rs || hx > R - rs) continue;
                            }
                            float d1 = dist(hx, hy, sx, sy);
                            float d2 = dist(gx, gy, hx, hy);
                            if (d1 < rs * 1.5f || d2 < rs * 1.5f) continue;
                            float fx = (gx - hx) / d2, fy = (gy - hy) / d2;
                            float cos = fx * ux + fy * uy;
                            if (cos < 0.10f) continue;
                            int blk = coinLegBlockers;
                            for (Piece o : a.pieces) {
                                if (o == t || o == s0 || o.type == PieceType.STRIKER) continue;
                                if (segDist(o.x, o.y, sx, sy, hx, hy) < o.radius + rs * 0.95f) blk++;
                                if (segDist(o.x, o.y, hx, hy, gx, gy) < o.radius + rs * 0.95f) blk++;
                            }
                            float score = d1 + d2 + tl * 1.5f + (1f - cos) * W * 1.5f + blk * W
                                    + 1.35f * W + move + queenCost;
                            out.add(make(2, t, p, sx, sy, hx, hy, gx, gy, hx, hy, cos, d1 + d2 + tl, blk, score, W));
                        }
                    }

                    // ---- 1: coin bank. The coin bounces off a wall first, then drops in the pocket.
                    for (int wall = 0; wall < 4; wall++) {
                        float wv, pmx, pmy;
                        if (wall == 0) { wv = L + rt; pmx = 2 * wv - p.x; pmy = p.y; }
                        else if (wall == 1) { wv = R - rt; pmx = 2 * wv - p.x; pmy = p.y; }
                        else if (wall == 2) { wv = T + rt; pmx = p.x; pmy = 2 * wv - p.y; }
                        else { wv = B - rt; pmx = p.x; pmy = 2 * wv - p.y; }
                        float dx = pmx - t.x, dy = pmy - t.y;
                        float dl = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dl < 1f) continue;
                        float hx, hy;
                        if (wall < 2) {
                            if (Math.abs(dx) < 1e-6f) continue;
                            float lam = (wv - t.x) / dx;
                            if (lam < 0.02f || lam > 0.98f) continue;
                            hx = wv;
                            hy = t.y + lam * dy;
                            if (hy < T + rt || hy > B - rt) continue;
                        } else {
                            if (Math.abs(dy) < 1e-6f) continue;
                            float lam = (wv - t.y) / dy;
                            if (lam < 0.02f || lam > 0.98f) continue;
                            hx = t.x + lam * dx;
                            hy = wv;
                            if (hx < L + rt || hx > R - rt) continue;
                        }
                        // a "bank" right next to the pocket is just a straight shot, skip it
                        if (dist(hx, hy, p.x, p.y) < 0.2f * W) continue;
                        float bx = dx / dl, by = dy / dl;
                        float cgx = t.x - bx * (rt + rs);
                        float cgy = t.y - by * (rt + rs);
                        if (cgx < L + rs || cgx > R - rs || cgy < T + rs || cgy > B - rs) continue;
                        float vx0 = cgx - sx, vy0 = cgy - sy;
                        float sl = (float) Math.sqrt(vx0 * vx0 + vy0 * vy0);
                        if (sl <= MIN_SHOT_RS * rs) continue;
                        float vx = vx0 / sl, vy = vy0 / sl;
                        float cos = vx * bx + vy * by;
                        boolean awayFromBaseline = !(sy > T + 0.5f * a.board.height()) || vy <= -0.10f;
                        if (cos < 0.10f || !awayFromBaseline) continue;
                        float l1 = dist(hx, hy, t.x, t.y);
                        float l2 = dist(p.x, p.y, hx, hy);
                        int blk = 0;
                        for (Piece o : a.pieces) {
                            if (o == t || o == s0 || o.type == PieceType.STRIKER) continue;
                            if (segDist(o.x, o.y, sx, sy, cgx, cgy) < o.radius + rs * 0.95f) blk++;
                            if (segDist(o.x, o.y, t.x, t.y, hx, hy) < o.radius + rt * 0.95f) blk++;
                            if (segDist(o.x, o.y, hx, hy, p.x, p.y) < o.radius + rt * 0.95f) blk++;
                        }
                        float score = sl + (l1 + l2) * 1.5f + (1f - cos) * W * 1.5f + blk * W
                                + 1.60f * W + move + queenCost;
                        out.add(make(1, t, p, sx, sy, cgx, cgy, cgx, cgy, hx, hy, cos, sl + l1 + l2, blk, score, W));
                    }
                }
            }
        }
        Collections.sort(out, new Comparator<Shot>() {
            @Override public int compare(Shot x, Shot y) { return Float.compare(x.score, y.score); }
        });
        return out;
    }

    private static Shot make(int kind, Piece t, Pocket p, float sx, float sy, float aimX, float aimY,
                             float ghostX, float ghostY, float bankX, float bankY, float cos,
                             float travelPx, int blockers, float score, float boardW) {
        Shot sh = new Shot();
        sh.valid = true;
        sh.kind = kind;
        sh.target = t;
        sh.pocket = p;
        sh.strikerX = sx;
        sh.strikerY = sy;
        sh.aimX = aimX;
        sh.aimY = aimY;
        sh.ghostX = ghostX;
        sh.ghostY = ghostY;
        sh.bankX = bankX;
        sh.bankY = bankY;
        float c = Math.max(-1f, Math.min(1f, cos));
        sh.cutAngleDeg = (float) Math.toDegrees(Math.acos(c));
        sh.blockers = blockers;
        sh.score = score;
        float travel = travelPx / Math.max(1f, boardW * 1.6f);
        float cutPenalty = sh.cutAngleDeg / 75f * 0.15f;
        float power = 0.3f + 0.7f * travel + cutPenalty;
        if (kind != 0) power *= 1.25f;
        sh.power = Math.max(0.3f, Math.min(1f, power));
        return sh;
    }

    /** The best few different shots (one per coin, pocket and kind), cheapest first. */
    public static List<Shot> distinctTop(List<Shot> all, int count) {
        List<Shot> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Shot s : all) {
            String key = Math.round(s.target.x) + ":" + Math.round(s.target.y) + ":"
                    + Math.round(s.pocket.x) + ":" + Math.round(s.pocket.y) + ":" + s.kind;
            if (!seen.add(key)) continue;
            out.add(s);
            if (out.size() >= count) break;
        }
        return out;
    }

    /** The single best shot, or an "aim at the nearest coin" shot when nothing fits. */
    public static Shot best(Analysis a, List<Shot> all, boolean anyCoin) {
        if (!all.isEmpty()) return all.get(0);
        Shot fallback = new Shot();
        if (a == null || a.striker == null || !a.board.detected) return fallback;
        aimNearest(fallback, a, a.striker, anyCoin, a.board.width());
        return fallback;
    }

    /** Last resort: hit the nearest wanted coin head-on and let it go towards its closest pocket. */
    private static void aimNearest(Shot best, Analysis a, Piece s, boolean anyCoin, float boardW) {
        Piece near = null;
        float nearD = Float.MAX_VALUE;
        for (Piece t : a.pieces) {
            if (t == s || t.type == PieceType.STRIKER || t.type == PieceType.UNKNOWN) continue;
            if (!t.mine && t.type != PieceType.QUEEN) continue;
            float d = dist(t.x, t.y, s.x, s.y);
            if (d < nearD && d > 1f) {
                nearD = d;
                near = t;
            }
        }
        if (near == null) return;
        Pocket pk = null;
        float pd = Float.MAX_VALUE;
        for (Pocket p : a.pockets) {
            float d = dist(p.x, p.y, near.x, near.y);
            if (d < pd) {
                pd = d;
                pk = p;
            }
        }
        if (pk == null) return;
        float vx = (near.x - s.x) / nearD;
        float vy = (near.y - s.y) / nearD;
        float gx = near.x - vx * (near.radius + s.radius);
        float gy = near.y - vy * (near.radius + s.radius);
        float ux = pk.x - near.x, uy = pk.y - near.y;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy);
        if (uLen < 1f) return;
        ux /= uLen;
        uy /= uLen;
        float cos = vx * ux + vy * uy;
        Shot f = make(0, near, pk, s.x, s.y, gx, gy, gx, gy, -1f, -1f, cos, nearD + uLen, 99,
                nearD, boardW);
        copy(f, best);
    }

    private static void copy(Shot from, Shot to) {
        to.valid = from.valid;
        to.kind = from.kind;
        to.target = from.target;
        to.pocket = from.pocket;
        to.strikerX = from.strikerX;
        to.strikerY = from.strikerY;
        to.aimX = from.aimX;
        to.aimY = from.aimY;
        to.ghostX = from.ghostX;
        to.ghostY = from.ghostY;
        to.bankX = from.bankX;
        to.bankY = from.bankY;
        to.cutAngleDeg = from.cutAngleDeg;
        to.afterDx = from.afterDx;
        to.afterDy = from.afterDy;
        to.score = from.score;
        to.power = from.power;
        to.blockers = from.blockers;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
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
