//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import javax0.license3j.License;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.LoadingPanel;
import pt.omst.gui.ZoomableTimeIntervalSelector;
import pt.omst.gui.datasource.DataSourceEvent;
import pt.omst.gui.datasource.DataSourceListener;
import pt.omst.gui.datasource.FolderDataSource;
import pt.omst.gui.datasource.PulvisConnection;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.util.GuiUtils;
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
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        contactCollection = new ContactCollection();

        // Initialize components
        dataSourceManager = new DataSourceManagerPanel();
        dataSourceManager.addDataSourceListener(this);

        slippyMap = new SlippyMap();

        timeSelector = new ZoomableTimeIntervalSelector(minTime, maxTime);

        contactsMapOverlay = new ContactsMapOverlay(contactCollection);
        slippyMap.addMapOverlay(contactsMapOverlay);
        contactsMapOverlay.setContactSelectionListener(contact -> {
            log.info("Contact selected: {}", contact.getContact().getLabel());
            setContact(contact);
        });
        
        contactEditor = new VerticalContactEditor();
        // observationsPanel = new ObservationsPanel();
        // contactDetailsPanel = new ContactDetailsFormPanel();

        // Create top panel with data source manager
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(dataSourceManager, BorderLayout.CENTER);

        // Create bottom panel with time selector
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(timeSelector, BorderLayout.CENTER);

        // Create east panel (collapsible side panel)
        eastPanel = createEastPanel();
        eastPanel.setPreferredSize(new Dimension(400, 600));
        eastPanel.setMinimumSize(new Dimension(300, 400));

        // Create center panel with map and toggle button
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(slippyMap, BorderLayout.CENTER);

        // Create main split pane (horizontal split between center and east)
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(centerPanel);
        mainSplitPane.setRightComponent(eastPanel);
        mainSplitPane.setResizeWeight(0.7); // Give 70% space to map
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setContinuousLayout(true);

        // Create toggle button for side panel (after split pane is created)
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

        JPanel toggleButtonPanel = new JPanel(new BorderLayout());
        toggleButtonPanel.add(toggleEastPanelButton, BorderLayout.NORTH);
        centerPanel.add(toggleButtonPanel, BorderLayout.EAST);

        // Assemble main layout
        add(topPanel, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        loadPreferences();

        SwingUtilities.invokeLater(() -> {

            timeSelector.addPropertyChangeListener("selection", evt -> {
                Instant[] selection = (Instant[]) evt.getNewValue();
                Instant startTime = selection[0];
                Instant endTime = selection[1];
                contactCollection.applyFilters(
                        new QuadTree.Region(slippyMap.getVisibleBounds()),
                        startTime,
                        endTime);
                log.info("Selection changed: {} to {}", startTime, endTime);
            });

            slippyMap.addPropertyChangeListener("visibleBounds", evt -> {
                // When map bounds change, re-apply filters to update contacts
                Instant startTime = timeSelector.getSelectedStartTime();
                Instant endTime = timeSelector.getSelectedEndTime();
                contactCollection.applyFilters(
                        new QuadTree.Region(slippyMap.getVisibleBounds()),
                        startTime,
                        endTime);
                log.info("Map bounds changed, re-applied contact filters");
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

    public void savePreferences() {
        // store divider location and data sources
        Preferences prefs = Preferences.userNodeForPackage(TargetManager.class);

        // Save divider location
        int dividerLocation = mainSplitPane.getDividerLocation();
        prefs.putInt("mainSplitPane.dividerLocation", dividerLocation);
        log.debug("Saved divider location: {}", dividerLocation);

        // Save side panel visibility
        prefs.putBoolean("eastPanel.visible", eastPanelVisible);
        log.debug("Saved side panel visibility: {}", eastPanelVisible);

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

        // Load divider location (default to -1 which means use default)
        int dividerLocation = prefs.getInt("mainSplitPane.dividerLocation", -1);
        if (dividerLocation > 0) {
            // Defer setting divider location until after component is visible
            javax.swing.SwingUtilities.invokeLater(() -> {
                mainSplitPane.setDividerLocation(dividerLocation);
                log.debug("Loaded divider location: {}", dividerLocation);
            });
        }

        // Load side panel visibility (default to true)
        boolean visible = prefs.getBoolean("eastPanel.visible", true);
        log.debug("Loaded side panel visibility: {}", visible);
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (visible && !eastPanelVisible) {
                showContactDetails();
            } else if (!visible && eastPanelVisible) {
                hideContactDetails();
            }
        });

        // Load time selection
        long startTimeMillis = prefs.getLong("timeSelector.startTime", -1);
        long endTimeMillis = prefs.getLong("timeSelector.endTime", -1);
        if (startTimeMillis > 0 && endTimeMillis > 0) {
            Instant startTime = Instant.ofEpochMilli(startTimeMillis);
            Instant endTime = Instant.ofEpochMilli(endTimeMillis);
            log.debug("Loaded time selection: {} to {}", startTime, endTime);
            javax.swing.SwingUtilities.invokeLater(() -> {
                timeSelector.setSelectedInterval(startTime, endTime);
            });
        }

        try {
            dataSourceManager.loadFromPreferences();
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

    private JPanel createContactEditorSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JScrollPane scrollPane = new JScrollPane(contactEditor);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
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
    public static JMenuBar createMenuBar(JFrame frame) {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

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
            }
            case PulvisConnection pcs -> {
                log.info("Pulvis connection added: {}", pcs.getDisplayName());
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
                break;
            }
            case PulvisConnection pcs -> {
                log.info("Pulvis connection removed: {}", pcs.getDisplayName());
                break;
            }
            default -> {
                break;
            }
        }
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
        frame.setJMenuBar(createMenuBar(frame));
        frame.add(layout);
        layout.loadPreferences();
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
