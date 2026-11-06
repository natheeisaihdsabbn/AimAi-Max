package com.aimai.max.assistant;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the carrom board, its four pockets, the coins, the queen and the striker in a screenshot.
 * Sizes are calibrated on real Carrom Disc Pool frames: coin radius is 2.7% and striker radius 3.6%
 * of the playing-board width, which holds on every phone because the board always fills the width.
 */
public final class CarromBoardAnalyzer {

    private CarromBoardAnalyzer() {}

    public enum PieceType { STRIKER, QUEEN, BLACK, WHITE, UNKNOWN }

    public static class Piece {
        public float x;
        public float y;
        public float radius;
        public PieceType type;

        public Piece(float x, float y, float radius, PieceType type) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.type = type;
        }
    }

    public static class Board {
        public float left;
        public float top;
        public float right;
        public float bottom;
        public boolean detected;

        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    public static class Pocket {
        public float x;
        public float y;
        public float radius;

        public Pocket(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    public static class Analysis {
        public Board board = new Board();
        public List<Piece> pieces = new ArrayList<>();
        public List<Pocket> pockets = new ArrayList<>();
        public Piece striker;
    }

    private static final int F_WOOD = 1;
    private static final int F_WHITE = 2;
    private static final int F_BLACK = 4;
    private static final int F_RED = 8;
    private static final int F_ORANGE = 16;

    private static final float COIN_RADIUS_RATIO = 0.027f;
    private static final float STRIKER_RADIUS_RATIO = 0.036f;

    public static Analysis analyze(Bitmap bmp) {
        Analysis result = new Analysis();
        if (bmp == null || bmp.isRecycled()) {
            return result;
        }
        try {
            return analyzeInternal(bmp, result);
        } catch (RuntimeException e) {
            return new Analysis();
        }
    }

    // ------------------------------------------------------------------ colour flags

    private static int flags(int px, float[] hsv) {
        Color.colorToHSV(px, hsv);
        float hh = hsv[0];
        float s = hsv[1];
        float v = hsv[2];
        int f = 0;
        if (v < 0.33f && s < 0.35f) f |= F_BLACK;
        if (s < 0.22f && v > 0.80f) f |= F_WHITE;
        if ((hh >= 350f || hh <= 8f) && s > 0.65f && v > 0.75f) f |= F_RED;
        if (hh >= 28f && hh <= 48f && s > 0.68f && v > 0.80f) f |= F_ORANGE;
        if (hh >= 15f && hh <= 55f && s >= 0.20f && s <= 0.80f && v > 0.45f) f |= F_WOOD;
        return f;
    }

    /** Longest run of indices whose value is above thr, bridging gaps of up to maxGap cells. */
    private static int[] longestRun(int[] v, float thr, int maxGap) {
        int bestS = -1, bestE = -1, curS = -1, curE = -1, gap = 0;
        for (int i = 0; i < v.length; i++) {
            if (v[i] > thr) {
                if (curS < 0) curS = i;
                curE = i;
                gap = 0;
            } else if (curS >= 0) {
                gap++;
                if (gap > maxGap) {
                    if (bestS < 0 || curE - curS > bestE - bestS) { bestS = curS; bestE = curE; }
                    curS = -1;
                    gap = 0;
                }
            }
        }
        if (curS >= 0 && (bestS < 0 || curE - curS > bestE - bestS)) { bestS = curS; bestE = curE; }
        return bestS < 0 ? null : new int[]{bestS, bestE};
    }

    private static int maxOf(int[] v) {
        int m = 0;
        for (int x : v) if (x > m) m = x;
        return m;
    }

    // ------------------------------------------------------------------ main analysis

    private static Analysis analyzeInternal(Bitmap bmp, Analysis result) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int step = Math.max(2, Math.min(w, h) / 300);
        int gw = w / step;
        int gh = h / step;
        if (gw < 20 || gh < 20) {
            return result;
        }

        byte[] f = new byte[gw * gh];
        float[] hsv = new float[3];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int sx = Math.min(w - 1, gx * step + step / 2);
                int sy = Math.min(h - 1, gy * step + step / 2);
                f[gy * gw + gx] = (byte) flags(bmp.getPixel(sx, sy), hsv);
            }
        }

        // ---- board: the largest block of wood-coloured rows and columns
        int[] rowWood = new int[gh];
        int[] colWood = new int[gw];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                if ((f[gy * gw + gx] & F_WOOD) != 0) { rowWood[gy]++; colWood[gx]++; }
            }
        }
        int[] rr = longestRun(rowWood, Math.max(gw * 0.15f, maxOf(rowWood) * 0.45f), 3);
        int[] cr = longestRun(colWood, Math.max(gh * 0.10f, maxOf(colWood) * 0.45f), 3);
        if (rr == null || cr == null) {
            return result;
        }
        int top = rr[0], bottom = rr[1], left = cr[0], right = cr[1];

        int[] rowWood2 = new int[gh];
        int[] colWood2 = new int[gw];
        for (int gy = top; gy <= bottom; gy++) {
            for (int gx = left; gx <= right; gx++) {
                if ((f[gy * gw + gx] & F_WOOD) != 0) { rowWood2[gy]++; colWood2[gx]++; }
            }
        }
        int[] rr2 = longestRun(rowWood2, (right - left + 1) * 0.40f, 3);
        int[] cr2 = longestRun(colWood2, (bottom - top + 1) * 0.40f, 3);
        if (rr2 != null && cr2 != null) {
            top = rr2[0]; bottom = rr2[1]; left = cr2[0]; right = cr2[1];
        }
        if (bottom - top < 10 || right - left < 10) {
            return result;
        }

        int bw = right - left + 1;
        int bh = bottom - top + 1;
        if (bw > bh * 1.15f) {
            int cxm = (left + right) / 2;
            left = cxm - bh / 2;
            right = left + bh - 1;
        } else if (bh > bw * 1.15f) {
            int cym = (top + bottom) / 2;
            top = cym - bw / 2;
            bottom = top + bw - 1;
        }
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(gw - 1, right);
        bottom = Math.min(gh - 1, bottom);

        float boardWpx = (right - left + 1) * (float) step;
        float boardHpx = (bottom - top + 1) * (float) step;
        float base = Math.min(boardWpx, boardHpx);
        // A real carrom board fills most of the screen width. Anything much smaller is a menu or lobby.
        if (base < 0.45f * Math.min(w, h)) {
            return result;
        }

        result.board.left = left * step;
        result.board.top = top * step;
        result.board.right = (right + 1) * step;
        result.board.bottom = (bottom + 1) * step;
        result.board.detected = true;
        float boardH = result.board.height();

        // ---- pockets: the big dark disc in each corner (fallback: fixed inset from the corner)
        int cw = Math.max(6, Math.round(0.16f * base / step));
        int[][] corners = {
                {left, top},
                {right - cw + 1, top},
                {left, bottom - cw + 1},
                {right - cw + 1, bottom - cw + 1}};
        float inset = 0.04f * base;
        float[][] fallback = {
                {result.board.left + inset, result.board.top + inset},
                {result.board.right - inset, result.board.top + inset},
                {result.board.left + inset, result.board.bottom - inset},
                {result.board.right - inset, result.board.bottom - inset}};
        boolean[] seen = new boolean[gw * gh];
        for (int i = 0; i < 4; i++) {
            Pocket p = findPocket(f, seen, gw, gh, corners[i][0], corners[i][1], cw, step, base);
            if (p == null) p = new Pocket(fallback[i][0], fallback[i][1], 0.04f * base);
            result.pockets.add(p);
        }

        float rc = COIN_RADIUS_RATIO * base;
        float rs = STRIKER_RADIUS_RATIO * base;
        float rcg = rc / step;

        // ---- striker: the glowing orange ring around it (only shown on your own turn)
        Piece striker = findStriker(f, gw, gh, left, top, right, bottom, step, base,
                result.board, result.pockets, rs);
        if (striker != null) {
            result.pieces.add(striker);
            result.striker = striker;
        }

        List<float[]> excl = new ArrayList<>();
        for (Pocket pk : result.pockets) {
            excl.add(new float[]{pk.x / step - 0.5f, pk.y / step - 0.5f, 1.3f * pk.radius / step});
        }
        if (striker != null) {
            excl.add(new float[]{striker.x / step - 0.5f, striker.y / step - 0.5f, 1.6f * rs / step});
        }

        // ---- coins: dense blobs of one colour; works even when a coin is cut up by glare marks
        List<float[]> blackPeaks = findPeaks(f, F_BLACK, gw, gh, left, top, right, bottom, rcg, 0.55f, excl);
        List<float[]> whitePeaks = findPeaks(f, F_WHITE, gw, gh, left, top, right, bottom, rcg, 0.62f, excl);
        List<float[]> redPeaks = findPeaks(f, F_RED, gw, gh, left, top, right, bottom, rcg, 0.60f, excl);

        List<float[]> blackCoins = new ArrayList<>(blackPeaks);
        List<float[]> whiteCoins = new ArrayList<>();
        for (float[] wp : whitePeaks) {
            // A "white" blob that sits on dark pixels is a black coin with a white glare stripe.
            if (maskFraction(f, F_BLACK, gw, gh, wp[0], wp[1], rcg) > 0.15f) {
                boolean dup = false;
                for (float[] b : blackCoins) {
                    float dx = b[0] - wp[0], dy = b[1] - wp[1];
                    if (dx * dx + dy * dy < (1.3f * rcg) * (1.3f * rcg)) { dup = true; break; }
                }
                if (!dup) blackCoins.add(wp);
            } else {
                whiteCoins.add(wp);
            }
        }
        for (float[] p : blackCoins) {
            result.pieces.add(new Piece((p[0] + 0.5f) * step, (p[1] + 0.5f) * step, rc, PieceType.BLACK));
        }
        for (float[] p : whiteCoins) {
            result.pieces.add(new Piece((p[0] + 0.5f) * step, (p[1] + 0.5f) * step, rc, PieceType.WHITE));
        }
        for (float[] p : redPeaks) {
            result.pieces.add(new Piece((p[0] + 0.5f) * step, (p[1] + 0.5f) * step, rc, PieceType.QUEEN));
        }
        return result;
    }

    // ------------------------------------------------------------------ pockets

    private static Pocket findPocket(byte[] f, boolean[] seen, int gw, int gh,
                                     int x0, int y0, int size, int step, float base) {
        int bestN = 0;
        double bestSx = 0, bestSy = 0;
        int x1 = Math.min(gw - 1, x0 + size - 1);
        int y1 = Math.min(gh - 1, y0 + size - 1);
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        int[] stack = new int[(x1 - x0 + 1) * (y1 - y0 + 1) + 1];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int idx = y * gw + x;
                if (seen[idx] || (f[idx] & F_BLACK) == 0) continue;
                int sp = 0;
                stack[sp++] = idx;
                seen[idx] = true;
                int n = 0;
                double sx = 0, sy = 0;
                while (sp > 0) {
                    int cur = stack[--sp];
                    int cx = cur % gw;
                    int cy = cur / gw;
                    n++;
                    sx += cx;
                    sy += cy;
                    if (cx + 1 <= x1) { int ni = cur + 1; if (!seen[ni] && (f[ni] & F_BLACK) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cx - 1 >= x0) { int ni = cur - 1; if (!seen[ni] && (f[ni] & F_BLACK) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cy + 1 <= y1) { int ni = cur + gw; if (!seen[ni] && (f[ni] & F_BLACK) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cy - 1 >= y0) { int ni = cur - gw; if (!seen[ni] && (f[ni] & F_BLACK) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                }
                if (n > bestN) {
                    bestN = n;
                    bestSx = sx / n;
                    bestSy = sy / n;
                }
            }
        }
        if (bestN < 20) return null;
        float r = (float) Math.sqrt(bestN / Math.PI) * step;
        return new Pocket((float) (bestSx + 0.5) * step, (float) (bestSy + 0.5) * step,
                Math.max(r, 0.036f * base));
    }

    // ------------------------------------------------------------------ striker

    private static Piece findStriker(byte[] f, int gw, int gh, int left, int top, int right, int bottom,
                                     int step, float base, Board board, List<Pocket> pockets, float rs) {
        boolean[] seen = new boolean[gw * gh];
        int[] stack = new int[gw * gh];
        int bestN = 0;
        float bestX = 0, bestY = 0;
        float boardH = board.height();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int idx = y * gw + x;
                if (seen[idx] || (f[idx] & F_ORANGE) == 0) continue;
                int sp = 0;
                stack[sp++] = idx;
                seen[idx] = true;
                int n = 0;
                int minX = x, maxX = x, minY = y, maxY = y;
                while (sp > 0) {
                    int cur = stack[--sp];
                    int cx = cur % gw;
                    int cy = cur / gw;
                    n++;
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;
                    if (cx + 1 <= right) { int ni = cur + 1; if (!seen[ni] && (f[ni] & F_ORANGE) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cx - 1 >= left) { int ni = cur - 1; if (!seen[ni] && (f[ni] & F_ORANGE) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cy + 1 <= bottom) { int ni = cur + gw; if (!seen[ni] && (f[ni] & F_ORANGE) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                    if (cy - 1 >= top) { int ni = cur - gw; if (!seen[ni] && (f[ni] & F_ORANGE) != 0) { seen[ni] = true; stack[sp++] = ni; } }
                }
                float cwid = maxX - minX + 1;
                float chei = maxY - minY + 1;
                float d = (cwid + chei) / 2f * step;
                float asp = cwid / chei;
                if (d < 0.08f * base || d > 0.15f * base || asp < 0.7f || asp > 1.4f) continue;
                float bx = (minX + maxX + 1) / 2f * step;
                float by = (minY + maxY + 1) / 2f * step;
                if (by < board.top + 0.55f * boardH) continue;
                boolean nearPocket = false;
                for (Pocket pk : pockets) {
                    float dx = bx - pk.x, dy = by - pk.y;
                    if (Math.sqrt(dx * dx + dy * dy) < 2.2f * pk.radius) { nearPocket = true; break; }
                }
                if (nearPocket) continue;
                if (n > bestN) {
                    bestN = n;
                    bestX = bx;
                    bestY = by;
                }
            }
        }
        if (bestN == 0) return null;
        return new Piece(bestX, bestY, rs, PieceType.STRIKER);
    }

    // ------------------------------------------------------------------ coin peaks

    private static int[] integral(byte[] f, int bit, int gw, int gh) {
        int[] ii = new int[(gw + 1) * (gh + 1)];
        for (int y = 0; y < gh; y++) {
            int rowSum = 0;
            for (int x = 0; x < gw; x++) {
                if ((f[y * gw + x] & bit) != 0) rowSum++;
                ii[(y + 1) * (gw + 1) + (x + 1)] = ii[y * (gw + 1) + (x + 1)] + rowSum;
            }
        }
        return ii;
    }

    /** Returns {xCell, yCell, density} for every dense blob, strongest first, one per coin. */
    private static List<float[]> findPeaks(byte[] f, int bit, int gw, int gh,
                                           int left, int top, int right, int bottom,
                                           float rcg, float thr, List<float[]> excl) {
        List<float[]> out = new ArrayList<>();
        int half = Math.max(2, Math.round(0.55f * rcg));
        int area = (2 * half + 1) * (2 * half + 1);
        int[] ii = integral(f, bit, gw, gh);
        int stride = gw + 1;

        int y0 = Math.max(top + half, half);
        int y1 = Math.min(bottom - half, gh - 1 - half);
        int x0 = Math.max(left + half, half);
        int x1 = Math.min(right - half, gw - 1 - half);
        if (y1 < y0 || x1 < x0) return out;

        long[] keys = new long[(y1 - y0 + 1) * (x1 - x0 + 1)];
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int s = ii[(y + half + 1) * stride + (x + half + 1)]
                        - ii[(y - half) * stride + (x + half + 1)]
                        - ii[(y + half + 1) * stride + (x - half)]
                        + ii[(y - half) * stride + (x - half)];
                float density = s / (float) area;
                if (density >= thr) {
                    keys[n++] = ((long) (1000 - Math.round(density * 1000f)) << 32) | (long) (y * gw + x);
                }
            }
        }
        Arrays.sort(keys, 0, n);

        float minSep = 1.5f * rcg;
        List<int[]> chosen = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int idx = (int) (keys[k] & 0xffffffffL);
            int y = idx / gw;
            int x = idx % gw;
            boolean skip = false;
            for (int[] c : chosen) {
                float dx = x - c[0], dy = y - c[1];
                if (dx * dx + dy * dy < minSep * minSep) { skip = true; break; }
            }
            if (skip) continue;
            for (float[] e : excl) {
                float dx = x - e[0], dy = y - e[1];
                if (dx * dx + dy * dy < e[2] * e[2]) { skip = true; break; }
            }
            if (skip) continue;
            chosen.add(new int[]{x, y});

            // refine the centre: average of the coloured cells inside one coin radius
            float cx = x, cy = y;
            float reach = rcg * 1.1f;
            int r = (int) Math.ceil(reach);
            for (int iter = 0; iter < 3; iter++) {
                double sx = 0, sy = 0;
                int cnt = 0;
                for (int yy = Math.max(0, (int) cy - r); yy <= Math.min(gh - 1, (int) cy + r); yy++) {
                    for (int xx = Math.max(0, (int) cx - r); xx <= Math.min(gw - 1, (int) cx + r); xx++) {
                        if ((f[yy * gw + xx] & bit) == 0) continue;
                        float dx = xx - cx, dy = yy - cy;
                        if (dx * dx + dy * dy > reach * reach) continue;
                        sx += xx;
                        sy += yy;
                        cnt++;
                    }
                }
                if (cnt == 0) break;
                cx = (float) (sx / cnt);
                cy = (float) (sy / cnt);
            }
            out.add(new float[]{cx, cy, 1000 - (keys[k] >>> 32)});
        }
        return out;
    }

    private static float maskFraction(byte[] f, int bit, int gw, int gh, float cx, float cy, float rad) {
        int r = (int) Math.ceil(rad);
        int total = 0, hit = 0;
        for (int yy = Math.max(0, (int) cy - r); yy <= Math.min(gh - 1, (int) cy + r); yy++) {
            for (int xx = Math.max(0, (int) cx - r); xx <= Math.min(gw - 1, (int) cx + r); xx++) {
                float dx = xx - cx, dy = yy - cy;
                if (dx * dx + dy * dy > rad * rad) continue;
                total++;
                if ((f[yy * gw + xx] & bit) != 0) hit++;
            }
        }
        return total == 0 ? 0f : hit / (float) total;
    }
}
