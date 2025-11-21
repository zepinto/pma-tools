//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterfall.VehiclePositionHUD;
import pt.omst.sidescan.SidescanParser;
import pt.omst.sidescan.SidescanParserFactory;

public class HudOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private VehiclePositionHUD hud = null;
    @Override
    public void cleanup(RasterfallTiles waterfall) {

    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        //createHud();
        Thread.ofVirtual().start(this::createHud);
    }

    private synchronized void createHud() {
        if (hud == null) {
            SidescanParser parser = SidescanParserFactory.build(waterfall.getRastersFolder().getParentFile());
            VehiclePositionHUD tmp = new VehiclePositionHUD(parser, 200, 200);            
            tmp.setPathColor(new Color(255,255,255,128));
            hud = tmp;         
            waterfall.repaint();   
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        if (hud == null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, 200));
            int x = waterfall.getVisibleRect().x + waterfall.getVisibleRect().width / 2 - 30;
            int y = waterfall.getVisibleRect().y + waterfall.getVisibleRect().height - 50;
            g.drawString("loading...", x, y);
            return;
        }
        double timestamp = 0.001 * waterfall.getTimestamp();
        hud.setTimestamp(timestamp);

        int xMargin = (waterfall.getVisibleRect().width - hud.getWidth())/2;
        int yMargin = waterfall.getVisibleRect().height - hud.getHeight();
        BufferedImage image = hud.getImage(timestamp);
        if (image != null) {
            g.drawImage(image, waterfall.getVisibleRect().x + xMargin, waterfall.getVisibleRect().y + yMargin, null);
        }
    }
}
