package pt.omst.mapview;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public interface MapPainter extends Comparable<MapPainter> {
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

    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    default int compareTo(MapPainter o) {
        int priorityCompare = Integer.compare(getLayerPriority(), o.getLayerPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Same priority, check if both are Comparable and can compare to each other
        if (this instanceof Comparable && o instanceof Comparable 
                && this.getClass().isAssignableFrom(o.getClass())) {
            return ((Comparable) this).compareTo(o);
        }
        // Fall back to compare by name
        return getName().compareTo(o.getName());
    }
}
