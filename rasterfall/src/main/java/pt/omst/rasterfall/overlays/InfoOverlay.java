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
import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;

import pt.omst.rasterfall.RasterfallTiles;

public class InfoOverlay extends AbstractOverlay {

    private final JLabel label = new JLabel();
    private final Point2D lastPoint = new Point2D.Double();
    private final String html = "<html><body style='color: white; font-family: Helvetica; font-size: 12px;'>";
    private RasterfallTiles waterfall;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    {
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }
    @Override
    public void cleanup(RasterfallTiles waterfall) {
        // TODO Auto-generated method stub
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        label.setText(html + "0m</body></html>");
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        label.setForeground(Color.WHITE);
        label.setBackground(new Color(0, 0, 0, 150));
        label.setOpaque(true);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        lastPoint.setLocation(e.getPoint());
        if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
            double range = waterfall.getRange(lastPoint);
            label.setText(html + waterfall.getWorldPosition(lastPoint) + "<br>" +
                    sdf.format(new Date(waterfall.getTimestamp(lastPoint))) + "<br>" + String.format("%.1f m", range) + "</body></html>");
            waterfall.repaint();
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_EXITED) {
            lastPoint.setLocation(waterfall.getVisibleRect().getX()+waterfall.getVisibleRect().getWidth()/2,
                    waterfall.getVisibleRect().getY()+waterfall.getVisibleRect().getHeight()/2);
            label.setText(html + waterfall.getWorldPosition(lastPoint) + "<br>" +
                    sdf.format(new Date(waterfall.getTimestamp(lastPoint))) + "<br>" + String.format("%.1f m", 0f) + "</body></html>");
            waterfall.repaint();
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine((int)lastPoint.getX()-10, (int)lastPoint.getY(), (int)lastPoint.getX()+10, (int)lastPoint.getY());
        g2.drawLine((int)lastPoint.getX(), (int)lastPoint.getY()-10, (int)lastPoint.getX(), (int)lastPoint.getY()+10);
        g2.translate(waterfall.getVisibleRect().x, waterfall.getVisibleRect().y);
        g2.translate(20, waterfall.getVisibleRect().getHeight()-90);
        label.setBounds(0, 0, 310, 70);
        label.paint(g2);
        g2.translate(-waterfall.getVisibleRect().x-20, -waterfall.getVisibleRect().y+waterfall.getVisibleRect().getHeight()+90);
    }
}


