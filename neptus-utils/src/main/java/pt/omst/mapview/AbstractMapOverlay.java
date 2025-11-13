//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Abstract base class for SlippyMap overlays.
 * Overlays can add visual elements and handle user interactions on the map.
 */
public abstract class AbstractMapOverlay {

    /**
     * Get the icon for this overlay's toolbar button.
     * @return the icon, or null if no icon is needed
     */
    public ImageIcon getIcon() {
        return null;
    }

    /**
     * Get the tooltip text for this overlay's toolbar button.
     * @return the tooltip text
     */
    public String getTooltip() {
        return getClass().getSimpleName();
    }

    /**
     * Get the display name for this overlay's toolbar button.
     * @return the display name
     */
    public String getToolbarName() {
        return getClass().getSimpleName();
    }

    /**
     * Called when the overlay is being removed or disabled.
     * Use this to clean up any resources or listeners.
     * @param map the map this overlay was attached to
     */
    public abstract void cleanup(SlippyMap map);

    /**
     * Called when the overlay is being added or enabled.
     * Use this to set up any resources or listeners.
     * @param map the map this overlay is being attached to
     */
    public abstract void install(SlippyMap map);

    /**
     * Paint the overlay on the map.
     * @param g the graphics context
     * @param c the component being painted
     */
    public void paint(Graphics g, JComponent c) {
        // Override in subclasses to draw overlay content
    }

    /**
     * Process a mouse event.
     * @param e the mouse event
     * @param map the map component
     * @return true if the event was consumed and should not be processed further
     */
    public boolean processMouseEvent(MouseEvent e, SlippyMap map) {
        return false;
    }

    /**
     * Process a mouse motion event.
     * @param e the mouse event
     * @param map the map component
     * @return true if the event was consumed and should not be processed further
     */
    public boolean processMouseMotionEvent(MouseEvent e, SlippyMap map) {
        return false;
    }

    /**
     * Process a mouse wheel event.
     * @param e the mouse wheel event
     * @param map the map component
     * @return true if the event was consumed and should not be processed further
     */
    public boolean processMouseWheelEvent(MouseWheelEvent e, SlippyMap map) {
        return false;
    }
}
