package com.aimai.max.assistant;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/** Turns one screenshot into a scan result and broadcasts it to the overlay. Not thread-safe: use one thread. */
public class ScanPipeline {

    private static final long HOLD_LAST_MS = 1500L;
    /** The game's striker slider sits this far (in board widths) below the bottom edge of the board. */
    private static final float SLIDER_BELOW_BOARD = 0.368f;

    private int stableFrameCount = 0;
    private List<float[]> previousPieces = new ArrayList<>();
    private CarromBoardAnalyzer.Analysis lastGood;
    private long lastGoodAt = 0L;

    public void reset() {
        stableFrameCount = 0;
        previousPieces = new ArrayList<>();
        lastGood = null;
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

    /**
     * Which coins are mine? "AUTO" reads the little coin icon next to my name at the top-left of the
     * game screen (black or white). Anything unclear means "any coin".
     */
    private CarromBoardAnalyzer.PieceType wantedCoins(Context ctx, Bitmap bmp) {
        String mine = ctx.getSharedPreferences("aimai_settings", Context.MODE_PRIVATE)
                .getString("my_coins", "AUTO");
        if ("BLACK".equals(mine)) return CarromBoardAnalyzer.PieceType.BLACK;
        if ("WHITE".equals(mine)) return CarromBoardAnalyzer.PieceType.WHITE;
        if ("ANY".equals(mine)) return null;

        int w = bmp.getWidth(), h = bmp.getHeight();
        int cx = Math.round(0.111f * w), cy = Math.round(0.161f * h);
        float[] hsv = new float[3];
        float sumS = 0, sumV = 0;
        int n = 0;
        for (int dy = -6; dy <= 6; dy += 3) {
            for (int dx = -6; dx <= 6; dx += 3) {
                int x = Math.max(0, Math.min(w - 1, cx + dx));
                int y = Math.max(0, Math.min(h - 1, cy + dy));
                Color.colorToHSV(bmp.getPixel(x, y), hsv);
                sumS += hsv[1];
                sumV += hsv[2];
                n++;
            }
        }
        float s = sumS / n, v = sumV / n;
        if (v < 0.40f && s < 0.40f) return CarromBoardAnalyzer.PieceType.BLACK;
        if (s < 0.25f && v > 0.70f) return CarromBoardAnalyzer.PieceType.WHITE;
        return null;
    }

    public void process(Context ctx, Bitmap bmp) {
        long started = android.os.SystemClock.elapsedRealtime();
        CarromBoardAnalyzer.Analysis result = CarromBoardAnalyzer.analyze(bmp);
        long now = android.os.SystemClock.elapsedRealtime();
        float frameW = bmp.getWidth();
        float frameH = bmp.getHeight();

        // Our own guide drawing can hide the striker for one frame. Keep the last good scan briefly.
        if (result.board.detected && result.striker != null) {
            lastGood = result;
            lastGoodAt = now;
        } else if (lastGood != null && now - lastGoodAt < HOLD_LAST_MS) {
            result = lastGood;
        } else {
            lastGood = null;
        }

        int strikerCount = result.striker != null ? 1 : 0;
        int coinCount = 0;
        for (CarromBoardAnalyzer.Piece p : result.pieces) {
            if (p != result.striker) coinCount++;
        }
        android.util.Log.d("AimAiMax", "scan " + (now - started) + "ms pockets=" + result.pockets.size()
                + " pieces=" + result.pieces.size() + " striker=" + strikerCount + " coins=" + coinCount);

        float tol = Math.max(6f, 0.012f * Math.max(1f, result.board.width()));
        if (samePositions(previousPieces, result.pieces, tol)) {
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
        guideIntent.putExtra("strikerCount", strikerCount);
        guideIntent.putExtra("gutiCount", coinCount);

        CarromBoardAnalyzer.Piece striker = result.striker;
        if (striker != null && result.board.detected) {
            CarromBoardAnalyzer.PieceType wanted = wantedCoins(ctx, bmp);
            boolean slide = ctx.getSharedPreferences("aimai_settings", Context.MODE_PRIVATE)
                    .getBoolean("slide_enabled", true);
            CarromShotPlanner.Shot shot = slide
                    ? CarromShotPlanner.planBest(result, wanted)
                    : CarromShotPlanner.plan(result, wanted);
            guideIntent.putExtra("isStriker", true);
            guideIntent.putExtra("pieceX", striker.x / frameW);
            guideIntent.putExtra("pieceY", striker.y / frameH);
            guideIntent.putExtra("plannedX", (shot.valid ? shot.strikerX : striker.x) / frameW);
            guideIntent.putExtra("sliderY",
                    (result.board.bottom + SLIDER_BELOW_BOARD * result.board.width()) / frameH);
            guideIntent.putExtra("strikerRadius", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusX", striker.radius / frameW);
            guideIntent.putExtra("strikerRadiusY", striker.radius / frameH);
            guideIntent.putExtra("shotFound", shot.valid);
            if (shot.valid) {
                guideIntent.putExtra("targetPieceX", shot.target.x / frameW);
                guideIntent.putExtra("targetPieceY", shot.target.y / frameH);
                guideIntent.putExtra("targetPieceRadiusX", shot.target.radius / frameW);
                guideIntent.putExtra("targetPieceRadiusY", shot.target.radius / frameH);
                guideIntent.putExtra("ghostX", shot.ghostX / frameW);
                guideIntent.putExtra("ghostY", shot.ghostY / frameH);
                guideIntent.putExtra("shotPower", shot.power);
                guideIntent.putExtra("pocketX", shot.pocket.x / frameW);
                guideIntent.putExtra("pocketY", shot.pocket.y / frameH);
                guideIntent.putExtra("pocketRadius", shot.pocket.radius / frameW);
                guideIntent.putExtra("cutAngle", shot.cutAngleDeg);
                guideIntent.putExtra("targetFound", true);
            } else {
                guideIntent.putExtra("targetFound", false);
            }
        } else {
            guideIntent.putExtra("isStriker", false);
            guideIntent.putExtra("targetFound", false);
            guideIntent.putExtra("shotFound", false);
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
