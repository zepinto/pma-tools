//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages base map selection and preferences for SlippyMap.
 */
@Slf4j
public class BaseMapManager {
    private static final String PREF_TILE_SOURCE = "tileSource";
    private TileSource currentTileSource;
    private final Preferences prefs;
    private final TileSourceChangeListener changeListener;
    
    /**
     * Interface for components that need to be notified of tile source changes.
     */
    public interface TileSourceChangeListener {
        void onTileSourceChanged(TileSource newSource);
    }
    
    /**
     * Creates a new BaseMapManager.
     * @param changeListener Listener to be notified when tile source changes
     */
    public BaseMapManager(TileSourceChangeListener changeListener) {
        this.changeListener = changeListener;
        this.prefs = Preferences.userNodeForPackage(BaseMapManager.class);
        this.currentTileSource = loadTileSourcePreference();
    }
    
    /**
     * Load saved tile source preference.
     */
    private TileSource loadTileSourcePreference() {
        String savedSource = prefs.get(PREF_TILE_SOURCE, TileSource.OPENSTREETMAP.name());
        try {
            return TileSource.valueOf(savedSource);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tile source preference: {}, using default", savedSource);
            return TileSource.OPENSTREETMAP;
        }
    }
    
    /**
     * Get the current tile source.
     */
    public TileSource getCurrentTileSource() {
        return currentTileSource;
    }
    
    /**
     * Set the tile source and save preference.
     */
    public void setTileSource(TileSource source) {
        if (source != currentTileSource) {
            currentTileSource = source;
            prefs.put(PREF_TILE_SOURCE, source.name());
            if (changeListener != null) {
                changeListener.onTileSourceChanged(source);
            }
        }
    }
    
    /**
     * Creates a menu for base map selection that can be added to a menu bar.
     * @return JMenu containing radio button items for each tile source
     */
    public JMenu createBaseMapMenu() {
        JMenu baseMapMenu = new JMenu("Base Map");
        ButtonGroup group = new ButtonGroup();
        
        for (TileSource source : TileSource.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(source.getDisplayName());
            item.setSelected(source == currentTileSource);
            item.addActionListener(e -> {
                if (currentTileSource != source) {
                    setTileSource(source);
                }
            });
            group.add(item);
            baseMapMenu.add(item);
        }
        
        return baseMapMenu;
    }
    
    /**
     * Setup the popup menu for base map selection.
     * @param component The component to attach the popup menu to
     */
    public void setupPopupMenu(JComponent component) {
        JPopupMenu popup = new JPopupMenu();
        
        // Create Base Map submenu
        JMenu baseMapMenu = new JMenu("Base Map");
        ButtonGroup group = new ButtonGroup();
        
        for (TileSource source : TileSource.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(source.getDisplayName());
            item.setSelected(source == currentTileSource);
            item.addActionListener(e -> {
                if (currentTileSource != source) {
                    setTileSource(source);
                }
            });
            group.add(item);
            baseMapMenu.add(item);
        }
        
        popup.add(baseMapMenu);
        
        // Add popup menu trigger
        component.addMouseListener(new MouseAdapter() {
            private Point pressPoint = null;
            private boolean isRightButton = false;
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    pressPoint = e.getPoint();
                    isRightButton = true;
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isRightButton && javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    // Only show popup if mouse hasn't moved significantly (not a drag)
                    if (pressPoint != null) {
                        int dx = Math.abs(e.getX() - pressPoint.x);
                        int dy = Math.abs(e.getY() - pressPoint.y);
                        if (dx <= 3 && dy <= 3) {
                            showPopup(e);
                        }
                    }
                }
                pressPoint = null;
                isRightButton = false;
            }
            
            private void showPopup(MouseEvent e) {
                // Update selection state in submenu
                for (int i = 0; i < baseMapMenu.getItemCount(); i++) {
                    JRadioButtonMenuItem item = (JRadioButtonMenuItem) baseMapMenu.getItem(i);
                    for (TileSource source : TileSource.values()) {
                        if (item.getText().equals(source.getDisplayName())) {
                            item.setSelected(source == currentTileSource);
                            break;
                        }
                    }
                }
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
}
