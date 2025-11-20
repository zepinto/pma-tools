//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;

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
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import javax0.license3j.License;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.contacts.ContactUtils;
import pt.omst.contacts.PulvisContactSynchronizer;
import pt.omst.contacts.browser.editor.VerticalContactEditor;
import pt.omst.contacts.browser.filtering.ContactFilterListener;
import pt.omst.contacts.browser.filtering.ContactFilterPanel;
import pt.omst.contacts.reports.GenerateReportDialog;
import pt.omst.contacts.watcher.RecursiveFileWatcher;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.LoadingPanel;
import pt.omst.gui.ZoomableTimeIntervalSelector;
import pt.omst.gui.datasource.DataSourceEvent;
import pt.omst.gui.datasource.DataSourceListener;
import pt.omst.gui.datasource.DataSource;
import pt.omst.gui.datasource.FolderDataSource;
import pt.omst.gui.datasource.PulvisDataSource;
import pt.omst.gui.jobs.BackgroundJob;
import pt.omst.gui.jobs.JobManager;
import pt.omst.gui.jobs.TaskStatusIndicator;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.mapview.SlippyMap;
import pt.omst.pulvis.PulvisConnection;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;
import pt.omst.rasterlib.contacts.QuadTree;

/**
 * Main layout for the map viewer application.
 * Consists of:
 * - Top: DataSourceManagerPanel
 * - Center: SlippyMap (main display area)
 * - Bottom: ZoomableTimeIntervalSelector
 * - East (collapsible): Side panel with ObservationsPanel and contact details
 * form
 */
@Slf4j
@Getter
public class TargetManager extends JPanel implements AutoCloseable, DataSourceListener {

    private final SlippyMap slippyMap;
    private final DataSourceManagerPanel dataSourceManager;
    private final ZoomableTimeIntervalSelector timeSelector;
    // private final ObservationsPanel observationsPanel;
    // private final ContactDetailsFormPanel contactDetailsPanel;
    private final VerticalContactEditor contactEditor;
    private final ContactCollection contactCollection;
    private final ContactsMapOverlay contactsMapOverlay;
    private final JSplitPane mainSplitPane;
    private final JPanel eastPanel;
    private final JButton toggleEastPanelButton;
    private boolean eastPanelVisible = true;
    private final JPanel statusBar;
    private final JLabel totalContactsLabel;
    private final JLabel visibleContactsLabel;
    private final TaskStatusIndicator taskStatusIndicator;
    private boolean firstPaintDone = false;
    private final Map<PulvisDataSource, PulvisConnection> pulvisConnections = new HashMap<>();
    private final Map<PulvisDataSource, PulvisContactSynchronizer> pulvisSynchronizers = new HashMap<>();

    // File watcher for auto-refresh
    private RecursiveFileWatcher fileWatcher;
    private final Map<File, Long> ignoreOwnWritesUntil = new ConcurrentHashMap<>();
    private static final long IGNORE_WINDOW_MS = 500; // 500ms after save
    private boolean autoRefreshEnabled = true; // User preference
    private int activeConversionJobs = 0; // Track bulk conversion operations

    // West panel (filter panel)
    private final ContactFilterPanel filterPanel;
    private final JPanel westPanel;
    private final JSplitPane outerSplitPane;
    private final JButton toggleWestPanelButton;
    private boolean westPanelVisible = false;

    // Debouncing for zoom events
    private javax.swing.Timer updateContactsTimer;
    private boolean isZooming = false;

    /**
     * Creates a new MapViewerLayout with default time range.
     */
    public TargetManager() {
        this(Instant.now().minusSeconds(86400), Instant.now()); // Default: last 24 hours
    }

    /**
     * Creates a new MapViewerLayout with specified time range.
     * 
     * @param minTime Minimum time for the time selector
     * @param maxTime Maximum time for the time selector
     */
    public TargetManager(Instant minTime, Instant maxTime) {
        setLayout(new BorderLayout(5, 0));
        
        contactCollection = new ContactCollection();

        // Initialize components
        dataSourceManager = new DataSourceManagerPanel();
        dataSourceManager.addDataSourceListener(this);

        slippyMap = new SlippyMap();

        timeSelector = new ZoomableTimeIntervalSelector(minTime, maxTime);

        contactsMapOverlay = new ContactsMapOverlay(contactCollection);
        slippyMap.addMapOverlay(contactsMapOverlay);
        contactsMapOverlay.setTargetManager(this);
        contactsMapOverlay.setContactSelectionListener(contact -> {
            log.info("Contact selected: {}", contact.getContact().getLabel());
            setContact(contact);
        });

        // Add component listener to detect first paint and update contacts
        slippyMap.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!firstPaintDone && slippyMap.getWidth() > 0 && slippyMap.getHeight() > 0) {
                    firstPaintDone = true;
                    SwingUtilities.invokeLater(() -> {
                        updateVisibleContacts(true);
                        log.info("First paint completed, updated visible contacts");
                    });
                }
            }
        });

        contactEditor = new VerticalContactEditor();

        // Initialize file watcher
        try {
            fileWatcher = new RecursiveFileWatcher(this::handleFileChange);
            fileWatcher.addExtension("zct");
            fileWatcher.start();
            log.info("File watcher initialized and started");
        } catch (IOException e) {
            log.error("Failed to initialize file watcher", e);
        }

        // Listen for contact saves to refresh the map overlay
        contactEditor.addSaveListener((contactId, zctFile) -> {
            log.info("Contact saved: {}, refreshing map overlay", contactId);

            // Register this as our own write to ignore file watcher event
            ignoreOwnWritesUntil.put(zctFile, System.currentTimeMillis() + IGNORE_WINDOW_MS);

            try {
                contactCollection.refreshContact(zctFile);
            } catch (IOException e) {
                log.error("Failed to refresh contact {} in collection after save", zctFile.getName(), e);
            }
            contactsMapOverlay.refreshContact(zctFile);
            slippyMap.repaint();
            saveContact(contactId, zctFile);
            updateStatusBar();
        });

        // Create top panel with data source manager
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(dataSourceManager, BorderLayout.CENTER);

        // Initialize status bar labels
        totalContactsLabel = new JLabel("Total: 0");
        totalContactsLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        totalContactsLabel.setPreferredSize(new Dimension(100, 24));
        visibleContactsLabel = new JLabel("Visible: 0");
        visibleContactsLabel.setFont(new Font("Dialog", Font.PLAIN, 10));        
        visibleContactsLabel.setPreferredSize(new Dimension(100, 24));
        taskStatusIndicator = new TaskStatusIndicator(null); // Will set proper parent later
        taskStatusIndicator.setBorder(new EmptyBorder(0,0,0,0));

        // Create status bar with background job indicator and contact counts
        statusBar = createStatusBar();

        // Create bottom panel with time selector and status bar
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(timeSelector, BorderLayout.CENTER);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        // Create east panel (collapsible side panel)
        eastPanel = createEastPanel();
        eastPanel.setPreferredSize(new Dimension(400, 600));
        eastPanel.setMinimumSize(new Dimension(300, 400));

        // Create filter panel (west panel)
        filterPanel = new ContactFilterPanel();
        westPanel = createWestPanel();
        westPanel.setPreferredSize(new Dimension(300, 600));
        westPanel.setMinimumSize(new Dimension(250, 400));

        // Create center panel with map and toggle buttons
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(slippyMap, BorderLayout.CENTER);

        // Create main split pane (horizontal split between center and east)
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(centerPanel);
        mainSplitPane.setRightComponent(eastPanel);
        mainSplitPane.setResizeWeight(0.7); // Give 70% space to map
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setContinuousLayout(true);

        // Create outer split pane (horizontal split between west filter panel and main
        // split pane)
        outerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        outerSplitPane.setLeftComponent(null); // Start with west panel hidden
        outerSplitPane.setRightComponent(mainSplitPane);
        outerSplitPane.setResizeWeight(0.0); // Give priority to main content
        outerSplitPane.setOneTouchExpandable(false);
        outerSplitPane.setContinuousLayout(true);

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
            westPanelVisible = !westPanelVisible;
            if (westPanelVisible) {
                showFilters();
            } else {
                hideFilters();
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
            eastPanelVisible = !eastPanelVisible;
            if (eastPanelVisible) {
                showContactDetails();
            } else {
                hideContactDetails();
            }
        });

        JPanel toggleEastButtonPanel = new JPanel(new BorderLayout());
        toggleEastButtonPanel.add(toggleEastPanelButton, BorderLayout.NORTH);
        centerPanel.add(toggleEastButtonPanel, BorderLayout.EAST);

        // Assemble main layout
        add(topPanel, BorderLayout.NORTH);
        add(outerSplitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        loadPreferences();

        // Initialize debounce timer for zoom events (300ms delay)
        updateContactsTimer = new javax.swing.Timer(300, e -> {
            isZooming = false;
            updateVisibleContacts(true); // Update filter panel after zoom settles
            log.info("Zoom settled, updated contacts with filter panel refresh");
        });
        updateContactsTimer.setRepeats(false);

        SwingUtilities.invokeLater(() -> {
            // Connect filter panel listeners
            filterPanel.addFilterListener(new ContactFilterListener() {
                @Override
                public void onFilterChanged() {
                    log.info("Filters changed, updating visible contacts");
                    updateVisibleContacts(true); // Always update filter panel when filters change
                }

                @Override
                public void onContactSelected(CompressedContact contact) {
                    log.info("Contact selected from filter panel: {}", contact.getContact().getLabel());
                    setContact(contact);
                    // Center map on contact (maintain zoom level by passing current z)
                    slippyMap.focus(contact.getLatitude(), contact.getLongitude(), slippyMap.getLevelOfDetail());
                }
            });

            timeSelector.addPropertyChangeListener("selection", evt -> {
                Instant[] selection = (Instant[]) evt.getNewValue();
                Instant startTime = selection[0];
                Instant endTime = selection[1];
                updateVisibleContacts(true); // Update filter panel when time selection changes
                updateStatusBar();
                log.info("Selection changed: {} to {}", startTime, endTime);
            });

            slippyMap.addPropertyChangeListener("visibleBounds", evt -> {
                // Debounce rapid zoom/pan events
                isZooming = true;
                updateContactsTimer.restart();
                // Update contacts immediately but skip filter panel refresh during zoom
                updateVisibleContacts(false);
                updateStatusBar();
                log.debug("Map bounds changed, debouncing contact update");
            });
        });
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

    public void showFilters() {
        westPanelVisible = true;
        outerSplitPane.setLeftComponent(westPanel);
        outerSplitPane.setDividerLocation(300); // Default width for filter panel
        toggleWestPanelButton.setText("◂");
        outerSplitPane.revalidate();
        outerSplitPane.repaint();
    }

    public void hideFilters() {
        westPanelVisible = false;
        outerSplitPane.setLeftComponent(null);
        toggleWestPanelButton.setText("▸");
        outerSplitPane.revalidate();
        outerSplitPane.repaint();
    }

    /**
     * Handles file system changes detected by the file watcher.
     * Processes CREATE, MODIFY, and DELETE events for .zct files.
     */
    private void handleFileChange(String eventType, File file) {
        if (!autoRefreshEnabled) {
            log.debug("Auto-refresh disabled, ignoring file change: {}", file.getName());
            return;
        }
        
        // Skip auto-refresh during bulk conversion operations
        if (activeConversionJobs > 0) {
            log.debug("Skipping file watcher event during conversion: {}", file.getName());
            return;
        }

        // Must run on EDT for UI updates
        SwingUtilities.invokeLater(() -> {
            try {
                switch (eventType) {
                    case "CREATE":
                        log.info("New contact detected: {}", file.getName());
                        // Delay to allow file to be fully written, then retry if needed
                        addContactWithRetry(file, 3, 200);
                        break;

                    case "MODIFY":
                        if (!shouldIgnoreOwnWrite(file)) {
                            log.info("Contact modified externally: {}", file.getName());
                            // Delay to allow file to be fully written, then retry if needed
                            refreshContactWithRetry(file, 3, 200);
                        }
                        break;

                    case "DELETE":
                        log.info("Contact deleted: {}", file.getName());
                        contactCollection.removeContact(file);

                        // Clear editor if displaying deleted contact
                        if (contactEditor.getZctFile() != null &&
                                contactEditor.getZctFile().equals(file)) {
                            contactEditor.clearObservations();
                        }

                        updateVisibleContacts(true);
                        updateStatusBar();
                        slippyMap.repaint();
                        break;
                }
            } catch (Exception e) {
                log.error("Error handling file change event", e);
            }
        });
    }

    /**
     * Checks if a file modification should be ignored because it was caused by this
     * application.
     */
    private boolean shouldIgnoreOwnWrite(File file) {
        Long ignoreUntil = ignoreOwnWritesUntil.get(file);
        if (ignoreUntil != null) {
            if (System.currentTimeMillis() < ignoreUntil) {
                log.debug("Ignoring own write for file: {}", file.getName());
                return true;
            }
            ignoreOwnWritesUntil.remove(file); // Expired
        }
        return false;
    }

    /**
     * Adds a contact with retry logic to handle files still being written.
     * Uses a background thread with delays to avoid blocking the EDT.
     * 
     * @param file The .zct file to add
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Initial delay in milliseconds before first attempt
     */
    private void addContactWithRetry(File file, int maxRetries, int delayMs) {
        IndexedRasterUtils.background(() -> {
            int attempt = 0;
            Exception lastException = null;
            
            while (attempt < maxRetries) {
                try {
                    // Wait before attempting to read (file may still be writing)
                    Thread.sleep(delayMs * (attempt + 1)); // Increasing delay
                    
                    // Try to add the contact
                    contactCollection.addContact(file);
                    
                    // Success - update UI on EDT
                    SwingUtilities.invokeLater(() -> {
                        try {
                            updateVisibleContacts(true);
                            updateStatusBar();
                            slippyMap.repaint();
                        } catch (Exception e) {
                            log.error("Error updating UI after adding contact {}", file.getName(), e);
                        }
                    });
                    
                    log.debug("Successfully added contact {} after {} attempt(s)", 
                        file.getName(), attempt + 1);
                    return; // Success
                    
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    if (attempt < maxRetries) {
                        log.debug("Failed to add contact {} (attempt {}/{}): {}", 
                            file.getName(), attempt, maxRetries, e.getMessage());
                    }
                }
            }
            
            // All retries failed
            log.warn("Failed to add contact {} after {} attempts", 
                file.getName(), maxRetries, lastException);
        });
    }

    /**
     * Refreshes a contact with retry logic to handle files still being written.
     * Uses a background thread with delays to avoid blocking the EDT.
     * 
     * @param file The .zct file to refresh
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Initial delay in milliseconds before first attempt
     */
    private void refreshContactWithRetry(File file, int maxRetries, int delayMs) {
        IndexedRasterUtils.background(() -> {
            int attempt = 0;
            Exception lastException = null;
            
            while (attempt < maxRetries) {
                try {
                    // Wait before attempting to read (file may still be writing)
                    Thread.sleep(delayMs * (attempt + 1)); // Increasing delay
                    
                    // Try to refresh the contact (throws IOException if file is incomplete/corrupt)
                    contactCollection.refreshContact(file);
                    
                    // Success - update UI on EDT
                    SwingUtilities.invokeLater(() -> {
                        try {
                            contactsMapOverlay.refreshContact(file);
                            
                            // Auto-reload if this is the currently displayed contact
                            if (contactEditor.getZctFile() != null &&
                                    contactEditor.getZctFile().equals(file)) {
                                try {
                                    log.info("Reloading modified contact in editor: {}", file.getName());
                                    contactEditor.loadZct(file);
                                } catch (IOException e) {
                                    log.error("Failed to reload contact in editor", e);
                                }
                            }
                            
                            updateVisibleContacts(true);
                            updateStatusBar();
                            slippyMap.repaint();
                        } catch (Exception e) {
                            log.error("Error updating UI after contact refresh for {}", file.getName(), e);
                        }
                    });
                    
                    log.debug("Successfully refreshed contact {} after {} attempt(s)", 
                        file.getName(), attempt + 1);
                    return; // Success
                    
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    if (attempt < maxRetries) {
                        log.debug("Failed to refresh contact {} (attempt {}/{}): {}", 
                            file.getName(), attempt, maxRetries, e.getMessage());
                    }
                }
            }
            
            // All retries failed
            log.warn("Failed to refresh contact {} after {} attempts", 
                file.getName(), maxRetries, lastException);
        });
    }

    public void savePreferences() {
        // store divider location and data sources
        Preferences prefs = Preferences.userNodeForPackage(TargetManager.class);

        // Save main divider location
        int dividerLocation = mainSplitPane.getDividerLocation();
        prefs.putInt("mainSplitPane.dividerLocation", dividerLocation);
        log.debug("Saved divider location: {}", dividerLocation);

        // Save east panel visibility
        prefs.putBoolean("eastPanel.visible", eastPanelVisible);
        log.debug("Saved east panel visibility: {}", eastPanelVisible);

        // Save west panel state
        prefs.putBoolean("westPanel.visible", westPanelVisible);
        if (westPanelVisible) {
            int westDivider = outerSplitPane.getDividerLocation();
            prefs.putInt("westPanel.dividerLocation", westDivider);
            log.debug("Saved west panel divider location: {}", westDivider);
        }
        log.debug("Saved west panel visibility: {}", westPanelVisible);

        // Save filter selections
        Set<String> classifications = filterPanel.getSelectedClassifications();
        Set<String> confidences = filterPanel.getSelectedConfidences();
        Set<String> labels = filterPanel.getSelectedLabels();
        prefs.put("filters.classifications", String.join(",", classifications));
        prefs.put("filters.confidence", String.join(",", confidences));
        prefs.put("filters.labels", String.join(",", labels));
        log.debug("Saved filter selections");

        // Save auto-refresh preference
        prefs.putBoolean("autoRefresh.enabled", autoRefreshEnabled);
        log.debug("Saved auto-refresh preference: {}", autoRefreshEnabled);

        // Save icon size preference
        int iconSize = IconCache.getInstance().getIconSize();
        prefs.putInt("iconSize", iconSize);
        log.debug("Saved icon size preference: {}", iconSize);

        // Save time selection
        Instant startTime = timeSelector.getSelectedStartTime();
        Instant endTime = timeSelector.getSelectedEndTime();
        if (startTime != null && endTime != null) {
            prefs.putLong("timeSelector.startTime", startTime.toEpochMilli());
            prefs.putLong("timeSelector.endTime", endTime.toEpochMilli());
            log.debug("Saved time selection: {} to {}", startTime, endTime);
        }

        try {
            dataSourceManager.saveToPreferences();
            // Force flush to ensure preferences are written to disk
            prefs.flush();
        } catch (Exception e) {
            log.error("Error saving preferences", e);
        }
    }

    public void loadPreferences() {
        // load divider location and data sources
        Preferences prefs = Preferences.userNodeForPackage(TargetManager.class);

        // Load main divider location (default to -1 which means use default)
        int dividerLocation = prefs.getInt("mainSplitPane.dividerLocation", -1);
        if (dividerLocation > 0) {
            // Defer setting divider location until after component is visible
            javax.swing.SwingUtilities.invokeLater(() -> {
                mainSplitPane.setDividerLocation(dividerLocation);
                log.debug("Loaded main divider location: {}", dividerLocation);
            });
        }

        // Load east panel visibility (default to true)
        boolean eastVisible = prefs.getBoolean("eastPanel.visible", true);
        log.debug("Loaded east panel visibility: {}", eastVisible);
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (eastVisible && !eastPanelVisible) {
                showContactDetails();
            } else if (!eastVisible && eastPanelVisible) {
                hideContactDetails();
            }
        });

        // Load west panel visibility (default to false - collapsed)
        boolean westVisible = prefs.getBoolean("westPanel.visible", false);
        int westDivider = prefs.getInt("westPanel.dividerLocation", 300);
        log.debug("Loaded west panel visibility: {}, divider: {}", westVisible, westDivider);
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (westVisible) {
                showFilters();
                if (westDivider > 0) {
                    outerSplitPane.setDividerLocation(westDivider);
                }
            }
        });

        // Load filter selections
        String classificationsStr = prefs.get("filters.classifications", "");
        String confidencesStr = prefs.get("filters.confidence", "");
        String labelsStr = prefs.get("filters.labels", "");

        javax.swing.SwingUtilities.invokeLater(() -> {
            if (!classificationsStr.isEmpty()) {
                Set<String> classifications = Set.of(classificationsStr.split(","));
                filterPanel.setSelectedClassifications(classifications);
                log.debug("Loaded classification filters: {}", classifications);
            }
            if (!confidencesStr.isEmpty()) {
                Set<String> confidences = Set.of(confidencesStr.split(","));
                filterPanel.setSelectedConfidences(confidences);
                log.debug("Loaded confidence filters: {}", confidences);
            }
            if (!labelsStr.isEmpty()) {
                Set<String> labels = Set.of(labelsStr.split(","));
                filterPanel.setSelectedLabels(labels);
                log.debug("Loaded label filters: {}", labels);
            }
        });

        // Load auto-refresh preference (default to true)
        autoRefreshEnabled = prefs.getBoolean("autoRefresh.enabled", true);
        log.debug("Loaded auto-refresh preference: {}", autoRefreshEnabled);

        // Load icon size preference (default to 12)
        int iconSize = prefs.getInt("iconSize", 12);
        IconCache.getInstance().setIconSize(iconSize);
        log.debug("Loaded icon size preference: {}", iconSize);

        // Load time selection
        long startTimeMillis = prefs.getLong("timeSelector.startTime", -1);
        long endTimeMillis = prefs.getLong("timeSelector.endTime", -1);

        if (startTimeMillis > 0 && endTimeMillis > 0) {
            Instant startTime = Instant.ofEpochMilli(startTimeMillis);
            Instant endTime = Instant.ofEpochMilli(endTimeMillis);
            log.debug("Loaded time selection: {} to {}", startTime, endTime);
            javax.swing.SwingUtilities.invokeLater(() -> {
                timeSelector.setSelectedInterval(startTime, endTime);
                updateVisibleContacts(true);
            });
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            updateVisibleContacts(true);
            log.info("Applied contact filters on load");
        });

        try {
            log.info("Loading data sources from preferences...");
            dataSourceManager.loadFromPreferences();
            log.info("Data sources loaded. Current count: {}", dataSourceManager.getDataSources().size());
        } catch (Exception e) {
            log.error("Error loading data sources from preferences", e);
        }
    }

    /**
     * Creates the east panel containing observations and contact details.
     */
    private JPanel createEastPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        panel.add(createContactEditorSection(), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the west panel containing the contact filter panel.
     */
    private JPanel createWestPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JScrollPane scrollPane = new JScrollPane(filterPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(scrollPane, BorderLayout.CENTER);
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

    /**
     * Creates the status bar with background job indicator and contact counts.
     */
    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(2,2,0,2));
        panel.setPreferredSize(new Dimension(100, 26));
        
        // Left side: Contact counts
        JPanel countsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        countsPanel.add(totalContactsLabel);
        countsPanel.add(visibleContactsLabel);

        // Right side: Background job indicator
        JPanel jobPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        jobPanel.add(taskStatusIndicator);

        panel.add(countsPanel, BorderLayout.WEST);
        panel.add(jobPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Updates the status bar with current contact counts.
     */
    private void updateStatusBar() {
        SwingUtilities.invokeLater(() -> {
            int totalContacts = contactCollection.getAllContacts().size();
            int visibleContacts = contactCollection.getFilteredContacts().size();

            totalContactsLabel.setText("Total: " + totalContacts);
            visibleContactsLabel.setText("Visible: " + visibleContacts);
        });
    }

    /**
     * Sets the contact to be displayed in the details panel.
     * 
     * @param contact The contact to display
     */
    public void setContact(CompressedContact contact) {
        try {
            contactEditor.loadZct(contact.getZctFile());
        } catch (Exception e) {
            log.error("Error loading contact in editor", e);
        }
    }

    /**
     * Clears all observations from the observations panel.
     */
    public void clearObservations() {
        contactEditor.clearObservations();
    }

    /**
     * Connects to a Pulvis server via WebSocket and initiates synchronization.
     * 
     * @param pcs the Pulvis connection configuration
     */
    private void connectToPulvis(PulvisDataSource pcs) {
        // Don't connect if already connected
        if (pulvisConnections.containsKey(pcs)) {
            log.warn("Already connected to Pulvis at {}", pcs.getBaseUrl());
            return;
        }

        // Create connection
        PulvisConnection connection = new PulvisConnection(pcs.getHost(), pcs.getPort());
        pulvisConnections.put(pcs, connection);

        // Create sync folder for this Pulvis instance
        String syncFolderName = String.format("pulvis-%s-%d", pcs.getHost(), pcs.getPort());
        File syncFolder = new File("contacts", syncFolderName);

        // Create synchronizer
        PulvisContactSynchronizer synchronizer = new PulvisContactSynchronizer(
                connection,
                syncFolder,
                contactCollection,
                progress -> {
                    // Progress callback - could update UI here if needed
                    log.debug("Sync progress: {} contacts", progress.get());
                });
        pulvisSynchronizers.put(pcs, synchronizer);

        connection.connect().thenRun(() -> {
            log.info("Connected to Pulvis WS for contacts at {}", pcs.getBaseUrl());

            // Start initial download in background job
            BackgroundJob downloadJob = new BackgroundJob("Download from " + pcs.getHost()) {
                private int totalContacts = 0;

                @Override
                protected Void doInBackground() throws Exception {
                    updateStatus("Starting download...");

                    // Start download and track progress
                    synchronizer.downloadAllContacts().whenComplete((count, throwable) -> {
                        if (throwable != null) {
                            log.error("Download failed", throwable);
                            updateStatus("Failed: " + throwable.getMessage());
                        } else {
                            totalContacts = count;
                            updateStatus("Completed: " + count + " contacts");
                            setProgress(100);

                            // Add sync folder to file watcher
                            if (fileWatcher != null) {
                                try {
                                    fileWatcher.addRoot(syncFolder);
                                } catch (IOException e) {
                                    log.error("Failed to add sync folder to file watcher", e);
                                }
                            }
                        }
                    });

                    // Poll for progress updates
                    while (!synchronizer.isInitialSyncComplete() && !isCancelled()) {
                        Thread.sleep(500);
                        int current = synchronizer.getStatistics().get("downloaded");
                        if (totalContacts > 0) {
                            setProgress((current * 100) / totalContacts);
                        }
                        updateStatus(String.format("Downloaded %d contacts...", current));
                    }

                    return null;
                }
            };

            JobManager.getInstance().submit(downloadJob);

        }).exceptionally(ex -> {
            log.error("Error connecting to Pulvis WS at {}", pcs.getBaseUrl(), ex);
            pulvisConnections.remove(pcs);
            pulvisSynchronizers.remove(pcs);
            return null;
        });

        connection.addEventListener(ce -> {
            log.info("Received contact event from Pulvis: {}", ce.getEventType());
            synchronizer.handleContactEvent(ce);
        });
    }

    /**
     * Disconnects from a Pulvis server.
     * 
     * @param pcs the Pulvis connection to disconnect
     */
    private void disconnectFromPulvis(PulvisDataSource pcs) {
        PulvisConnection connection = pulvisConnections.remove(pcs);
        if (connection != null) {
            try {
                connection.disconnect();
                log.info("Disconnected from Pulvis at {}", pcs.getBaseUrl());
            } catch (Exception e) {
                log.error("Error disconnecting from Pulvis at {}", pcs.getBaseUrl(), e);
            }
        }
    }

    /**
     * Updates the visible contacts based on current map bounds and time selection.
     * 
     * @param updateFilterPanel whether to update the filter panel contact list
     */
    private void updateVisibleContacts(boolean updateFilterPanel) {
        Instant startTime = timeSelector.getSelectedStartTime();
        Instant endTime = timeSelector.getSelectedEndTime();

        // Get filter criteria from filter panel
        Set<String> classifications = filterPanel.getSelectedClassifications();
        Set<String> confidences = filterPanel.getSelectedConfidences();
        Set<String> labels = filterPanel.getSelectedLabels();

        // Apply filters with all criteria
        contactCollection.applyFilters(
                new QuadTree.Region(slippyMap.getVisibleBounds()),
                startTime,
                endTime,
                classifications,
                confidences,
                labels);

        // Update the filter panel's contact list only if requested
        // (skip during rapid zoom/pan to improve performance)
        if (updateFilterPanel) {
            // Schedule filter panel update after a short delay to allow background
            // filtering to complete
            javax.swing.Timer filterPanelTimer = new javax.swing.Timer(350, e -> {
                filterPanel.setContacts(contactCollection.getFilteredContacts());
                updateStatusBar();
            });
            filterPanelTimer.setRepeats(false);
            filterPanelTimer.start();
        }
    }

    /**
     * Updates the time range for the time selector.
     * 
     * @param minTime New minimum time
     * @param maxTime New maximum time
     */
    public void setTimeRange(Instant minTime, Instant maxTime) {
        timeSelector.setAbsoluteMinTime(minTime);
        timeSelector.setAbsoluteMaxTime(maxTime);
    }

    /**
     * Gets the currently selected time interval.
     * 
     * @return Array with [startTime, endTime]
     */
    public Instant[] getSelectedTimeInterval() {
        return new Instant[] {
                timeSelector.getSelectedStartTime(),
                timeSelector.getSelectedEndTime()
        };
    }

    /**
     * Shows the license information dialog.
     * 
     * @param parent Parent frame for the dialog
     */
    public static void showLicenseDialog(JFrame parent) {
        JFrame licenseFrame = new JFrame("Software License");
        LicensePanel panel = new LicensePanel();
        licenseFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        try {
            License mainLicense = LicenseChecker.getMainLicense();
            if (mainLicense != null) {
                License activation = LicenseChecker.getLicenseActivation();
                panel.setLicense(mainLicense, activation);
            }
        } catch (Exception e) {
            log.error("Error loading license", e);
        }

        licenseFrame.add(panel);
        licenseFrame.pack();
        licenseFrame.setLocationRelativeTo(parent);
        licenseFrame.setVisible(true);
    }

    /**
     * Creates a menu bar for the application.
     * 
     * @param frame The parent frame
     * @return The configured menu bar
     */
    public static JMenuBar createMenuBar(JFrame frame, TargetManager targetManager) {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        // Preferences submenu
        JMenu preferencesMenu = new JMenu("Preferences");

        // Base Map submenu (only if targetManager has a slippyMap)
        if (targetManager != null && targetManager.slippyMap != null) {
            JMenu baseMapMenu = targetManager.slippyMap.getBaseMapManager().createBaseMapMenu();
            preferencesMenu.add(baseMapMenu);
            preferencesMenu.addSeparator();
        }

        // Dark Mode toggle
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.setSelected(GuiUtils.isDarkTheme());
        darkModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean isDark = darkModeItem.isSelected();
                GuiUtils.setTheme(isDark ? "dark" : "light");
                // Update the map's dark mode setting
                if (targetManager != null && targetManager.slippyMap != null) {
                    targetManager.slippyMap.setDarkMode(isDark);
                }
            }
        });
        preferencesMenu.add(darkModeItem);
        preferencesMenu.addSeparator();

        // Auto-Refresh toggle
        JCheckBoxMenuItem autoRefreshItem = new JCheckBoxMenuItem("Auto-Refresh Contacts");
        autoRefreshItem.setSelected(targetManager != null && targetManager.autoRefreshEnabled);
        autoRefreshItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (targetManager != null) {
                    targetManager.autoRefreshEnabled = autoRefreshItem.isSelected();
                    log.info("Auto-refresh contacts: {}", targetManager.autoRefreshEnabled);
                }
            }
        });
        preferencesMenu.add(autoRefreshItem);
        preferencesMenu.addSeparator();

        // Icon Size submenu
        JMenu iconSizeMenu = new JMenu("Icon Size");
        int[] iconSizes = { 8, 12, 16, 20, 24, 32 };
        int currentSize = IconCache.getInstance().getIconSize();

        for (int size : iconSizes) {
            JMenuItem sizeItem = new JMenuItem(size + " px" + (size == currentSize ? " ✓" : ""));
            final int selectedSize = size;
            sizeItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    IconCache.getInstance().setIconSize(selectedSize);
                    if (targetManager != null && targetManager.slippyMap != null) {
                        targetManager.slippyMap.repaint();
                    }
                    // Update menu to show new selection
                    for (int i = 0; i < iconSizeMenu.getItemCount(); i++) {
                        JMenuItem item = iconSizeMenu.getItem(i);
                        String text = item.getText().replaceAll(" ✓", "");
                        if (text.equals(selectedSize + " px")) {
                            item.setText(text + " ✓");
                        } else {
                            item.setText(text);
                        }
                    }
                }
            });
            iconSizeMenu.add(sizeItem);
        }
        preferencesMenu.add(iconSizeMenu);
        preferencesMenu.addSeparator();

        // Contact Types menu item
        JMenuItem contactTypesItem = new JMenuItem("Contact Types");
        contactTypesItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ContactItemsEditor editor = new ContactItemsEditor();
                editor.setVisible(true);
            }
        });
        preferencesMenu.add(contactTypesItem);

        // Labels menu item
        JMenuItem labelsItem = new JMenuItem("Labels");
        labelsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LabelsEditor editor = new LabelsEditor();
                editor.setVisible(true);
            }
        });
        preferencesMenu.add(labelsItem);

        fileMenu.add(preferencesMenu);
        fileMenu.addSeparator();

        // Reports submenu
        JMenu reportsMenu = new JMenu("Reports");

        // Generate Report menu item
        JMenuItem generateReportItem = new JMenuItem("Generate Report...");
        generateReportItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (targetManager != null && targetManager.contactCollection != null) {
                    GenerateReportDialog dialog = new GenerateReportDialog(frame, targetManager.contactCollection);
                    dialog.setVisible(true);
                } else {
                    GuiUtils.errorMessage(frame, "Error", "No contact collection available.");
                }
            }
        });
        reportsMenu.add(generateReportItem);

        // Edit Template menu item
        JMenuItem editTemplateItem = new JMenuItem("Edit Template...");
        editTemplateItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // Extract template from resources to temp file
                    java.io.InputStream templateStream = TargetManager.class
                            .getResourceAsStream("/templates/report-template.html");
                    if (templateStream == null) {
                        GuiUtils.errorMessage(frame, "Error", "Template file not found in resources.");
                        return;
                    }

                    java.io.File tempFile = java.io.File.createTempFile("contact-report-template-", ".html");
                    java.nio.file.Files.copy(templateStream, tempFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    templateStream.close();

                    GuiUtils.infoMessage(frame, "Template Editor",
                            "Opening template in system editor.\n\n" +
                                    "Note: Changes will NOT be saved to the application.\n" +
                                    "This is a temporary file for reference only.\n\n" +
                                    "File location: " + tempFile.getAbsolutePath());

                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                        if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
                            desktop.edit(tempFile);
                        } else if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                            desktop.open(tempFile);
                        } else {
                            GuiUtils.errorMessage(frame, "Not Supported",
                                    "Desktop edit operation not supported on this system.\n" +
                                            "Please open the file manually: " + tempFile.getAbsolutePath());
                        }
                    } else {
                        GuiUtils.errorMessage(frame, "Not Supported",
                                "Desktop operations not supported on this system.\n" +
                                        "Please open the file manually: " + tempFile.getAbsolutePath());
                    }
                } catch (java.io.IOException ex) {
                    log.error("Error editing template", ex);
                    GuiUtils.errorMessage(frame, "Error", "Failed to open template: " + ex.getMessage());
                }
            }
        });
        reportsMenu.add(editTemplateItem);

        fileMenu.add(reportsMenu);
        fileMenu.addSeparator();

        // Exit menu item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                System.exit(0);
            }
        });

        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Tools menu
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem convertMarksItem = new JMenuItem("Convert Legacy Marks...");
        convertMarksItem.addActionListener(e -> {
            if (targetManager != null) {
                targetManager.convertLegacyMarks();
            }
        });
        toolsMenu.add(convertMarksItem);
        
        JMenuItem sendAllItem = new JMenuItem("Send All to Data Manager...");
        sendAllItem.addActionListener(e -> {
            if (targetManager != null) {
                targetManager.sendAllContactsToPulvis();
            }
        });
        toolsMenu.add(sendAllItem);
        menuBar.add(toolsMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        // Software License menu item
        JMenuItem licenseItem = new JMenuItem("Software License");
        licenseItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showLicenseDialog(frame);
            }
        });

        helpMenu.add(licenseItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    @Override
    public void close() throws Exception {
        // Close file watcher
        if (fileWatcher != null) {
            try {
                fileWatcher.close();
                log.info("File watcher closed");
            } catch (Exception e) {
                log.error("Error closing file watcher", e);
            }
        }

        // Close all Pulvis connections
        for (Map.Entry<PulvisDataSource, PulvisConnection> entry : pulvisConnections.entrySet()) {
            try {
                entry.getValue().disconnect();
                log.info("Closed Pulvis connection to {}", entry.getKey().getBaseUrl());
            } catch (Exception e) {
                log.error("Error closing Pulvis connection to {}", entry.getKey().getBaseUrl(), e);
            }
        }
        pulvisConnections.clear();

        if (slippyMap != null) {
            slippyMap.close();
        }
        log.info("Target Manager closed");
    }

    @Override
    public void sourceAdded(DataSourceEvent event) {
        switch (event.getDataSource()) {
            case FolderDataSource cds -> {
                log.info("Folder data source added: {}", cds.getFolder().getName());
                contactCollection.addRootFolder(cds.getFolder());
                updateStatusBar();

                // Add folder to file watcher
                if (fileWatcher != null && autoRefreshEnabled) {
                    try {
                        fileWatcher.addRoot(cds.getFolder());
                        log.info("Added folder to file watcher: {}", cds.getFolder());
                    } catch (IOException e) {
                        log.error("Failed to add folder to file watcher", e);
                    }
                }
            }
            case PulvisDataSource pcs -> {
                log.info("Pulvis connection added: {}", pcs.getDisplayName());
                connectToPulvis(pcs);
            }
            default -> {
                log.warn("Unsupported data source type added: {}", event.getDataSource().getClass().getName());
            }
        }
    }

    @Override
    public void sourceRemoved(DataSourceEvent event) {
        switch (event.getDataSource()) {
            case FolderDataSource cds -> {
                log.info("Folder data source removed: {}", cds.getFolder().getName());
                contactCollection.removeRootFolder(cds.getFolder());
                updateStatusBar();

                // Remove folder from file watcher
                if (fileWatcher != null) {
                    fileWatcher.removeRoot(cds.getFolder());
                    log.info("Removed folder from file watcher: {}", cds.getFolder());
                }
                break;
            }
            case PulvisDataSource pcs -> {
                log.info("Pulvis connection removed: {}", pcs.getDisplayName());
                disconnectFromPulvis(pcs);
                break;
            }
            default -> {
                break;
            }
        }
    }

    /**
     * Groups multiple contacts into a main contact asynchronously.
     * Merges observations, raster files, and annotations from merge contacts into
     * main contact.
     * Deletes merged contacts on success, rolls back on failure.
     * 
     * @param mainContact   The contact to merge into
     * @param mergeContacts The contacts to merge (will be deleted)
     */
    public void groupContactsAsync(CompressedContact mainContact, List<CompressedContact> mergeContacts) {
        IndexedRasterUtils.background(() -> {
            List<File> backupFiles = new ArrayList<>();
            Path tempDir = null;

            try {
                // Step 1: Extract main contact to temp directory
                tempDir = Files.createTempDirectory("contact_group_");
                log.info("Extracting main contact to temp directory: {}", tempDir);

                if (!ZipUtils.unZip(mainContact.getZctFile().getAbsolutePath(), tempDir.toString())) {
                    throw new IOException("Failed to extract main contact ZIP");
                }

                // Step 2: Load main contact data
                pt.omst.rasterlib.Contact mainContactData = pt.omst.rasterlib.contacts.CompressedContact
                        .extractCompressedContact(mainContact.getZctFile());

                if (mainContactData.getObservations() == null) {
                    mainContactData.setObservations(new ArrayList<>());
                }

                int initialObsCount = mainContactData.getObservations().size();
                log.debug("Initial main contact has {} observations", initialObsCount);

                // Step 3: Collect all labels for deduplication
                Set<String> labelSet = new HashSet<>();
                for (pt.omst.rasterlib.Observation obs : mainContactData.getObservations()) {
                    if (obs.getAnnotations() != null) {
                        for (pt.omst.rasterlib.Annotation ann : obs.getAnnotations()) {
                            if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.LABEL
                                    && ann.getText() != null && !ann.getText().trim().isEmpty()) {
                                labelSet.add(ann.getText().toLowerCase());
                            }
                        }
                    }
                }

                // Step 4: Process each merge contact
                for (CompressedContact mergeContact : mergeContacts) {
                    log.info("Processing merge contact: {}", mergeContact.getContact().getLabel());

                    // Extract merge contact data
                    pt.omst.rasterlib.Contact mergeContactData = pt.omst.rasterlib.contacts.CompressedContact
                            .extractCompressedContact(mergeContact.getZctFile());

                    if (mergeContactData.getObservations() == null) {
                        log.debug("Merge contact has no observations, skipping");
                        continue;
                    }

                    log.debug("Merge contact has {} observations", mergeContactData.getObservations().size());

                    // Extract merge contact to temp location for raster file access
                    Path mergeTempDir = Files.createTempDirectory("contact_merge_");
                    try {
                        if (!ZipUtils.unZip(mergeContact.getZctFile().getAbsolutePath(), mergeTempDir.toString())) {
                            log.warn("Failed to extract merge contact ZIP: {}", mergeContact.getZctFile());
                            continue;
                        }

                        // Process each observation
                        for (pt.omst.rasterlib.Observation obs : mergeContactData.getObservations()) {
                            // Ensure observation has a UUID (generate if missing)
                            if (obs.getUuid() == null) {
                                obs.setUuid(java.util.UUID.randomUUID());
                                log.debug("Generated UUID for observation without UUID");
                            }

                            log.debug("Processing observation {} with raster: {}",
                                    obs.getUuid(), obs.getRasterFilename());

                            // Create new observation copy (preserve UUID as per requirements)
                            pt.omst.rasterlib.Observation newObs = new pt.omst.rasterlib.Observation();
                            newObs.setUuid(obs.getUuid()); // Preserve original UUID
                            newObs.setDepth(obs.getDepth());
                            newObs.setLatitude(obs.getLatitude());
                            newObs.setLongitude(obs.getLongitude());
                            newObs.setSystemName(obs.getSystemName());
                            newObs.setTimestamp(obs.getTimestamp());
                            newObs.setUserName(obs.getUserName());

                            // Copy raster file if exists
                            if (obs.getRasterFilename() != null && !obs.getRasterFilename().isEmpty()) {
                                File sourceRasterFile = mergeTempDir.resolve(obs.getRasterFilename()).toFile();
                                if (sourceRasterFile.exists()) {
                                    String targetFilename = obs.getRasterFilename();
                                    File targetRasterFile = tempDir.resolve(targetFilename).toFile();

                                    // Handle filename conflict by postfixing observation UUID
                                    if (targetRasterFile.exists()) {
                                        String baseName = targetFilename;
                                        String extension = "";
                                        int lastDot = targetFilename.lastIndexOf('.');
                                        if (lastDot > 0) {
                                            baseName = targetFilename.substring(0, lastDot);
                                            extension = targetFilename.substring(lastDot);
                                        }
                                        targetFilename = baseName + "_" + obs.getUuid().toString() + extension;
                                        targetRasterFile = tempDir.resolve(targetFilename).toFile();
                                        log.info("Raster filename conflict resolved: {} -> {}",
                                                obs.getRasterFilename(), targetFilename);
                                    }

                                    Files.copy(sourceRasterFile.toPath(), targetRasterFile.toPath(),
                                            StandardCopyOption.REPLACE_EXISTING);
                                    newObs.setRasterFilename(targetFilename);
                                    log.debug("Copied raster file: {} -> {}",
                                            obs.getRasterFilename(), targetFilename);

                                    // Also copy the associated image file referenced in the raster JSON
                                    try {
                                        String rasterJson = Files.readString(sourceRasterFile.toPath());
                                        pt.omst.rasterlib.IndexedRaster indexedRaster = pt.omst.rasterlib.Converter
                                                .IndexedRasterFromJsonString(rasterJson);
                                        if (indexedRaster.getFilename() != null) {
                                            File sourceImageFile = mergeTempDir.resolve(indexedRaster.getFilename())
                                                    .toFile();
                                            if (sourceImageFile.exists()) {
                                                String imageFilename = indexedRaster.getFilename();
                                                File targetImageFile = tempDir.resolve(imageFilename).toFile();

                                                // Handle image filename conflict
                                                if (targetImageFile.exists()) {
                                                    String imageBaseName = imageFilename;
                                                    String imageExtension = "";
                                                    int imageDot = imageFilename.lastIndexOf('.');
                                                    if (imageDot > 0) {
                                                        imageBaseName = imageFilename.substring(0, imageDot);
                                                        imageExtension = imageFilename.substring(imageDot);
                                                    }
                                                    imageFilename = imageBaseName + "_" + obs.getUuid().toString()
                                                            + imageExtension;
                                                    targetImageFile = tempDir.resolve(imageFilename).toFile();

                                                    // Update the raster JSON with new image filename
                                                    indexedRaster.setFilename(imageFilename);
                                                    String updatedRasterJson = pt.omst.rasterlib.Converter
                                                            .IndexedRasterToJsonString(indexedRaster);
                                                    Files.writeString(targetRasterFile.toPath(), updatedRasterJson);
                                                    log.debug("Image filename conflict resolved: {} -> {}",
                                                            sourceImageFile.getName(), imageFilename);
                                                }

                                                Files.copy(sourceImageFile.toPath(), targetImageFile.toPath(),
                                                        StandardCopyOption.REPLACE_EXISTING);
                                                log.debug("Copied image file: {}", imageFilename);
                                            } else {
                                                log.warn("Image file not found: {}", sourceImageFile);
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Failed to copy associated image file for raster {}: {}",
                                                obs.getRasterFilename(), e.getMessage());
                                    }
                                } else {
                                    log.warn("Raster file not found: {}", sourceRasterFile);
                                    newObs.setRasterFilename(obs.getRasterFilename());
                                }
                            } else {
                                log.debug("Observation has no raster filename");
                            }

                            // Filter and copy annotations (exclude CLASSIFICATION and CONFIDENCE,
                            // deduplicate labels)
                            List<pt.omst.rasterlib.Annotation> newAnnotations = new ArrayList<>();
                            if (obs.getAnnotations() != null) {
                                for (pt.omst.rasterlib.Annotation ann : obs.getAnnotations()) {
                                    // Skip CLASSIFICATION and CONFIDENCE types
                                    if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.CLASSIFICATION) {
                                        continue;
                                    }
                                    // Note: CONFIDENCE is not in the enum, so we only check CLASSIFICATION

                                    // For LABEL annotations, deduplicate case-insensitively
                                    if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.LABEL) {
                                        if (ann.getText() == null || ann.getText().trim().isEmpty()) {
                                            continue; // Skip empty labels
                                        }
                                        String labelKey = ann.getText().toLowerCase();
                                        if (labelSet.contains(labelKey)) {
                                            continue; // Skip duplicate label
                                        }
                                        labelSet.add(labelKey);
                                    }

                                    // Copy annotation
                                    newAnnotations.add(ann);
                                }
                            }
                            newObs.setAnnotations(newAnnotations);

                            // Add observation to main contact
                            mainContactData.getObservations().add(newObs);
                            log.debug("Added observation {} to main contact, total now: {}",
                                    newObs.getUuid(), mainContactData.getObservations().size());
                        }
                    } finally {
                        // Clean up merge temp directory
                        try {
                            Files.walk(mergeTempDir)
                                    .sorted(java.util.Comparator.reverseOrder())
                                    .map(Path::toFile)
                                    .forEach(File::delete);
                        } catch (Exception e) {
                            log.warn("Failed to clean up merge temp directory: {}", mergeTempDir, e);
                        }
                    }
                }

                // Step 5: Save updated contact.json
                int finalObsCount = mainContactData.getObservations().size();
                log.debug("Saving merged contact with {} observations (started with {}, added {})",
                        finalObsCount, initialObsCount, finalObsCount - initialObsCount);
                String updatedJson = pt.omst.rasterlib.Converter.ContactToJsonString(mainContactData);
                File contactJsonFile = tempDir.resolve("contact.json").toFile();
                Files.writeString(contactJsonFile.toPath(), updatedJson);
                log.debug("Wrote contact.json to temp directory");

                // Step 6: Repackage main contact ZIP
                File mainZctFile = mainContact.getZctFile();
                File backupMainZct = new File(mainZctFile.getParentFile(),
                        mainZctFile.getName() + ".backup");
                Files.copy(mainZctFile.toPath(), backupMainZct.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                backupFiles.add(backupMainZct);

                if (!ZipUtils.zipDir(mainZctFile.getAbsolutePath(), tempDir.toString())) {
                    throw new IOException("Failed to repackage main contact ZIP");
                }

                log.info("Successfully saved merged contact: {}", mainZctFile);

                // Verify what was saved
                pt.omst.rasterlib.Contact verifyContact = pt.omst.rasterlib.contacts.CompressedContact
                        .extractCompressedContact(mainZctFile);
                log.debug("Verification: Saved ZIP contains {} observations",
                        verifyContact.getObservations().size());

                // Step 7: Backup and delete merged contacts
                for (CompressedContact mergeContact : mergeContacts) {
                    File mergeZctFile = mergeContact.getZctFile();
                    File backupMergeZct = new File(mergeZctFile.getParentFile(),
                            mergeZctFile.getName() + ".backup");
                    Files.copy(mergeZctFile.toPath(), backupMergeZct.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    backupFiles.add(backupMergeZct);
                }

                // Register all modified/deleted files to ignore watcher events
                ignoreOwnWritesUntil.put(mainZctFile, System.currentTimeMillis() + 2000); // 2 second window for batch
                for (CompressedContact mergeContact : mergeContacts) {
                    ignoreOwnWritesUntil.put(mergeContact.getZctFile(), System.currentTimeMillis() + 2000);
                }

                // Delete merged contacts
                for (CompressedContact mergeContact : mergeContacts) {
                    Files.delete(mergeContact.getZctFile().toPath());
                    log.info("Deleted merged contact: {}", mergeContact.getZctFile());
                }

                // Step 8: Clean up backups on success
                for (File backup : backupFiles) {
                    try {
                        Files.deleteIfExists(backup.toPath());
                    } catch (Exception e) {
                        log.warn("Failed to delete backup file: {}", backup, e);
                    }
                }

                // Step 9: Update UI on success
                SwingUtilities.invokeLater(() -> {
                    // Remove merged contacts from collection
                    for (CompressedContact mergeContact : mergeContacts) {
                        contactCollection.removeContact(mergeContact.getZctFile());
                    }

                    log.debug("Refreshing main contact in collection: {}", mainZctFile);
                    // Refresh main contact in collection
                    try {
                        contactCollection.refreshContact(mainZctFile);
                    } catch (IOException e) {
                        log.error("Failed to refresh contact {} in collection after grouping", mainZctFile.getName(), e);
                    }

                    // Reload the main contact in the editor if it's currently displayed
                    try {
                        log.debug("Reloading contact in editor from file: {}", mainZctFile);
                        CompressedContact refreshedContact = new CompressedContact(mainZctFile);
                        log.debug("CompressedContact created with {} observations",
                                refreshedContact.getContact().getObservations().size());
                        contactEditor.loadZct(mainZctFile);
                        log.info("Reloaded merged contact in editor with {} observations",
                                refreshedContact.getContact().getObservations().size());
                    } catch (Exception e) {
                        log.error("Failed to reload contact in editor after grouping", e);
                    }

                    // Repaint map
                    slippyMap.repaint();

                    // Update filter panel
                    updateVisibleContacts(true);

                    log.info("Contact grouping completed successfully");
                });

            } catch (Exception e) {
                log.error("Error grouping contacts, rolling back changes", e);

                // Rollback: restore from backups
                for (File backup : backupFiles) {
                    try {
                        String originalPath = backup.getAbsolutePath().replace(".backup", "");
                        Files.copy(backup.toPath(), Path.of(originalPath),
                                StandardCopyOption.REPLACE_EXISTING);
                        Files.deleteIfExists(backup.toPath());
                        log.info("Restored from backup: {}", originalPath);
                    } catch (Exception rollbackEx) {
                        log.error("Failed to restore from backup: {}", backup, rollbackEx);
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    pt.lsts.neptus.util.GuiUtils.errorMessage(
                            slippyMap,
                            "Group Contacts Failed",
                            "Failed to group contacts: " + e.getMessage());
                });
            } finally {
                // Clean up temp directory
                if (tempDir != null) {
                    try {
                        Files.walk(tempDir)
                                .sorted(java.util.Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    } catch (Exception e) {
                        log.warn("Failed to clean up temp directory: {}", tempDir, e);
                    }
                }
            }
        });
    }
    
    /**
     * Reloads all contacts from disk by rescanning the data source directories.
     * Called after bulk operations like legacy marker conversion.
     */
    private void reloadContactsFromDisk() {
        log.info("Reloading contacts from all data sources");
        
        // Clear existing contacts
        for (CompressedContact contact : new ArrayList<>(contactCollection.getAllContacts())) {
            contactCollection.removeContact(contact.getZctFile());
        }
        
        // Reload from all data sources
        for (DataSource dataSource : dataSourceManager.getDataSources()) {
            if (dataSource instanceof FolderDataSource) {
                File folder = ((FolderDataSource) dataSource).getFolder();
                if (folder.exists()) {
                    log.debug("Scanning folder: {}", folder.getAbsolutePath());
                    try {
                        ContactCollection folderContacts = ContactCollection.fromFolder(folder);
                        for (CompressedContact contact : folderContacts.getAllContacts()) {
                            try {
                                contactCollection.addContact(contact.getZctFile());
                            } catch (IOException e) {
                                log.warn("Failed to add contact during reload: {}", contact.getZctFile(), e);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to load contacts from folder: {}", folder, e);
                    }
                }
            }
        }
        
        updateVisibleContacts(true);
        updateStatusBar();
        slippyMap.repaint();
        log.info("Contact reload complete: {} total contacts", contactCollection.getAllContacts().size());
    }

    /**
     * Recursively finds all folders containing marks.dat files.
     * 
     * @param parentFolder The root folder to search from
     * @return List of folders containing marks.dat files
     */
    private static List<File> findFoldersWithMarks(File parentFolder) {
        List<File> folders = new ArrayList<>();
        if (parentFolder == null || !parentFolder.exists() || !parentFolder.isDirectory()) {
            return folders;
        }
        
        File marksFile = new File(parentFolder, "marks.dat");
        if (marksFile.exists()) {
            folders.add(parentFolder);
        }
        
        File[] children = parentFolder.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    folders.addAll(findFoldersWithMarks(child));
                }
            }
        }
        return folders;
    }

    /**
     * Converts legacy marks.dat files to compressed contacts.
     * Shows a folder selection dialog, recursively searches for folders with marks.dat,
     * and converts each one using a separate BackgroundJob.
     * Skips folders that already have .zct files in their contacts/ subfolder.
     */
    public void convertLegacyMarks() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Folder to Convert Legacy Marks");
        
        Window window = SwingUtilities.getWindowAncestor(this);
        int result = fileChooser.showOpenDialog(window);
        
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File rootFolder = fileChooser.getSelectedFile();
        List<File> foldersWithMarks = findFoldersWithMarks(rootFolder);
       
        int jobsSubmitted = 0;
        
        // Submit jobs for each folder
        for (File folder : foldersWithMarks) {
            // Check if already converted
            File contactsFolder = new File(folder, "contacts");
            if (!contactsFolder.exists()) {
                contactsFolder.mkdirs();
            }
            
            final File folderToConvert = folder;
            BackgroundJob conversionJob = new BackgroundJob("Convert " + folder.getName()) {
                @Override
                protected Void doInBackground() throws Exception {
                    updateStatus("Converting markers from " + folderToConvert.getName() + "...");
                    
                    try {
                        Collection<pt.omst.rasterlib.Contact> contacts = 
                            ContactUtils.convertContacts(folderToConvert);
                        setProgress(100);
                        updateStatus("Completed: " + contacts.size() + " contacts");
                        log.info("Converted {} markers from {}", contacts.size(), folderToConvert);
                    } catch (Exception e) {
                        updateStatus("Failed: " + e.getMessage());
                        log.error("Conversion failed for {}", folderToConvert, e);
                        throw e;
                    }
                    
                    return null;
                }
                
                @Override
                protected void done() {
                    super.done();
                    synchronized (TargetManager.this) {
                        activeConversionJobs--;
                        if (activeConversionJobs == 0) {
                            // All conversion jobs complete - reload contacts
                            log.info("All conversion jobs complete, reloading contacts from disk");
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    reloadContactsFromDisk();
                                } catch (Exception e) {
                                    log.error("Failed to reload contacts after conversion", e);
                                }
                            });
                        }
                    }
                }
            };
            
            synchronized (this) {
                activeConversionJobs++;
            }
            JobManager.getInstance().submit(conversionJob);
            jobsSubmitted++;
        }
        
        if (jobsSubmitted > 0) {
            GuiUtils.infoMessage(window, "Conversion Started", 
                String.format("Converting %d folder(s).\n\nCheck status bar for progress.", 
                    jobsSubmitted));
        } else {
            GuiUtils.infoMessage(window, "No Conversion Needed", 
                String.format("Found %d folder(s) with marks.dat, but all have already been converted.", 
                    foldersWithMarks.size()));
        }
    }

    /**
     * Sends all contacts to connected Pulvis Data Manager servers.
     * Creates a BackgroundJob that uploads all contacts with error handling.
     */
    public void sendAllContactsToPulvis() {
        // Check if any Pulvis connections exist
        if (pulvisSynchronizers.isEmpty()) {
            Window window = SwingUtilities.getWindowAncestor(this);
            GuiUtils.infoMessage(window, "No Data Manager Connected", 
                "No Pulvis Data Manager connections found.\n\n" +
                "Please add a Pulvis connection in the data sources panel first.");
            return;
        }
        
        // Get all contact files
        List<CompressedContact> allContacts = contactCollection.getAllContacts();
        
        if (allContacts.isEmpty()) {
            Window window = SwingUtilities.getWindowAncestor(this);
            GuiUtils.infoMessage(window, "No Contacts", 
                "No contacts found to send.");
            return;
        }
        
        // Confirm with user
        Window window = SwingUtilities.getWindowAncestor(this);
        int result = javax.swing.JOptionPane.showConfirmDialog(
            window,
            String.format("Send %d contact(s) to %d Pulvis Data Manager server(s)?\n\n" +
                "This may take some time. Existing contacts will be updated.",
                allContacts.size(), pulvisSynchronizers.size()),
            "Confirm Send All",
            javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE
        );
        
        if (result != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        
        // Create background job
        BackgroundJob uploadJob = new BackgroundJob("Send All to Data Manager") {
            @Override
            protected Void doInBackground() throws Exception {
                int totalContacts = allContacts.size();
                int totalServers = pulvisSynchronizers.size();
                int totalUploads = totalContacts * totalServers;
                int completed = 0;
                
                updateStatus(String.format("Uploading %d contacts to %d server(s)...", 
                    totalContacts, totalServers));
                
                // Track failures per server
                Map<String, List<String>> serverFailures = new java.util.concurrent.ConcurrentHashMap<>();
                
                // Upload to each server
                for (Map.Entry<PulvisDataSource, PulvisContactSynchronizer> entry : 
                        pulvisSynchronizers.entrySet()) {
                    
                    if (isCancelled()) {
                        updateStatus("Cancelled by user");
                        return null;
                    }
                    
                    PulvisDataSource dataSource = entry.getKey();
                    PulvisContactSynchronizer synchronizer = entry.getValue();
                    String serverName = dataSource.getHost() + ":" + dataSource.getPort();
                    List<String> failures = new ArrayList<>();
                    serverFailures.put(serverName, failures);
                    
                    updateStatus(String.format("Uploading to %s...", serverName));
                    
                    // Upload each contact
                    for (int i = 0; i < allContacts.size(); i++) {
                        if (isCancelled()) {
                            updateStatus("Cancelled by user");
                            return null;
                        }
                        
                        CompressedContact contact = allContacts.get(i);
                        File zctFile = contact.getZctFile();
                        
                        try {
                            // Upload synchronously and wait for completion
                            synchronizer.uploadContact(zctFile).get(30, java.util.concurrent.TimeUnit.SECONDS);
                            
                        } catch (java.util.concurrent.TimeoutException e) {
                            String msg = "Upload timeout after 30 seconds";
                            failures.add(contact.getContact().getLabel() + ": " + msg);
                            log.warn("Timeout uploading {} to {}", 
                                contact.getContact().getLabel(), serverName);
                                
                        } catch (Exception e) {
                            String errorMsg = e.getCause() != null ? 
                                e.getCause().getMessage() : e.getMessage();
                            
                            // Ignore "already exists" type errors
                            if (errorMsg != null && 
                                (errorMsg.contains("409") || 
                                 errorMsg.toLowerCase().contains("conflict") ||
                                 errorMsg.toLowerCase().contains("already exists"))) {
                                log.debug("Contact {} already exists on {}, skipping", 
                                    contact.getContact().getLabel(), serverName);
                            } else {
                                failures.add(contact.getContact().getLabel() + ": " + errorMsg);
                                log.error("Error uploading {} to {}: {}", 
                                    contact.getContact().getLabel(), serverName, errorMsg);
                            }
                        }
                        
                        completed++;
                        setProgress((completed * 100) / totalUploads);
                        updateStatus(String.format("Uploaded %d/%d contacts...", 
                            completed, totalUploads));
                    }
                }
                
                // Report results
                SwingUtilities.invokeLater(() -> {
                    int totalFailures = serverFailures.values().stream()
                        .mapToInt(List::size).sum();
                    
                    if (totalFailures == 0) {
                        GuiUtils.infoMessage(window, "Upload Complete", 
                            String.format("Successfully uploaded %d contact(s) to %d server(s).",
                                totalContacts, totalServers));
                    } else {
                        // Show detailed failure report
                        StringBuilder report = new StringBuilder();
                        report.append(String.format("Uploaded with %d failure(s):\n\n", totalFailures));
                        
                        for (Map.Entry<String, List<String>> entry : serverFailures.entrySet()) {
                            if (!entry.getValue().isEmpty()) {
                                report.append(String.format("%s (%d failures):\n", 
                                    entry.getKey(), entry.getValue().size()));
                                for (String failure : entry.getValue()) {
                                    report.append("  • ").append(failure).append("\n");
                                }
                                report.append("\n");
                            }
                        }
                        
                        GuiUtils.errorMessage(window, "Upload Completed with Errors", 
                            report.toString());
                    }
                });
                
                setProgress(100);
                updateStatus("Completed");
                return null;
            }
        };
        
        JobManager.getInstance().submit(uploadJob);
    }

    public void saveContact(final UUID contactId, final File zctFile) {
        log.info("Saving contact {} to Pulvis...", contactId);
        // Upload to all connected Pulvis servers
        IndexedRasterUtils.background(() -> {
            for (PulvisContactSynchronizer synchronizer : pulvisSynchronizers.values()) {
                synchronizer.uploadContact(zctFile);
            }
        });
    }

    /**
     * Main method for testing the layout.
     */
    public static void main(String[] args) {

        GuiUtils.setLookAndFeel();

        JWindow splash = LoadingPanel.showSplashScreen("Loading application...");
        LoadingPanel panel = LoadingPanel.getLoadingPanel(splash);
        panel.setStatus("Checking license...");

        // Check license
        try {
            LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
        } catch (Exception e) {
            log.error("License check failed", e);
            splash.dispose();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    GuiUtils.errorMessage(null, "License Check Failed",
                            "The application license is invalid or missing.\n\n" +
                                    "Error: " + e.getMessage() + "\n\n" +
                                    "Please contact support or check your license activation.");
                });
            } catch (Exception dialogEx) {
                dialogEx.printStackTrace();
            }
            System.exit(1);
        }

        panel.setStatus("Starting application...");

        // Create test time range (last 10 years to tomorrow)
        Instant minTime = Instant.now().minusSeconds(10L * 365 * 86400); // Approximately 10 years
        Instant maxTime = Instant.now().plusSeconds(86400); // 1 day ahead

        TargetManager layout = new TargetManager(minTime, maxTime);

        panel.setStatus("Launching main window...");

        // Create frame manually to set menu bar before making visible
        JFrame frame = new JFrame("Target Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 900);
        frame.setJMenuBar(createMenuBar(frame, layout));
        frame.add(layout);

        // Load preferences after adding to frame but before making visible
        SwingUtilities.invokeLater(() -> {
            layout.loadPreferences();
        });

        GuiUtils.centerOnScreen(frame);
        frame.setVisible(true);
        splash.dispose();

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    layout.savePreferences();
                    layout.close();
                } catch (Exception ex) {
                    log.error("Error closing layout", ex);
                }
            }
        });
    }
}
