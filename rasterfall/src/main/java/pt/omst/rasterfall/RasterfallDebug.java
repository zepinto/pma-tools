//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Global debug flag and utility methods for rasterfall debugging visualization.
 * When debug is enabled, all rasterfall components display additional visual information
 * to help diagnose coordinate transformation and measurement issues.
 */
public class RasterfallDebug {
    
    /**
     * Global debug flag. Set to true to enable comprehensive debugging information
     * across all rasterfall components.
     * Can be enabled via system property: -Drasterfall.debug=true
     */
    public static boolean debug = Boolean.getBoolean("rasterfall.debug");
    
    private static final SimpleDateFormat debugTimestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    static {
        debugTimestampFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        System.out.println("RasterfallDebug initialized: " + (debug ? "ENABLED" : "DISABLED"));
        System.out.println("System property 'rasterfall.debug' = " + System.getProperty("rasterfall.debug"));
    }
    
    /**
     * Draw a labeled rectangle for debugging purposes
     */
    public static void drawLabeledRect(Graphics2D g2, Rectangle bounds, String label, Color color) {
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g2.setColor(color);
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.drawString(label, bounds.x + 5, bounds.y + 15);
    }
    
    /**
     * Format timestamp for debug display
     */
    public static String formatTimestamp(long timestamp) {
        return debugTimestampFormat.format(new Date(timestamp));
    }
    
    /**
     * Format coordinate for debug display
     */
    public static String formatCoord(double value) {
        return String.format("%.2f", value);
    }
    
    /**
     * Toggle debug mode
     */
    public static void toggle() {
        debug = !debug;
        System.out.println("RasterfallDebug: " + (debug ? "ENABLED" : "DISABLED"));
    }
}
