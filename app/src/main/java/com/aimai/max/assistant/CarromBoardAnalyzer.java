package com.aimai.max.assistant;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the carrom board, its four pockets, every coin (any colour), the queen and the striker.
 * Sizes are calibrated on real Carrom Disc Pool frames: coin radius is 2.7% and striker radius 3.6%
 * of the playing-board width, which holds on every phone because the board always fills the width.
 * Coins are found as dense blobs that are NOT board-wood coloured, so coin skins of any colour work.
 */
public final class CarromBoardAnalyzer {

    private CarromBoardAnalyzer() {}

    public enum PieceType { STRIKER, QUEEN, BLACK, WHITE, OTHER, UNKNOWN }

    public static class Piece {
        public float x;
        public float y;
        public float radius;
        public PieceType type;
        /** Average colour of the piece (ARGB). */
        public int rgb;
        /** True when this coin belongs to the player (set by the scan pipeline). */
        public boolean mine;

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
    private static final int F_COLOR = 32;
    private static final int F_NONWOOD = 64;
    private static final int F_GLOW = 128;

    private static final float COIN_RADIUS_RATIO = 0.027f;
    private static final float STRIKER_RADIUS_RATIO = 0.036f;

    /** Fixed decoration circles of the board, as fractions of the board box (x, y). */
    private static final float[][] DECO = {
            {0.2215f, 0.143f}, {0.78f, 0.143f}, {0.148f, 0.213f}, {0.86f, 0.213f},
            {0.148f, 0.781f}, {0.86f, 0.781f}, {0.2215f, 0.851f}, {0.78f, 0.851f}};

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
        boolean orange = hh >= 28f && hh <= 48f && s > 0.68f && v > 0.80f;
        if (orange) f |= F_ORANGE;
        if (s > 0.50f && v > 0.35f && hh > 60f && hh < 350f) f |= F_COLOR;
        boolean wood = hh >= 15f && hh <= 55f && s >= 0.20f && s <= 0.80f && v > 0.45f;
        if (wood) f |= F_WOOD;
        if (!wood && v > 0.10f) f |= F_NONWOOD;
        // bright glow around the active striker: orange, cyan, blue or purple
        if (orange || (s > 0.40f && v > 0.60f && hh >= 150f && hh <= 330f)
                || (s > 0.50f && v > 0.60f && hh > 60f && hh < 350f)) {
            f |= F_GLOW;
        }
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
        int step = Math.max(2, Math.round(Math.min(w, h) / 330f));
        int gw = w / step;
        int gh = h / step;
        if (gw < 20 || gh < 20) {
            return result;
        }

        byte[] f = new byte[gw * gh];
        byte[] lum = new byte[gw * gh];
        float[] hsv = new float[3];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int sx = Math.min(w - 1, gx * step + step / 2);
                int sy = Math.min(h - 1, gy * step + step / 2);
                int px = bmp.getPixel(sx, sy);
                f[gy * gw + gx] = (byte) flags(px, hsv);
                lum[gy * gw + gx] = (byte) Math.round(hsv[2] * 255f);
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

        // ---- striker: white-rimmed ring first, then the glow ring (any striker design)
        Piece striker = findStrikerRing(f, gw, gh, left, top, right, bottom, step, base, result.board, rs);
        if (striker == null) {
            striker = findStrikerGlow(f, lum, gw, gh, left, top, right, bottom, step, base, rs);
        }
        if (striker != null) {
            result.pieces.add(striker);
            result.striker = striker;
        }

        List<float[]> excl = new ArrayList<>();
        for (Pocket pk : result.pockets) {
            excl.add(new float[]{pk.x / step - 0.5f, pk.y / step - 0.5f, 1.3f * pk.radius / step});
        }
        if (striker != null) {
            // the glow ring around the striker also looks "not wood": keep it out of the coin search
            excl.add(new float[]{striker.x / step - 0.5f, striker.y / step - 0.5f, 1.75f * rs / step});
        }

        // ---- coins of any colour: dense blobs that are not wood-coloured
        List<float[]> peaks = findPeaks(f, F_NONWOOD, gw, gh, left, top, right, bottom, rcg, 0.62f, excl);
        for (float[] p : peaks) {
            float px = (p[0] + 0.5f) * step, py = (p[1] + 0.5f) * step;
            Piece piece = classifyCoin(bmp, px, py, rc, step);
            if (isDecoration(piece, result.board, px, py)) continue;
            if (isCornerBracket(piece, result.pockets, px, py, base)) continue;
            result.pieces.add(piece);
        }
        return result;
    }

    // ------------------------------------------------------------------ coin colour

    private static Piece classifyCoin(Bitmap bmp, float px, float py, float rc, int step) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        float[] hsv = new float[3];
        int[] sr = new int[25], sg = new int[25], sb = new int[25];
        int n = 0, dark = 0;
        float r0 = 0.6f * rc;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                float ox = dx * r0 / 2f, oy = dy * r0 / 2f;
                if (ox * ox + oy * oy > r0 * r0) continue;
                int x = Math.max(0, Math.min(w - 1, Math.round(px + ox)));
                int y = Math.max(0, Math.min(h - 1, Math.round(py + oy)));
                int c = bmp.getPixel(x, y);
                sr[n] = Color.red(c);
                sg[n] = Color.green(c);
                sb[n] = Color.blue(c);
                Color.colorToHSV(c, hsv);
                if (hsv[2] < 0.35f && hsv[1] < 0.45f) dark++;
                n++;
            }
        }
        Arrays.sort(sr, 0, n);
        Arrays.sort(sg, 0, n);
        Arrays.sort(sb, 0, n);
        int ar = sr[n / 2], ag = sg[n / 2], ab = sb[n / 2];
        int rgb = Color.rgb(ar, ag, ab);
        Color.colorToHSV(rgb, hsv);
        float hh = hsv[0], s = hsv[1], v = hsv[2];
        float darkFrac = dark / (float) n;

        PieceType type;
        if ((v < 0.35f && s < 0.45f) || (darkFrac >= 0.25f && s < 0.45f)) {
            type = PieceType.BLACK;
        } else if (s < 0.28f && v > 0.70f) {
            type = PieceType.WHITE;
        } else if ((hh >= 345f || hh <= 12f) && s > 0.70f && v > 0.70f) {
            type = PieceType.QUEEN;
        } else {
            type = PieceType.OTHER;
        }
        Piece piece = new Piece(px, py, rc, type);
        piece.rgb = rgb;
        return piece;
    }

    /** The eight fixed circles painted on the board are red/orange; real coins are not mistaken for them. */
    private static boolean isDecoration(Piece piece, Board board, float px, float py) {
        if (piece.type == PieceType.BLACK || piece.type == PieceType.WHITE) return false;
        float[] hsv = new float[3];
        Color.colorToHSV(piece.rgb, hsv);
        boolean reddish = hsv[0] < 25f && hsv[1] > 0.55f && hsv[1] < 0.80f;
        if (!reddish) return false;
        float rx = (px - board.left) / board.width();
        float ry = (py - board.top) / board.height();
        for (float[] d : DECO) {
            if (Math.abs(rx - d[0]) < 0.05f && Math.abs(ry - d[1]) < 0.05f) return true;
        }
        return false;
    }

    /** Golden L-shaped brackets at the board corners are solid saturated yellow-orange. */
    private static boolean isCornerBracket(Piece piece, List<Pocket> pockets, float px, float py, float base) {
        if (piece.type != PieceType.OTHER) return false;
        float[] hsv = new float[3];
        Color.colorToHSV(piece.rgb, hsv);
        if (!(hsv[1] > 0.93f && hsv[2] > 0.93f && hsv[0] > 35f && hsv[0] < 56f)) return false;
        for (Pocket pk : pockets) {
            float dx = px - pk.x, dy = py - pk.y;
            if (Math.sqrt(dx * dx + dy * dy) < 0.14f * base) return true;
        }
        return false;
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

    /**
     * White-rimmed strikers: a white rim, a patterned (non-white) centre and nothing white just outside.
     * Plain coins are white all the way through, so they never match.
     */
    private static Piece findStrikerRing(byte[] f, int gw, int gh, int left, int top, int right, int bottom,
                                         int step, float base, Board board, float rs) {
        float ringR = 0.0285f * base / step;
        float innerR = 0.0095f * base / step;
        float outerR = 0.043f * base / step;
        int y0 = (int) (top + 0.55f * (bottom - top));
        double bestScore = -10;
        int bestX = -1, bestY = -1;
        for (int y = y0; y <= bottom - 2; y++) {
            for (int x = left + 2; x <= right - 2; x++) {
                if ((f[y * gw + x] & F_WHITE) != 0) continue;
                float ring = ringHit(f, F_WHITE, gw, gh, x, y, ringR, 24);
                if (ring < 0.55f) continue;
                float inner = ringHit(f, F_WHITE, gw, gh, x, y, innerR, 8);
                if (inner > 0.30f) continue;
                float outer = ringHit(f, F_WHITE, gw, gh, x, y, outerR, 24);
                if (outer > 0.35f) continue;
                double score = ring - inner - outer;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) return null;
        return refinedStriker(f, F_WHITE, gw, gh, bestX, bestY, ringR, 0.6f, 1.25f, step, rs);
    }

    /**
     * Glowing strikers (orange, cyan, blue ... ring that appears on your own turn). Works for any striker
     * design. The middle must show a pattern, so a plain-wood patch with a ring-shaped mark never matches.
     */
    private static Piece findStrikerGlow(byte[] f, byte[] lum, int gw, int gh, int left, int top, int right,
                                         int bottom, int step, float base, float rs) {
        float ringR = 0.047f * base / step;
        int y0 = (int) (top + 0.55f * (bottom - top));
        double bestScore = -10;
        int bestX = -1, bestY = -1;
        for (int y = y0; y <= bottom - 2; y++) {
            for (int x = left + 2; x <= right - 2; x++) {
                float ring = ringHit(f, F_GLOW, gw, gh, x, y, ringR, 24);
                if (ring < 0.60f) continue;
                float ring2 = ringHit(f, F_GLOW, gw, gh, x, y, ringR * 0.85f, 24);
                double score = ring + ring2 * 0.5;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) return null;
        // the striker body in the middle must be patterned (high brightness variation)
        float rin = 0.4f * rs / step;
        int reach = (int) Math.ceil(rin);
        double sum = 0, sumSq = 0;
        int n = 0;
        for (int yy = Math.max(0, bestY - reach); yy <= Math.min(gh - 1, bestY + reach); yy++) {
            for (int xx = Math.max(0, bestX - reach); xx <= Math.min(gw - 1, bestX + reach); xx++) {
                float dx = xx - bestX, dy = yy - bestY;
                if (dx * dx + dy * dy > rin * rin) continue;
                int l = lum[yy * gw + xx] & 0xFF;
                sum += l;
                sumSq += (double) l * l;
                n++;
            }
        }
        if (n < 4) return null;
        double mean = sum / n;
        double std = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
        if (std < 10) return null;
        return refinedStriker(f, F_GLOW, gw, gh, bestX, bestY, ringR, 0.65f, 1.3f, step, rs);
    }

    private static Piece refinedStriker(byte[] f, int bit, int gw, int gh, int bestX, int bestY, float ringR,
                                        float lo, float hi, int step, float rs) {
        int reach = (int) Math.ceil(hi * ringR);
        double sx = 0, sy = 0;
        int cnt = 0;
        for (int yy = Math.max(0, bestY - reach); yy <= Math.min(gh - 1, bestY + reach); yy++) {
            for (int xx = Math.max(0, bestX - reach); xx <= Math.min(gw - 1, bestX + reach); xx++) {
                if ((f[yy * gw + xx] & bit) == 0) continue;
                double d = Math.hypot(xx - bestX, yy - bestY);
                if (d < lo * ringR || d > hi * ringR) continue;
                sx += xx;
                sy += yy;
                cnt++;
            }
        }
        float cx = bestX, cy = bestY;
        if (cnt > 5) {
            cx = (float) (sx / cnt);
            cy = (float) (sy / cnt);
        }
        return new Piece((cx + 0.5f) * step, (cy + 0.5f) * step, rs, PieceType.STRIKER);
    }

    private static float ringHit(byte[] f, int bit, int gw, int gh, float cx, float cy, float rad, int samples) {
        int hit = 0;
        for (int i = 0; i < samples; i++) {
            double a = 2 * Math.PI * i / samples;
            int x = Math.round((float) (cx + Math.cos(a) * rad));
            int y = Math.round((float) (cy + Math.sin(a) * rad));
            if (x >= 0 && x < gw && y >= 0 && y < gh && (f[y * gw + x] & bit) != 0) hit++;
        }
        return hit / (float) samples;
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

            // refine the centre: average of the matching cells inside one coin radius
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
}
