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
import pt.omst.rasterlib.contacts.ContactCollection;

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
    private ContactCollection contactCollection;
    private final ContactsMapOverlay contactsMapOverlay;
    private final DataSourceManagerPanel dataSourceManager;
    private final InteractionMapOverlay interactionMapOverlay = new InteractionMapOverlay();
    private final PathMapOverlay pathMapOverlay;
    private final JSplitPane mainSplitPane;
    private final JPanel eastPanel;
    private final JButton toggleEastPanelButton;
    private boolean eastPanelVisible = true;
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
        this.contactCollection = contactCollection;
        setLayout(new BorderLayout(5, 0));
        
        prefs = Preferences.userNodeForPackage(MapViewer.class);

        // Initialize components
        dataSourceManager = new DataSourceManagerPanel();
        dataSourceManager.addDataSourceListener(this);
        
        slippyMap = new SlippyMap();
        contactsMapOverlay = new ContactsMapOverlay(contactCollection);
        pathMapOverlay = new PathMapOverlay();
        slippyMap.addMapOverlay(pathMapOverlay);
        slippyMap.addMapOverlay(interactionMapOverlay);
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
                contactCollection.refreshContact(zctFile);
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

        // Create east panel (contact editor)
        eastPanel = createEastPanel();
        eastPanel.setPreferredSize(new Dimension(400, 600));
        eastPanel.setMinimumSize(new Dimension(300, 400));

        // Create center panel with map and toggle button
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(slippyMap, BorderLayout.CENTER);

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

        // Create main split pane (horizontal split between center and east)
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(centerPanel);
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
        if (!hadPreferences && !contactCollection.getAllContacts().isEmpty()) {
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

        contactCollection.addChangeListener(() -> {
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
        this.contactCollection = waterfall.getContacts();
        refreshContacts();
    }

    public void refreshContacts() {
        contactsMapOverlay.setContactCollection(contactCollection);
        showAllContacts(); // Reapply filters with new collection
        updateStatusBar();
        repaint();
    }
    
    /**
     * Reloads contacts from disk (called after grouping operations that modify files).
     */
    private void reloadContacts() {
        if (contactCollection != null && contactCollection.getAllContacts() != null && !contactCollection.getAllContacts().isEmpty()) {
            // Get contacts folder from first contact's parent directory
            File firstContactFile = contactCollection.getAllContacts().get(0).getZctFile();
            File contactsFolder = firstContactFile.getParentFile();
            
            log.info("Reloading contacts from folder: {}", contactsFolder);
            contactCollection = pt.omst.rasterlib.contacts.ContactCollection.fromFolder(contactsFolder);
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
            int totalContacts = contactCollection.getAllContacts().size();
//            int visibleContacts = contactCollection.getFilteredContacts().size();
            totalContactsLabel.setText("Total: " + totalContacts);
//            visibleContactsLabel.setText("Visible: " + visibleContacts);
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
        contactCollection.applyFilters(null, null, null, null, null, null);
        slippyMap.repaint();
    }

    /**
     * Centers the map on all contacts.
     */
    private void centerMapOnContacts() {
        var allContacts = contactCollection.getAllContacts();
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
            if (viewer != null && viewer.contactCollection != null) {
                GenerateReportDialog dialog = new GenerateReportDialog(frame, viewer.contactCollection);
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
        log.info("Mouse moved to lat: {}, lon: {}, time: {}", latitude, longitude, timestamp);
        interactionMapOverlay.setMouseLocation(new LocationType(latitude, longitude));
        repaint();
    }

    @Override
    public void onVisibleBoundsChanged(LocationType loc1, LocationType loc2, LocationType loc3, LocationType loc4, Instant startTime,
            Instant endTime) {

        log.info("Visible bounds changed: loc1={}, loc2={}, loc3={}, loc4={}, startTime={}, endTime={}",
                loc1, loc2, loc3, loc4, startTime, endTime);

        interactionMapOverlay.setVisibleBounds(loc1, loc2, loc3, loc4);

        repaint();
    }

    @Override
    public void sourceAdded(DataSourceEvent event) {
        log.info("Data source added: {}", event.getDataSource().getDisplayName());
        // Handle data source addition - subclasses or listeners can override behavior
    }

    @Override
    public void sourceRemoved(DataSourceEvent event) {
        log.info("Data source removed: {}", event.getDataSource().getDisplayName());
        // Handle data source removal - subclasses or listeners can override behavior
    }
}
