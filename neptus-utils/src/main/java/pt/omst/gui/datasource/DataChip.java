//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import lombok.Getter;
import pt.lsts.neptus.util.GuiUtils;

/**
 * A visual "chip" component representing a data source.
 * Displays an icon, name, and a remove button in a rounded pill-like container.
 */
public class DataChip extends JPanel {
    
    @Getter
    private final DataSource dataSource;
    
    private final JButton removeButton;
    private final JLabel iconLabel;
    private final JLabel ledLabel; // LED indicator for connection status
    private final JLabel nameLabel;
    
    private static final int ARC_SIZE = 20;
    
    // Folder data source colors
    private static final Color LIGHT_FOLDER_BG = new Color(220, 240, 220);
    private static final Color LIGHT_FOLDER_BORDER = new Color(150, 200, 150);
    private static final Color DARK_FOLDER_BG = new Color(50, 70, 50);
    private static final Color DARK_FOLDER_BORDER = new Color(80, 110, 80);
    
    // Database data source colors
    private static final Color LIGHT_DB_BG = new Color(220, 230, 250);
    private static final Color LIGHT_DB_BORDER = new Color(150, 170, 220);
    private static final Color DARK_DB_BG = new Color(50, 60, 80);
    private static final Color DARK_DB_BORDER = new Color(80, 90, 120);
    
    // Generic/fallback colors
    private static final Color LIGHT_BG = new Color(230, 230, 230);
    private static final Color LIGHT_BORDER = new Color(180, 180, 180);
    private static final Color DARK_BG = new Color(60, 63, 65);
    private static final Color DARK_BORDER = new Color(80, 83, 85);
    
    private Runnable onRemove;
    
    /**
     * Creates a new data chip for the specified data source.
     * 
     * @param dataSource the data source to display
     */
    public DataChip(DataSource dataSource) {
        this.dataSource = dataSource;
        
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        
        // Icon label
        Icon icon = dataSource.getIcon();
        if (icon != null) {
            iconLabel = new JLabel(icon);
            add(iconLabel);
        } else {
            iconLabel = null;
        }
        
        // LED indicator for PulvisConnection
        if (dataSource instanceof PulvisConnection) {
            PulvisConnection pulvis = (PulvisConnection) dataSource;
            ledLabel = new JLabel(pulvis.getStatusIcon());
            ledLabel.setToolTipText(pulvis.isConnected() ? "Connected" : "Not connected");
            add(ledLabel);
        } else {
            ledLabel = null;
        }
        
        // Name label
        nameLabel = new JLabel(dataSource.getDisplayName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(12f));
        add(nameLabel);
        
        // Remove button
        removeButton = new JButton("×");
        removeButton.setFont(removeButton.getFont().deriveFont(16f));
        removeButton.setFocusPainted(false);
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setMargin(new Insets(0, 1, 0, 2));
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.setToolTipText("Remove");
        
        removeButton.addActionListener(e -> {
            if (onRemove != null) {
                onRemove.run();
            }
        });
        
        add(removeButton);
        
        // Set tooltip
        setToolTipText(dataSource.getTooltipText());
        
        // Set preferred size - use actual preferred size without adding extra
        Dimension pref = super.getPreferredSize();
        setPreferredSize(new Dimension(pref.width + 2, pref.height));
    }
    
    /**
     * Sets the callback to execute when the remove button is clicked.
     * 
     * @param onRemove the callback to run on removal
     */
    public void setOnRemove(Runnable onRemove) {
        this.onRemove = onRemove;
    }
    
    /**
     * Updates the LED indicator for PulvisConnection data sources.
     * Should be called periodically to refresh the connection status.
     */
    public void updateLedStatus() {
        if (ledLabel != null && dataSource instanceof PulvisConnection) {
            PulvisConnection pulvis = (PulvisConnection) dataSource;
            ledLabel.setIcon(pulvis.getStatusIcon());
            ledLabel.setToolTipText(pulvis.isConnected() ? "Connected" : "Not connected");
        }
    }
    
    private void updateColors() {
        if (nameLabel == null || removeButton == null) {
            return;
        }
        
        boolean isDark = GuiUtils.isDarkTheme();
        
        if (isDark) {
            nameLabel.setForeground(Color.WHITE);
            removeButton.setForeground(new Color(200, 200, 200));
        } else {
            nameLabel.setForeground(Color.BLACK);
            removeButton.setForeground(new Color(100, 100, 100));
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        
        boolean isDark = GuiUtils.isDarkTheme();
        
        // Draw rounded background
        RoundRectangle2D roundRect = new RoundRectangle2D.Double(
            0, 0, getWidth() - 1, getHeight() - 1, ARC_SIZE, ARC_SIZE
        );
        
        // Choose colors based on data source type
        Color bgColor, borderColor;
        if (dataSource instanceof FolderDataSource) {
            bgColor = isDark ? DARK_FOLDER_BG : LIGHT_FOLDER_BG;
            borderColor = isDark ? DARK_FOLDER_BORDER : LIGHT_FOLDER_BORDER;
        } else if (dataSource instanceof PulvisConnection) {
            bgColor = isDark ? DARK_DB_BG : LIGHT_DB_BG;
            borderColor = isDark ? DARK_DB_BORDER : LIGHT_DB_BORDER;
        } else {
            // Fallback for other types
            bgColor = isDark ? DARK_BG : LIGHT_BG;
            borderColor = isDark ? DARK_BORDER : LIGHT_BORDER;
        }
        
        g2d.setColor(bgColor);
        g2d.fill(roundRect);
        
        // Draw border
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(roundRect);
        
        g2d.dispose();
        
        super.paintComponent(g);
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        updateColors();
    }
}
