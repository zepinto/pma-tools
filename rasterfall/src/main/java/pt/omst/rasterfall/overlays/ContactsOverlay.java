//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.Graphics;

import javax.swing.JComponent;

import pt.omst.rasterfall.RasterfallTiles;

public class ContactsOverlay extends AbstractOverlay{

    RasterfallTiles waterfall;

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
        // List<RasterContactInfo> contacts = waterfall.getVisibleContactInfos();
        // for (RasterContactInfo contact : contacts) {
            
        //     Point2D.Double pos = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getStartTimeStamp()), contact.getMinRange());
        //     Point2D.Double pos2 = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getEndTimeStamp()), contact.getMaxRange());

        //     g.setColor(Color.white);
        //     g.drawRect((int) pos.getX(), (int) pos.getY(), (int)(pos2.getX()-pos.getX()), (int)(pos2.getY()-pos.getY()));
            
        //     System.out.println("Contact info: " + contact);
        // }

    }
}
