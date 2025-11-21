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

        // Get all contacts and draw bounding boxes for those visible in the current view
        List<CompressedContact> contacts = waterfall.getContacts().getAllContacts();
        
        for (CompressedContact contact : contacts) {
            try {
                IndexedRasterUtils.RasterContactInfo info = IndexedRasterUtils.getContactInfo(contact);
                
                // Get screen positions for the bounding box corners
                Point2D.Double topLeft = waterfall.getScreenPosition(
                    Instant.ofEpochMilli(info.getStartTimeStamp()), 
                    info.getMinRange()
                );
                Point2D.Double bottomRight = waterfall.getScreenPosition(
                    Instant.ofEpochMilli(info.getEndTimeStamp()), 
                    info.getMaxRange()
                );
                
                // Skip if either position is null (contact not in visible tiles)
                if (topLeft == null || bottomRight == null)
                    continue;
                
                // Calculate rectangle dimensions
                int x = (int) Math.min(topLeft.x, bottomRight.x);
                int y = (int) Math.min(topLeft.y, bottomRight.y);
                int width = (int) Math.abs(bottomRight.x - topLeft.x);
                int height = (int) Math.abs(bottomRight.y - topLeft.y);
                
                // Draw semi-transparent shadow
                g2.setColor(new Color(0, 0, 0, 100));
                g2.setStroke(new BasicStroke(3.0f));
                g2.drawRect(x, y, width, height);
                
                // Draw white bounding box
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRect(x, y, width, height);
                
                // Draw label if available
                if (contact.getLabel() != null && !contact.getLabel().isEmpty()) {
                    g2.setColor(new Color(255, 255, 255, 230));
                    g2.drawString(contact.getLabel(), x + 5, y - 5);
                }
            } catch (Exception e) {
                log.debug("Error drawing contact bounding box: {}", e.getMessage());
            }
        }
        
        g2.dispose();
    }
}
