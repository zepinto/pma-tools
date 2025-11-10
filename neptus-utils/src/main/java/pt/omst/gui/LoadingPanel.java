//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.gui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import pt.omst.neptus.util.ImageUtils;

/**
 * A sleek animated loading panel with OceanScan logo for use as a splash screen.
 * Features a modern gradient background, animated spinner, and status messages.
 * 
 * @author OceanScan-MST
 */
public class LoadingPanel extends JPanel {
    
    private static final long serialVersionUID = 1L;
    
    // Animation state
    private float rotation = 0f;
    private float glowAlpha = 0f;
    private boolean glowIncreasing = true;
    private Timer animationTimer;
    
    // UI Components
    private final JLabel statusLabel;
    private final JLabel logoLabel;
    
    // Colors
    private static final Color BACKGROUND_START = new Color(10, 25, 45);
    private static final Color BACKGROUND_END = new Color(20, 40, 70);
    private static final Color ACCENT_COLOR = new Color(0, 150, 255);
    private static final Color SPINNER_COLOR = new Color(0, 180, 255, 200);
    
    // Dimensions
    private static final int LOGO_WIDTH = 400;
    private static final int LOGO_HEIGHT = 120;
    private static final int SPINNER_SIZE = 60;
    private static final int SPINNER_THICKNESS = 6;
    
    /**
     * Creates a new loading panel with OceanScan branding
     */
    public LoadingPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(600, 400));
        setBackground(BACKGROUND_START);
        
        // Create center panel for logo and spinner
        JPanel centerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw animated spinner
                int centerX = getWidth() / 2;
                int centerY = (int) (getHeight() * 0.7);
                
                drawSpinner(g2d, centerX, centerY);
                
                g2d.dispose();
            }
        };
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        
        // Load and add OceanScan logo
        Image oceanScanImage = ImageUtils.getScaledImage("images/oceanscan.png", LOGO_WIDTH, LOGO_HEIGHT);
        logoLabel = oceanScanImage != null 
            ? new JLabel(new javax.swing.ImageIcon(oceanScanImage)) 
            : new JLabel("OceanScan");
        
        logoLabel.setAlignmentX(CENTER_ALIGNMENT);
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Status label
        statusLabel = new JLabel("Initializing...");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Add components to center panel
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(logoLabel);
        centerPanel.add(Box.createVerticalStrut(80));
        centerPanel.add(Box.createVerticalGlue());
        
        // Bottom panel for status
        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 30, 20));
        
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        bottomPanel.add(statusLabel);
        
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Start animation
        startAnimation();
    }
    
    /**
     * Draws the animated spinner
     */
    private void drawSpinner(Graphics2D g2d, int centerX, int centerY) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw glow effect
        if (glowAlpha > 0) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowAlpha * 0.3f));
            g2d.setColor(ACCENT_COLOR);
            
            for (int i = 0; i < 3; i++) {
                int glowSize = SPINNER_SIZE + (i + 1) * 8;
                Ellipse2D glowCircle = new Ellipse2D.Double(
                    centerX - glowSize / 2.0,
                    centerY - glowSize / 2.0,
                    glowSize,
                    glowSize
                );
                g2d.draw(glowCircle);
            }
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
        // Draw spinning arc
        g2d.setColor(SPINNER_COLOR);
        g2d.setStroke(new java.awt.BasicStroke(SPINNER_THICKNESS, 
            java.awt.BasicStroke.CAP_ROUND, 
            java.awt.BasicStroke.JOIN_ROUND));
        
        // Main arc
        Arc2D arc = new Arc2D.Double(
            centerX - SPINNER_SIZE / 2.0,
            centerY - SPINNER_SIZE / 2.0,
            SPINNER_SIZE,
            SPINNER_SIZE,
            rotation,
            270,
            Arc2D.OPEN
        );
        g2d.draw(arc);
        
        // Secondary arc (trailing)
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        Arc2D arc2 = new Arc2D.Double(
            centerX - SPINNER_SIZE / 2.0,
            centerY - SPINNER_SIZE / 2.0,
            SPINNER_SIZE,
            SPINNER_SIZE,
            rotation + 180,
            90,
            Arc2D.OPEN
        );
        g2d.draw(arc2);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Draw gradient background
        GradientPaint gradient = new GradientPaint(
            0, 0, BACKGROUND_START,
            0, getHeight(), BACKGROUND_END
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        g2d.dispose();
    }
    
    /**
     * Starts the loading animation
     */
    private void startAnimation() {
        animationTimer = new Timer(16, new ActionListener() { // ~60 FPS
            @Override
            public void actionPerformed(ActionEvent e) {
                // Rotate spinner
                rotation = (rotation + 4) % 360;
                
                // Pulse glow
                if (glowIncreasing) {
                    glowAlpha += 0.02f;
                    if (glowAlpha >= 1.0f) {
                        glowAlpha = 1.0f;
                        glowIncreasing = false;
                    }
                } else {
                    glowAlpha -= 0.02f;
                    if (glowAlpha <= 0.0f) {
                        glowAlpha = 0.0f;
                        glowIncreasing = true;
                    }
                }
                
                repaint();
            }
        });
        animationTimer.start();
    }
    
    /**
     * Stops the loading animation
     */
    public void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
    
    /**
     * Updates the status message displayed on the loading panel
     * 
     * @param status the new status message
     */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }
    
    /**
     * Shows a loading splash screen window
     * 
     * @return the JWindow containing the loading panel
     */
    public static JWindow showSplashScreen() {
        return showSplashScreen("Initializing...", null);
    }
    
    /**
     * Shows a loading splash screen with a custom status message
     * 
     * @param status initial status message
     * @return the JWindow containing the loading panel
     */
    public static JWindow showSplashScreen(String status) {
        return showSplashScreen(status, null);
    }
    
    /**
     * Shows a loading splash screen with a custom status message centered on a parent component
     * 
     * @param status initial status message
     * @param parent parent component to center the splash screen on (null for screen center)
     * @return the JWindow containing the loading panel
     */
    public static JWindow showSplashScreen(String status, java.awt.Component parent) {
        JWindow splash = new JWindow();
        LoadingPanel loadingPanel = new LoadingPanel();
        loadingPanel.setStatus(status);
        splash.setContentPane(loadingPanel);
        splash.pack();
        splash.setLocationRelativeTo(parent);
        splash.setAlwaysOnTop(true);
        splash.setVisible(true);
        splash.toFront();
        
        // Force immediate rendering
        splash.paint(splash.getGraphics());
        
        return splash;
    }
    
    /**
     * Gets the LoadingPanel from a splash screen window
     * 
     * @param splash the splash screen window
     * @return the LoadingPanel instance, or null if not found
     */
    public static LoadingPanel getLoadingPanel(JWindow splash) {
        if (splash != null && splash.getContentPane() instanceof LoadingPanel) {
            return (LoadingPanel) splash.getContentPane();
        }
        return null;
    }
    
    /**
     * Hides and disposes the splash screen
     * 
     * @param splash the splash screen window to hide
     */
    public static void hideSplashScreen(JWindow splash) {
        if (splash != null) {
            LoadingPanel panel = getLoadingPanel(splash);
            if (panel != null) {
                panel.stopAnimation();
            }
            splash.setVisible(false);
            splash.dispose();
        }
    }
    
    /**
     * Demo main method
     */
    public static void main(String[] args) {
        pt.omst.neptus.util.GuiUtils.setLookAndFeel();
        
        JWindow splash = LoadingPanel.showSplashScreen("Loading application...");
        LoadingPanel panel = getLoadingPanel(splash);
        
        // Simulate loading process
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                if (panel != null) panel.setStatus("Loading modules...");
                Thread.sleep(1500);
                if (panel != null) panel.setStatus("Initializing components...");
                Thread.sleep(1500);
                if (panel != null) panel.setStatus("Almost ready...");
                Thread.sleep(1000);
                
                // Close splash
                javax.swing.SwingUtilities.invokeLater(() -> {
                    hideSplashScreen(splash);
                    System.out.println("Application loaded!");
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
