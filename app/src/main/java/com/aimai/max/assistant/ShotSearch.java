package com.aimai.max.assistant;

import com.aimai.max.assistant.CarromBoardAnalyzer.Analysis;
import com.aimai.max.assistant.CarromBoardAnalyzer.Piece;
import com.aimai.max.assistant.CarromBoardAnalyzer.PieceType;
import com.aimai.max.assistant.CarromShotPlanner.Shot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the best shot in about a second: the planner proposes many shots (direct, coin bank, striker
 * bank, from every striker position), then each one is played in the physics simulator at three power
 * levels. The shot that pots the most of my coins (and nothing of the opponent) wins.
 */
public final class ShotSearch {

    private ShotSearch() {}

    public static final float[] SPEEDS = {14f, 20f, 28f};
    public static final float[] STRENGTH = {0.30f, 0.60f, 1.00f};

    public static final class Outcome {
        public Shot shot;
        public int level;
        public float simScore;
        public boolean potsSomething;
        /** Polylines in pixels: [kind, n, x0, y0, x1, y1 ...] repeated. kind 0 striker, 1 mine, 2 other. */
        public float[] paths;
    }

    public static final class Result {
        public Outcome best;
        public List<Outcome> alts = new ArrayList<>();
        public int tried;
    }

    public static Result search(Analysis a, boolean anyCoin, boolean slide, long budgetMs, int maxCands) {
        Result result = new Result();
        if (a == null || a.striker == null || !a.board.detected || a.pockets.size() < 4) {
            return result;
        }
        long started = android.os.SystemClock.elapsedRealtime();
        List<Shot> all = CarromShotPlanner.planAll(a, anyCoin, slide);
        List<Shot> cands = CarromShotPlanner.distinctTop(all, maxCands);
        if (cands.isEmpty()) {
            Shot fb = CarromShotPlanner.best(a, all, anyCoin);
            if (!fb.valid) return result;
            cands.add(fb);
        }

        Piece s0 = a.striker;
        List<CarromSim.Disc> base = new ArrayList<>();
        for (Piece p : a.pieces) {
            if (p == s0 || p.type == PieceType.STRIKER || p.type == PieceType.UNKNOWN) continue;
            CarromSim.Disc d = new CarromSim.Disc();
            d.x = p.x;
            d.y = p.y;
            d.r = p.radius;
            d.mass = 1f;
            if (p.type == PieceType.QUEEN) d.owner = CarromSim.QUEEN;
            else d.owner = (anyCoin || p.mine) ? CarromSim.MINE : CarromSim.OTHER;
            base.add(d);
        }
        CarromSim.Disc strikerDisc = new CarromSim.Disc();
        strikerDisc.r = s0.radius;
        strikerDisc.mass = 1.4f;
        strikerDisc.owner = CarromSim.STRIKER;
        base.add(strikerDisc);
        int si = base.size() - 1;

        float[] px = new float[4], py = new float[4];
        float pr = 0f;
        for (int i = 0; i < 4; i++) {
            px[i] = a.pockets.get(i).x;
            py[i] = a.pockets.get(i).y;
            pr += a.pockets.get(i).radius / 4f;
        }
        float L = a.board.left, T = a.board.top, R = a.board.right, B = a.board.bottom;

        List<Outcome> outcomes = new ArrayList<>();
        for (Shot cand : cands) {
            if (android.os.SystemClock.elapsedRealtime() - started > budgetMs && !outcomes.isEmpty()) break;
            strikerDisc.x = cand.strikerX;
            strikerDisc.y = cand.strikerY;
            float dx = cand.aimX - cand.strikerX, dy = cand.aimY - cand.strikerY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) continue;
            dx /= len;
            dy /= len;

            Outcome bestForShot = null;
            for (int level = 0; level < SPEEDS.length; level++) {
                CarromSim.Result r = CarromSim.run(base, si, dx, dy, SPEEDS[level], L, T, R, B, px, py, pr, false);
                result.tried++;
                float score = 100f * r.minePotted + 60f * r.queenPotted - 120f * r.otherPotted
                        - (r.strikerPotted ? 500f : 0f)
                        - 0.02f * cand.score + 3f * level;
                if (bestForShot == null || score > bestForShot.simScore) {
                    bestForShot = new Outcome();
                    bestForShot.shot = cand;
                    bestForShot.level = level;
                    bestForShot.simScore = score;
                    bestForShot.potsSomething = r.minePotted + r.queenPotted > 0 && !r.strikerPotted;
                }
            }
            if (bestForShot != null) outcomes.add(bestForShot);
        }
        if (outcomes.isEmpty()) return result;

        Collections.sort(outcomes, new Comparator<Outcome>() {
            @Override public int compare(Outcome x, Outcome y) { return Float.compare(y.simScore, x.simScore); }
        });

        Outcome best = outcomes.get(0);
        if (!best.potsSomething) {
            // Nothing is sure to drop. Fall back to the cleanest geometric shot at a matching power.
            Shot g = cands.get(0);
            Outcome fb = new Outcome();
            fb.shot = g;
            fb.level = g.power < 0.45f ? 0 : (g.power < 0.75f ? 1 : 2);
            fb.simScore = best.simScore;
            fb.potsSomething = false;
            best = fb;
        }
        result.best = best;
        best.paths = record(base, si, best, L, T, R, B, px, py, pr);
        int limit = Math.min(6, outcomes.size());
        for (int i = 0; i < limit; i++) {
            Outcome o = outcomes.get(i);
            if (o.shot == best.shot) continue;
            if (result.alts.size() >= 5) break;
            o.paths = record(base, si, o, L, T, R, B, px, py, pr);
            result.alts.add(o);
        }
        return result;
    }

    private static float[] record(List<CarromSim.Disc> base, int si, Outcome o, float L, float T, float R,
                                  float B, float[] px, float[] py, float pr) {
        Shot s = o.shot;
        CarromSim.Disc striker = base.get(si);
        striker.x = s.strikerX;
        striker.y = s.strikerY;
        float dx = s.aimX - s.strikerX, dy = s.aimY - s.strikerY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return new float[0];
        CarromSim.Result r = CarromSim.run(base, si, dx / len, dy / len, SPEEDS[o.level], L, T, R, B, px, py, pr, true);
        List<Float> out = new ArrayList<>();
        int drawn = 0;
        for (int i = 0; i < r.discs.size(); i++) {
            CarromSim.Disc d = r.discs.get(i);
            if (d.pathCount < 2) continue;
            float mx = d.path[(d.pathCount - 1) * 2] - d.path[0];
            float my = d.path[(d.pathCount - 1) * 2 + 1] - d.path[1];
            if (mx * mx + my * my < 22f * 22f && d.owner != CarromSim.STRIKER) continue;
            if (d.owner != CarromSim.STRIKER && drawn >= 6) continue;
            out.add((float) (d.owner == CarromSim.STRIKER ? 0 : (d.owner == CarromSim.OTHER ? 2 : 1)));
            out.add((float) d.pathCount);
            for (int k = 0; k < d.pathCount; k++) {
                out.add(d.path[k * 2]);
                out.add(d.path[k * 2 + 1]);
            }
            if (d.owner != CarromSim.STRIKER) drawn++;
        }
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }
}
