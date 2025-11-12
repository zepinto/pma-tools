//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.LoadingPanel;
import pt.omst.gui.ZoomableTimeIntervalSelector;
import pt.omst.gui.datasource.DataSource;
import pt.omst.gui.datasource.DataSourceEvent;
import pt.omst.gui.datasource.DataSourceListener;
import pt.omst.gui.datasource.FolderDataSource;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;
import pt.omst.rasterlib.mapview.IndexedRasterPainter;

/**
 * Map Viewer Application that combines SlippyMap with data source management,
 * time interval selection, and displays indexed rasters and contacts.
 */
@Slf4j
public class MapViewerApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private SlippyMap map;
    private DataSourceManagerPanel dataSourcePanel;
    private ZoomableTimeIntervalSelector timeSelector;
    private List<MapPainter> allPainters = new ArrayList<>();
    private List<ContactCollection> allContacts = new ArrayList<>();
    private Instant minTime = Instant.now().minus(Duration.ofDays(365));
    private Instant maxTime = Instant.now();

    public MapViewerApp() {
        setTitle("Map Viewer - Rasters & Contacts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initializeComponents();
        setupMenubar();
    }

    private void initializeComponents() {
        // Create the map with empty marker list
        map = new SlippyMap(new ArrayList<>());

        // Create time selector
        timeSelector = new ZoomableTimeIntervalSelector(minTime, maxTime);
        timeSelector.addPropertyChangeListener("selection", evt -> {
            Instant[] selection = (Instant[]) evt.getNewValue();
            log.info("Time selection changed: {} to {}", selection[0], selection[1]);
            updateDisplayedData(selection[0], selection[1]);
        });

        // Create data source panel
        dataSourcePanel = new DataSourceManagerPanel();
        dataSourcePanel.addDataSourceListener(new DataSourceListener() {
            @Override
            public void sourceAdded(DataSourceEvent event) {
                DataSource source = event.getDataSource();
                log.info("Data source added: {}", source.getDisplayName());
                loadDataFromSource(source);
            }

            @Override
            public void sourceRemoved(DataSourceEvent event) {
                DataSource source = event.getDataSource();
                log.info("Data source removed: {}", source.getDisplayName());
                // For simplicity, reload all data
                reloadAllData();
            }
        });

        // Create bottom panel with data source manager and time selector
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.add(dataSourcePanel, BorderLayout.CENTER);
        bottomPanel.add(timeSelector, BorderLayout.SOUTH);

        // Add components to frame
        add(map, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupMenubar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        // Exit menu item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        // Software License menu item
        JMenuItem licenseItem = new JMenuItem("Software License");
        licenseItem.addActionListener(e -> showLicenseDialog());

        helpMenu.add(licenseItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void showLicenseDialog() {
        JFrame licenseFrame = new JFrame("Software License");
        LicensePanel panel = new LicensePanel();
        licenseFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        try {
            panel.setLicense(LicenseChecker.getMainLicense(), LicenseChecker.getLicenseActivation());
        } catch (Exception e) {
            log.error("Error loading license", e);
        }
        licenseFrame.setContentPane(panel);
        licenseFrame.setSize(600, 400);
        licenseFrame.setLocationRelativeTo(this);
        licenseFrame.setVisible(true);
    }

    private void loadDataFromSource(DataSource source) {
        if (source instanceof FolderDataSource) {
            FolderDataSource folderSource = (FolderDataSource) source;
            File folder = folderSource.getFolder();
            log.info("Loading data from folder: {}", folder.getAbsolutePath());

            // Load raster data
            loadRastersFromFolder(folder);

            // Load contact data
            loadContactsFromFolder(folder);

            // Update time bounds
            updateTimeBounds();

            // Update display with current time selection
            Instant[] selection = new Instant[]{timeSelector.getSelectedStartTime(), timeSelector.getSelectedEndTime()};
            updateDisplayedData(selection[0], selection[1]);
        }
    }

    private void loadRastersFromFolder(File folder) {
        List<File> jsonFiles = findJsonFiles(folder);
        log.info("Found {} JSON files in {}", jsonFiles.size(), folder.getAbsolutePath());

        for (File jsonFile : jsonFiles) {
            try {
                String jsonContent = Files.readString(jsonFile.toPath());
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(jsonContent);

                // Create painter for this raster
                IndexedRasterPainter painter = new IndexedRasterPainter(jsonFile.getParentFile(), raster);
                allPainters.add(painter);

                log.info("Loaded raster: {}", jsonFile.getName());
            } catch (Exception e) {
                log.warn("Failed to load raster from {}: {}", jsonFile.getName(), e.getMessage());
            }
        }
    }

    private void loadContactsFromFolder(File folder) {
        try {
            ContactCollection collection = new ContactCollection(folder);
            if (collection.getAllContacts().size() > 0) {
                allContacts.add(collection);
                log.info("Loaded {} contacts from {}", collection.getAllContacts().size(), folder.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Failed to load contacts from {}: {}", folder.getAbsolutePath(), e.getMessage());
        }
    }

    private List<File> findJsonFiles(File folder) {
        List<File> jsonFiles = new ArrayList<>();
        if (!folder.exists() || !folder.isDirectory()) {
            return jsonFiles;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return jsonFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                jsonFiles.addAll(findJsonFiles(file));
            } else if (file.getName().endsWith(".json") && !file.getName().equals("package.json")) {
                // Check if this is a raster index file
                jsonFiles.add(file);
            }
        }

        return jsonFiles;
    }

    private void updateTimeBounds() {
        Instant min = Instant.now();
        Instant max = Instant.EPOCH;

        // Check raster times
        for (MapPainter painter : allPainters) {
            if (painter instanceof IndexedRasterPainter) {
                IndexedRasterPainter rasterPainter = (IndexedRasterPainter) painter;
                try {
                    long startTime = rasterPainter.getStartTimestamp();
                    long endTime = rasterPainter.getEndTimestamp();
                    Instant start = Instant.ofEpochMilli(startTime);
                    Instant end = Instant.ofEpochMilli(endTime);

                    if (start.isBefore(min)) min = start;
                    if (end.isAfter(max)) max = end;
                } catch (Exception e) {
                    // Ignore if painter doesn't have timestamp methods
                }
            }
        }

        // Check contact times
        for (ContactCollection collection : allContacts) {
            List<CompressedContact> contacts = collection.getAllContacts();
            for (CompressedContact contact : contacts) {
                Instant contactTime = Instant.ofEpochMilli(contact.getTimestamp());
                if (contactTime.isBefore(min)) min = contactTime;
                if (contactTime.isAfter(max)) max = contactTime;
            }
        }

        // Update time selector bounds if we found valid times
        if (max.isAfter(Instant.EPOCH) && min.isBefore(Instant.now())) {
            // Add some padding
            minTime = min.minus(Duration.ofHours(1));
            maxTime = max.plus(Duration.ofHours(1));
            timeSelector.setAbsoluteMinTime(minTime);
            timeSelector.setAbsoluteMaxTime(maxTime);
            timeSelector.setSelectedInterval(minTime, maxTime);
            log.info("Updated time bounds: {} to {}", minTime, maxTime);
        }
    }

    private void updateDisplayedData(Instant startTime, Instant endTime) {
        // Clear all painters from map
        map.close();

        // Re-initialize map to clear painters
        // Since we can't easily remove painters, we'll need to filter during paint
        // For now, add all painters back (could be optimized with time filtering)
        for (MapPainter painter : allPainters) {
            if (painter instanceof IndexedRasterPainter) {
                IndexedRasterPainter rasterPainter = (IndexedRasterPainter) painter;
                try {
                    long painterStart = rasterPainter.getStartTimestamp();
                    long painterEnd = rasterPainter.getEndTimestamp();

                    // Check if raster overlaps with selected time range
                    if (painterEnd >= startTime.toEpochMilli() && painterStart <= endTime.toEpochMilli()) {
                        map.addRasterPainter(rasterPainter);
                    }
                } catch (Exception e) {
                    // If no timestamp info, show it anyway
                    map.addRasterPainter(rasterPainter);
                }
            } else {
                map.addRasterPainter(painter);
            }
        }

        // Add contacts within time range
        for (ContactCollection collection : allContacts) {
            List<CompressedContact> filteredContacts = collection.contactsBetween(
                    startTime.toEpochMilli(), endTime.toEpochMilli());
            if (!filteredContacts.isEmpty()) {
                // Create a filtered collection painter
                // Note: ContactCollection implements MapPainter
                map.addRasterPainter(collection);
            }
        }

        map.repaint();
        log.info("Updated display with {} painters for time range {} to {}",
                allPainters.size(), startTime, endTime);
    }

    private void reloadAllData() {
        // Clear all loaded data
        allPainters.clear();
        allContacts.clear();
        map.close();

        // Reload from all current data sources
        for (DataSource source : dataSourcePanel.getDataSources()) {
            loadDataFromSource(source);
        }
    }

    public static void main(String[] args) {
        // Check license
        try {
            LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
        } catch (Exception e) {
            log.error("License check failed", e);
            System.exit(1);
        }

        GuiUtils.setLookAndFeel();

        // Show loading splash screen
        JWindow splash = LoadingPanel.showSplashScreen("Loading Map Viewer...");

        SwingUtilities.invokeLater(() -> {
            try {
                MapViewerApp app = new MapViewerApp();
                app.setVisible(true);

                // Close splash screen
                if (splash != null) {
                    splash.dispose();
                }

                log.info("Map Viewer application started");
            } catch (Exception e) {
                log.error("Error starting application", e);
                if (splash != null) {
                    splash.dispose();
                }
                System.exit(1);
            }
        });
    }
}
