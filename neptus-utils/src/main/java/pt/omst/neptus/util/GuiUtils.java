//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2025 OceanScan-MST
 * All rights reserved.
 *
 * This file is part of Neptus Utilities.
 */

package pt.omst.neptus.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Hashtable;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * GUI utility methods for standalone applications.
 * 
 * @author Paulo Dias
 * @author Ze Carlos
 */
public class GuiUtils {
    /** Log handle. */
    private static final Logger LOG = LoggerFactory.getLogger(GuiUtils.class);
    
    private static boolean lookAndFeelSet = false;

    /**
     * Sets the Look and Feel to FlatLaf (modern flat UI).
     * Uses FlatDarkLaf by default, but can be configured via system property.
     */
    public static void setLookAndFeel() {
        if (lookAndFeelSet) {
            return;
        }
        
        try {
            // Check for dark mode preference
            String theme = System.getProperty("flatlaf.theme", "dark");
            
            if ("dark".equalsIgnoreCase(theme)) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            
            lookAndFeelSet = true;
            LOG.info("Look and Feel set to FlatLaf ({})", theme);
        } catch (Exception e) {
            LOG.error("Failed to set Look and Feel", e);
            try {
                // Fallback to system L&F
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                lookAndFeelSet = true;
            } catch (Exception ex) {
                LOG.error("Failed to set system Look and Feel", ex);
            }
        }
    }

    /**
     * Loads an image from a resource path.
     * 
     * @param resourcePath Path to the image resource
     * @return BufferedImage or null if not found
     */
    public static BufferedImage getImage(String resourcePath) {
        try {
            InputStream is = GuiUtils.class.getResourceAsStream(resourcePath);
            if (is == null) {
                // Try loading from file system
                is = GuiUtils.class.getClassLoader().getResourceAsStream(resourcePath);
            }
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (IOException e) {
            LOG.warn("Failed to load image: {}", resourcePath, e);
        }
        return null;
    }

    /**
     * Loads an image icon from a resource path.
     * 
     * @param resourcePath Path to the image resource
     * @return ImageIcon or null if not found
     */
    public static ImageIcon getIcon(String resourcePath) {
        BufferedImage img = getImage(resourcePath);
        return img != null ? new ImageIcon(img) : null;
    }

    /**
     * Shows an error message dialog.
     * 
     * @param parent Parent component
     * @param title Dialog title
     * @param message Error message
     */
    public static void errorMessage(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows an error message dialog with exception details.
     * 
     * @param parent Parent component
     * @param title Dialog title
     * @param e Exception to display
     */
    public static void errorMessage(Component parent, String title, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        errorMessage(parent, title, message);
    }

    /**
     * Shows an info message dialog.
     * 
     * @param parent Parent component
     * @param title Dialog title
     * @param message Info message
     */
    public static void infoMessage(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows a warning message dialog.
     * 
     * @param parent Parent component
     * @param title Dialog title
     * @param message Warning message
     */
    public static void warnMessage(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Shows a confirmation dialog.
     * 
     * @param parent Parent component
     * @param title Dialog title
     * @param message Confirmation message
     * @return true if user confirmed, false otherwise
     */
    public static boolean confirmDialog(Component parent, String title, String message) {
        int result = JOptionPane.showConfirmDialog(parent, message, title, 
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Creates a scaled image icon.
     * 
     * @param original Original image
     * @param width Target width
     * @param height Target height
     * @return Scaled ImageIcon
     */
    public static ImageIcon getScaledIcon(BufferedImage original, int width, int height) {
        if (original == null) {
            return null;
        }
        Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /**
     * Test if an image has transparency.
     * 
     * @param image Image to test
     * @return true if image has alpha channel
     */
    public static boolean testImage(BufferedImage image) {
        return image != null && image.getColorModel().hasAlpha();
    }

    /**
     * Centers a window on the screen.
     * 
     * @param window Window to center
     */
    public static void centerOnScreen(Window window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension windowSize = window.getSize();
        int x = (screenSize.width - windowSize.width) / 2;
        int y = (screenSize.height - windowSize.height) / 2;
        window.setLocation(x, y);
    }

    /**
     * Gets the default font size for the current Look and Feel.
     * 
     * @return Default font size
     */
    public static int getDefaultFontSize() {
        Font font = UIManager.getFont("Label.font");
        return font != null ? font.getSize() : 12;
    }

    /**
     * Check if dark theme is currently active.
     * 
     * @return true if dark theme is active
     */
    public static boolean isDarkTheme() {
        String theme = System.getProperty("flatlaf.theme", "dark");
        return "dark".equalsIgnoreCase(theme);
    }

    /**
     * Create a test frame for a component.
     * 
     * @param component Component to display
     * @param title Frame title
     * @param width Frame width
     * @param height Frame height
     * @return The created frame
     */
    public static JFrame testFrame(Component component, String title, int width, int height) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(width, height);
        frame.add(component);
        frame.setVisible(true);
        centerOnScreen(frame);
        return frame;
    }

    private static final Hashtable<Integer, NumberFormat> nformats = new Hashtable<>();

    public static NumberFormat getNeptusDecimalFormat(int fractionDigits) {
        if (nformats.containsKey(fractionDigits))
            return nformats.get(fractionDigits);

        NumberFormat df = getNeptusDecimalFormat();
        df.setMaximumFractionDigits(fractionDigits);
        df.setMinimumFractionDigits(fractionDigits);
        nformats.put(fractionDigits, df);
        return df;
    }

    public static NumberFormat getNeptusDecimalFormat() {
        NumberFormat df = DecimalFormat.getInstance(Locale.US);
        df.setGroupingUsed(false);
        df.setMaximumFractionDigits(15);
        return df;
    }
}
