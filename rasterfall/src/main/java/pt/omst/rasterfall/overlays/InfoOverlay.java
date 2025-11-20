//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;

import pt.omst.rasterfall.RasterfallDebug;
import pt.omst.rasterfall.RasterfallScrollbar;
import pt.omst.rasterfall.RasterfallTile;
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
            
            if (RasterfallDebug.debug) {
                // Comprehensive debug information
                label.setText(buildDebugHTML());
            } else {
                // Standard info display
                label.setText(html + waterfall.getWorldPosition(lastPoint) + "<br>" +
                        sdf.format(new Date(waterfall.getTimestamp(lastPoint))) + "<br>" + String.format("%.1f m", range) + "</body></html>");
            }
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
        
        if (RasterfallDebug.debug) {
            // Position at top-right corner with larger size for debug info
            g2.translate(waterfall.getVisibleRect().getWidth() - 620, 20);
            label.setBounds(0, 0, 600, 500);
        } else {
            // Standard position at bottom-left
            g2.translate(20, waterfall.getVisibleRect().getHeight()-90);
            label.setBounds(0, 0, 310, 70);
        }
        
        label.paint(g2);
    }
    
    private String buildDebugHTML() {
        StringBuilder sb = new StringBuilder(html);
        sb.append("<div style='font-size: 10px; font-family: Monospaced;'>");
        
        // Mouse & Screen coordinates
        sb.append("<b style='color: #FFD700;'>MOUSE & SCREEN</b><br>");
        sb.append(String.format("Screen: (%.0f, %.0f)<br>", lastPoint.getX(), lastPoint.getY()));
        Rectangle visRect = waterfall.getVisibleRect();
        sb.append(String.format("ViewportRel: (%.0f, %.0f)<br>", 
            lastPoint.getX() - visRect.x, lastPoint.getY() - visRect.y));
        
        // Viewport State
        sb.append("<br><b style='color: #FFD700;'>VIEWPORT STATE</b><br>");
        sb.append(String.format("Position: (%d, %d)<br>", visRect.x, visRect.y));
        sb.append(String.format("Size: %d x %d<br>", visRect.width, visRect.height));
        sb.append(String.format("Bounds: [%d,%d,%d,%d]<br>", 
            visRect.x, visRect.y, visRect.x + visRect.width, visRect.y + visRect.height));
        sb.append(String.format("Zoom: %.2fx<br>", waterfall.getZoom()));
        
        // Time Information
        sb.append("<br><b style='color: #FFD700;'>TIME INFO</b><br>");
        long centerTime = waterfall.getTimestamp();
        long topTime = waterfall.getTopTimestamp();
        long bottomTime = waterfall.getBottomTimestamp();
        long mouseTime = waterfall.getTimestamp(lastPoint);
        sb.append(String.format("Center: %s<br>", RasterfallDebug.formatTimestamp(centerTime)));
        sb.append(String.format("Top: %s<br>", RasterfallDebug.formatTimestamp(topTime)));
        sb.append(String.format("Bottom: %s<br>", RasterfallDebug.formatTimestamp(bottomTime)));
        sb.append(String.format("Mouse: %s<br>", sdf.format(new Date(mouseTime))));
        
        // Position Data
        sb.append("<br><b style='color: #FFD700;'>POSITION DATA</b><br>");
        RasterfallTiles.TilesPosition pos = waterfall.getPosition(lastPoint);
        if (pos != null) {
            sb.append(String.format("Location: %s<br>", pos.location()));
            sb.append(String.format("Range: %.2f m<br>", pos.range()));
            if (pos.pose() != null) {
                sb.append(String.format("Altitude: %.2f m<br>", pos.pose().getAltitude()));
                sb.append(String.format("Heading: %.1f°<br>", Math.toDegrees(pos.pose().getPhi())));
            }
        }
        
        // Tile Details
        sb.append("<br><b style='color: #FFD700;'>TILE INFO</b><br>");
        RasterfallTile currentTile = findTileAtPoint(lastPoint);
        if (currentTile != null) {
            int tileIndex = waterfall.getComponents().length;
            for (int i = 0; i < waterfall.getComponents().length; i++) {
                if (waterfall.getComponent(i) == currentTile) {
                    tileIndex = i;
                    break;
                }
            }
            sb.append(String.format("Tile: #%d<br>", tileIndex));
            sb.append(String.format("Tile Size: %d x %d<br>", 
                currentTile.getWidth(), currentTile.getHeight()));
            sb.append(String.format("Tile Bounds: (%d, %d)<br>", 
                currentTile.getBounds().x, currentTile.getBounds().y));
            sb.append(String.format("Samples: %d<br>", currentTile.getSamplesCount()));
            
            int relY = (int)(lastPoint.getY() - currentTile.getBounds().y);
            int sampleIndex = (int)(((float)relY/currentTile.getHeight()) * currentTile.getSamplesCount());
            sampleIndex = currentTile.getSamplesCount() - sampleIndex - 1;
            sb.append(String.format("Sample #: %d<br>", sampleIndex));
        }
        
        // Scrollbar State
        sb.append("<br><b style='color: #FFD700;'>SCROLLBAR</b><br>");
        RasterfallScrollbar scrollbar = findScrollbar();
        if (scrollbar != null) {
            sb.append(String.format("Height: %d px<br>", scrollbar.getHeight()));
            // Note: position field is private, can't access directly
            sb.append(String.format("Start: %s<br>", 
                RasterfallDebug.formatTimestamp(scrollbar.getStartTime())));
            sb.append(String.format("End: %s<br>", 
                RasterfallDebug.formatTimestamp(scrollbar.getEndTime())));
        }
        
        // Transformation Metrics
        sb.append("<br><b style='color: #FFD700;'>TRANSFORMS</b><br>");
        long timeSpan = bottomTime - topTime;
        double pixelsPerSecond = visRect.height / (timeSpan / 1000.0);
        sb.append(String.format("Px/sec: %.2f<br>", pixelsPerSecond));
        
        if (!waterfall.getRasters().isEmpty()) {
            double maxRange = waterfall.getRasters().get(0).getSensorInfo().getMaxRange();
            double pixelsPerMeter = waterfall.getWidth() / (maxRange * 2.0);
            sb.append(String.format("Px/meter: %.2f<br>", pixelsPerMeter));
            sb.append(String.format("MaxRange: %.1f m<br>", maxRange));
        }
        
        // Coordinate Roundtrip Test
        if (pos != null) {
            Point2D.Double screenPos = waterfall.getScreenPosition(pos);
            if (screenPos != null) {
                double errorX = Math.abs(screenPos.x - lastPoint.getX());
                double errorY = Math.abs(screenPos.y - lastPoint.getY());
                sb.append("<br><b style='color: #FFD700;'>ROUNDTRIP TEST</b><br>");
                sb.append(String.format("Error: (%.2f, %.2f) px<br>", errorX, errorY));
            }
        }
        
        sb.append("</div></body></html>");
        return sb.toString();
    }
    
    private RasterfallTile findTileAtPoint(Point2D point) {
        for (int i = 0; i < waterfall.getComponentCount(); i++) {
            if (waterfall.getComponent(i) instanceof RasterfallTile) {
                RasterfallTile tile = (RasterfallTile) waterfall.getComponent(i);
                if (tile.getBounds().contains(point)) {
                    return tile;
                }
            }
        }
        return null;
    }
    
    private RasterfallScrollbar findScrollbar() {
        // Navigate up to find the scrollbar sibling
        Container parent = waterfall.getParent();
        while (parent != null) {
            if (parent.getParent() != null) {
                Container grandParent = parent.getParent();
                for (int i = 0; i < grandParent.getComponentCount(); i++) {
                    if (grandParent.getComponent(i) instanceof RasterfallScrollbar) {
                        return (RasterfallScrollbar) grandParent.getComponent(i);
                    }
                }
            }
            parent = parent.getParent();
        }
        return null;
    }
}


