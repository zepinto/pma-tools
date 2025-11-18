//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import javax.swing.*;

import pt.lsts.neptus.util.GuiUtils;

import java.awt.*;
import java.util.ArrayList;

/**
 * Example application demonstrating the SlippyMap overlay system.
 */
public class MapOverlayExample {

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SlippyMap Overlay Example");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            
            SlippyMap map = new SlippyMap();
            
            // Create overlays
            GridOverlay gridOverlay = new GridOverlay();
            RulerOverlay rulerOverlay = new RulerOverlay();
            
            // Register overlays with the map
            JToggleButton gridButton = new JToggleButton("Grid");
            gridButton.setToolTipText("Show coordinate grid");
            map.registerOverlay(gridOverlay, gridButton, false);
            
            JToggleButton rulerButton = new JToggleButton("Ruler");
            rulerButton.setToolTipText("Measure distances");
            map.registerOverlay(rulerOverlay, rulerButton, true);
            
            // Get the toolbar panel from overlay manager
            JPanel toolbar = map.getOverlayManager().getToolbarPanel();
            toolbar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            // Add components to frame
            frame.add(map, BorderLayout.CENTER);
            frame.add(toolbar, BorderLayout.NORTH);
            
            // Add instructions
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            infoPanel.add(new JLabel("Grid: Shows lat/lon grid lines"));
            infoPanel.add(new JLabel("Ruler: Click to add measurement points, right-click to clear"));
            frame.add(infoPanel, BorderLayout.SOUTH);
            
            frame.setSize(1000, 700);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Focus on Portugal
            map.focus(39.5, -8.0, 7);
        });
    }
    
    /**
     * Simple implementation of MapMarker for testing.
     */
    static class SimpleMapMarker implements MapMarker {
        private final double lat;
        private final double lon;
        private final String label;
        
        SimpleMapMarker(double lat, double lon, String label) {
            this.lat = lat;
            this.lon = lon;
            this.label = label;
        }
        
        @Override
        public double getLatitude() {
            return lat;
        }
        
        @Override
        public double getLongitude() {
            return lon;
        }
        
        @Override
        public String getLabel() {
            return label;
        }
    }
}
