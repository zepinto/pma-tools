//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.neptus.util;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * Utility class for image loading
 */
public class ImageUtils {
    
    /**
     * Load an image from the classpath
     * @param path the path to the image resource
     * @return the loaded image, or null if not found
     */
    public static java.awt.Image getImage(String path) {
        try {
            InputStream is = ImageUtils.class.getClassLoader().getResourceAsStream(path);
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static Image getScaledImage(String imagePath, int maxWidth, int maxHeight) {
        Image img = getImage(imagePath);
        return ImageUtils.getScaledImage(img, maxWidth, maxHeight, false);
    }

    /**
     * This method scales a given image according to the maximum value of width and height given
     *
     * @param originalImage The image to be scaled
     * @param maxWidth      The maximum allowed width for the image
     * @param maxHeight     The maximum allowed height for the image
     * @param mayDistort    Selects whether the image may be distorted (in case maxWidth/maxHeight
     *                      != imgWidth/imgHeight)
     * @return scaled image.
     */
    public static Image getScaledImage(Image originalImage, int maxWidth, int maxHeight, boolean mayDistort) {
        if (originalImage == null)
            return null;

        if (mayDistort)
            return originalImage.getScaledInstance(maxWidth, maxHeight, Image.SCALE_SMOOTH);

        if (originalImage.getWidth(null) < 0)
            return originalImage.getScaledInstance(maxWidth, maxHeight, Image.SCALE_SMOOTH);

        double imgRatio = (double) originalImage.getWidth(null) / (double) originalImage.getHeight(null);
        double desiredRatio = (double) maxWidth / (double) maxHeight;
        int width, height;

        if (desiredRatio > imgRatio) {
            height = maxHeight;
            width = (int) (maxHeight * imgRatio);
        } else {
            width = maxWidth;
            height = (int) (maxWidth / imgRatio);
        }
        return originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }
}
