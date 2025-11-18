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

import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.core.Pair;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.MeasurementType;

public class MeasurementEditor extends MouseZoomAndDrag {

    private static final int CIRCLE_RADIUS = 5;
    protected int draggingPoint = 0;
    protected final MeasurementType measurementType;
    private final Color color;

    public MeasurementEditor(SidescanObservationPanel component, MeasurementType measurementType, Color color) {
        super(component);
        this.measurementType = measurementType;
        this.color = color;
    }

    protected void clearAnnotation() {
        Annotation myAnnotation = null;
        for (Annotation annotation : component.getObservation().getAnnotations()) {
            if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT && annotation.getMeasurementType() == measurementType) {
                myAnnotation = annotation;
                break;
            }
        }
        if (myAnnotation != null) {
            component.getObservation().getAnnotations().remove(myAnnotation);
        }
    }

    protected Pair<Point2D.Double, Point2D.Double> getAnnotationPointsOnScreen() {
        Annotation myAnnotation = null;
        for (Annotation annotation : component.getObservation().getAnnotations()) {
            if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT && annotation.getMeasurementType() == measurementType) {
                myAnnotation = annotation;
                break;
            }
        }
        if (myAnnotation == null) {
            return null;
        }
        Point2D.Double pt1 = component.imageToScreenCoords(new Point2D.Double(myAnnotation.getNormalizedX(), myAnnotation.getNormalizedY()));
        Point2D.Double pt2 = component.imageToScreenCoords(new Point2D.Double(myAnnotation.getNormalizedX2(), myAnnotation.getNormalizedY2()));
        return new Pair<>(pt1, pt2);
    }

    protected Double getDistance() {
        Annotation myAnnotation = null;
        for (Annotation annotation : component.getObservation().getAnnotations()) {
            if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT && annotation.getMeasurementType() == measurementType) {
                myAnnotation = annotation;
                break;
            }
        }
        if (myAnnotation == null) {
            return null;
        }

        Point2D.Double pt1 = component.imageToWorldCoords(new Point2D.Double(myAnnotation.getNormalizedX(), myAnnotation.getNormalizedY()));
        Point2D.Double pt2 = component.imageToWorldCoords(new Point2D.Double(myAnnotation.getNormalizedX2(), myAnnotation.getNormalizedY2()));
        LocationType loc1 = new LocationType(pt1.y, pt1.x);
        LocationType loc2 = new LocationType(pt2.y, pt2.x);
        return loc1.getHorizontalDistanceInMeters(loc2);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (measurementType != MeasurementType.BOX && e.getClickCount() == 2) {
            clearAnnotation();
            repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Pair<Point2D.Double, Point2D.Double> edges = getAnnotationPointsOnScreen();

        super.mousePressed(e);

        if (edges != null) {
            if (e.getPoint().distance(edges.first()) < CIRCLE_RADIUS) {
                draggingPoint = 1;
            }
            else if (e.getPoint().distance(edges.second()) < CIRCLE_RADIUS) {
                draggingPoint = 2;
            }
            else {
                draggingPoint = 0;
            }
        }
        else {
            Point2D.Double screenCoords = new Point2D.Double(e.getPoint().getX(), e.getPoint().getY());
            Point2D.Double imageCoords = component.screenToImageCoords(screenCoords);
            Annotation newAnnotation = new Annotation();
            newAnnotation.setAnnotationType(AnnotationType.MEASUREMENT);
            newAnnotation.setMeasurementType(measurementType);
            newAnnotation.setNormalizedX(imageCoords.x);
            newAnnotation.setNormalizedY(imageCoords.y);
            newAnnotation.setNormalizedX2(imageCoords.x);
            newAnnotation.setNormalizedY2(imageCoords.y);
            newAnnotation.setTimestamp(component.getObservation().getTimestamp());
            newAnnotation.setUserName(System.getProperty("user.name"));
            component.getObservation().getAnnotations().add(newAnnotation);
            System.out.println("Added new annotation: " + newAnnotation);
            draggingPoint = 2;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);

        if (draggingPoint != 0) {
            draggingPoint = 0;
            component.fireObservationChanged();
            component.repaint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (draggingPoint != 0) {
            Pair<Point2D.Double, Point2D.Double> edges = getAnnotationPointsOnScreen();
            if (edges == null) {
                super.mouseDragged(e);
                return;
            }
            Annotation myAnnotation = null;
            for (Annotation annotation : component.getObservation().getAnnotations()) {
                if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT && annotation.getMeasurementType() == measurementType) {
                    myAnnotation = annotation;
                    break;
                }
            }
            if (myAnnotation == null) {
                return;
            }

            if (draggingPoint == 1) {
                Point2D oldCoords = new Point2D.Double(myAnnotation.getNormalizedX(), myAnnotation.getNormalizedY());
                Point2D.Double pt1 = component.screenToImageCoords(new Point2D.Double(e.getPoint().getX(), e.getPoint().getY()));
                myAnnotation.setNormalizedX(pt1.x);
                if (measurementType != MeasurementType.HEIGHT) {
                    myAnnotation.setNormalizedY(pt1.y);
                    myAnnotation.setValue(getDistance());
                }
                else {
                    myAnnotation.setNormalizedY(myAnnotation.getNormalizedY2());
                    Point2D.Double pt2 = component.screenToImageCoords(new Point2D.Double(myAnnotation.getNormalizedX2(), myAnnotation.getNormalizedY2()));
                    myAnnotation.setValue(component.getShadowHeight(pt1, pt2));
                }

            }
            else if (draggingPoint == 2) {
                Point2D.Double pt2 = component.screenToImageCoords(new Point2D.Double(e.getPoint().getX(), e.getPoint().getY()));
                myAnnotation.setNormalizedX2(pt2.x);
                if (measurementType != MeasurementType.HEIGHT) {
                    myAnnotation.setNormalizedY2(pt2.y);
                    myAnnotation.setValue(getDistance());
                }
                else {
                    Point2D.Double pt1 = component.screenToImageCoords(new Point2D.Double(myAnnotation.getNormalizedX(), myAnnotation.getNormalizedY()));
                    myAnnotation.setNormalizedY2(myAnnotation.getNormalizedY());
                    myAnnotation.setValue(component.getShadowHeight(pt1, pt2));
                }
            }
            component.setMouseDragPosition(e.getPoint());
            component.setMousePosition(e.getPoint());
            component.repaint();
        }
        else
            super.mouseDragged(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Pair<Point2D.Double, Point2D.Double> edges = getAnnotationPointsOnScreen();
        super.mouseMoved(e);
        if (edges != null) {
            if (e.getPoint().distance(edges.first()) < CIRCLE_RADIUS) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            else if (e.getPoint().distance(edges.second()) < CIRCLE_RADIUS) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            else {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }

    @Override
    public void paint(Graphics2D g2d) {
        Pair<Point2D.Double, Point2D.Double> edges = getAnnotationPointsOnScreen();
        if (edges == null)
            return;
        if (measurementType == MeasurementType.BOX) {
            int minX = (int) Math.min(edges.first().x, edges.second().x);
            int minY = (int) Math.min(edges.first().y, edges.second().y);
            int width = (int) Math.abs(edges.first().x - edges.second().x);
            int height = (int) Math.abs(edges.first().y - edges.second().y);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.white);
            g2d.drawOval((int) edges.first().x - 5, (int) edges.first().y - 5, 10,10);
            g2d.drawOval((int) edges.second().x - 5, (int) edges.second().y - 5, 10,10);

            g2d.setColor(new Color(255,255,255));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(minX, minY, width, height);
        }
        else {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.white);
            g2d.drawOval((int) edges.first().x - 5, (int) edges.first().y - 5, 10,10);
            g2d.drawOval((int) edges.second().x - 5, (int) edges.second().y - 5, 10,10);

            g2d.setColor(new Color(255,255,255));
            g2d.setStroke(new BasicStroke(2));
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawLine((int) edges.first().x, (int) edges.first().y, (int) edges.second().x, (int) edges.second().y);

            Double distance = getDistance();
            if (distance != null) {
                g2d.setColor(Color.WHITE);
                String measurementTypeString = measurementType.toString().toLowerCase();
                String str = measurementTypeString+": "+String.format("%.2f m", distance);
                //calculate string size
                FontMetrics metrics = g2d.getFontMetrics();
                Rectangle2D rect = metrics.getStringBounds(str, g2d);
                //draw string centered in the bottom of the panel
                g2d.drawString(str, (int) (component.getWidth() - rect.getWidth()) / 2, component.getHeight() - 12);

            }
        }
    }
}
