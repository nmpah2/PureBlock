package com.pureblock.lightweight;

import android.graphics.Bitmap;

public class MathematicalAnalyzer {

    // Pre-allocated array to keep memory footprint under 1MB during processing
    private static int[] pixels = new int[160 * 120];

    public static boolean analyzeFrame(Bitmap downscaledBitmap) {
        if (downscaledBitmap == null) return false;

        int width = downscaledBitmap.getWidth();
        int height = downscaledBitmap.getHeight();
        
        // Dump pixels into primitive array (Zero memory allocation overhead)
        downscaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int skinPixelCount = 0;
        int edgeCurveCount = 0;
        int totalPixels = width * height;

        // Optimized mathematical scanning loop
        for (int y = 1; y < height - 1; y += 2) { // Step by 2 for 50% speed optimization
            for (int x = 1; x < width - 1; x += 2) {
                int idx = y * width + x;
                int pixel = pixels[idx];

                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                // 1. Explicit/Mild Skin-Tone Matrix Formula (RGB Space)
                boolean isSkin = (r > 95 && g > 40 && b > 20 &&
                        (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15) &&
                        Math.abs(r - g) > 15 && r > g && r > b);

                if (isSkin) {
                    skinPixelCount++;

                    // 2. Inline Lightweight Sobel Edge Detection (Detects fabric folds/body curves)
                    // Calculates gradient changes of neighboring pixels
                    int pLeft = pixels[idx - 1] & 0xff;
                    int pRight = pixels[idx + 1] & 0xff;
                    int pTop = pixels[idx - width] & 0xff;
                    int pBottom = pixels[idx + width] & 0xff;

                    int hGradient = Math.abs(pLeft - pRight);
                    int vGradient = Math.abs(pTop - pBottom);

                    // If a skin pixel sits precisely on a soft structural gradient curve
                    if ((hGradient + vGradient) > 30 && (hGradient + vGradient) < 80) {
                        edgeCurveCount++;
                    }
                }
            }
        }

        double skinRatio = (double) skinPixelCount / (totalPixels / 4.0);
        double curveRatio = (double) edgeCurveCount / (double) Math.max(1, skinPixelCount);

        // Threshold tuning for mild sensual focus composition patterns
        return (skinRatio > 0.25 && curveRatio > 0.35);
    }
          }
