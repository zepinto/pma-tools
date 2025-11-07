package pt.omst.rasterlib.contacts;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;

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
            g2d.setColor(Color.white);
            g2d.drawString((float)worldCoords.x + ", " + (float)worldCoords.y, 10, 20);
        }
    }
}

