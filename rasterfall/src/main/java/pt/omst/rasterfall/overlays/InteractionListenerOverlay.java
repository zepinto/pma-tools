package pt.omst.rasterfall.overlays;

import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JComponent;
import javax.swing.JLayer;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.omst.rasterfall.RasterfallTiles;

@Slf4j
public class InteractionListenerOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private CopyOnWriteArrayList<RasterfallListener> listeners = new CopyOnWriteArrayList<>();
    private Instant lastVisibleStartTime = null;
    private Instant lastVisibleEndTime = null;
    private boolean active = false;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        log.info("InteractionListenerOverlay active: {}", active);
        if (active && waterfall != null) {
            // Force update of visible bounds when activated
            lastVisibleStartTime = null;
            lastVisibleEndTime = null;
            waterfall.repaint();
        }
    }

    public static interface RasterfallListener {
        public void onMouseMoved(double latitude, double longitude, Instant timestamp);
        public void onVisibleBoundsChanged(LocationType loc1, LocationType loc2, LocationType loc3, LocationType loc4,
                Instant startTime, Instant endTime);
    }

    public void addListener(RasterfallListener listener) {
        listeners.add(listener);
        log.info("Added RasterfallListener: {}", listener);
    }

    public void removeListener(RasterfallListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        listeners.clear();
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        log.info("InteractionListenerOverlay installed.");
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (!active) return;
        
        LocationType loc = waterfall.getWorldPosition(new Point2D.Double(e.getX(), e.getY()));
        if (loc != null) {
            Instant timestamp = waterfall.getTimeAtScreenY(e.getY());
            for (RasterfallListener listener : listeners) {
                listener.onMouseMoved(loc.getLatitudeDegs(), loc.getLongitudeDegs(), timestamp);
            }
            
        }        
    }

    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (!active) return;
        
        if (e.getID() == MouseEvent.MOUSE_EXITED) {
            for (RasterfallListener listener : listeners) {
                listener.onMouseMoved(Double.NaN, Double.NaN, null);
            }
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (!active) return;
       
        Instant start = waterfall.getTimeAtScreenY(waterfall.getVisibleRect().y);
       
        Instant end = waterfall.getTimeAtScreenY(waterfall.getVisibleRect().y + waterfall.getVisibleRect().height - 1);

        if (lastVisibleStartTime == null || lastVisibleEndTime == null ||
                !lastVisibleStartTime.equals(start) || !lastVisibleEndTime.equals(end)) {
            lastVisibleStartTime = start;
            lastVisibleEndTime = end;
            LocationType topLeft = waterfall.getWorldPosition(new Point2D.Double(0, 0));
            LocationType topRight = waterfall.getWorldPosition(new Point2D.Double(waterfall.getWidth()-1, 0));
            LocationType bottomLeft = waterfall.getWorldPosition(new Point2D.Double(0, waterfall.getHeight()-1));
            LocationType bottomRight = waterfall.getWorldPosition(new Point2D.Double(waterfall.getWidth()-1, waterfall.getHeight()-1));
            for (RasterfallListener listener : listeners) {
                listener.onVisibleBoundsChanged(bottomLeft, topLeft, topRight, bottomRight, start, end);
            }
        }
    }

}
