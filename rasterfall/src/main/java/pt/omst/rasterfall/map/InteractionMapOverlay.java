package pt.omst.rasterfall.map;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import javax.swing.JComponent;

import pt.lsts.neptus.core.LocationType;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;

public class InteractionMapOverlay extends AbstractMapOverlay{

    private LocationType mouseLocation = null;
    private LocationType[] visibleBounds = null;

    public void setMouseLocation(LocationType loc) {
        this.mouseLocation = loc;
    }

    public void setVisibleBounds(LocationType loc1, LocationType loc2, LocationType loc3, LocationType loc4) {
        this.visibleBounds = new LocationType[] {loc1, loc2, loc3, loc4};
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

        if (visibleBounds != null && visibleBounds.length == 4) {
            g2d.setColor(java.awt.Color.BLUE);
            Point2D p1 = map.getScreenPosition(visibleBounds[0]);
            Point2D p2 = map.getScreenPosition(visibleBounds[1]);
            Point2D p3 = map.getScreenPosition(visibleBounds[2]);
            Point2D p4 = map.getScreenPosition(visibleBounds[3]);
            g2d.draw(new Line2D.Double(p1, p2));
            g2d.draw(new Line2D.Double(p2, p4));
            g2d.draw(new Line2D.Double(p4, p3));
            g2d.draw(new Line2D.Double(p3, p1));
        }
    }
    
}
