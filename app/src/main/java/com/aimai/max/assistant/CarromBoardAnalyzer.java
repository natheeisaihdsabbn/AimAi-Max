package com.aimai.max.assistant;

import android.graphics.Bitmap;
import android.graphics.Color;

public class CarromBoardAnalyzer {

    public static class AnalysisResult {
        public final boolean boardCandidateFound;
        public final float centerX;
        public final float centerY;
        public final float boardWidth;
        public final float boardHeight;

        public AnalysisResult(boolean found, float cx, float cy,
                              float width, float height) {
            boardCandidateFound = found;
            centerX = cx;
            centerY = cy;
            boardWidth = width;
            boardHeight = height;
        }
    }

    public AnalysisResult analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < 100 || bitmap.getHeight() < 100) {
            return new AnalysisResult(false, 0f, 0f, 0f, 0f);
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int warmPixels = 0;
        int totalPixels = 0;

        int stepX = Math.max(1, width / 180);
        int stepY = Math.max(1, height / 180);

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                totalPixels++;

                boolean warm = red > 110
                        && green > 65
                        && red > green
                        && green > blue * 0.85f
                        && red - blue > 20;

                if (warm) {
                    warmPixels++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (totalPixels == 0 || maxX <= minX || maxY <= minY) {
            return new AnalysisResult(false, 0f, 0f, 0f, 0f);
        }

        float warmRatio = (float) warmPixels / totalPixels;
        float boxWidth = (float) (maxX - minX) / width;
        float boxHeight = (float) (maxY - minY) / height;
        float aspectRatio = (float) (maxX - minX) / (maxY - minY);

        boolean candidate = warmRatio > 0.08f
                && warmRatio < 0.75f
                && boxWidth > 0.20f
                && boxHeight > 0.20f
                && aspectRatio > 0.65f
                && aspectRatio < 1.45f;

        if (!candidate) {
            return new AnalysisResult(false, 0f, 0f, 0f, 0f);
        }

        float centerX = ((minX + maxX) / 2f) / width;
        float centerY = ((minY + maxY) / 2f) / height;

        return new AnalysisResult(
                true,
                centerX,
                centerY,
                boxWidth,
                boxHeight
        );
    }
}
