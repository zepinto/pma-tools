//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages overlays for a SlippyMap.
 * This class handles registration, activation, and rendering of map overlays.
 */
@Slf4j
public class MapOverlayManager implements Closeable {

    private final CopyOnWriteArrayList<AbstractMapOverlay> activeOverlays = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AbstractMapOverlay> registeredOverlays = new CopyOnWriteArrayList<>();
    private final SlippyMap map;
    private final ButtonGroup exclusiveGroup = new ButtonGroup();
    private final JPanel toolbarPanel;

    /**
     * Creates a new overlay manager for the specified map.
     * @param map the map to manage overlays for
     */
    public MapOverlayManager(SlippyMap map) {
        this.map = map;
        this.toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    }

    /**
     * Register a new overlay with an associated toggle button.
     * @param overlay the overlay to register
     * @param button the button to control this overlay
     * @param exclusive if true, only one exclusive overlay can be active at a time
     */
    public void registerOverlay(AbstractMapOverlay overlay, AbstractButton button, boolean exclusive) {
        registeredOverlays.add(overlay);
        
        button.setMargin(new Insets(0, 0, 0, 0));
        if (overlay.getTooltip() != null) {
            button.setToolTipText(overlay.getTooltip());
        }
        
        if (exclusive) {
            exclusiveGroup.add(button);
        }
        
        button.addActionListener(e -> {
            boolean selected = button.isSelected();
            if (button instanceof JToggleButton) {
                selected = ((JToggleButton) button).isSelected();
            }
            toggleOverlay(overlay, selected);
        });
        
        toolbarPanel.add(button);
        log.info("Registered overlay: {}", overlay.getToolbarName());
    }

    /**
     * Register a new overlay and create a default toggle button for it.
     * @param overlay the overlay to register
     * @param exclusive if true, only one exclusive overlay can be active at a time
     * @return the created button
     */
    public JToggleButton registerOverlay(AbstractMapOverlay overlay, boolean exclusive) {
        JToggleButton button = new JToggleButton(overlay.getToolbarName());
        if (overlay.getIcon() != null) {
            button.setIcon(overlay.getIcon());
        }
        registerOverlay(overlay, button, exclusive);
        return button;
    }

    /**
     * Toggle an overlay on or off.
     * @param overlay the overlay to toggle
     * @param active true to activate, false to deactivate
     */
    public void toggleOverlay(AbstractMapOverlay overlay, boolean active) {
        if (active && !activeOverlays.contains(overlay)) {
            activeOverlays.add(overlay);
            overlay.install(map);
            log.debug("Activated overlay: {}", overlay.getToolbarName());
        } else if (!active && activeOverlays.contains(overlay)) {
            overlay.cleanup(map);
            activeOverlays.remove(overlay);
            log.debug("Deactivated overlay: {}", overlay.getToolbarName());
        }
        map.repaint();
    }

    /**
     * Activate an overlay programmatically.
     * @param overlay the overlay to activate
     */
    public void activateOverlay(AbstractMapOverlay overlay) {
        toggleOverlay(overlay, true);
    }

    /**
     * Deactivate an overlay programmatically.
     * @param overlay the overlay to deactivate
     */
    public void deactivateOverlay(AbstractMapOverlay overlay) {
        toggleOverlay(overlay, false);
    }

    /**
     * Check if an overlay is currently active.
     * @param overlay the overlay to check
     * @return true if the overlay is active
     */
    public boolean isActive(AbstractMapOverlay overlay) {
        return activeOverlays.contains(overlay);
    }

    /**
     * Get the toolbar panel containing overlay control buttons.
     * @return the toolbar panel
     */
    public JPanel getToolbarPanel() {
        return toolbarPanel;
    }

    /**
     * Get all currently active overlays.
     * @return list of active overlays
     */
    public java.util.List<AbstractMapOverlay> getActiveOverlays() {
        return new java.util.ArrayList<>(activeOverlays);
    }

    /**
     * Paint all active overlays.
     * This method should be called from SlippyMap's paintComponent method.
     * @param g the graphics context
     * @param c the component being painted
     */
    public void paint(Graphics g, JComponent c) {
        // Paint all active overlays
        for (AbstractMapOverlay overlay : activeOverlays) {
            try {
                overlay.paint(g, c);
            } catch (Exception e) {
                log.error("Error painting overlay {}: {}", overlay.getToolbarName(), e.getMessage());
            }
        }
    }

    /**
     * Process a mouse event through all active overlays.
     * @param e the mouse event
     * @return true if any overlay consumed the event
     */
    public boolean processMouseEvent(java.awt.event.MouseEvent e) {
        for (AbstractMapOverlay overlay : activeOverlays) {
            try {
                if (overlay.processMouseEvent(e, map)) {
                    return true; // Event was consumed
                }
            } catch (Exception ex) {
                log.error("Error processing mouse event in overlay {}: {}", overlay.getToolbarName(), ex.getMessage());
            }
        }
        return false;
    }

    /**
     * Process a mouse motion event through all active overlays.
     * @param e the mouse event
     * @return true if any overlay consumed the event
     */
    public boolean processMouseMotionEvent(java.awt.event.MouseEvent e) {
        for (AbstractMapOverlay overlay : activeOverlays) {
            try {
                if (overlay.processMouseMotionEvent(e, map)) {
                    return true; // Event was consumed
                }
            } catch (Exception ex) {
                log.error("Error processing mouse motion in overlay {}: {}", overlay.getToolbarName(), ex.getMessage());
            }
        }
        return false;
    }

    /**
     * Process a mouse wheel event through all active overlays.
     * @param e the mouse wheel event
     * @return true if any overlay consumed the event
     */
    public boolean processMouseWheelEvent(java.awt.event.MouseWheelEvent e) {
        for (AbstractMapOverlay overlay : activeOverlays) {
            try {
                if (overlay.processMouseWheelEvent(e, map)) {
                    return true; // Event was consumed
                }
            } catch (Exception ex) {
                log.error("Error processing mouse wheel in overlay {}: {}", overlay.getToolbarName(), ex.getMessage());
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        // Cleanup all active overlays
        for (AbstractMapOverlay overlay : activeOverlays) {
            try {
                overlay.cleanup(map);
            } catch (Exception e) {
                log.error("Error cleaning up overlay {}: {}", overlay.getToolbarName(), e.getMessage());
            }
        }
        activeOverlays.clear();
        registeredOverlays.clear();
    }
}
