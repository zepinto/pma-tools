//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: Jose Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SensorInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;

public class SonarCoverageOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private SonarCoverageWidget widget;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        // Nothing to cleanup
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        this.widget = new SonarCoverageWidget();
        this.widget.setSize(400, 400); // Set a fixed size for the widget
        // Make the widget transparent so we can handle background transparency
        // ourselves if needed,
        // but SonarCoverageWidget sets background to WATER_COLOR.
        // We want the overlay to be translucid.
        this.widget.setOpaque(false);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (waterfall == null || widget == null)
            return;

        // Get data from the center of the screen
        Point2D centerPoint = new Point2D.Double(
                waterfall.getVisibleRect().getCenterX(),
                waterfall.getVisibleRect().getCenterY());

        RasterfallTiles.TilesPosition pos = waterfall.getPosition(centerPoint);

        if (pos != null) {
            Pose pose = pos.pose();
            widget.setPose(pose);
        }

        if (!waterfall.getRasters().isEmpty()) {
            SensorInfo sensorInfo = waterfall.getRasters().getFirst().getSensorInfo();
            widget.setSensorInfo(sensorInfo);
        }

        Graphics2D g2d = (Graphics2D) g.create();

        // Position at bottom-right of visible area
        int width = widget.getWidth();
        int height = widget.getHeight();
        int x = waterfall.getVisibleRect().x + waterfall.getVisibleRect().width - width - 20;
        int y = waterfall.getVisibleRect().y + waterfall.getVisibleRect().height - height - 20;

        g2d.translate(x, y);

        // Set transparency
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

        // Set the widget bounds so it knows its size for painting
        widget.setBounds(0, 0, width, height);

        // Draw the widget using paintComponent instead of paint to avoid buffer
        // strategy issues
        widget.paintComponent(g2d);

        g2d.dispose();
    }

    @Override
    public String getToolbarName() {
        return "Coverage";
    }
}
