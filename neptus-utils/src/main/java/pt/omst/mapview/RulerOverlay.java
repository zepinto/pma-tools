//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import javax.swing.*;

import pt.lsts.neptus.core.LocationType;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * An overlay that allows measuring distances between points on the map.
 */
public class RulerOverlay extends AbstractMapOverlay {

    private SlippyMap map;
    private final List<LocationType> points = new ArrayList<>();
    private LocationType hoverPoint = null;

    @Override
    public String getTooltip() {
        return "Measure distances (click to add points, right-click to clear)";
    }

    @Override
    public String getToolbarName() {
        return "Ruler";
    }

    @Override
    public void cleanup(SlippyMap map) {
        this.map = null;
        points.clear();
        hoverPoint = null;
    }

    @Override
    public void install(SlippyMap map) {
        this.map = map;
        points.clear();
        hoverPoint = null;
    }

    @Override
    public boolean processMouseEvent(MouseEvent e, SlippyMap map) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                // Add point
                LocationType loc = map.getRealWorldPosition(e.getX(), e.getY());
                points.add(loc);
                map.repaint();
                return true; // Consume event
            } else if (SwingUtilities.isRightMouseButton(e)) {
                // Clear points
                points.clear();
                map.repaint();
                return true; // Consume event
            }
        }
        return false;
    }

    @Override
    public boolean processMouseMotionEvent(MouseEvent e, SlippyMap map) {
        if (e.getID() == MouseEvent.MOUSE_MOVED) {
            hoverPoint = map.getRealWorldPosition(e.getX(), e.getY());
            map.repaint();
        }
        return false;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (map == null || points.isEmpty()) return;

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw lines between points
        g2d.setColor(new Color(255, 0, 0, 200));
        g2d.setStroke(new BasicStroke(2.0f));

        double totalDistance = 0;
        LocationType prevPoint = null;

        for (LocationType point : points) {
            Point2D screenPos = map.getScreenPosition(point);
            
            // Draw point
            g2d.fillOval((int) screenPos.getX() - 4, (int) screenPos.getY() - 4, 8, 8);
            
            // Draw line from previous point
            if (prevPoint != null) {
                Point2D prevScreenPos = map.getScreenPosition(prevPoint);
                g2d.drawLine((int) prevScreenPos.getX(), (int) prevScreenPos.getY(),
                           (int) screenPos.getX(), (int) screenPos.getY());
                
                double dist = prevPoint.getDistanceInMeters(point);
                totalDistance += dist;
                
                // Draw segment distance
                int midX = (int) ((prevScreenPos.getX() + screenPos.getX()) / 2);
                int midY = (int) ((prevScreenPos.getY() + screenPos.getY()) / 2);
                String distText = String.format("%.1f m", dist);
                g2d.setColor(Color.WHITE);
                g2d.fillRect(midX - 30, midY - 10, 60, 18);
                g2d.setColor(Color.BLACK);
                g2d.drawString(distText, midX - 25, midY + 3);
                g2d.setColor(new Color(255, 0, 0, 200));
            }
            
            prevPoint = point;
        }

        // Draw line to hover point
        if (hoverPoint != null && !points.isEmpty()) {
            LocationType lastPoint = points.get(points.size() - 1);
            Point2D lastScreenPos = map.getScreenPosition(lastPoint);
            Point2D hoverScreenPos = map.getScreenPosition(hoverPoint);
            
            g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                                        0, new float[]{5}, 0));
            g2d.drawLine((int) lastScreenPos.getX(), (int) lastScreenPos.getY(),
                       (int) hoverScreenPos.getX(), (int) hoverScreenPos.getY());
        }

        // Draw total distance
        if (points.size() > 1) {
            String totalText = String.format("Total: %.1f m", totalDistance);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(totalText);
            
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRect(10, map.getHeight() - 40, textWidth + 10, 25);
            g2d.setColor(Color.WHITE);
            g2d.drawString(totalText, 15, map.getHeight() - 22);
        }
    }
}
