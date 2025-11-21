//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.swing.JFileChooser;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import javax0.license3j.License;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.contacts.ContactUtils;
import pt.omst.contacts.services.ContactFileWatcherService;
import pt.omst.contacts.services.PulvisConnectionManager;
import pt.omst.contacts.services.TargetManagerPreferences;
import pt.omst.contacts.services.TargetManagerPreferences.BooleanHolder;
import pt.omst.contacts.browser.editor.VerticalContactEditor;
import pt.omst.contacts.browser.filtering.ContactFilterListener;
import pt.omst.contacts.browser.filtering.ContactFilterPanel;
import pt.omst.contacts.reports.GenerateReportDialog;
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
public class TargetManager extends JPanel implements AutoCloseable, DataSourceListener, ContactGroupingHandler {

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
    private final BooleanHolder eastPanelVisible = new BooleanHolder(true);
    private final JPanel statusBar;
    private final JLabel totalContactsLabel;
    private final JLabel visibleContactsLabel;
    private final TaskStatusIndicator taskStatusIndicator;
    private boolean firstPaintDone = false;
    private final PulvisConnectionManager pulvisConnectionManager;
    private final ContactFileWatcherService fileWatcherService;
    private final TargetManagerPreferences preferencesManager;

    // West panel (filter panel)
    private final ContactFilterPanel filterPanel;
    private final JPanel westPanel;
    private final JSplitPane outerSplitPane;
    private final JButton toggleWestPanelButton;
    private final BooleanHolder westPanelVisible = new BooleanHolder(false);

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
        pulvisConnectionManager = new PulvisConnectionManager(contactCollection);
        fileWatcherService = new ContactFileWatcherService(contactCollection);

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

        // Listen for contact saves to refresh the map overlay
        contactEditor.addSaveListener((contactId, zctFile) -> {
            log.info("Contact saved: {}, refreshing map overlay", contactId);

            // Register this as our own write to ignore file watcher event
            fileWatcherService.ignoreFileTemporarily(zctFile);

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
        taskStatusIndicator.setBorder(new EmptyBorder(0, 0, 0, 0));

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
            westPanelVisible.set(!westPanelVisible.get());
            if (westPanelVisible.get()) {
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
            eastPanelVisible.set(!eastPanelVisible.get());
            if (eastPanelVisible.get()) {
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

        // Initialize preferences manager (must be done after all UI components are
        // created)
        preferencesManager = new TargetManagerPreferences(
                mainSplitPane,
                outerSplitPane,
                filterPanel,
                timeSelector,
                dataSourceManager,
                fileWatcherService,
                eastPanelVisible,
                westPanelVisible,
                () -> updateVisibleContacts(true),
                this::showContactDetails,
                this::hideContactDetails,
                this::showFilters);

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
        eastPanelVisible.set(true);
        mainSplitPane.setRightComponent(eastPanel);
        mainSplitPane.setDividerLocation(0.7);
        toggleEastPanelButton.setText("▸");
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    public void hideContactDetails() {
        eastPanelVisible.set(false);
        mainSplitPane.setRightComponent(null);
        toggleEastPanelButton.setText("◂");
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    public void showFilters() {
        westPanelVisible.set(true);
        outerSplitPane.setLeftComponent(westPanel);
        outerSplitPane.setDividerLocation(300); // Default width for filter panel
        toggleWestPanelButton.setText("◂");
        outerSplitPane.revalidate();
        outerSplitPane.repaint();
    }

    public void hideFilters() {
        westPanelVisible.set(false);
        outerSplitPane.setLeftComponent(null);
        toggleWestPanelButton.setText("▸");
        outerSplitPane.revalidate();
        outerSplitPane.repaint();
    }

    public void savePreferences() {
        preferencesManager.savePreferences();
    }

    public void loadPreferences() {
        preferencesManager.loadPreferences();
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
        panel.setBorder(new EmptyBorder(2, 2, 0, 2));
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
        pulvisConnectionManager.connectToServer(pcs);
    }

    /**
     * Disconnects from a Pulvis server.
     * 
     * @param pcs the Pulvis connection to disconnect
     */
    private void disconnectFromPulvis(PulvisDataSource pcs) {
        pulvisConnectionManager.disconnectFromServer(pcs);
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
     * Shows the About dialog with version and copyright information.
     * 
     * @param parent Parent frame for the dialog
     */
    public static void showAboutDialog(JFrame parent) {
        String version = "2025.11.00 (Beta)";
        String message = "<html><body style='width: 300px; padding: 10px;'>" +
                "<h2 style='margin: 0; color: #2c3e50;'>TargetManager</h2>" +
                "<p style='margin: 10px 0;'><b>Version:</b> " + version + "</p>" +
                "<p style='margin: 10px 0;'><b>Copyright:</b> © 2025<br>" +
                "OceanScan - Marine Systems & Technology, Lda.</p>" +
                "<hr style='margin: 10px 0;'>" +
                "<p style='margin: 10px 0; font-size: 10px;'>" +
                "Professional desktop application for managing and<br>" +
                "visualizing marine contact data from sidescan sonar surveys." +
                "</p>" +
                "<p style='margin: 10px 0; font-size: 10px;'>" +
                "<b>Website:</b> <a href='https://www.oceanscan-mst.com'>www.oceanscan-mst.com</a><br>" +
                "<b>Email:</b> support@omst.pt" +
                "</p>" +
                "</body></html>";

        JEditorPane editorPane = new JEditorPane("text/html", message);
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    log.error("Failed to open link", ex);
                }
            }
        });

        JOptionPane.showMessageDialog(
                parent,
                editorPane,
                "About TargetManager",
                JOptionPane.INFORMATION_MESSAGE);
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
        autoRefreshItem.setSelected(targetManager != null && targetManager.fileWatcherService.isAutoRefreshEnabled());
        autoRefreshItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (targetManager != null) {
                    targetManager.fileWatcherService.setAutoRefreshEnabled(autoRefreshItem.isSelected());
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

        // Username menu item
        JMenuItem usernameItem = new JMenuItem("Set Username...");
        usernameItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pt.omst.util.UserPreferencesDialog.showDialog(frame);
            }
        });
        preferencesMenu.add(usernameItem);
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
                    // Ensure templates directory exists
                    java.io.File templatesDir = new java.io.File("conf/templates");
                    if (!templatesDir.exists() || templatesDir.listFiles() == null
                            || templatesDir.listFiles().length == 0) {
                        // Extract default templates
                        pt.omst.contacts.reports.ContactReportGenerator.extractDefaultTemplates();
                    }

                    // Show file chooser for template selection
                    javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser(templatesDir);
                    fileChooser.setDialogTitle("Select Template to Edit");
                    fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
                    javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
                            "HTML Templates", "html");
                    fileChooser.setFileFilter(filter);

                    int result = fileChooser.showOpenDialog(frame);
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        java.io.File selectedTemplate = fileChooser.getSelectedFile();

                        GuiUtils.infoMessage(frame, "Template Editor",
                                "Opening template in system editor.\n\n" +
                                        "Note: Changes are saved directly to the template.\n" +
                                        "The changes will take effect immediately.\n\n" +
                                        "File location: " + selectedTemplate.getAbsolutePath());

                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                            if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
                                desktop.edit(selectedTemplate);
                            } else if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                                desktop.open(selectedTemplate);
                            } else {
                                GuiUtils.errorMessage(frame, "Not Supported",
                                        "Desktop edit operation not supported on this system.\n" +
                                                "Please open the file manually: " + selectedTemplate.getAbsolutePath());
                            }
                        } else {
                            GuiUtils.errorMessage(frame, "Not Supported",
                                    "Desktop operations not supported on this system.\n" +
                                            "Please open the file manually: " + selectedTemplate.getAbsolutePath());
                        }
                    }
                } catch (Exception ex) {
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

        // User Manual menu item (F1)
        JMenuItem manualItem = new JMenuItem("User Manual");
        manualItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        manualItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pt.omst.contacts.help.ManualViewerDialog.showManual(frame);
            }
        });
        helpMenu.add(manualItem);
        helpMenu.addSeparator();

        // Software License menu item
        JMenuItem licenseItem = new JMenuItem("Software License");
        licenseItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showLicenseDialog(frame);
            }
        });
        helpMenu.add(licenseItem);
        helpMenu.addSeparator();

        // About menu item
        JMenuItem aboutItem = new JMenuItem("About TargetManager");
        aboutItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAboutDialog(frame);
            }
        });
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        return menuBar;
    }

    @Override
    public void close() throws Exception {
        // Close file watcher
        fileWatcherService.stopWatching();

        // Close all Pulvis connections
        pulvisConnectionManager.closeAll();

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
                try {
                    fileWatcherService.addFolder(cds.getFolder());
                } catch (IOException e) {
                    log.error("Failed to add folder to file watcher", e);
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
                fileWatcherService.removeFolder(cds.getFolder());
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
                fileWatcherService.ignoreFileTemporarily(mainZctFile, 2000); // 2 second window for batch
                for (CompressedContact mergeContact : mergeContacts) {
                    fileWatcherService.ignoreFileTemporarily(mergeContact.getZctFile(), 2000);
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
                        log.error("Failed to refresh contact {} in collection after grouping", mainZctFile.getName(),
                                e);
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
     * Shows a folder selection dialog, recursively searches for folders with
     * marks.dat,
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
                        Collection<pt.omst.rasterlib.Contact> contacts = ContactUtils.convertContacts(folderToConvert);
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
                    fileWatcherService.decrementConversionJobs();
                    if (fileWatcherService.getActiveConversionJobs() == 0) {
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
            };

            fileWatcherService.incrementConversionJobs();
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
        Window window = SwingUtilities.getWindowAncestor(this);
        pulvisConnectionManager.sendAllContactsToPulvis(window);
    }

    public void saveContact(final UUID contactId, final File zctFile) {
        pulvisConnectionManager.saveContact(contactId, zctFile);
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
