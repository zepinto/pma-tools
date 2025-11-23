package pt.omst.rasterfall.mosaic;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class MaxAlphaComposite implements Composite {

    // Singleton instance for convenience
    public static final MaxAlphaComposite INSTANCE = new MaxAlphaComposite();

    private MaxAlphaComposite() {
        // Private constructor for singleton pattern
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        // We need to ensure both color models can provide alpha.
        // For simplicity, we'll assume they are compatible enough for getAlpha()
        // and that setDataElements/getDataElements will work.
        return new MaxAlphaCompositeContext(srcColorModel, dstColorModel);
    }

    private static class MaxAlphaCompositeContext implements CompositeContext {
        private ColorModel srcCM;
        private ColorModel dstCM;

        public MaxAlphaCompositeContext(ColorModel srcCM, ColorModel dstCM) {
            this.srcCM = srcCM;
            this.dstCM = dstCM;
        }

        @Override
        public void dispose() {
            // No resources to release in this simple context
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int minX = Math.max(src.getMinX(), dstIn.getMinX());
            int minY = Math.max(src.getMinY(), dstIn.getMinY());
            int maxX = Math.min(src.getMinX() + src.getWidth(), dstIn.getMinX() + dstIn.getWidth());
            int maxY = Math.min(src.getMinY() + src.getHeight(), dstIn.getMinY() + dstIn.getHeight());

            // Optimization: If dstOut is the same as dstIn, we're modifying in place.
            // If not, dstIn is our read-only destination, dstOut is our write-only.
            // The logic below handles both cases correctly by always reading from dstIn
            // and writing to dstOut.

            // These objects are reused for each pixel to avoid allocations in the loop.
            // getDataElements and setDataElements work with the underlying "packed" pixel data
            // type of the ColorModel, which is more general than assuming int[] RGBA.
            Object srcPixelData = null;
            Object dstPixelData = null;

            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    // Get pixel data elements for source and destination
                    srcPixelData = src.getDataElements(x, y, srcPixelData);
                    dstPixelData = dstIn.getDataElements(x, y, dstPixelData);

                    // Get alpha values
                    // ColorModel.getAlpha returns alpha in the range 0-255
                    int srcAlpha = srcCM.getAlpha(srcPixelData);
                    int dstAlpha = dstCM.getAlpha(dstPixelData);

                    // Compare alpha and set the output pixel
                    // If srcAlpha >= dstAlpha, use source pixel (prefers source on tie)
                    if (srcAlpha >= dstAlpha) {
                        dstOut.setDataElements(x, y, srcPixelData);
                    } else {
                        dstOut.setDataElements(x, y, dstPixelData);
                    }
                }
            }
        }
    }
}