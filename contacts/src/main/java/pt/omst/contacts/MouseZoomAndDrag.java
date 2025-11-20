//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class MouseZoomAndDrag extends AbstractInteraction<SidescanObservationPanel> {

    public MouseZoomAndDrag(SidescanObservationPanel component) {
        super(component);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double delta = 0.1f * e.getPreciseWheelRotation();
        if (e.getModifiersEx() == MouseEvent.CTRL_DOWN_MASK)
            component.setHeightMeters(component.getHeightMeters() - delta * 10);
            //component.setZoomFactorX(component.getZoomFactorX()- delta);
        else if (e.getModifiersEx() == MouseEvent.SHIFT_DOWN_MASK)
            component.setHeightMeters(component.getHeightMeters() + delta * 10);
            //component.setZoomFactorX(component.getZoomFactorX()+ delta);
        else if (e.getModifiersEx() == MouseEvent.ALT_DOWN_MASK) {
            component.setZoomFactorX(component.getZoomFactorX() + delta);
            component.setZoomFactorY(component.getZoomFactorY() + delta);
        }
        else {
            component.setZoomFactorX(component.getZoomFactorX()- delta);
            component.setZoomFactorY(component.getZoomFactorY()- delta);
        }
        // Clamp zoom factor
        component.setZoomFactorX(Math.max(0.1, component.getZoomFactorX()));
        component.setZoomFactorY(Math.max(0.1, component.getZoomFactorY()));
        component.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        component.setMouseDragPosition(e.getPoint());
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        component.setMousePosition(e.getPoint());
        component.repaint();
    }

    public void mouseExited(MouseEvent e) {
        component.setMousePosition(null);
        component.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int dx = e.getX() - component.getMouseDragPosition().x;
        int dy = e.getY() - component.getMouseDragPosition().y;

        component.setOffsetX(component.getOffsetX() + dx);
        component.setOffsetY(component.getOffsetY() + dy);

        component.setMouseDragPosition(e.getPoint());
        component.setMousePosition(e.getPoint());
        component.repaint();
    }

    @Override
    public void paint(Graphics2D g2d) {
        // Draw image coordinates if mouse is over the panel
        if (component.getMousePosition() != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Point2D.Double imgCoords = component.screenToImageCoords(new Point2D.Double(component.getMousePosition().x, component.getMousePosition().y));
            Point2D.Double worldCoords = component.imageToWorldCoords(imgCoords);
            
            // Format both coordinate types
            String imageText = String.format("Image: (%.4f, %.4f)", imgCoords.x, imgCoords.y);
            String worldText = String.format("Lat/Lon: (%.6f, %.6f)", worldCoords.x, worldCoords.y);
            
            FontMetrics metrics = g2d.getFontMetrics();
            Rectangle2D imageBounds = metrics.getStringBounds(imageText, g2d);
            Rectangle2D worldBounds = metrics.getStringBounds(worldText, g2d);
            int maxWidth = (int) Math.max(imageBounds.getWidth(), worldBounds.getWidth());
            int lineHeight = (int) imageBounds.getHeight();
            int padding = 3;
            
            // Draw semi-transparent background for both lines
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRect(10 - padding, 20 - lineHeight - padding, 
                        maxWidth + padding * 2, lineHeight * 2 + padding * 2);
            
            // Draw white text on top
            g2d.setColor(Color.white);
            g2d.drawString(imageText, 10, 20);
            g2d.drawString(worldText, 10, 20 + lineHeight);
        }
    }
}

