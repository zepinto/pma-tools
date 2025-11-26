package pt.omst.rasterfall.overlays;

import java.awt.Graphics;
import java.awt.Point;
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
        } else if (!active) {
            // Clear bounds/cursor when deactivated
            for (RasterfallListener listener : listeners) {
                listener.onMouseMoved(Double.NaN, Double.NaN, null);
                listener.onVisibleBoundsChanged(null, null, null);
            }
        }
    }

    public static interface RasterfallListener {
        public void onMouseMoved(double latitude, double longitude, Instant timestamp);
        public void onVisibleBoundsChanged(LocationType[] boundaryPoints, Instant startTime, Instant endTime);
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
        
        // Get the visible viewport bounds
        java.awt.Rectangle visibleRect = waterfall.getVisibleRect();
        int viewX = visibleRect.x;
        int viewY = visibleRect.y;
        int viewWidth = visibleRect.width;
        int viewHeight = visibleRect.height;
       
        Instant start = waterfall.getTimeAtScreenY(viewY);
        Instant end = waterfall.getTimeAtScreenY(viewY + viewHeight - 1);

        if (lastVisibleStartTime == null || lastVisibleEndTime == null ||
                !lastVisibleStartTime.equals(start) || !lastVisibleEndTime.equals(end)) {
            lastVisibleStartTime = start;
            lastVisibleEndTime = end;
            
            // Compute 12 boundary points: 4 corners + 4 intermediate on each side (left/right)
            LocationType[] boundaryPoints = new LocationType[12];
            
            // Top edge: just the two corners (left to right)
            boundaryPoints[0] = waterfall.getWorldPosition(new Point2D.Double(viewX, viewY)); // top-left
            boundaryPoints[1] = waterfall.getWorldPosition(new Point2D.Double(viewX + viewWidth - 1, viewY)); // top-right
            
            // Right edge: 4 intermediate points (top to bottom, excluding corners)
            for (int i = 0; i < 4; i++) {
                int y = viewY + ((i + 1) * viewHeight / 5);
                boundaryPoints[2 + i] = waterfall.getWorldPosition(new Point2D.Double(viewX + viewWidth - 1, y));
            }
            
            // Bottom edge: just the two corners (right to left)
            boundaryPoints[6] = waterfall.getWorldPosition(new Point2D.Double(viewX + viewWidth - 1, viewY + viewHeight - 1)); // bottom-right
            boundaryPoints[7] = waterfall.getWorldPosition(new Point2D.Double(viewX, viewY + viewHeight - 1)); // bottom-left
            
            // Left edge: 4 intermediate points (bottom to top, excluding corners)
            for (int i = 0; i < 4; i++) {
                int y = viewY + viewHeight - 1 - ((i + 1) * viewHeight / 5);
                boundaryPoints[8 + i] = waterfall.getWorldPosition(new Point2D.Double(viewX, y));
            }
            
            for (RasterfallListener listener : listeners) {
                listener.onVisibleBoundsChanged(boundaryPoints, start, end);
            }
        }
    }

}
