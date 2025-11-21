//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.List;

import javax.swing.JComponent;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.contacts.CompressedContact;

@Slf4j
public class ContactsOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
    }
    
    @Override
    public void paint(Graphics g, JComponent c) {
        if (waterfall == null)
            return;
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Get contacts visible in the current time range
        long topTimestamp = waterfall.getTopTimestamp();
        long bottomTimestamp = waterfall.getBottomTimestamp();
        
        if (topTimestamp < 0 || bottomTimestamp < 0) {
            g2.dispose();
            return;
        }
        
        List<CompressedContact> allContacts = waterfall.getContacts().getAllContacts();
        
        for (CompressedContact contact : allContacts) {
            IndexedRasterUtils.RasterContactInfo info = IndexedRasterUtils.getContactInfo(contact);
            
            // Skip if contact is outside visible time range
            if (info.getEndTimeStamp() < topTimestamp || info.getStartTimeStamp() > bottomTimestamp) {
                continue;
            }
            
            // Skip if no box annotation
            if (info.getBoxAnnotation() == null) {
                continue;
            }
            
            // Get screen positions for the bounding box corners
            Point2D.Double topLeft = waterfall.getSlantedScreenPosition(
                Instant.ofEpochMilli(info.getStartTimeStamp()), 
                info.getMinRange()
            );
            Point2D.Double bottomRight = waterfall.getSlantedScreenPosition(
                Instant.ofEpochMilli(info.getEndTimeStamp()), 
                info.getMaxRange()
            );
            
            if (topLeft == null || bottomRight == null) {
                continue;
            }
            
            // Calculate bounding box dimensions
            int x = (int) Math.min(topLeft.x, bottomRight.x);
            int y = (int) Math.min(topLeft.y, bottomRight.y);
            int width = (int) Math.abs(bottomRight.x - topLeft.x);
            int height = (int) Math.abs(bottomRight.y - topLeft.y);
            
            // Skip if bounding box is too small to be meaningful
            if (width < 2 || height < 2) {
                continue;
            }
            
            // Draw shadow (black outline)
            g2.setColor(new Color(0, 0, 0, 128));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(x, y, width, height);
            
            // Draw main bounding box (white)
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(x, y, width, height);
            
            // Draw label if present
            if (info.getLabel() != null && !info.getLabel().isEmpty()) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(x, y - 15, g2.getFontMetrics().stringWidth(info.getLabel()) + 6, 15);
                g2.setColor(Color.WHITE);
                g2.drawString(info.getLabel(), x + 3, y - 3);
            }
        }
        
        g2.dispose();
    }
}
