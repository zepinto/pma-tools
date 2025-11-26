//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.image.Raster;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.I18n;
import pt.omst.gui.datasource.DataChip;
import pt.omst.gui.datasource.DataSource;
import pt.omst.gui.datasource.DataSourceEvent;
import pt.omst.gui.datasource.DataSourceListener;
import pt.omst.gui.datasource.DatabaseConnectionDialog;
import pt.omst.gui.datasource.FolderDataSource;
import pt.omst.gui.datasource.PulvisDataSource;
import pt.omst.gui.datasource.RasterfallDataSource;

/**
 * A panel for managing multiple data sources with a visual chip-based interface.
 * Supports adding folders and database connections, with extensible architecture
 * for future data source types.
 * 
 * <p>Example usage:
 * <pre>
 * DataSourceManagerPanel panel = new DataSourceManagerPanel();
 * panel.addDataSourceListener(new DataSourceListener() {
 *     public void sourceAdded(DataSourceEvent e) {
 *         System.out.println("Added: " + e.getDataSource().getDisplayName());
 *     }
 *     public void sourceRemoved(DataSourceEvent e) {
 *         System.out.println("Removed: " + e.getDataSource().getDisplayName());
 *     }
 * });
 * </pre>
 */
@Slf4j
public class DataSourceManagerPanel extends JPanel {
    
    private final JPanel chipsPanel;
    private final List<DataSourceListener> listeners;
    private final List<DataSource> dataSources;
    private final Timer ledUpdateTimer;
    
    private static final int LED_UPDATE_INTERVAL_MS = 5000; // Update LED every 5 seconds
    
    /**
     * Creates a new DataSourceManagerPanel.
     */
    public DataSourceManagerPanel() {
        this.listeners = new ArrayList<>();
        this.dataSources = new ArrayList<>();
        
        setLayout(new BorderLayout(5, 5));
        //setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create chips display area (CENTER)
        chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        chipsPanel.setBackground(UIManager.getColor("TextArea.background"));
        
        // Create panel with action buttons
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = createActionBar();
        
        JScrollPane scrollPane = new JScrollPane(chipsPanel);
        scrollPane.setBorder(UIManager.getBorder("TextField.border"));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        topPanel.add(buttonPanel, BorderLayout.WEST);
        topPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.CENTER);
        
        // Setup LED update timer for PulvisConnection status
        ledUpdateTimer = new Timer(LED_UPDATE_INTERVAL_MS, e -> updateAllLedIndicators());
        ledUpdateTimer.start();
    }
    
    private JPanel createActionBar() {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        
        // Create icon-only buttons
        JButton addFolderButton = new JButton(UIManager.getIcon("FileView.directoryIcon"));
        addFolderButton.setToolTipText(I18n.textOrDefault("datasource.add.folder", "Add Folder..."));
        addFolderButton.setFocusPainted(false);
        addFolderButton.addActionListener(e -> addFolder());
        
        JButton addDbButton = new JButton(UIManager.getIcon("FileView.computerIcon"));
        addDbButton.setToolTipText(I18n.textOrDefault("datasource.add.database", "Add DB Connection..."));
        addDbButton.setFocusPainted(false);
        addDbButton.addActionListener(e -> addDatabaseConnection());
        
        actionBar.add(addFolderButton);
        actionBar.add(addDbButton);
        
        return actionBar;
    }
    
    public void addFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle(I18n.textOrDefault("datasource.select.folder", "Select Folder"));
        
        Window window = SwingUtilities.getWindowAncestor(this);
        int result = fileChooser.showOpenDialog(window);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            
            // Check if folder or parent/child already exists
            if (isFolderAlreadyAdded(selectedFolder)) {
                GuiUtils.errorMessage(window, "Folder Already Added",
                    I18n.textOrDefault("datasource.error.folder.exists", 
                        "This folder or a parent/child folder has already been added."));
                log.warn("Folder already added or conflicts with existing folder: {}", 
                    selectedFolder.getAbsolutePath());
                return;
            }
            
            FolderDataSource dataSource = new FolderDataSource(selectedFolder);
            addDataSource(dataSource);
            log.info("Added folder data source: {}", selectedFolder.getAbsolutePath());
        }
    }

    public void addRasterfallSource(RasterfallDataSource rds) {
        // remove any previous RasterfallDataSource
        List<DataSource> toRemove = new ArrayList<>();
        for (DataSource source : dataSources) {
            if (source instanceof RasterfallDataSource) {
                toRemove.add(source);
            }
        }
        for (DataSource source : toRemove) {
            removeDataSource(source);
        }
        addDataSource(rds);
        log.info("Added Rasterfall data source: {}", rds.getDisplayName());
    }
    
    /**
     * Checks if a folder has already been added, or if it's a parent or child
     * of an already added folder.
     * 
     * @param folder the folder to check
     * @return true if the folder conflicts with existing folders
     */
    private boolean isFolderAlreadyAdded(File folder) {
        try {
            String canonicalPath = folder.getCanonicalPath();
            
            for (DataSource source : dataSources) {
                if (source instanceof FolderDataSource) {
                    FolderDataSource folderSource = (FolderDataSource) source;
                    String existingPath = folderSource.getFolder().getCanonicalPath();
                    
                    // Check if it's the same folder
                    if (canonicalPath.equals(existingPath)) {
                        return true;
                    }
                    
                    // Check if new folder is a child of existing folder
                    if (canonicalPath.startsWith(existingPath + File.separator)) {
                        return true;
                    }
                    
                    // Check if new folder is a parent of existing folder
                    if (existingPath.startsWith(canonicalPath + File.separator)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking folder paths", e);
        }
        
        return false;
    }
    
    private void addDatabaseConnection() {
        Window window = SwingUtilities.getWindowAncestor(this);
        PulvisDataSource dataSource = DatabaseConnectionDialog.showDialog(window);
        
        if (dataSource != null) {
            addDataSource(dataSource);
            log.info("Added database data source: {}", dataSource.getDisplayName());
        }
    }
    
    /**
     * Adds a data source to the panel programmatically.
     * 
     * @param dataSource the data source to add
     */
    public void addDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("Data source cannot be null");
        }
        
        // Check for duplicates
        if (dataSources.contains(dataSource)) {
            log.warn("Data source already exists: {}", dataSource.getDisplayName());
            return;
        }
        
        // Add to internal list
        dataSources.add(dataSource);
        
        // Create chip
        DataChip chip = new DataChip(dataSource);
        chip.setOnRemove(() -> removeDataSource(dataSource, chip));
        chipsPanel.add(chip);
        
        // Update display
        chipsPanel.revalidate();
        chipsPanel.repaint();
        
        // Fire event
        fireSourceAdded(dataSource);
    }
    
    /**
     * Removes a data source from the panel.
     * 
     * @param dataSource the data source to remove
     */
    public void removeDataSource(DataSource dataSource) {
        // Find and remove the chip
        for (int i = 0; i < chipsPanel.getComponentCount(); i++) {
            if (chipsPanel.getComponent(i) instanceof DataChip) {
                DataChip chip = (DataChip) chipsPanel.getComponent(i);
                if (chip.getDataSource().equals(dataSource)) {
                    removeDataSource(dataSource, chip);
                    break;
                }
            }
        }
    }
    
    private void removeDataSource(DataSource dataSource, DataChip chip) {
        // Remove from internal list
        dataSources.remove(dataSource);
        
        // Remove chip from display
        chipsPanel.remove(chip);
        chipsPanel.revalidate();
        chipsPanel.repaint();
        
        // Fire event
        fireSourceRemoved(dataSource);
        
        log.info("Removed data source: {}", dataSource.getDisplayName());
    }
    
    /**
     * Gets all data sources currently managed by this panel.
     * 
     * @return a list of all data sources
     */
    public List<DataSource> getDataSources() {
        return new ArrayList<>(dataSources);
    }

    public List<PulvisDataSource> getDatabaseConnections() {
        List<PulvisDataSource> dbConnections = new ArrayList<>();
        for (DataSource source : dataSources) {
            if (source instanceof PulvisDataSource) {
                dbConnections.add((PulvisDataSource) source);
            }
        }
        return dbConnections;
    }

    public List<File> getFolderDataSources() {
        List<File> folders = new ArrayList<>();
        for (DataSource source : dataSources) {
            if (source instanceof FolderDataSource) {
                folders.add(((FolderDataSource) source).getFolder());
            }
        }
        return folders;
    }
    
    /**
     * Removes all data sources from the panel.
     */
    public void clearDataSources() {
        List<DataSource> toRemove = new ArrayList<>(dataSources);
        for (DataSource source : toRemove) {
            removeDataSource(source);
        }
    }
    
    /**
     * Updates all LED indicators for PulvisConnection data sources.
     */
    private void updateAllLedIndicators() {
        for (int i = 0; i < chipsPanel.getComponentCount(); i++) {
            if (chipsPanel.getComponent(i) instanceof DataChip) {
                DataChip chip = (DataChip) chipsPanel.getComponent(i);
                chip.updateLedStatus();
            }
        }
    }
    
    /**
     * Cleanup method to stop the LED update timer.
     * Should be called when the panel is no longer needed.
     */
    public void dispose() {
        if (ledUpdateTimer != null) {
            ledUpdateTimer.stop();
        }
    }
    
    /**
     * Adds a data source listener to receive notifications of changes.
     * 
     * @param listener the listener to add
     */
    public void addDataSourceListener(DataSourceListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Removes a data source listener.
     * 
     * @param listener the listener to remove
     */
    public void removeDataSourceListener(DataSourceListener listener) {
        listeners.remove(listener);
    }
    
    private void fireSourceAdded(DataSource dataSource) {
        DataSourceEvent event = new DataSourceEvent(this, dataSource);
        for (DataSourceListener listener : listeners) {
            try {
                listener.sourceAdded(event);
            } catch (Exception e) {
                log.error("Error notifying listener of source addition", e);
            }
        }
    }
    
    private void fireSourceRemoved(DataSource dataSource) {
        DataSourceEvent event = new DataSourceEvent(this, dataSource);
        for (DataSourceListener listener : listeners) {
            try {
                listener.sourceRemoved(event);
            } catch (Exception e) {
                log.error("Error notifying listener of source removal", e);
            }
        }
    }
    
    /**
     * Saves the current data sources to user preferences.
     * Stores folder paths and database connection details.
     */
    public void saveToPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(DataSourceManagerPanel.class);
        
        // Clear previous entries
        try {
            prefs.clear();
        } catch (Exception e) {
            log.error("Error clearing preferences", e);
        }
        
        // Count each type
        int folderCount = 0;
        int dbCount = 0;
        
        for (DataSource source : dataSources) {
            if (source instanceof FolderDataSource) {
                FolderDataSource folder = (FolderDataSource) source;
                prefs.put("folder." + folderCount, folder.getFolder().getAbsolutePath());
                folderCount++;
            } else if (source instanceof PulvisDataSource) {
                PulvisDataSource db = (PulvisDataSource) source;
                prefs.put("db." + dbCount + ".host", db.getHost());
                prefs.putInt("db." + dbCount + ".port", db.getPort());
                dbCount++;
            }
        }
        
        prefs.putInt("folder.count", folderCount);
        prefs.putInt("db.count", dbCount);
        
        // Force flush to ensure preferences are written to disk
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
        
        log.info("Saved {} folders and {} database connections to preferences", folderCount, dbCount);
    }
    
    /**
     * Loads data sources from user preferences.
     * Restores previously saved folder paths and database connections.
     */
    public void loadFromPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(DataSourceManagerPanel.class);
        
        // Load folders
        int folderCount = prefs.getInt("folder.count", 0);
        for (int i = 0; i < folderCount; i++) {
            String path = prefs.get("folder." + i, null);
            if (path != null) {
                File folder = new File(path);
                if (folder.exists() && folder.isDirectory()) {
                    addDataSource(new FolderDataSource(folder));
                    log.debug("Loaded folder from preferences: {}", path);
                } else {
                    log.warn("Folder from preferences no longer exists: {}", path);
                }
            }
        }
        
        // Load database connections
        int dbCount = prefs.getInt("db.count", 0);
        for (int i = 0; i < dbCount; i++) {
            String host = prefs.get("db." + i + ".host", null);
            int port = prefs.getInt("db." + i + ".port", 8080);
            
            if (host != null) {
                addDataSource(new PulvisDataSource(host, port));
                log.debug("Loaded database connection from preferences: {}:{}", host, port);
            }
        }
        
        log.info("Loaded {} folders and {} database connections from preferences", folderCount, dbCount);
    }
    
    /**
     * Example main method demonstrating usage of DataSourceManagerPanel.
     */
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
         // Create test data spanning 10 years
        Instant now = Instant.now();
        Instant tenYearsAgo = now.minus(Duration.ofDays(10 * 365));

        JFrame frame = new JFrame("Data Source Manager Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        DataSourceManagerPanel panel = new DataSourceManagerPanel();
        ZoomableTimeIntervalSelector selector = new ZoomableTimeIntervalSelector(tenYearsAgo, now);
        panel.add(selector, BorderLayout.SOUTH);
        
        // // Add listener to demonstrate event handling
        // panel.addDataSourceListener(new DataSourceListener() {
        //     @Override
        //     public void sourceAdded(DataSourceEvent event) {
        //         DataSource source = event.getDataSource();
        //         System.out.println("Source Added: " + source.getDisplayName());
        //         System.out.println("  Type: " + source.getClass().getSimpleName());
        //         System.out.println("  Details: " + source.getTooltipText().replaceAll("<[^>]*>", ""));
        //     }
            
        //     @Override
        //     public void sourceRemoved(DataSourceEvent event) {
        //         DataSource source = event.getDataSource();
        //         System.out.println("Source Removed: " + source.getDisplayName());
        //     }
        // });
        
        // Add some example data sources programmatically
        //panel.addDataSource(new FolderDataSource(new File(System.getProperty("user.home"))));
        //panel.addDataSource(new DatabaseDataSource("localhost", 3306, "testdb", "root", "", "MySQL"));
        
        frame.setContentPane(panel);
        frame.setSize(700, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        System.out.println("\n=== Data Source Manager Demo ===");
        System.out.println("Use the buttons to add folders or database connections.");
        System.out.println("Click the × button on any chip to remove it.");
        System.out.println("Console output will show all add/remove events.\n");
    }
}
