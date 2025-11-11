package pt.omst.mapview;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public interface MapPainter {
    default int getLayerPriority() {
        return 0;
    }

    default Rectangle2D.Double getBounds() {
        return null; // Default implementation returns null, subclasses can override
    }
    
    default void paint(Graphics2D g, SlippyMap map) {
        // Default implementation does nothing, subclasses can override
    }

    default String getName() {
        return this.getClass().getSimpleName(); // Default name is the class name
    }
    
}
