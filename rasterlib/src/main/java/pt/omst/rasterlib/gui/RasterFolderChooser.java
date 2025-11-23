//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterlib.sscache.SidescanDataCollection;

/**
 * A custom JFileChooser that validates raster folders during selection.
 * Valid folders must contain either:
 * a) A subfolder named "rasterIndex/" with JSON files, OR
 * b) One or more files with .sdf extension, OR
 * c) One or more files with .jsf extension, OR
 * d) One or more files with .sds extension
 * 
 * An accessory panel shows real-time validation status as the user browses.
 */
@Slf4j
public class RasterFolderChooser extends JFileChooser {
    
    private static final long serialVersionUID = 1L;
    private static final int CACHE_SIZE = 50;
    
    // Cache for validation results to avoid repeated file system checks
    private final Map<String, Boolean> validationCache = new LinkedHashMap<String, Boolean>(CACHE_SIZE + 1, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    
    private final JLabel statusLabel;
    private final JPanel accessoryPanel;
    
    /**
     * Creates a new RasterFolderChooser configured for directory selection
     * with real-time validation feedback.
     */
    public RasterFolderChooser() {
        super();
        setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        setDialogTitle("Select Raster Folder");
        
        // Create accessory panel with status indicator
        accessoryPanel = new JPanel(new BorderLayout());
        accessoryPanel.setPreferredSize(new Dimension(200, 80));
        accessoryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 10, 10, 10),
            BorderFactory.createLineBorder(Color.GRAY, 1)
        ));
        
        statusLabel = new JLabel("Select a folder...");
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        accessoryPanel.add(statusLabel, BorderLayout.CENTER);
        
        setAccessory(accessoryPanel);
        
        // Listen for folder selection changes
        addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    updateValidationStatus();
                }
            }
        });
    }
    
    /**
     * Validates if the given folder is a valid raster folder.
     * A valid folder must contain either:
     * - A rasterIndex/ subfolder with JSON files, OR
     * - Raw sidescan data files (.sdf, .jsf, or .sds)
     * 
     * @param folder the folder to validate
     * @return true if the folder contains valid raster data
     */
    public static boolean isValidRasterFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return false;
        }
        
        // Check for rasterIndex subfolder with JSON files
        if (SidescanDataCollection.hasRasterIndex(folder)) {
            return true;
        }
        
        // Check for raw sidescan data files in the folder
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".sdf") || name.endsWith(".jsf") || name.endsWith(".sds")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Updates the validation status display based on the currently selected folder.
     */
    private void updateValidationStatus() {
        File selectedFile = getSelectedFile();
        
        if (selectedFile == null) {
            statusLabel.setText("<html><center>Select a folder...</center></html>");
            statusLabel.setIcon(null);
            return;
        }
        
        // Check cache first
        String path = selectedFile.getAbsolutePath();
        Boolean cachedResult = validationCache.get(path);
        boolean isValid;
        
        if (cachedResult != null) {
            isValid = cachedResult;
        } else {
            isValid = isValidRasterFolder(selectedFile);
            validationCache.put(path, isValid);
        }
        
        // Update status display
        if (isValid) {
            statusLabel.setText("<html><center><font color='green'>✓</font> Valid raster folder</center></html>");
            statusLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
        } else {
            statusLabel.setText("<html><center><font color='orange'>⚠</font> Invalid folder</center></html>");
            statusLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        }
    }
    
    /**
     * Validates the selected folder before approval. If the folder is invalid,
     * shows a warning dialog with the validation criteria and allows the user
     * to proceed anyway or cancel the selection.
     */
    @Override
    public void approveSelection() {
        File selectedFolder = getSelectedFile();
        
        if (selectedFolder != null && !isValidRasterFolder(selectedFolder)) {
            String message = "The selected folder does not appear to contain valid raster data.\n\n" +
                    "Valid folders must contain one of the following:\n" +
                    "  a) A subfolder named 'rasterIndex/' with JSON files\n" +
                    "  b) One or more files with .sdf extension\n" +
                    "  c) One or more files with .jsf extension\n" +
                    "  d) One or more files with .sds extension\n\n" +
                    "Do you want to select this folder anyway?";
            
            Component parent = this.getParent();
            int result = JOptionPane.showConfirmDialog(
                parent,
                message,
                "Invalid Raster Folder",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result != JOptionPane.YES_OPTION) {
                log.info("User cancelled selection of invalid folder: {}", selectedFolder.getAbsolutePath());
                return; // Don't proceed with selection
            }
            
            log.warn("User proceeded with invalid folder selection: {}", selectedFolder.getAbsolutePath());
        }
        
        super.approveSelection();
    }
    
    /**
     * Main method for testing the RasterFolderChooser.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            pt.lsts.neptus.util.GuiUtils.setLookAndFeel();
            
            RasterFolderChooser chooser = new RasterFolderChooser();
            int result = chooser.showOpenDialog(null);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                System.out.println("Selected folder: " + selected.getAbsolutePath());
                System.out.println("Is valid: " + isValidRasterFolder(selected));
            } else {
                System.out.println("Selection cancelled");
            }
            
            System.exit(0);
        });
    }
}
