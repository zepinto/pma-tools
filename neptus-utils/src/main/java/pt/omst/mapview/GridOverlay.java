//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import javax.swing.*;
import java.awt.*;

/**
 * An overlay that draws a grid of latitude/longitude lines on the map.
 */
public class GridOverlay extends AbstractMapOverlay {

    private SlippyMap map;
    private double gridSpacing = 0.1; // degrees

    @Override
    public String getTooltip() {
        return "Show coordinate grid";
    }

    @Override
    public String getToolbarName() {
        return "Grid";
    }

    @Override
    public void cleanup(SlippyMap map) {
        this.map = null;
    }

    @Override
    public void install(SlippyMap map) {
        this.map = map;
    }

    /**
     * Set the grid spacing in degrees.
     * @param spacing the spacing in degrees
     */
    public void setGridSpacing(double spacing) {
        this.gridSpacing = spacing;
        if (map != null) {
            map.repaint();
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (map == null) return;

        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.setStroke(new BasicStroke(1.0f));

        double[] bounds = map.getVisibleBounds();
        double minLat = bounds[0];
        double minLon = bounds[1];
        double maxLat = bounds[2];
        double maxLon = bounds[3];

        // Draw longitude lines (vertical)
        double startLon = Math.floor(minLon / gridSpacing) * gridSpacing;
        for (double lon = startLon; lon <= maxLon; lon += gridSpacing) {
            double[] topScreen = map.latLonToScreen(maxLat, lon);
            double[] bottomScreen = map.latLonToScreen(minLat, lon);
            
            if (topScreen[0] >= 0 && topScreen[0] <= map.getWidth()) {
                g2d.drawLine((int) topScreen[0], (int) topScreen[1],
                           (int) bottomScreen[0], (int) bottomScreen[1]);
            }
        }

        // Draw latitude lines (horizontal)
        double startLat = Math.floor(minLat / gridSpacing) * gridSpacing;
        for (double lat = startLat; lat <= maxLat; lat += gridSpacing) {
            double[] leftScreen = map.latLonToScreen(lat, minLon);
            double[] rightScreen = map.latLonToScreen(lat, maxLon);
            
            if (leftScreen[1] >= 0 && leftScreen[1] <= map.getHeight()) {
                g2d.drawLine((int) leftScreen[0], (int) leftScreen[1],
                           (int) rightScreen[0], (int) rightScreen[1]);
            }
        }
    }
}
