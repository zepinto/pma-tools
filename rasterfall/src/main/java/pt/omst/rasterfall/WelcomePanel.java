//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Welcome panel displayed when RasterFall application starts with no data loaded.
 * Shows the application icon, OceanScan logo, version information, and license details.
 */
public class WelcomePanel extends JPanel {
    
    private static final long serialVersionUID = 1L;
    private static final String VERSION = "2025.11.00";
    
    public WelcomePanel() {
        setLayout(new GridBagLayout());
        updateColors();
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.anchor = GridBagConstraints.CENTER;
        
        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        
        // Rasterfall icon
        try {
            Image rasterfallImage = ImageIO.read(getClass().getResourceAsStream("/images/rasterfall.png"));
            if (rasterfallImage != null) {
                // Scale to reasonable size while maintaining aspect ratio
                int maxSize = 200;
                int width = rasterfallImage.getWidth(null);
                int height = rasterfallImage.getHeight(null);
                double scale = Math.min((double)maxSize / width, (double)maxSize / height);
                int scaledWidth = (int)(width * scale);
                int scaledHeight = (int)(height * scale);
                
                Image scaledImage = rasterfallImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
                JLabel iconLabel = new JLabel(new ImageIcon(scaledImage));
                iconLabel.setAlignmentX(CENTER_ALIGNMENT);
                contentPanel.add(iconLabel);
                contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
            }
        } catch (IOException e) {
            System.err.println("Failed to load rasterfall icon: " + e.getMessage());
        }
        
        // Application title
        JLabel titleLabel = new JLabel("Sidescan RasterFall");
        titleLabel.setFont(new Font("Sans-Serif", Font.BOLD, 32));
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Version
        JLabel versionLabel = new JLabel("Version " + VERSION);
        versionLabel.setFont(new Font("Sans-Serif", Font.PLAIN, 16));
        versionLabel.setAlignmentX(CENTER_ALIGNMENT);
        contentPanel.add(versionLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        
        // OceanScan logo
        try {
            Image oceanscanImage = ImageIO.read(getClass().getResourceAsStream("/images/oceanscan.png"));
            if (oceanscanImage != null) {
                // Scale logo to appropriate size
                int maxLogoWidth = 300;
                int width = oceanscanImage.getWidth(null);
                int height = oceanscanImage.getHeight(null);
                double scale = (double)maxLogoWidth / width;
                int scaledWidth = (int)(width * scale);
                int scaledHeight = (int)(height * scale);
                
                Image scaledLogo = oceanscanImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
                JLabel logoLabel = new JLabel(new ImageIcon(scaledLogo));
                logoLabel.setAlignmentX(CENTER_ALIGNMENT);
                contentPanel.add(logoLabel);
                contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        } catch (IOException e) {
            System.err.println("Failed to load OceanScan logo: " + e.getMessage());
        }
        
        // Copyright
        JLabel copyrightLabel = new JLabel("Copyright © 2025 OceanScan - Marine Systems & Technology, Lda.");
        copyrightLabel.setFont(new Font("Sans-Serif", Font.PLAIN, 11));
        copyrightLabel.setAlignmentX(CENTER_ALIGNMENT);
        contentPanel.add(copyrightLabel);
        
        add(contentPanel, gbc);
    }
    
    /**
     * Updates panel colors based on current theme.
     */
    private void updateColors() {
        // Get theme-aware colors from UIManager
        Color backgroundColor = javax.swing.UIManager.getColor("Panel.background");
        Color foregroundColor = javax.swing.UIManager.getColor("Label.foreground");
        
        setBackground(backgroundColor != null ? backgroundColor : Color.WHITE);
        
        // Update all child components recursively
        updateComponentColors(this, foregroundColor);
    }
    
    /**
     * Recursively updates foreground colors for all label components.
     */
    private void updateComponentColors(java.awt.Container container, Color foreground) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof JLabel) {
                comp.setForeground(foreground);
            }
            if (comp instanceof java.awt.Container) {
                updateComponentColors((java.awt.Container) comp, foreground);
            }
        }
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        // Update colors when theme changes
        if (isDisplayable()) {
            updateColors();
        }
    }
}
