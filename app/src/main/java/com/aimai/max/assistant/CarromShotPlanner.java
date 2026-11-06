package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;
import com.aimai.max.assistant.CarromBoardAnalyzer.Piece;
import com.aimai.max.assistant.CarromBoardAnalyzer.PieceType;
import com.aimai.max.assistant.CarromBoardAnalyzer.Pocket;

public final class CarromShotPlanner {

    private CarromShotPlanner() {}

    public static class Shot {
        public boolean valid;
        public Piece target;
        public Pocket pocket;
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
    }

    /**
     * Picks the best shot. A crowded board often has no completely clear shot, so blocked shots are
     * allowed but cost extra; the cheapest one wins. If nothing fits, the nearest wanted coin is
     * aimed at directly.
     */
    public static Shot plan(Analysis a, PieceType wanted) {
        Shot best = new Shot();
        if (a == null || a.striker == null || !a.board.detected) {
            return best;
        }

        Piece s = a.striker;
        float bestScore = Float.MAX_VALUE;
        float boardW = a.board.width();

        for (Piece t : a.pieces) {
            if (t == s || t.type == PieceType.STRIKER || t.type == PieceType.UNKNOWN) {
                continue;
            }
            if (wanted != null && t.type != wanted && t.type != PieceType.QUEEN) {
                continue;
            }

            for (Pocket p : a.pockets) {
                float tx = p.x - t.x;
                float ty = p.y - t.y;
                float tLen = (float) Math.sqrt(tx * tx + ty * ty);
                if (tLen < 1f) {
                    continue;
                }
                float ux = tx / tLen;
                float uy = ty / tLen;

                float gx = t.x - ux * (t.radius + s.radius);
                float gy = t.y - uy * (t.radius + s.radius);

                if (gx < a.board.left + s.radius || gx > a.board.right - s.radius
                        || gy < a.board.top + s.radius || gy > a.board.bottom - s.radius) {
                    continue;
                }

                float sx = gx - s.x;
                float sy = gy - s.y;
                float sLen = (float) Math.sqrt(sx * sx + sy * sy);
                if (sLen < 1f) {
                    continue;
                }
                float vx = sx / sLen;
                float vy = sy / sLen;

                float cos = vx * ux + vy * uy;
                if (cos < 0.2588f) {
                    continue;
                }

                int blockers = 0;
                for (Piece o : a.pieces) {
                    if (o == t || o == s) {
                        continue;
                    }
                    if (segDist(o.x, o.y, s.x, s.y, gx, gy) < o.radius + s.radius * 0.95f) {
                        blockers++;
                    }
                    if (segDist(o.x, o.y, t.x, t.y, p.x, p.y) < o.radius + t.radius * 0.95f) {
                        blockers++;
                    }
                }

                float score = sLen + tLen * 1.5f + (1f - cos) * boardW * 1.5f + blockers * boardW * 1.0f;
                if (score < bestScore) {
                    bestScore = score;
                    fill(best, s, t, p, gx, gy, ux, uy, vx, vy, cos, sLen, tLen, boardW);
                    best.score = score;
                    best.blockers = blockers;
                }
            }
        }

        if (!best.valid) {
            aimNearest(best, a, s, wanted, boardW);
        }
        return best;
    }

    private static void fill(Shot best, Piece s, Piece t, Pocket p, float gx, float gy,
                             float ux, float uy, float vx, float vy, float cos,
                             float sLen, float tLen, float boardW) {
        best.valid = true;
        best.target = t;
        best.pocket = p;
        best.ghostX = gx;
        best.ghostY = gy;
        best.contactX = gx + ux * s.radius;
        best.contactY = gy + uy * s.radius;
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
    private static void aimNearest(Shot best, Analysis a, Piece s, PieceType wanted, float boardW) {
        Piece near = null;
        float nearD = Float.MAX_VALUE;
        for (Piece t : a.pieces) {
            if (t == s || t.type == PieceType.STRIKER || t.type == PieceType.UNKNOWN) {
                continue;
            }
            if (wanted != null && t.type != wanted && t.type != PieceType.QUEEN) {
                continue;
            }
            float dx = t.x - s.x, dy = t.y - s.y;
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
        float vx = (near.x - s.x) / nearD;
        float vy = (near.y - s.y) / nearD;
        float gx = near.x - vx * (near.radius + s.radius);
        float gy = near.y - vy * (near.radius + s.radius);
        float ux = pk.x - near.x, uy = pk.y - near.y;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy);
        if (uLen < 1f) {
            return;
        }
        ux /= uLen;
        uy /= uLen;
        float cos = vx * ux + vy * uy;
        fill(best, s, near, pk, gx, gy, ux, uy, vx, vy, cos, nearD, uLen, boardW);
        best.score = nearD;
        best.blockers = 99;
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
