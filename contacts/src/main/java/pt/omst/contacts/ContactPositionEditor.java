//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import pt.lsts.neptus.core.CoordinateUtil;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.MeasurementType;

/**
 * Interaction for editing the contact position by dragging a crosshair.
 * The crosshair represents the contact's position stored as an Annotation
 * with MeasurementType.POSITION. The position is stored in normalized 
 * image coordinates (0.0 to 1.0).
 * By default, if no position annotation exists, it starts at the center of the image.
 */
public class ContactPositionEditor extends MouseZoomAndDrag {

    private static final int CROSSHAIR_SIZE = 15;
    private static final int HIT_RADIUS = 10;
    private boolean dragging = false;

    public ContactPositionEditor(SidescanObservationPanel component) {
        super(component);
    }

    /**
     * Gets the position annotation, or null if none exists.
     */
    private Annotation getPositionAnnotation() {
        for (Annotation annotation : component.getObservation().getAnnotations()) {
            if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT 
                    && annotation.getMeasurementType() == MeasurementType.POSITION) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * Gets or creates the position annotation.
     * If no position annotation exists, creates one at the center (0.5, 0.5).
     */
    private Annotation getOrCreatePositionAnnotation() {
        Annotation annotation = getPositionAnnotation();
        if (annotation == null) {
            annotation = new Annotation();
            annotation.setAnnotationType(AnnotationType.MEASUREMENT);
            annotation.setMeasurementType(MeasurementType.POSITION);
            annotation.setNormalizedX(0.5);
            annotation.setNormalizedY(0.5);
            annotation.setTimestamp(component.getObservation().getTimestamp());
            annotation.setUserName(System.getProperty("user.name"));
            component.getObservation().getAnnotations().add(annotation);
        }
        return annotation;
    }

    /**
     * Gets the current contact position in normalized image coordinates.
     * If no position annotation exists, returns the center of the image.
     */
    private Point2D.Double getContactPositionInImageCoords() {
        Annotation annotation = getPositionAnnotation();
        if (annotation == null) {
            return new Point2D.Double(0.5, 0.5);
        }
        return new Point2D.Double(annotation.getNormalizedX(), annotation.getNormalizedY());
    }

    /**
     * Gets the contact position in screen coordinates.
     */
    private Point2D.Double getContactPositionOnScreen() {
        Point2D.Double imageCoords = getContactPositionInImageCoords();
        return component.imageToScreenCoords(imageCoords);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point2D.Double crosshairPos = getContactPositionOnScreen();
        double distance = e.getPoint().distance(crosshairPos.x, crosshairPos.y);
        
        if (distance <= HIT_RADIUS) {
            dragging = true;
            // Ensure annotation exists when starting to drag
            getOrCreatePositionAnnotation();
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            super.mousePressed(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (dragging) {
            dragging = false;
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            component.fireObservationChanged();
        }
        super.mouseReleased(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragging) {
            // Convert screen position to normalized image coords
            Point2D.Double screenCoords = new Point2D.Double(e.getX(), e.getY());
            Point2D.Double imageCoords = component.screenToImageCoords(screenCoords);
            
            double normalizedX = imageCoords.x;
            double normalizedY = imageCoords.y;
            
            // Update or create the position annotation
            Annotation annotation = getOrCreatePositionAnnotation();
            annotation.setNormalizedX(normalizedX);
            annotation.setNormalizedY(normalizedY);
            
            // Update the observation's lat/lon from the current position
            updateObservationPosition(normalizedX, normalizedY);
            
            component.setMousePosition(e.getPoint());
            component.repaint();
        } else {
            super.mouseDragged(e);
        }
    }

    /**
     * Updates the observation's latitude and longitude based on normalized image coordinates.
     */
    private void updateObservationPosition(double normalizedX, double normalizedY) {
        Point2D.Double imageCoords = new Point2D.Double(normalizedX, normalizedY);
        Point2D.Double worldCoords = component.imageToWorldCoords(imageCoords);
        component.getObservation().setLatitude(worldCoords.x);
        component.getObservation().setLongitude(worldCoords.y);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        Point2D.Double crosshairPos = getContactPositionOnScreen();
        double distance = e.getPoint().distance(crosshairPos.x, crosshairPos.y);
        
        if (distance <= HIT_RADIUS) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    @Override
    public void paint(Graphics2D g2d) {
        super.paint(g2d);
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        Point2D.Double pos = getContactPositionOnScreen();
        int x = (int) pos.x;
        int y = (int) pos.y;
        
        // Draw shadow for visibility
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.setStroke(new BasicStroke(4f));
        drawCrosshair(g2d, x, y);
        
        // Draw crosshair
        g2d.setColor(Color.CYAN);
        g2d.setStroke(new BasicStroke(2f));
        drawCrosshair(g2d, x, y);
        
        // Draw center circle
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawOval(x - 5, y - 5, 10, 10);
        g2d.setColor(Color.CYAN);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawOval(x - 5, y - 5, 10, 10);
        
        // Get position info - show world coords in DM format
        Point2D.Double imageCoords = getContactPositionInImageCoords();
        Point2D.Double worldCoords = component.imageToWorldCoords(imageCoords);
        
        String latStr = CoordinateUtil.latitudeAsPrettyString(worldCoords.x, false);
        String lonStr = CoordinateUtil.longitudeAsPrettyString(worldCoords.y, false);
        String positionText = String.format("Position: %s, %s", latStr, lonStr);
        
        FontMetrics metrics = g2d.getFontMetrics();
        Rectangle2D textBounds = metrics.getStringBounds(positionText, g2d);
        
        int textX = (int) (component.getWidth() - textBounds.getWidth()) / 2;
        int textY = component.getHeight() - 30;
        int padding = 3;
        
        // Draw semi-transparent background
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(textX - padding, textY - (int) textBounds.getHeight() - padding,
                (int) textBounds.getWidth() + padding * 2, (int) textBounds.getHeight() + padding * 2);
        
        // Draw text
        g2d.setColor(Color.CYAN);
        g2d.drawString(positionText, textX, textY);
        
        // Debug info - show raster range info
        double minRange = component.getRaster().getSensorInfo().getMinRange();
        double maxRange = component.getRaster().getSensorInfo().getMaxRange();
        String debugText = String.format("Raster range: %.1f to %.1f m (center slant: %.1f m)", 
                minRange, maxRange, minRange + (maxRange - minRange) * 0.5);
        
        Rectangle2D debugBounds = metrics.getStringBounds(debugText, g2d);
        int debugX = (int) (component.getWidth() - debugBounds.getWidth()) / 2;
        int debugY = component.getHeight() - 50;
        
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(debugX - padding, debugY - (int) debugBounds.getHeight() - padding,
                (int) debugBounds.getWidth() + padding * 2, (int) debugBounds.getHeight() + padding * 2);
        g2d.setColor(Color.YELLOW);
        g2d.drawString(debugText, debugX, debugY);
    }

    private void drawCrosshair(Graphics2D g2d, int x, int y) {
        // Horizontal line
        g2d.drawLine(x - CROSSHAIR_SIZE, y, x - 6, y);
        g2d.drawLine(x + 6, y, x + CROSSHAIR_SIZE, y);
        
        // Vertical line
        g2d.drawLine(x, y - CROSSHAIR_SIZE, x, y - 6);
        g2d.drawLine(x, y + 6, x, y + CROSSHAIR_SIZE);
    }
}
