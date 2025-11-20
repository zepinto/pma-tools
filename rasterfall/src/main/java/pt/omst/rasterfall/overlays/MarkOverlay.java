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
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JOptionPane;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTiles;

@Slf4j
public class MarkOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private Point2D.Double firstPoint = null;
    private Point2D.Double lastPoint = null;
    private Point2D.Double currentPoint = null;

    private RasterfallTiles.TilesPosition firstPosition = null, lastPosition = null;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        firstPoint = lastPoint = currentPoint = null;
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        firstPoint = lastPoint = currentPoint = null;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);

        // Draw rectangle when we have two points
        Point2D.Double drawPoint = (lastPoint != null) ? lastPoint : currentPoint;

        if (firstPoint != null && drawPoint != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(255, 255, 255, 200));

            // Calculate rectangle bounds
            int x = (int) Math.min(firstPoint.getX(), drawPoint.getX());
            int y = (int) Math.min(firstPoint.getY(), drawPoint.getY());
            int width = (int) Math.abs(drawPoint.getX() - firstPoint.getX());
            int height = (int) Math.abs(drawPoint.getY() - firstPoint.getY());

            // Draw rectangle
            g2.drawRect(x, y, width, height);

            // Fill with semi-transparent white
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRect(x, y, width, height);

            g2.dispose();
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
            if (lastPoint != null) {
                // Reset for new rectangle
                lastPoint = firstPoint = null;
                firstPosition = lastPosition = null;
            } else if (firstPoint == null) {
                // First click - set starting point
                firstPoint = new Point2D.Double(e.getX(), e.getY());
                firstPosition = waterfall.getPosition(firstPoint);
                currentPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            } else {
                // Second click - complete rectangle and show dialog
                lastPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(lastPoint);
                currentPoint = null;

                // Calculate top-left and bottom-right coordinates
                int topLeftX = (int) Math.min(firstPoint.getX(), lastPoint.getX());
                int topLeftY = (int) Math.min(firstPoint.getY(), lastPoint.getY());
                int bottomRightX = (int) Math.max(firstPoint.getX(), lastPoint.getX());
                int bottomRightY = (int) Math.max(firstPoint.getY(), lastPoint.getY());

                // Calculate center coordinates
                int centerX = (topLeftX + bottomRightX) / 2;
                int centerY = (topLeftY + bottomRightY) / 2;

                // Get world position (latitude/longitude) of center
                pt.lsts.neptus.core.LocationType centerLocation = waterfall.getWorldPosition(
                        new Point2D.Double(centerX, centerY));

                String centerLatLon = "N/A";
                if (centerLocation != null) {
                    centerLatLon = String.format("%.6f°, %.6f°",
                            centerLocation.getLatitudeDegs(),
                            centerLocation.getLongitudeDegs());
                }

                // Show dialog with coordinates
                String message = String.format(
                        "Rectangle Coordinates:\n\n" +
                                "Top-Left: (%d, %d)\n" +
                                "Bottom-Right: (%d, %d)\n\n" +
                                "Width: %d px\n" +
                                "Height: %d px\n\n" +
                                "Center (Lat, Lon): %s",
                        topLeftX, topLeftY,
                        bottomRightX, bottomRightY,
                        bottomRightX - topLeftX,
                        bottomRightY - topLeftY,
                        centerLatLon);

                JOptionPane.showMessageDialog(
                        waterfall,
                        message,
                        "Rectangle Selection",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            waterfall.repaint();
        }
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends RasterfallTiles> l) {
        if (firstPosition != null) {
            firstPoint = waterfall.getScreenPosition(firstPosition);
        }
        if (lastPoint != null) {
            lastPoint = waterfall.getScreenPosition(lastPosition);
        }
        waterfall.repaint();
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_MOVED && firstPoint != null) {
            if (lastPoint == null) {
                currentPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            }
            waterfall.repaint();
        }
    }

}
