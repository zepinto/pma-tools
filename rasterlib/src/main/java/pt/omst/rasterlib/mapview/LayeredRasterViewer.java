//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.contacts.ContactCollection;

/**
 * A map viewer application that loads indexed rasters and contacts from a folder hierarchy,
 * organizing them into layers that can be toggled and filtered by timestamp.
 * 
 * <p>This viewer recursively searches for folders containing rasterIndex subdirectories and
 * groups the rasters within each folder as a separate layer. It also loads all .zct contact
 * files from the folder hierarchy and displays them on the map.</p>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Layer-based organization: Each folder with rasterIndex becomes a layer</li>
 *   <li>Toggle visibility: Individual layers and contacts can be shown/hidden</li>
 *   <li>Time filtering: Filter rasters and contacts by timestamp range</li>
 *   <li>Interactive map: Pan, zoom, and explore the data</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // Run from command line with folder path
 * LayeredRasterViewer /path/to/data/folder
 * 
 * // Or run and use File > Open Folder menu
 * LayeredRasterViewer
 * </pre>
 * 
 * @see RasterLayer
 * @see FilterableContactCollection
 * @see IndexedRasterPainter
 */
@Slf4j
public class LayeredRasterViewer extends JFrame {
    
    private final SlippyMap map;
    private final JPanel layerPanel;
    private final List<RasterLayer> rasterLayers = new ArrayList<>();
    private final List<JPanel> layerControlPanels = new ArrayList<>();
    private FilterableContactCollection filterableContactCollection;
    private JCheckBox showContactsCheckbox;
    private JCheckBox showRastersCheckbox;
    private JSpinner startDateSpinner;
    private JSpinner endDateSpinner;
    private long globalMinTimestamp = Long.MAX_VALUE;
    private long globalMaxTimestamp = Long.MIN_VALUE;
    
    public LayeredRasterViewer() {
        super("Layered Raster and Contact Viewer");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create menu bar
        setupMenuBar();
        
        // Initialize the map
        map = new SlippyMap(new ArrayList<>());
        map.setPreferredSize(new Dimension(800, 600));
        
        // Create the sidebar with layer controls
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 600));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("Layers");
        titleLabel.setFont(titleLabel.getFont().deriveFont(18.0f));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Global toggles
        JPanel globalTogglesPanel = new JPanel();
        globalTogglesPanel.setLayout(new BoxLayout(globalTogglesPanel, BoxLayout.Y_AXIS));
        globalTogglesPanel.setBorder(BorderFactory.createTitledBorder("Display Options"));
        
        showRastersCheckbox = new JCheckBox("Show Rasters", true);
        showRastersCheckbox.addActionListener(e -> {
            boolean visible = showRastersCheckbox.isSelected();
            for (RasterLayer layer : rasterLayers) {
                if (visible) {
                    // Restore individual layer visibility state
                    layer.setVisible(true);
                } else {
                    layer.setVisible(false);
                }
            }
            map.repaint();
        });
        
        showContactsCheckbox = new JCheckBox("Show Contacts", true);
        showContactsCheckbox.addActionListener(e -> {
            if (filterableContactCollection != null) {
                filterableContactCollection.setVisible(showContactsCheckbox.isSelected());
                map.repaint();
            }
        });
        
        globalTogglesPanel.add(showRastersCheckbox);
        globalTogglesPanel.add(showContactsCheckbox);
        titlePanel.add(globalTogglesPanel);
        titlePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Time filter panel
        JPanel timeFilterPanel = new JPanel(new GridBagLayout());
        timeFilterPanel.setBorder(BorderFactory.createTitledBorder("Time Filter"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        timeFilterPanel.add(new JLabel("Start:"), gbc);
        
        gbc.gridx = 1;
        startDateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor startEditor = new JSpinner.DateEditor(startDateSpinner, "yyyy-MM-dd HH:mm");
        startDateSpinner.setEditor(startEditor);
        timeFilterPanel.add(startDateSpinner, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        timeFilterPanel.add(new JLabel("End:"), gbc);
        
        gbc.gridx = 1;
        endDateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor endEditor = new JSpinner.DateEditor(endDateSpinner, "yyyy-MM-dd HH:mm");
        endDateSpinner.setEditor(endEditor);
        timeFilterPanel.add(endDateSpinner, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JButton applyFilterButton = new JButton("Apply Filter");
        applyFilterButton.addActionListener(e -> applyTimeFilter());
        timeFilterPanel.add(applyFilterButton, gbc);
        
        gbc.gridy = 3;
        JButton clearFilterButton = new JButton("Clear Filter");
        clearFilterButton.addActionListener(e -> clearTimeFilter());
        timeFilterPanel.add(clearFilterButton, gbc);
        
        titlePanel.add(timeFilterPanel);
        titlePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Layer list panel
        layerPanel = new JPanel();
        layerPanel.setLayout(new BoxLayout(layerPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(layerPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        sidebar.add(titlePanel, BorderLayout.NORTH);
        sidebar.add(scrollPane, BorderLayout.CENTER);
        
        // Add components to frame
        add(map, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);
        
        pack();
        setLocationRelativeTo(null);
    }
    
    /**
     * Sets up the menu bar.
     */
    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem openFolderItem = new JMenuItem("Open Folder...");
        openFolderItem.addActionListener(e -> openFolder());
        fileMenu.add(openFolderItem);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }
    
    /**
     * Opens a folder chooser dialog.
     */
    private void openFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Folder with Raster Data");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            loadFromFolderAsync(selectedFolder);
        }
    }
    
    /**
     * Loads rasters and contacts from a folder in a background thread.
     */
    private void loadFromFolderAsync(File rootFolder) {
        // Disable UI during loading
        setEnabled(false);
        
        new Thread(() -> {
            try {
                loadFromFolder(rootFolder);
            } finally {
                SwingUtilities.invokeLater(() -> setEnabled(true));
            }
        }, "FolderLoader").start();
    }
    
    /**
     * Loads rasters and contacts from a folder recursively.
     * Each subfolder containing rasterIndex becomes a separate layer.
     */
    public void loadFromFolder(File rootFolder) {
        log.info("Loading data from folder: {}", rootFolder.getAbsolutePath());
        
        // Find all folders containing rasterIndex subdirectories (background thread)
        List<File> layerFolders = findLayerFolders(rootFolder);
        log.info("Found {} layer folders", layerFolders.size());
        
        LocationType firstCenter = null;
        List<RasterLayer> newLayers = new ArrayList<>();
        FilterableContactCollection newContactCollection = null;
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;
        
        // Load each layer (background thread)
        for (File layerFolder : layerFolders) {
            try {
                RasterLayer layer = new RasterLayer(layerFolder);
                if (!layer.getRasterPainters().isEmpty()) {
                    newLayers.add(layer);
                    
                    // Update timestamp range
                    minTimestamp = Math.min(minTimestamp, layer.getMinTimestamp());
                    maxTimestamp = Math.max(maxTimestamp, layer.getMaxTimestamp());
                    
                    // Get center from first raster if not set
                    if (firstCenter == null && !layer.getRasterPainters().isEmpty()) {
                        IndexedRasterPainter firstPainter = layer.getRasterPainters().get(0);
                        IndexedRaster raster = firstPainter.getRaster();
                        if (raster.getSamples() != null && !raster.getSamples().isEmpty()) {
                            firstCenter = new LocationType(
                                raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLatitude(),
                                raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLongitude()
                            );
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error loading layer from {}: {}", layerFolder.getAbsolutePath(), e.getMessage());
            }
        }
        
        // Load contacts from the entire folder tree (background thread)
        try {
            ContactCollection contactCollection = new ContactCollection(rootFolder);
            newContactCollection = new FilterableContactCollection(contactCollection);
            log.info("Loaded {} contacts", contactCollection.getAllContacts().size());
        } catch (IOException e) {
            log.error("Error loading contacts: {}", e.getMessage());
        }
        
        // Update UI on EDT
        final LocationType finalCenter = firstCenter;
        final FilterableContactCollection finalContactCollection = newContactCollection;
        final long finalMinTimestamp = minTimestamp;
        final long finalMaxTimestamp = maxTimestamp;
        
        SwingUtilities.invokeLater(() -> {
            // Clear existing layers
            rasterLayers.clear();
            layerControlPanels.clear();
            layerPanel.removeAll();
            
            // Clear existing painters from map
            // Add new layers to map and UI
            for (RasterLayer layer : newLayers) {
                rasterLayers.add(layer);
                map.addRasterPainter(layer);
                addLayerControl(layer);
            }
            
            // Add contacts
            if (finalContactCollection != null) {
                filterableContactCollection = finalContactCollection;
                map.addRasterPainter(filterableContactCollection);
            }
            
            // Update global timestamp range
            globalMinTimestamp = finalMinTimestamp;
            globalMaxTimestamp = finalMaxTimestamp;
            
            // Update time filter spinners with global range
            if (globalMinTimestamp != Long.MAX_VALUE) {
                startDateSpinner.setValue(new Date(globalMinTimestamp));
                endDateSpinner.setValue(new Date(globalMaxTimestamp));
            }
            
            // Focus map on first raster center
            if (finalCenter != null) {
                map.focus(finalCenter);
            }
            
            layerPanel.revalidate();
            layerPanel.repaint();
            map.repaint();
        });
    }
    
    /**
     * Finds all folders that should be treated as layers (contain rasterIndex subdirectory).
     */
    private List<File> findLayerFolders(File parentFolder) {
        List<File> folders = new ArrayList<>();
        findLayerFoldersRecursive(parentFolder, folders);
        return folders;
    }
    
    private void findLayerFoldersRecursive(File folder, List<File> result) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        
        // Check if this folder contains a rasterIndex subdirectory
        for (File file : files) {
            if (file.isDirectory() && file.getName().equals("rasterIndex")) {
                result.add(folder);
                return; // Don't recurse into this folder's children
            }
        }
        
        // Recurse into subdirectories
        for (File file : files) {
            if (file.isDirectory()) {
                findLayerFoldersRecursive(file, result);
            }
        }
    }
    
    /**
     * Adds a control checkbox for a layer to the sidebar.
     */
    private void addLayerControl(RasterLayer layer) {
        JPanel layerControlPanel = new JPanel(new BorderLayout());
        layerControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JCheckBox checkbox = new JCheckBox(layer.getName(), layer.isVisible());
        checkbox.addActionListener(e -> {
            layer.setVisible(checkbox.isSelected());
            map.repaint();
        });
        
        JLabel infoLabel = new JLabel(String.format("(%d rasters)", layer.getRasterPainters().size()));
        infoLabel.setForeground(Color.GRAY);
        
        layerControlPanel.add(checkbox, BorderLayout.WEST);
        layerControlPanel.add(infoLabel, BorderLayout.EAST);
        
        layerPanel.add(layerControlPanel);
        layerPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        
        // Store reference to control panel
        layerControlPanels.add(layerControlPanel);
    }
    
    /**
     * Applies the time filter to all layers.
     */
    private void applyTimeFilter() {
        Date startDate = (Date) startDateSpinner.getValue();
        Date endDate = (Date) endDateSpinner.getValue();
        
        long startTimestamp = startDate.getTime();
        long endTimestamp = endDate.getTime();
        
        for (int i = 0; i < rasterLayers.size(); i++) {
            RasterLayer layer = rasterLayers.get(i);
            layer.setStartTimestampFilter(startTimestamp);
            layer.setEndTimestampFilter(endTimestamp);
            
            // Check if layer has any visible rasters in the time range
            boolean hasVisibleRasters = false;
            for (IndexedRasterPainter painter : layer.getRasterPainters()) {
                long rasterTimestamp = painter.getStartTimestamp();
                if (rasterTimestamp >= startTimestamp && rasterTimestamp <= endTimestamp) {
                    hasVisibleRasters = true;
                    break;
                }
            }
            
            // Hide/show the layer control panel based on whether it has visible rasters
            if (i < layerControlPanels.size()) {
                layerControlPanels.get(i).setVisible(hasVisibleRasters);
            }
        }
        
        // Filter contacts as well
        if (filterableContactCollection != null) {
            filterableContactCollection.setStartTimestampFilter(startTimestamp);
            filterableContactCollection.setEndTimestampFilter(endTimestamp);
        }
        
        layerPanel.revalidate();
        layerPanel.repaint();
        map.repaint();
        log.info("Applied time filter: {} to {}", startDate, endDate);
    }
    
    /**
     * Clears the time filter from all layers.
     */
    private void clearTimeFilter() {
        for (RasterLayer layer : rasterLayers) {
            layer.setStartTimestampFilter(null);
            layer.setEndTimestampFilter(null);
        }
        
        // Clear contact filter
        if (filterableContactCollection != null) {
            filterableContactCollection.setStartTimestampFilter(null);
            filterableContactCollection.setEndTimestampFilter(null);
        }
        
        // Show all layer control panels
        for (JPanel panel : layerControlPanels) {
            panel.setVisible(true);
        }
        
        // Reset spinners to global range
        if (globalMinTimestamp != Long.MAX_VALUE) {
            startDateSpinner.setValue(new Date(globalMinTimestamp));
            endDateSpinner.setValue(new Date(globalMaxTimestamp));
        }
        
        layerPanel.revalidate();
        layerPanel.repaint();
        map.repaint();
        log.info("Cleared time filter");
    }
    
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            LayeredRasterViewer viewer = new LayeredRasterViewer();
            viewer.setVisible(true);
            
            // Example: Load from a folder
            // Replace with actual folder path or use File > Open Folder menu
            if (args.length > 0) {
                File folder = new File(args[0]);
                if (folder.exists() && folder.isDirectory()) {
                    viewer.loadFromFolder(folder);
                } else {
                    log.error("Invalid folder: {}", args[0]);
                }
            } else {
                log.info("Usage: LayeredRasterViewer <folder-path>");
                log.info("Or use File > Open Folder menu to select a folder.");
            }
        });
    }
}
