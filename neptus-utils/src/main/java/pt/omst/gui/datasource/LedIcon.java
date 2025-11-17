//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/**
 * A simple LED indicator icon that can show different states using colors.
 */
public class LedIcon implements Icon {
    
    private final int size;
    private final Color color;
    
    /**
     * Creates a new LED icon.
     * 
     * @param size the size of the LED in pixels
     * @param color the color of the LED
     */
    public LedIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }
    
    /**
     * Creates a gray LED icon indicating inactive/disconnected state.
     * 
     * @param size the size of the LED in pixels
     * @return a gray LED icon
     */
    public static LedIcon createInactive(int size) {
        return new LedIcon(size, new Color(128, 128, 128));
    }
    
    /**
     * Creates a green LED icon indicating active/connected state.
     * 
     * @param size the size of the LED in pixels
     * @return a green LED icon
     */
    public static LedIcon createActive(int size) {
        return new LedIcon(size, new Color(0, 200, 0));
    }
    
    /**
     * Creates a red LED icon indicating error state.
     * 
     * @param size the size of the LED in pixels
     * @return a red LED icon
     */
    public static LedIcon createError(int size) {
        return new LedIcon(size, new Color(220, 0, 0));
    }
    
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw outer circle (darker border)
        g2d.setColor(color.darker());
        g2d.fillOval(x, y, size, size);
        
        // Draw inner circle (main color with slight highlight)
        int innerSize = size - 2;
        g2d.setColor(color);
        g2d.fillOval(x + 1, y + 1, innerSize, innerSize);
        
        // Add a small highlight for 3D effect
        int highlightSize = size / 3;
        g2d.setColor(new Color(255, 255, 255, 150));
        g2d.fillOval(x + 2, y + 2, highlightSize, highlightSize);
        
        g2d.dispose();
    }
    
    @Override
    public int getIconWidth() {
        return size;
    }
    
    @Override
    public int getIconHeight() {
        return size;
    }
}
