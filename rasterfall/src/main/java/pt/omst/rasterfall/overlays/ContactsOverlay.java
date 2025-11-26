//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.List;

import javax.swing.JComponent;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRasterUtils.RasterContactInfo;
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
        List<RasterContactInfo> contacts = waterfall.getVisibleContacts();
        if (contacts == null || contacts.isEmpty())
            return;
        FontMetrics fm = g.getFontMetrics();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (RasterContactInfo contact : contacts) {

            Point2D.Double pos = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getStartTimeStamp()),
                    contact.getMinRange());
            Point2D.Double pos2 = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getEndTimeStamp()),
                    contact.getMaxRange());

            if (pos == null || pos2 == null) {
                log.warn("Skipping contact {} - null position: pos={}, pos2={}", contact.getLabel(), pos, pos2);
                continue;
            }
          
            g.setColor(Color.white);
            
            int x = (int) Math.min(pos.getX(), pos2.getX());
            int y = (int) Math.min(pos.getY(), pos2.getY());
            int width = (int) Math.abs(pos2.getX() - pos.getX());
            int height = (int) Math.abs(pos2.getY() - pos.getY());

            g.drawRect(x, y, width, height);
            int widthLabel = fm.stringWidth(contact.getLabel());
            // draw contact label
            g.setFont(g.getFont().deriveFont(10f));
            g.drawString(contact.getLabel(), x + width - widthLabel+20, y + height + 12);
        }
    }
}
