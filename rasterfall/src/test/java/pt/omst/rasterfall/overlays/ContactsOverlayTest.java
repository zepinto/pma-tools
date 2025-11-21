//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class ContactsOverlayTest {

    @Test
    public void testOverlayCreation() {
        ContactsOverlay overlay = new ContactsOverlay();
        assertNotNull(overlay, "ContactsOverlay should be created successfully");
    }

    @Test
    public void testInstallAndCleanup() {
        ContactsOverlay overlay = new ContactsOverlay();
        
        // Install and cleanup should not throw exceptions even with null waterfall
        // This is a defensive test
        assertDoesNotThrow(() -> overlay.cleanup(null));
    }

    @Test
    public void testPaintWithNullWaterfall() {
        ContactsOverlay overlay = new ContactsOverlay();
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        JPanel panel = new JPanel();
        
        // Paint should handle null waterfall gracefully
        assertDoesNotThrow(() -> overlay.paint(g, panel));
        
        g.dispose();
    }
}
