package com.aimai.max.assistant;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one screenshot into a scan result plus the best shots, and broadcasts it to the overlay.
 * Not thread-safe: use it from one thread.
 */
public class ScanPipeline {

    private static final long HOLD_LAST_MS = 1500L;

    private int stableFrameCount = 0;
    private int looseFrameCount = 0;
    private ShotSearch.Result cacheCur;
    private ShotSearch.Result cacheBest;
    private float cacheSx, cacheSy;
    private List<float[]> previousPieces = new ArrayList<>();
    private CarromBoardAnalyzer.Analysis lastGood;
    private long lastGoodAt = 0L;
    // red coin cover: if my red coin went in with my last shot, the next shot must cover it
    private boolean wasMyTurn = false, queenBeforeShot = false, queenOnLastTurn = false, coverPending = false;
    private long dimAt = 0L;

    private boolean redIconPrev = false, coverDone = false;
    private int mineAtRed = 0, coverDropFrames = 0;

    private static int[] cvHudRgb(Bitmap bmp, float fx) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int cx = Math.round(fx * w), cy = Math.round(0.161f * h);
        long sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int dy = -6; dy <= 6; dy += 3) {
            for (int dx = -6; dx <= 6; dx += 3) {
                int c = bmp.getPixel(Math.max(0, Math.min(w - 1, cx + dx)), Math.max(0, Math.min(h - 1, cy + dy)));
                sr += Color.red(c);
                sg += Color.green(c);
                sb += Color.blue(c);
                n++;
            }
        }
        return new int[]{(int) (sr / n), (int) (sg / n), (int) (sb / n)};
    }

    private static float cvColorDist(int rgb, int[] c) {
        float dr = Color.red(rgb) - c[0], dg = Color.green(rgb) - c[1], db = Color.blue(rgb) - c[2];
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** How many of my coins (not the red) are on the board, told apart by the colour of my coin icon. */
    private static int cvMyCoins(Bitmap bmp, CarromBoardAnalyzer.Analysis a, float mineX) {
        int[] mh = cvHudRgb(bmp, mineX), oh = cvHudRgb(bmp, mineX == 0.111f ? 0.889f : 0.111f);
        int n = 0;
        for (CarromBoardAnalyzer.Piece p : a.pieces) {
            if (p == a.striker || p.type == CarromBoardAnalyzer.PieceType.STRIKER
                    || p.type == CarromBoardAnalyzer.PieceType.QUEEN) continue;
            float dm = cvColorDist(p.rgb, mh), dop = cvColorDist(p.rgb, oh);
            if (dm < dop && dm < 150f) n++;
        }
        return n;
    }

    private static boolean isRedAt(Bitmap bmp, float fx) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int cx = Math.round(fx * w), cy = Math.round(0.161f * h);
        int r = Math.max(2, Math.round(0.012f * w));
        int[] rs = new int[25], gs = new int[25], bs = new int[25];
        int n = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = Math.max(0, Math.min(w - 1, cx + dx * r / 2));
                int y = Math.max(0, Math.min(h - 1, cy + dy * r / 2));
                int c = bmp.getPixel(x, y);
                rs[n] = Color.red(c); gs[n] = Color.green(c); bs[n] = Color.blue(c);
                n++;
            }
        }
        java.util.Arrays.sort(rs, 0, n);
        java.util.Arrays.sort(gs, 0, n);
        java.util.Arrays.sort(bs, 0, n);
        int mr = rs[n / 2], mg = gs[n / 2], mb = bs[n / 2];
        return mr > 105 && mr - mg > 70 && mr - mb > 25;
    }

    private static boolean redPendingIcon(Bitmap bmp) {
        return isRedAt(bmp, 0.174f) || isRedAt(bmp, 0.826f);
    }

    public void reset() {
        stableFrameCount = 0;
        looseFrameCount = 0;
        previousPieces = new ArrayList<>();
        lastGood = null;
        cacheCur = null;
        cacheBest = null;
    }

    private boolean samePositions(List<float[]> a, List<CarromBoardAnalyzer.Piece> b, float tol) {
        if (a.size() != b.size()) return false;
        for (CarromBoardAnalyzer.Piece p : b) {
            boolean found = false;
            for (float[] q : a) {
                float dx = q[0] - p.x, dy = q[1] - p.y;
                if (dx * dx + dy * dy <= tol * tol) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Like samePositions, but one coin that flickers in the scan (for example under our own drawing) is tolerated. */
    private boolean mostlySame(List<float[]> a, List<CarromBoardAnalyzer.Piece> b, float tol) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (Math.abs(a.size() - b.size()) > 1) return false;
        int matched = 0;
        for (CarromBoardAnalyzer.Piece p : b) {
            for (float[] q : a) {
                float dx = q[0] - p.x, dy = q[1] - p.y;
                if (dx * dx + dy * dy <= tol * tol) { matched++; break; }
            }
        }
        return matched >= Math.max(1, (int) Math.ceil(b.size() * 0.9f));
    }

    // ------------------------------------------------------------------ whose coins are whose

    private static int[] hudColor(Bitmap bmp, float fx) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int cx = Math.round(fx * w), cy = Math.round(0.161f * h);
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int dy = -6; dy <= 6; dy += 3) {
            for (int dx = -6; dx <= 6; dx += 3) {
                int x = Math.max(0, Math.min(w - 1, cx + dx));
                int y = Math.max(0, Math.min(h - 1, cy + dy));
                int c = bmp.getPixel(x, y);
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
            }
        }
        return new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
    }

    private static float colorDist(int rgb, int[] c) {
        float dr = Color.red(rgb) - c[0], dg = Color.green(rgb) - c[1], db = Color.blue(rgb) - c[2];
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Marks which coins are mine. AUTO compares each coin with the little coin icon next to my name
     * (top-left) and the one next to the opponent (top-right), so any coin skin works.
     */
    private static float hudActivity(Bitmap bmp, float left, float right) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int x0 = Math.max(0, Math.round(left * w)), x1 = Math.min(w - 1, Math.round(right * w));
        int y0 = Math.max(0, Math.round(0.105f * h)), y1 = Math.min(h - 1, Math.round(0.215f * h));
        long total = 0; int count = 0;
        for (int y = y0; y <= y1; y += 3) for (int x = x0; x <= x1; x += 3) {
            int c = bmp.getPixel(x, y);
            total += Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c)));
            count++;
        }
        return count == 0 ? 0f : total / (float) count;
    }

    private void markMine(Context ctx, Bitmap bmp, CarromBoardAnalyzer.Analysis a) {
        String mode = ctx.getSharedPreferences("aimai_settings", Context.MODE_PRIVATE)
                .getString("my_coins", "AUTO");
        float mineFx = "AUTO".equals(mode)
                ? (hudActivity(bmp, 0.04f, 0.18f) >= hudActivity(bmp, 0.82f, 0.96f) ? 0.111f : 0.889f)
                : 0.111f;
        int[] mineHud = hudColor(bmp, mineFx);
        int[] oppHud = hudColor(bmp, mineFx == 0.111f ? 0.889f : 0.111f);
        int count = 0;
        for (CarromBoardAnalyzer.Piece p : a.pieces) {
            if (p == a.striker || p.type == CarromBoardAnalyzer.PieceType.STRIKER) continue;
            boolean mine;
            if ("ANY".equals(mode)) {
                mine = p.type == CarromBoardAnalyzer.PieceType.WHITE
                        || p.type == CarromBoardAnalyzer.PieceType.BLACK;
            } else if ("BLACK".equals(mode)) {
                mine = p.type == CarromBoardAnalyzer.PieceType.BLACK;
            } else if ("WHITE".equals(mode)) {
                mine = p.type == CarromBoardAnalyzer.PieceType.WHITE;
            } else {
                float dm = colorDist(p.rgb, mineHud);
                float dop = colorDist(p.rgb, oppHud);
                // AUTO is deliberately conservative: an uncertain colour match is not ours.
                // Missing a shot is safer than ever aiming at an opponent coin.
                mine = p.type != CarromBoardAnalyzer.PieceType.QUEEN
                        && dm + 24f < dop && dm < 125f;
            }
            p.mine = mine;
            if (mine) count++;
        }
        // No brightness-based fallback: if ownership cannot be confirmed, do not take a shot.
    }

    // ------------------------------------------------------------------ output

    /**
     * It is my turn only when the striker slider under the board is bright wood coloured. On the other
     * player's turn the game dims it (dark purple), and no lines must be drawn and no shot taken then.
     */
    private static boolean isMyTurn(Bitmap bmp, CarromBoardAnalyzer.Analysis a) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float base = Math.min(a.board.width(), a.board.height());
        int cy = Math.round(a.board.bottom + 0.36f * base);
        int x0 = Math.round(0.22f * w);
        int x1 = Math.round(0.78f * w);
        int n = Math.max(1, (x1 - x0) / 4 + 1);
        for (int y = cy - 16; y <= cy + 16; y += 2) {
            if (y < 0 || y >= h) continue;
            int[] rs = new int[n];
            int[] gs = new int[n];
            int c = 0;
            for (int x = x0; x < x1 && c < n; x += 4) {
                int px = bmp.getPixel(x, y);
                rs[c] = Color.red(px);
                gs[c] = Color.green(px);
                c++;
            }
            java.util.Arrays.sort(rs, 0, c);
            java.util.Arrays.sort(gs, 0, c);
            if (c > 0 && rs[c / 2] > 150 && gs[c / 2] > 85) return true;
        }
        return false;
    }

    private static float[] normalizePaths(float[] raw, float fw, float fh) {
        if (raw == null) return new float[0];
        float[] out = new float[raw.length];
        int i = 0;
        while (i + 1 < raw.length) {
            out[i] = raw[i];
            int n = (int) raw[i + 1];
            out[i + 1] = raw[i + 1];
            int p = i + 2;
            for (int k = 0; k < n && p + 1 < raw.length; k++) {
                out[p] = raw[p] / fw;
                out[p + 1] = raw[p + 1] / fh;
                p += 2;
            }
            i = p;
        }
        return out;
    }

    private static void putPlan(Intent in, String pre, ShotSearch.Outcome o, float fw, float fh) {
        if (o == null || o.shot == null || !o.shot.valid) {
            in.putExtra(pre + "Found", false);
            return;
        }
        CarromShotPlanner.Shot s = o.shot;
        in.putExtra(pre + "Found", true);
        in.putExtra(pre + "StrikerX", s.strikerX / fw);
        in.putExtra(pre + "StrikerY", s.strikerY / fh);
        in.putExtra(pre + "AimX", s.aimX / fw);
        in.putExtra(pre + "AimY", s.aimY / fh);
        in.putExtra(pre + "GhostX", s.ghostX / fw);
        in.putExtra(pre + "GhostY", s.ghostY / fh);
        in.putExtra(pre + "TargetX", s.target.x / fw);
        in.putExtra(pre + "TargetY", s.target.y / fh);
        in.putExtra(pre + "PocketX", s.pocket.x / fw);
        in.putExtra(pre + "PocketY", s.pocket.y / fh);
        in.putExtra(pre + "Kind", s.kind);
        in.putExtra(pre + "Power", o.power > 0f ? o.power : ShotSearch.STRENGTH[Math.max(0, Math.min(2, o.level))]);
        in.putExtra(pre + "PowerMul", o.powerMultiplier > 0f ? o.powerMultiplier : 1f);
        in.putExtra(pre + "SimScore", o.simScore);
        in.putExtra(pre + "Pots", o.potsSomething);
        in.putExtra(pre + "Blockers", s.blockers);
        in.putExtra(pre + "Paths", normalizePaths(o.paths, fw, fh));
    }

    public void process(Context ctx, Bitmap bmp) {
        long started = android.os.SystemClock.elapsedRealtime();
        CarromBoardAnalyzer.Analysis result = CarromBoardAnalyzer.analyze(bmp);
        long scanned = android.os.SystemClock.elapsedRealtime();
        float frameW = bmp.getWidth();
        float frameH = bmp.getHeight();

        // Our own guide drawing can hide the striker for one frame. Keep the last good scan briefly.
        if (result.board.detected && result.striker != null) {
            lastGood = result;
            lastGoodAt = scanned;
        } else if (lastGood != null && scanned - lastGoodAt < HOLD_LAST_MS) {
            result = lastGood;
        } else {
            lastGood = null;
        }

        int strikerCount = result.striker != null ? 1 : 0;
        int coinCount = 0;
        for (CarromBoardAnalyzer.Piece p : result.pieces) {
            if (p != result.striker) coinCount++;
        }

        float tol = Math.max(6f, 0.012f * Math.max(1f, result.board.width()));
        boolean strictSame = samePositions(previousPieces, result.pieces, tol);
        boolean looseSame = strictSame || mostlySame(previousPieces, result.pieces, tol * 1.5f);
        looseFrameCount = looseSame ? looseFrameCount + 1 : 0;
        CarromBoardAnalyzer.Piece sNow = result.striker;
        boolean strikerShifted = sNow != null && cacheCur != null
                && (Math.abs(sNow.x - cacheSx) > tol || Math.abs(sNow.y - cacheSy) > tol);
        if (!looseSame || strikerShifted) {
            cacheCur = null;
            cacheBest = null;
        }
        if (strictSame) {
            stableFrameCount++;
        } else {
            stableFrameCount = 0;
            List<float[]> copy = new ArrayList<>();
            for (CarromBoardAnalyzer.Piece p : result.pieces) copy.add(new float[]{p.x, p.y});
            previousPieces = copy;
        }

        Intent guideIntent = new Intent("com.aimai.max.GUTI_GUIDE_DATA");
        guideIntent.setPackage(ctx.getPackageName());
        guideIntent.putExtra("boardStable", stableFrameCount >= 2);
        guideIntent.putExtra("boardSteady", looseFrameCount >= 1);
        guideIntent.putExtra("cover", coverPending);
        guideIntent.putExtra("strikerCount", strikerCount);
        guideIntent.putExtra("gutiCount", coinCount);

        CarromBoardAnalyzer.Piece striker = result.striker;
        boolean myTurn = result.board.detected && isMyTurn(bmp, result);
        boolean queenNow = false;
        for (CarromBoardAnalyzer.Piece qp : result.pieces) {
            if (qp.type == CarromBoardAnalyzer.PieceType.QUEEN) { queenNow = true; break; }
        }
        boolean turnNow = myTurn && striker != null;
        long clock = android.os.SystemClock.elapsedRealtime();
        if (turnNow && !wasMyTurn) {
            if (queenBeforeShot && !queenNow && clock - dimAt < 4500L) coverPending = true;
        } else if (!turnNow && wasMyTurn) {
            dimAt = clock;
            queenBeforeShot = queenOnLastTurn;
            coverPending = false;
        }
        if (turnNow) queenOnLastTurn = queenNow;
        wasMyTurn = turnNow;
        boolean redIcon = result.board.detected && redPendingIcon(bmp);
        if (!redIcon) {
            redIconPrev = false;
            coverDone = false;
            coverDropFrames = 0;
        } else {
            float mineHudX = hudActivity(bmp, 0.04f, 0.18f) >= hudActivity(bmp, 0.82f, 0.96f) ? 0.111f : 0.889f;
            mineNow = cvMyCoins(bmp, result, mineHudX);
            if (!redIconPrev) {
                mineAtRed = mineNow;
                coverDone = false;
                coverDropFrames = 0;
            } else if (!coverDone) {
                if (mineNow < mineAtRed) {
                    coverDropFrames++;
                    if (coverDropFrames >= 2) coverDone = true;
                } else {
                    coverDropFrames = 0;
                    if (mineNow > mineAtRed) mineAtRed = mineNow;
                }
            }
            redIconPrev = true;
        }
        coverPending = redIcon && !coverDone;
        ShotSearch.coverMode = coverPending;
        if (striker != null && result.board.detected && myTurn) {
            markMine(ctx, bmp, result);
            boolean slideOn = ctx.getSharedPreferences("aimai_settings", Context.MODE_PRIVATE)
                    .getBoolean("slide_enabled", true);
            // The shots are worked out once when the board has stopped and then kept, so the lines stay put.
            ShotSearch.Result cur;
            ShotSearch.Result best;
            if (cacheCur != null) {
                cur = cacheCur;
                best = cacheBest;
            } else if (looseFrameCount >= 1) {
                // Always scan the complete legal baseline before choosing the shot. A valid
                // current-position pot does not mean it is the best angle; the baseline may offer
                // a much straighter, clearer route to the same coin and pocket.
                cur = ShotSearch.search(result, false, false, 110L, 32);
                best = slideOn ? ShotSearch.search(result, false, true, 260L, 32) : null;
                cacheCur = cur;
                cacheBest = best;
                cacheSx = striker.x;
                cacheSy = striker.y;
            } else {
                cur = new ShotSearch.Result();
                best = null;
            }

            guideIntent.putExtra("isStriker", true);
            guideIntent.putExtra("pieceX", striker.x / frameW);
            guideIntent.putExtra("pieceY", striker.y / frameH);
            guideIntent.putExtra("strikerRadius", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusX", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusY", striker.radius / frameH);
            float base = Math.min(result.board.width(), result.board.height());
            guideIntent.putExtra("sliderY", (result.board.bottom + 0.36f * base) / frameH);
            float pocketR = 0f;
            for (CarromBoardAnalyzer.Pocket pk : result.pockets) pocketR += pk.radius / 4f;
            guideIntent.putExtra("pocketRadius", pocketR / frameW);

            putPlan(guideIntent, "cur", cur.best, frameW, frameH);
            putPlan(guideIntent, "best", best == null ? null : best.best, frameW, frameH);

            // Draw only the selected simulated route; alternate paths made the aim look ambiguous.
            guideIntent.putExtra("curAlts", new float[0]);

            boolean curFound = cur.best != null && cur.best.shot != null && cur.best.shot.valid;
            guideIntent.putExtra("targetFound", curFound);
            guideIntent.putExtra("shotFound", curFound);
            long done = android.os.SystemClock.elapsedRealtime();
            android.util.Log.d("AimAiMax", "scan " + (scanned - started) + "ms, search " + (done - scanned)
                    + "ms, coins=" + coinCount + ", tried=" + cur.tried
                    + (curFound ? ", pots=" + cur.best.potsSomething + ", kind=" + cur.best.shot.kind : ", no shot"));
        } else {
            guideIntent.putExtra("isStriker", false);
            guideIntent.putExtra("targetFound", false);
            guideIntent.putExtra("shotFound", false);
            guideIntent.putExtra("curFound", false);
            guideIntent.putExtra("bestFound", false);
        }
        ctx.sendBroadcast(guideIntent);

        Intent analysisIntent = new Intent("com.aimai.max.BOARD_ANALYSIS");
        analysisIntent.setPackage(ctx.getPackageName());
        analysisIntent.putExtra("found", result.board.detected);
        analysisIntent.putExtra("centerX", (result.board.left + result.board.right) / 2f / frameW);
        analysisIntent.putExtra("centerY", (result.board.top + result.board.bottom) / 2f / frameH);
        analysisIntent.putExtra("boardWidth", result.board.width() / frameW);
        analysisIntent.putExtra("boardHeight", result.board.height() / frameH);
        ctx.sendBroadcast(analysisIntent);
    }
}
