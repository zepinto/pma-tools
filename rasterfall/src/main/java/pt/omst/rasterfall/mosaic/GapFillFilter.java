//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.mosaic;

import java.awt.image.BufferedImage;

/**
 * A filter that fills transparent (alpha=0) pixels by interpolating from
 * neighboring non-transparent pixels vertically. This preserves the resolution
 * of existing data while filling gaps in sidescan mosaic images.
 */
public class GapFillFilter {

    /**
     * Fill transparent gaps in the image by interpolation in both directions.
     * Only pixels with alpha=0 are modified; existing data is preserved.
     * 
     * @param image The image to process (modified in place)
     * @param maxGapSize Maximum gap size to fill (in pixels)
     */
    public static void fillGaps(BufferedImage image, int maxGapSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        // First pass: fill vertical gaps (column by column)
        for (int x = 0; x < width; x++) {
            fillColumnGaps(image, x, height, maxGapSize);
        }

        // Second pass: fill horizontal gaps (row by row)
        for (int y = 0; y < height; y++) {
            fillRowGaps(image, y, width, maxGapSize);
        }
    }

    private static void fillRowGaps(BufferedImage image, int y, int width, int maxGapSize) {
        int x = 0;
        while (x < width) {
            int pixel = image.getRGB(x, y);
            int alpha = (pixel >> 24) & 0xFF;

            if (alpha < 10) {
                int gapStart = x;
                int gapEnd = x;

                while (gapEnd < width - 1) {
                    int nextPixel = image.getRGB(gapEnd + 1, y);
                    int nextAlpha = (nextPixel >> 24) & 0xFF;
                    if (nextAlpha >= 10) {
                        break;
                    }
                    gapEnd++;
                }

                int gapSize = gapEnd - gapStart + 1;

                if (gapSize <= maxGapSize) {
                    int leftPixel = 0;
                    boolean hasLeft = gapStart > 0;
                    if (hasLeft) {
                        leftPixel = image.getRGB(gapStart - 1, y);
                    }

                    int rightPixel = 0;
                    boolean hasRight = gapEnd < width - 1;
                    if (hasRight) {
                        rightPixel = image.getRGB(gapEnd + 1, y);
                    }

                    if (hasLeft && hasRight) {
                        fillGapInterpolatedHorizontal(image, y, gapStart, gapEnd, leftPixel, rightPixel);
                    } else if (hasLeft) {
                        fillGapSolidHorizontal(image, y, gapStart, gapEnd, leftPixel);
                    } else if (hasRight) {
                        fillGapSolidHorizontal(image, y, gapStart, gapEnd, rightPixel);
                    }
                }

                x = gapEnd + 1;
            } else {
                x++;
            }
        }
    }

    private static void fillGapInterpolatedHorizontal(BufferedImage image, int y, int gapStart, int gapEnd,
            int leftPixel, int rightPixel) {
        int gapSize = gapEnd - gapStart + 1;

        int aL = (leftPixel >> 24) & 0xFF;
        int rL = (leftPixel >> 16) & 0xFF;
        int gL = (leftPixel >> 8) & 0xFF;
        int bL = leftPixel & 0xFF;

        int aR = (rightPixel >> 24) & 0xFF;
        int rR = (rightPixel >> 16) & 0xFF;
        int gR = (rightPixel >> 8) & 0xFF;
        int bR = rightPixel & 0xFF;

        for (int i = 0; i < gapSize; i++) {
            float t = (float) (i + 1) / (gapSize + 1);

            int a = (int) (aL + t * (aR - aL));
            int r = (int) (rL + t * (rR - rL));
            int g = (int) (gL + t * (gR - gL));
            int b = (int) (bL + t * (bR - bL));

            int newPixel = (a << 24) | (r << 16) | (g << 8) | b;
            image.setRGB(gapStart + i, y, newPixel);
        }
    }

    private static void fillGapSolidHorizontal(BufferedImage image, int y, int gapStart, int gapEnd, int pixel) {
        for (int x = gapStart; x <= gapEnd; x++) {
            image.setRGB(x, y, pixel);
        }
    }

    private static void fillColumnGaps(BufferedImage image, int x, int height, int maxGapSize) {
        int y = 0;
        while (y < height) {
            int pixel = image.getRGB(x, y);
            int alpha = (pixel >> 24) & 0xFF;

            if (alpha < 10) {
                // Found a transparent pixel, find the gap extent
                int gapStart = y;
                int gapEnd = y;

                // Find where the gap ends
                while (gapEnd < height - 1) {
                    int nextPixel = image.getRGB(x, gapEnd + 1);
                    int nextAlpha = (nextPixel >> 24) & 0xFF;
                    if (nextAlpha >= 10) {
                        break;
                    }
                    gapEnd++;
                }

                int gapSize = gapEnd - gapStart + 1;

                // Only fill if gap is within the max size
                if (gapSize <= maxGapSize) {
                    // Find the pixel above the gap (if exists)
                    int abovePixel = 0;
                    boolean hasAbove = gapStart > 0;
                    if (hasAbove) {
                        abovePixel = image.getRGB(x, gapStart - 1);
                    }

                    // Find the pixel below the gap (if exists)
                    int belowPixel = 0;
                    boolean hasBelow = gapEnd < height - 1;
                    if (hasBelow) {
                        belowPixel = image.getRGB(x, gapEnd + 1);
                    }

                    // Fill the gap
                    if (hasAbove && hasBelow) {
                        // Interpolate between above and below
                        fillGapInterpolated(image, x, gapStart, gapEnd, abovePixel, belowPixel);
                    } else if (hasAbove) {
                        // Only have above, extend it
                        fillGapSolid(image, x, gapStart, gapEnd, abovePixel);
                    } else if (hasBelow) {
                        // Only have below, extend it
                        fillGapSolid(image, x, gapStart, gapEnd, belowPixel);
                    }
                    // If neither, leave transparent
                }

                y = gapEnd + 1;
            } else {
                y++;
            }
        }
    }

    private static void fillGapInterpolated(BufferedImage image, int x, int gapStart, int gapEnd,
            int abovePixel, int belowPixel) {
        int gapSize = gapEnd - gapStart + 1;

        int aA = (abovePixel >> 24) & 0xFF;
        int rA = (abovePixel >> 16) & 0xFF;
        int gA = (abovePixel >> 8) & 0xFF;
        int bA = abovePixel & 0xFF;

        int aB = (belowPixel >> 24) & 0xFF;
        int rB = (belowPixel >> 16) & 0xFF;
        int gB = (belowPixel >> 8) & 0xFF;
        int bB = belowPixel & 0xFF;

        for (int i = 0; i <= gapSize - 1; i++) {
            float t = (float) (i + 1) / (gapSize + 1);

            int a = (int) (aA + t * (aB - aA));
            int r = (int) (rA + t * (rB - rA));
            int g = (int) (gA + t * (gB - gA));
            int b = (int) (bA + t * (bB - bA));

            int newPixel = (a << 24) | (r << 16) | (g << 8) | b;
            image.setRGB(x, gapStart + i, newPixel);
        }
    }

    private static void fillGapSolid(BufferedImage image, int x, int gapStart, int gapEnd, int pixel) {
        for (int y = gapStart; y <= gapEnd; y++) {
            image.setRGB(x, y, pixel);
        }
    }
}
