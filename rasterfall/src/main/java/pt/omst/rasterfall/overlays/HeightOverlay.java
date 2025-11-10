//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;

import javax.swing.JComponent;
import javax.swing.JLayer;

import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRasterUtils;

public class HeightOverlay extends AbstractOverlay {

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
        if (firstPoint != null && lastPoint != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine((int)firstPoint.getX(), (int)firstPoint.getY(), (int)lastPoint.getX(), (int)lastPoint.getY());
        }
        else if (firstPoint != null && currentPoint != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.WHITE);
            g2.drawLine((int)firstPoint.getX(), (int)firstPoint.getY(), (int)currentPoint.getX(), (int)currentPoint.getY());
        }

        if (firstPosition != null && lastPosition != null && firstPosition.location() != null && lastPosition.location() != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255));
            g2.setStroke(new BasicStroke(2f));
            g2.setFont(new Font("Helvetica", Font.BOLD, 14));
            if (lastPoint != null)
                g2.drawString(String.format("%.1f m", calculateHeight(firstPosition, lastPosition)), (int)lastPoint.getX()+10, (int)lastPoint.getY()-10);
            else
                g2.drawString(String.format("%.1f m", calculateHeight(firstPosition, waterfall.getPosition(currentPoint))), (int)currentPoint.getX()+10, (int)currentPoint.getY()-10);
        }
    }

    double calculateHeight(RasterfallTiles.TilesPosition start, RasterfallTiles.TilesPosition end) {
        return IndexedRasterUtils.getHeight(start.range(), end.range(), start.pose().getAltitude());
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
            if (lastPoint != null) {
                lastPoint = firstPoint = null;
                firstPosition = lastPosition = null;
            }
            else if (firstPoint == null) {
                firstPoint = new Point2D.Double(e.getX(), e.getY());
                firstPosition = waterfall.getPosition(firstPoint);
                currentPoint = new Point2D.Double(e.getX(), firstPoint.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            } else {
                lastPoint = new Point2D.Double(e.getX(), firstPoint.getY());
                lastPosition = waterfall.getPosition(lastPoint);
                currentPoint = null;
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
                currentPoint = new Point2D.Double(e.getX(), firstPoint.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            }
            waterfall.repaint();
        }
    }
}
