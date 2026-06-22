package com.pureblock.lightweight;

import android.graphics.Bitmap;

public class MathematicalAnalyzer {

    public static boolean analyzeFrame(Bitmap downscaledBitmap) {
        if (downscaledBitmap == null) return false;

        int width = downscaledBitmap.getWidth();
        int height = downscaledBitmap.getHeight();
        
        // Fix: Dynamic allocation based on actual padded width to prevent array crashes
        int[] pixels = new int[width * height];
        downscaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int skinPixelCount = 0;
        int edgeCurveCount = 0;
        int totalPixels = width * height;

        // Optimized mathematical scanning loop (stepping by 2 for speed)
        for (int y = 1; y < height - 1; y += 2) {
            for (int x = 1; x < width - 1; x += 2) {
                int idx = y * width + x;
                int pixel = pixels[idx];

                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                // Skin-Tone Matrix Formula
                boolean isSkin = (r > 95 && g > 40 && b > 20 &&
                        (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15) &&
                        Math.abs(r - g) > 15 && r > g && r > b);

                if (isSkin) {
                    skinPixelCount++;

                    // Soft structural gradient curve tracking (Sobel logic)
                    int pLeft = pixels[idx - 1] & 0xff;
                    int pRight = pixels[idx + 1] & 0xff;
                    int pTop = pixels[idx - width] & 0xff;
                    int pBottom = pixels[idx + width] & 0xff;

                    int hGradient = Math.abs(pLeft - pRight);
                    int vGradient = Math.abs(pTop - pBottom);

                    if ((hGradient + vGradient) > 30 && (hGradient + vGradient) < 80) {
                        edgeCurveCount++;
                    }
                }
            }
        }

        double skinRatio = (double) skinPixelCount / (totalPixels / 4.0);
        double curveRatio = (double) edgeCurveCount / (double) Math.max(1, skinPixelCount);

        // Clear memory immediately
        pixels = null;

        return (skinRatio > 0.25 && curveRatio > 0.35);
    }
}
