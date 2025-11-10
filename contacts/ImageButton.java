//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import lombok.extern.java.Log;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

@Log
public class ImageButton extends JButton implements Closeable {

    private final BufferedImage image;

    public ImageButton(File imageFile) {
        BufferedImage image1;
        try {
                BufferedImage image0 = ImageIO.read(imageFile);
                image1 = Scalr.resize(image0, 64);
                image0.flush();
            }
            catch (IOException e) {
                image1 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                log.warning("Error reading image: " + e.getMessage());
            }
        image = image1;
        setPreferredSize(new Dimension(64, 64));
    }

    @Override
    public void paint(Graphics g) {
        if (image.getWidth() > getWidth() || image.getHeight() > getHeight())
            g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
        else {
            int xmargin = image.getWidth() - getWidth();
            int ymargin = image.getHeight() - getHeight();
            g.drawImage(image, xmargin/2, ymargin/2, this);
        }
        super.paint(g);
    }

    @Override
    public void close() throws IOException {
        image.flush();
    }

    public static void main(String[] args) {

    }
}
