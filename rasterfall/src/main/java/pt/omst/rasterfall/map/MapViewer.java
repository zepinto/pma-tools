//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.map;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.time.Instant;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.contacts.browser.ContactsMapOverlay;
import pt.omst.contacts.browser.filtering.ContactFilterListener;
import pt.omst.contacts.browser.filtering.ContactFilterPanel;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.datasource.DataSourceEvent;
import pt.omst.gui.datasource.DataSourceListener;
import pt.omst.contacts.browser.editor.VerticalContactEditor;
import pt.omst.contacts.reports.GenerateReportDialog;
import pt.omst.gui.datasource.RasterfallDataSource;
import pt.omst.gui.jobs.TaskStatusIndicator;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterfall.overlays.InteractionListenerOverlay.RasterfallListener;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.CompositeContactCollection;
import pt.omst.rasterlib.contacts.ContactCollection;
import pt.omst.rasterlib.contacts.ContactsSelection;

/**
 * Simplified contact viewer for RasterFall integration.
 * Shows contacts on a map with editing capabilities.
 * Data source is controlled by the parent RasterFall application.
 * No time filtering, no filter panel, no data source management.
 */
@Slf4j
@Getter
public class MapViewer extends JPanel implements AutoCloseable, RasterfallListener, DataSourceListener {

    private final SlippyMap slippyMap;
    private final VerticalContactEditor contactEditor;
    private final CompositeContactCollection compositeCollection;
    private ContactCollection rasterfallCollection;
    private ContactsSelection currentSelection;
    private final ContactsMapOverlay contactsMapOverlay;
    private final DataSourceManagerPanel dataSourceManager;
    private final ContactFilterPanel filterPanel;
    private final InteractionMapOverlay interactionMapOverlay = new InteractionMapOverlay();
    private final PathMapOverlay pathMapOverlay;
    private final JSplitPane mainSplitPane;
    private final JSplitPane westSplitPane;
    private final JPanel eastPanel;
    private final JPanel westPanel;
    private final JButton toggleEastPanelButton;
    private final JButton toggleWestPanelButton;
    private boolean eastPanelVisible = true;
    private boolean westPanelVisible = false; // Collapsed by default
    private final JPanel statusBar;
    private final JLabel totalContactsLabel;
    private final JLabel visibleContactsLabel;
    private final TaskStatusIndicator taskStatusIndicator;
    private final Preferences prefs;

    /**
     * Creates a new SimpleContactViewer with the given contact collection.
     * 
     * @param contactCollection The contact collection to display
     */
    public MapViewer(ContactCollection contactCollection) {
        // Create composite collection and add the initial collection
        this.compositeCollection = new CompositeContactCollection();
        this.rasterfallCollection = contactCollection;
        if (contactCollection != null) {
            this.compositeCollection.addCollection(contactCollection);
        }
        
        setLayout(new BorderLayout(5, 0));
        
        prefs = Preferences.userNodeForPackage(MapViewer.class);

        // Initialize components
        dataSourceManager = new DataSourceManagerPanel();
        dataSourceManager.addDataSourceListener(this);
        
        slippyMap = new SlippyMap();
        contactsMapOverlay = new ContactsMapOverlay(compositeCollection);
        slippyMap.addMapOverlay(interactionMapOverlay);
        pathMapOverlay = new PathMapOverlay();
        slippyMap.addMapOverlay(pathMapOverlay);        
        slippyMap.addMapOverlay(contactsMapOverlay);
        
        contactsMapOverlay.setContactSelectionListener(contact -> {
            log.info("Contact selected: {}", contact.getContact().getLabel());
            setContact(contact);
        });

        contactEditor = new VerticalContactEditor();

        // Listen for contact saves to refresh the map overlay
        contactEditor.addSaveListener((contactId, zctFile) -> {
            log.info("Contact saved: {}, refreshing map overlay", contactId);
            try {
                compositeCollection.refreshContact(zctFile);
            } catch (Exception e) {
                log.error("Failed to refresh contact {} in collection after save", zctFile.getName(), e);
            }
            contactsMapOverlay.refreshContact(zctFile);
            slippyMap.repaint();
            updateStatusBar();
        });

        // Initialize status bar labels
        totalContactsLabel = new JLabel("Total: 0");
        totalContactsLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        totalContactsLabel.setPreferredSize(new Dimension(100, 24));
        visibleContactsLabel = new JLabel("Visible: 0");
        visibleContactsLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        visibleContactsLabel.setPreferredSize(new Dimension(100, 24));
        taskStatusIndicator = new TaskStatusIndicator(null);
        taskStatusIndicator.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Create status bar
        statusBar = createStatusBar();

        // Create top panel with data source manager
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(dataSourceManager, BorderLayout.CENTER);

        // Create filter panel (west panel)
        filterPanel = new ContactFilterPanel();
        filterPanel.setContactCollection(compositeCollection);
        filterPanel.addFilterListener(new ContactFilterListener() {
            @Override
            public void onFilterChanged() {
                applyFilters();
            }

            @Override
            public void onContactSelected(CompressedContact contact) {
                setContact(contact);
                slippyMap.repaint();
            }
        });

        // Create west panel (filter panel)
        westPanel = new JPanel(new BorderLayout());
        westPanel.add(new JScrollPane(filterPanel), BorderLayout.CENTER);
        westPanel.setPreferredSize(new Dimension(280, 600));
        westPanel.setMinimumSize(new Dimension(200, 400));

        // Create east panel (contact editor)
        eastPanel = createEastPanel();
        eastPanel.setPreferredSize(new Dimension(400, 600));
        eastPanel.setMinimumSize(new Dimension(300, 400));

        // Create center panel with map and toggle buttons
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(slippyMap, BorderLayout.CENTER);

        // Create toggle button for west panel (filter panel)
        toggleWestPanelButton = new JButton("◂");
        toggleWestPanelButton.setFocusable(false);
        toggleWestPanelButton.setPreferredSize(new Dimension(12, 12));
        toggleWestPanelButton.setMinimumSize(new Dimension(12, 12));
        toggleWestPanelButton.setMaximumSize(new Dimension(12, 12));
        toggleWestPanelButton.setMargin(new Insets(0, 0, 0, 0));
        toggleWestPanelButton.setBorderPainted(false);
        toggleWestPanelButton.setContentAreaFilled(false);
        toggleWestPanelButton.setOpaque(false);
        toggleWestPanelButton.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        toggleWestPanelButton.addActionListener(e -> {
            if (westPanelVisible) {
                hideFilterPanel();
            } else {
                showFilterPanel();
            }
        });

        JPanel toggleWestButtonPanel = new JPanel(new BorderLayout());
        toggleWestButtonPanel.add(toggleWestPanelButton, BorderLayout.NORTH);
        centerPanel.add(toggleWestButtonPanel, BorderLayout.WEST);

        // Create toggle button for east panel (contact editor)
        toggleEastPanelButton = new JButton("▸");
        toggleEastPanelButton.setFocusable(false);
        toggleEastPanelButton.setPreferredSize(new Dimension(12, 12));
        toggleEastPanelButton.setMinimumSize(new Dimension(12, 12));
        toggleEastPanelButton.setMaximumSize(new Dimension(12, 12));
        toggleEastPanelButton.setMargin(new Insets(0, 0, 0, 0));
        toggleEastPanelButton.setBorderPainted(false);
        toggleEastPanelButton.setContentAreaFilled(false);
        toggleEastPanelButton.setOpaque(false);
        toggleEastPanelButton.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        toggleEastPanelButton.addActionListener(e -> {
            if (eastPanelVisible) {
                hideContactDetails();
            } else {
                showContactDetails();
            }
        });

        JPanel toggleEastButtonPanel = new JPanel(new BorderLayout());
        toggleEastButtonPanel.add(toggleEastPanelButton, BorderLayout.NORTH);
        centerPanel.add(toggleEastButtonPanel, BorderLayout.EAST);

        // Create west split pane (horizontal split between west and center)
        westSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        westSplitPane.setLeftComponent(null); // Start collapsed
        westSplitPane.setRightComponent(centerPanel);
        westSplitPane.setResizeWeight(0.0);
        westSplitPane.setOneTouchExpandable(false);
        westSplitPane.setContinuousLayout(true);

        // Create main split pane (horizontal split between west split and east)
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(westSplitPane);
        mainSplitPane.setRightComponent(eastPanel);
        mainSplitPane.setResizeWeight(0.7);
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setContinuousLayout(true);

        // Assemble main layout
        add(topPanel, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // Show all contacts first (before loading preferences)
        showAllContacts();

        // Load preferences
        boolean hadPreferences = loadPreferences();

        // If no saved preferences and we have contacts, center map on them
        if (!hadPreferences && !compositeCollection.getAllContacts().isEmpty()) {
            centerMapOnContacts();
        }

        // Initial update - show all contacts (before adding listener)
        showAllContacts();
        updateStatusBar();

        // Listen for map bounds changes (add listener AFTER initial setup)
        slippyMap.addPropertyChangeListener("visibleBounds", evt -> {
            updateVisibleContacts();
            updateStatusBar();
        });
        
        // Enable contact grouping in the overlay (without Pulvis syncing)
        ContactGrouper grouper = new ContactGrouper();
        grouper.setOnGroupingComplete(() -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    // Reload contacts from disk after grouping (files have been deleted/merged)
                    reloadContacts();
                    log.info("Contact grouping display refreshed");
                } catch (Exception e) {
                    log.error("Failed to refresh display after grouping", e);
                }
            });
        });
        contactsMapOverlay.setGroupingHandler(grouper);

        compositeCollection.addChangeListener(() -> {
            SwingUtilities.invokeLater(() -> {
                updateStatusBar();
                contactsMapOverlay.refresh();
                slippyMap.repaint();
            });
        });
    }

    public void setRasterfallDataSource(RasterfallDataSource rds) {
        log.info("Setting Rasterfall data source: {}", rds.getDisplayName());
        dataSourceManager.addRasterfallSource(rds);
    }

    public void loadWaterfall(RasterfallTiles waterfall) {
        pathMapOverlay.setWaterfall(waterfall);
        
        // Remove old rasterfall collection if present
        if (rasterfallCollection != null) {
            compositeCollection.removeCollection(rasterfallCollection);
        }
        
        // Add new rasterfall collection
        this.rasterfallCollection = waterfall.getContacts();
        if (rasterfallCollection != null) {
            compositeCollection.addCollection(rasterfallCollection);
        }
        
        refreshContacts();
    }

    public void refreshContacts() {
        contactsMapOverlay.setContactCollection(compositeCollection);
        showAllContacts(); // Reapply filters with new collection
        // Update filter panel with composite collection
        filterPanel.setContactCollection(compositeCollection);
        updateStatusBar();
        repaint();
    }
    
    /**
     * Reloads contacts from disk (called after grouping operations that modify files).
     */
    private void reloadContacts() {
        if (rasterfallCollection != null && rasterfallCollection.getAllContacts() != null && !rasterfallCollection.getAllContacts().isEmpty()) {
            // Get contacts folder from first contact's parent directory
            File firstContactFile = rasterfallCollection.getAllContacts().get(0).getZctFile();
            File contactsFolder = firstContactFile.getParentFile();
            
            log.info("Reloading contacts from folder: {}", contactsFolder);
            
            // Remove old rasterfall collection
            compositeCollection.removeCollection(rasterfallCollection);
            
            // Create and add new rasterfall collection
            rasterfallCollection = pt.omst.rasterlib.contacts.ContactCollection.fromFolder(contactsFolder);
            compositeCollection.addCollection(rasterfallCollection);
            
            refreshContacts();
            refresh();
        }
    }

    public void showContactDetails() {
        eastPanelVisible = true;
        mainSplitPane.setRightComponent(eastPanel);
        mainSplitPane.setDividerLocation(0.7);
        toggleEastPanelButton.setText("▸");
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    public void hideContactDetails() {
        eastPanelVisible = false;
        mainSplitPane.setRightComponent(null);
        toggleEastPanelButton.setText("◂");
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    public void showFilterPanel() {
        westPanelVisible = true;
        westSplitPane.setLeftComponent(westPanel);
        westSplitPane.setDividerLocation(280);
        toggleWestPanelButton.setText("◂");
        westSplitPane.revalidate();
        westSplitPane.repaint();
    }

    public void hideFilterPanel() {
        westPanelVisible = false;
        westSplitPane.setLeftComponent(null);
        toggleWestPanelButton.setText("▸");
        westSplitPane.revalidate();
        westSplitPane.repaint();
    }

    /**
     * Applies the current filter selections from the filter panel.
     */
    private void applyFilters() {
        var classifications = filterPanel.getSelectedClassifications();
        var confidences = filterPanel.getSelectedConfidences();
        var labels = filterPanel.getSelectedLabels();

        // Create a new selection with the specified filters
        currentSelection = compositeCollection.select(
            null, // region
            null, // start time
            null, // end time
            classifications.isEmpty() ? null : classifications,
            confidences.isEmpty() ? null : confidences,
            labels.isEmpty() ? null : labels
        );

        // Update the filter panel's contact list with filtered results
        filterPanel.setContacts(currentSelection.getContacts());
        
        // Update map display
        contactsMapOverlay.refresh();
        slippyMap.repaint();
        updateStatusBar();
    }

    public void savePreferences() {
        try {
            prefs.putInt("splitPaneDividerLocation", mainSplitPane.getDividerLocation());
            prefs.putBoolean("eastPanelVisible", eastPanelVisible);
            double[] bounds = slippyMap.getVisibleBounds();
            // bounds = [minLat, minLon, maxLat, maxLon]
            double centerLat = (bounds[0] + bounds[2]) / 2.0;
            double centerLon = (bounds[1] + bounds[3]) / 2.0;
            prefs.putDouble("mapCenterLat", centerLat);
            prefs.putDouble("mapCenterLon", centerLon);
            prefs.putInt("mapZoom", slippyMap.getLevelOfDetail());
            log.debug("Saved preferences");
        } catch (Exception e) {
            log.warn("Failed to save preferences", e);
        }
    }

    public boolean loadPreferences() {
        try {
            int dividerLocation = prefs.getInt("splitPaneDividerLocation", -1);
            if (dividerLocation > 0) {
                mainSplitPane.setDividerLocation(dividerLocation);
            }
            boolean wasVisible = prefs.getBoolean("eastPanelVisible", true);
            if (wasVisible) {
                showContactDetails();
            } else {
                hideContactDetails();
            }
            double lat = prefs.getDouble("mapCenterLat", Double.NaN);
            double lon = prefs.getDouble("mapCenterLon", Double.NaN);
            int zoom = prefs.getInt("mapZoom", -1);
            if (!Double.isNaN(lat) && !Double.isNaN(lon) && zoom > 0) {
                slippyMap.focus(lat, lon, zoom);
                log.debug("Loaded preferences");
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to load preferences", e);
            return false;
        }
    }

    private JPanel createEastPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(createContactEditorSection(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createContactEditorSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JScrollPane scrollPane = new JScrollPane(contactEditor);
        contactEditor.setPreferredSize(new Dimension(240, 240));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(2, 2, 0, 2));
        panel.setPreferredSize(new Dimension(100, 26));
        JPanel countsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        countsPanel.add(totalContactsLabel);
        countsPanel.add(visibleContactsLabel);
        JPanel jobPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        jobPanel.add(taskStatusIndicator);
        panel.add(countsPanel, BorderLayout.WEST);
        panel.add(jobPanel, BorderLayout.EAST);
        return panel;
    }

    private void updateStatusBar() {
        SwingUtilities.invokeLater(() -> {
            int totalContacts = compositeCollection.getAllContacts().size();
            int visibleContacts = currentSelection != null ? currentSelection.size() : totalContacts;
            totalContactsLabel.setText("Total: " + totalContacts);
            visibleContactsLabel.setText("Visible: " + visibleContacts);
        });
    }

    public void setContact(CompressedContact contact) {
        try {
            contactEditor.loadZct(contact.getZctFile());
        } catch (Exception e) {
            log.error("Error loading contact in editor", e);
        }
    }

    private void updateVisibleContacts() {
        // Don't apply spatial filtering - show all contacts
        // Spatial filtering disabled because contacts may not have valid coordinates
        showAllContacts();
    }

    /**
     * Shows all contacts without spatial filtering.
     */
    private void showAllContacts() {
        compositeCollection.applyFilters(null, null, null, null, null, null);
        slippyMap.repaint();
    }

    /**
     * Centers the map on all contacts.
     */
    private void centerMapOnContacts() {
        var allContacts = compositeCollection.getAllContacts();
        if (allContacts.isEmpty()) {
            return;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (var contact : allContacts) {
            double lat = contact.getContact().getLatitude();
            double lon = contact.getContact().getLongitude();
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        double centerLat = (minLat + maxLat) / 2.0;
        double centerLon = (minLon + maxLon) / 2.0;
        
        // Use zoom level 15 by default, or zoom out if needed to fit all contacts
        int zoom = 15;
        slippyMap.focus(centerLat, centerLon, zoom);
    }

    /**
     * Refreshes the contact display and status bar.
     * Call this when the underlying contact collection has been updated.
     */
    public void refresh() {
        updateVisibleContacts();
        updateStatusBar();
        slippyMap.repaint();
    }

    public static JMenuBar createMenuBar(JFrame frame, MapViewer viewer) {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu preferencesMenu = new JMenu("Preferences");
        if (viewer != null && viewer.slippyMap != null) {
            JMenu baseMapMenu = viewer.slippyMap.getBaseMapManager().createBaseMapMenu();
            preferencesMenu.add(baseMapMenu);
            preferencesMenu.addSeparator();
        }
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.setSelected(GuiUtils.isDarkTheme());
        darkModeItem.addActionListener(e -> {
            boolean isDark = darkModeItem.isSelected();
            GuiUtils.setTheme(isDark ? "dark" : "light");
            if (viewer != null && viewer.slippyMap != null) {
                viewer.slippyMap.setDarkMode(isDark);
            }
        });
        preferencesMenu.add(darkModeItem);
        fileMenu.add(preferencesMenu);
        fileMenu.addSeparator();
        JMenuItem generateReportItem = new JMenuItem("Generate Report...");
        generateReportItem.addActionListener(e -> {
            if (viewer != null && viewer.compositeCollection != null) {
                GenerateReportDialog dialog = new GenerateReportDialog(frame, viewer.compositeCollection);
                dialog.setVisible(true);
            } else {
                GuiUtils.errorMessage(frame, "Error", "No contact collection available.");
            }
        });
        fileMenu.add(generateReportItem);
        fileMenu.addSeparator();
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> frame.dispose());
        fileMenu.add(closeItem);
        menuBar.add(fileMenu);
        return menuBar;
    }

    @Override
    public void close() throws Exception {
        if (slippyMap != null) {
            slippyMap.close();
        }
        log.info("Simple Contact Viewer closed");
    }

    @Override
    public void onMouseMoved(double latitude, double longitude, Instant timestamp) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            interactionMapOverlay.setMouseLocation(null);
        } else {
            log.info("Mouse moved to lat: {}, lon: {}, time: {}", latitude, longitude, timestamp);
            interactionMapOverlay.setMouseLocation(new LocationType(latitude, longitude));
        }
        repaint();
    }

    @Override
    public void onVisibleBoundsChanged(LocationType[] boundaryPoints, Instant startTime,
            Instant endTime) {

        if (boundaryPoints == null) {
            log.info("Visible bounds cleared");
            interactionMapOverlay.clear();
        } else {
            log.info("Visible bounds changed: {} points, startTime={}, endTime={}",
                    boundaryPoints.length, startTime, endTime);
            interactionMapOverlay.setVisibleBounds(boundaryPoints);
        }

        repaint();
    }

    // Map to track folder data sources and their collections
    private final java.util.Map<File, ContactCollection> folderCollections = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void sourceAdded(DataSourceEvent event) {
        log.info("Data source added: {}", event.getDataSource().getDisplayName());
        
        // Handle folder data sources - create a new collection for the folder
        if (event.getDataSource() instanceof pt.omst.gui.datasource.FolderDataSource) {
            pt.omst.gui.datasource.FolderDataSource folderSource = 
                (pt.omst.gui.datasource.FolderDataSource) event.getDataSource();
            File folder = folderSource.getFolder();
            
            log.info("Creating collection for folder: {}", folder.getAbsolutePath());
            ContactCollection folderCollection = ContactCollection.fromFolder(folder);
            folderCollections.put(folder, folderCollection);
            compositeCollection.addCollection(folderCollection);
            
            // Refresh displays
            SwingUtilities.invokeLater(() -> {
                contactsMapOverlay.refresh();
                slippyMap.repaint();
                updateStatusBar();
            });
        }
    }

    @Override
    public void sourceRemoved(DataSourceEvent event) {
        log.info("Data source removed: {}", event.getDataSource().getDisplayName());
        
        // Handle folder data sources - remove the collection for the folder
        if (event.getDataSource() instanceof pt.omst.gui.datasource.FolderDataSource) {
            pt.omst.gui.datasource.FolderDataSource folderSource = 
                (pt.omst.gui.datasource.FolderDataSource) event.getDataSource();
            File folder = folderSource.getFolder();
            
            log.info("Removing collection for folder: {}", folder.getAbsolutePath());
            ContactCollection folderCollection = folderCollections.remove(folder);
            if (folderCollection != null) {
                compositeCollection.removeCollection(folderCollection);
            }
            
            // Refresh displays
            SwingUtilities.invokeLater(() -> {
                contactsMapOverlay.refresh();
                slippyMap.repaint();
                updateStatusBar();
            });
        }
    }
}
