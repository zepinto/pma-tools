//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComponent;

import pt.omst.rasterfall.RasterfallTiles;

public class GridOverlay extends AbstractOverlay {

    private double range;
    private RasterfallTiles waterfall;

    @Override
    public void cleanup(RasterfallTiles waterfall) {

    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        range = waterfall.getRasters().getFirst().getSensorInfo().getMaxRange();
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        double pixelsPerMeter = (double) waterfall.getWidth() / (2 * range);
        int spacing = 5;
        g.setColor(new Color(255, 255, 255, 100));

        for (int i = spacing; i < range * 2; i += spacing) {
            double posX = i * pixelsPerMeter;
            g.drawLine((int)posX, waterfall.getVisibleRect().y, (int)posX, (int)(waterfall.getVisibleRect().y+waterfall.getVisibleRect().getHeight()));
        }
    }
}
