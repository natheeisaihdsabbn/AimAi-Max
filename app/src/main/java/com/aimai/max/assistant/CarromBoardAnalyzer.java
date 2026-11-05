package com.aimai.max.assistant;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private static final int C_NONE = 0;
    private static final int C_WOOD = 1;
    private static final int C_WHITE = 2;
    private static final int C_BLACK = 3;
    private static final int C_RED = 4;

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

    private static Analysis analyzeInternal(Bitmap bmp, Analysis result) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int step = Math.max(2, Math.min(w, h) / 300);
        int gw = w / step;
        int gh = h / step;
        if (gw < 20 || gh < 20) {
            return result;
        }

        int[] cls = new int[gw * gh];
        float[] hsv = new float[3];

        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int sx = Math.min(w - 1, gx * step + step / 2);
                int sy = Math.min(h - 1, gy * step + step / 2);
                cls[gy * gw + gx] = classify(bmp.getPixel(sx, sy), hsv);
            }
        }

        // Pass 1: coarse extent from the densest wood rows/columns (ignores small wooden HUD bits).
        int[] rowWood = new int[gh];
        int[] colWood = new int[gw];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                if (cls[gy * gw + gx] == C_WOOD) { rowWood[gy]++; colWood[gx]++; }
            }
        }
        int[] rr = longestRun(rowWood, Math.max(gw * 0.15f, maxOf(rowWood) * 0.45f), 3);
        int[] cr = longestRun(colWood, Math.max(gh * 0.10f, maxOf(colWood) * 0.45f), 3);
        if (rr == null || cr == null) {
            return result;
        }
        int top = rr[0], bottom = rr[1], left = cr[0], right = cr[1];

        // Pass 2: recount only inside the coarse box so outside wood cannot stretch the edges.
        int[] rowWood2 = new int[gh];
        int[] colWood2 = new int[gw];
        for (int gy = top; gy <= bottom; gy++) {
            for (int gx = left; gx <= right; gx++) {
                if (cls[gy * gw + gx] == C_WOOD) { rowWood2[gy]++; colWood2[gx]++; }
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

        result.board.left = left * step;
        result.board.top = top * step;
        result.board.right = (right + 1) * step;
        result.board.bottom = (bottom + 1) * step;
        result.board.detected = true;

        float boardW = result.board.width();
        float boardH = result.board.height();
        float base = Math.min(boardW, boardH);

        float pr = base * 0.05f;
        float inset = pr * 0.9f;
        result.pockets.add(new Pocket(result.board.left + inset, result.board.top + inset, pr));
        result.pockets.add(new Pocket(result.board.right - inset, result.board.top + inset, pr));
        result.pockets.add(new Pocket(result.board.left + inset, result.board.bottom - inset, pr));
        result.pockets.add(new Pocket(result.board.right - inset, result.board.bottom - inset, pr));

        float dMin = base * 0.022f;
        float dMax = base * 0.10f;

        boolean[] seen = new boolean[gw * gh];
        int[] stack = new int[gw * gh];
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int idx = y * gw + x;
                int c = cls[idx];
                if (seen[idx] || c < C_WHITE) {
                    continue;
                }
                int sp = 0;
                stack[sp++] = idx;
                seen[idx] = true;
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                int count = 0;

                while (sp > 0) {
                    int cur = stack[--sp];
                    int cx = cur % gw;
                    int cy = cur / gw;
                    count++;
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;
                    for (int d = 0; d < 4; d++) {
                        int nx = cx + dx[d];
                        int ny = cy + dy[d];
                        if (nx < left || nx > right || ny < top || ny > bottom) {
                            continue;
                        }
                        int ni = ny * gw + nx;
                        if (!seen[ni] && cls[ni] == c) {
                            seen[ni] = true;
                            stack[sp++] = ni;
                        }
                    }
                }

                int cw = maxX - minX + 1;
                int ch = maxY - minY + 1;
                float diameter = (cw + ch) / 2f * step;
                if (diameter < dMin || diameter > dMax) {
                    continue;
                }
                float aspect = cw / (float) ch;
                if (aspect < 0.6f || aspect > 1.67f) {
                    continue;
                }
                float fill = count / (float) (cw * ch);
                if (fill < 0.45f) {
                    continue;
                }

                float px = (minX + maxX + 1) / 2f * step;
                float py = (minY + maxY + 1) / 2f * step;
                float radius = (cw + ch) / 4f * step;

                boolean nearPocket = false;
                for (Pocket pk : result.pockets) {
                    float ddx = px - pk.x;
                    float ddy = py - pk.y;
                    if (Math.sqrt(ddx * ddx + ddy * ddy) < pk.radius * 1.7f) {
                        nearPocket = true;
                        break;
                    }
                }
                if (nearPocket) {
                    continue;
                }

                PieceType type = PieceType.UNKNOWN;
                if (c == C_WHITE) type = PieceType.WHITE;
                else if (c == C_BLACK) type = PieceType.BLACK;
                else if (c == C_RED) type = PieceType.QUEEN;

                result.pieces.add(new Piece(px, py, radius, type));
            }
        }

        if (result.pieces.isEmpty()) {
            return result;
        }

        // Striker = the biggest white disc in the lower part of the board, clearly larger than the coins.
        Piece cand = null;
        for (Piece p : result.pieces) {
            if (p.type != PieceType.WHITE) continue;
            if (p.y < result.board.top + boardH * 0.70f) continue;
            if (cand == null || p.radius > cand.radius) {
                cand = p;
            }
        }
        if (cand != null) {
            List<Float> radii = new ArrayList<>();
            for (Piece p : result.pieces) {
                if (p != cand) radii.add(p.radius);
            }
            boolean bigEnough = true;
            if (!radii.isEmpty()) {
                Collections.sort(radii);
                float median = radii.get(radii.size() / 2);
                bigEnough = cand.radius >= median * 1.12f;
            }
            if (bigEnough) {
                cand.type = PieceType.STRIKER;
                result.striker = cand;
            }
        }
        return result;
    }

    private static int classify(int px, float[] hsv) {
        Color.colorToHSV(px, hsv);
        float hh = hsv[0];
        float s = hsv[1];
        float v = hsv[2];
        if (v < 0.22f) return C_BLACK;
        if (s < 0.18f && v > 0.80f) return C_WHITE;
        if ((hh < 14f || hh > 346f) && s > 0.50f && v > 0.40f) return C_RED;
        if (hh >= 18f && hh <= 52f && s >= 0.22f && s <= 0.80f && v > 0.50f) return C_WOOD;
        return C_NONE;
    }
}
