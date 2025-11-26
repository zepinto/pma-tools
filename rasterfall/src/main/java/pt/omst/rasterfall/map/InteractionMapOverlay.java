package pt.omst.rasterfall.map;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GeometryUtils;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;

public class InteractionMapOverlay extends AbstractMapOverlay{

    private LocationType mouseLocation = null;
    private LocationType[] visibleBounds = null;

    public void setMouseLocation(LocationType loc) {
        this.mouseLocation = loc;
    }

    public void setVisibleBounds(LocationType[] bounds) {
        this.visibleBounds = bounds;
    }

    public void clear() {
        this.mouseLocation = null;
        this.visibleBounds = null;
    }

    @Override
    public void cleanup(SlippyMap map) {
        // TODO Auto-generated method stub
    }

    @Override
    public void install(SlippyMap map) {
        // TODO Auto-generated method stub
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        Graphics2D g2d = (Graphics2D) g;
        SlippyMap map = (SlippyMap) c;
        if (mouseLocation != null) {
            g2d.setColor(java.awt.Color.RED);
            Point2D screenPoint = map.getScreenPosition(mouseLocation);
            g2d.draw(new Line2D.Double(screenPoint.getX() - 5, screenPoint.getY(), screenPoint.getX() + 5, screenPoint.getY()));
            g2d.draw(new Line2D.Double(screenPoint.getX(), screenPoint.getY() - 5, screenPoint.getX(), screenPoint.getY() + 5));
        }

        if (visibleBounds != null && visibleBounds.length >= 3) {
            // Convert locations to screen points, filtering nulls
            List<Point2D> screenPoints = new ArrayList<>();
            for (LocationType loc : visibleBounds) {
                if (loc != null) {
                    screenPoints.add(map.getScreenPosition(loc));
                }
            }
            
            if (screenPoints.size() >= 3) {
                // Calculate convex hull
                List<Point2D> hull = GeometryUtils.computeConvexHull(screenPoints);
                
                if (hull.size() >= 3) {
                    // Create polygon from hull points
                    Polygon polygon = new Polygon();
                    for (Point2D p : hull) {
                        polygon.addPoint((int) p.getX(), (int) p.getY());
                    }
                    
                    // Draw filled polygon with transparency
                    g2d.setColor(new java.awt.Color(0, 0, 255, 50));
                    g2d.fill(polygon);
                    
                    // Draw outline
                    g2d.setColor(java.awt.Color.BLUE);
                    g2d.draw(polygon);
                }
            }
        }
    }
    
}
