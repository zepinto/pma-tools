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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import javax0.license3j.License;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.contacts.ObservationsPanel;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.ZoomableTimeIntervalSelector;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.rasterlib.Contact;

/**
 * Main layout for the map viewer application.
 * Consists of:
 * - Top: DataSourceManagerPanel
 * - Center: SlippyMap (main display area)
 * - Bottom: ZoomableTimeIntervalSelector
 * - East (collapsible): Side panel with ObservationsPanel and contact details form
 */
@Slf4j
@Getter
public class TargetManager extends JPanel implements AutoCloseable {
    
    private final SlippyMap slippyMap;
    private final DataSourceManagerPanel dataSourceManager;
    private final ZoomableTimeIntervalSelector timeSelector;
    private final ObservationsPanel observationsPanel;
    private final ContactDetailsFormPanel contactDetailsPanel;
    
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
    
    /**
     * Creates a new MapViewerLayout with specified time range.
     * 
     * @param minTime Minimum time for the time selector
     * @param maxTime Maximum time for the time selector
     */
    public TargetManager(Instant minTime, Instant maxTime) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Initialize components
        dataSourceManager = new DataSourceManagerPanel();
        slippyMap = new SlippyMap();
        timeSelector = new ZoomableTimeIntervalSelector(minTime, maxTime);
        observationsPanel = new ObservationsPanel();
        contactDetailsPanel = new ContactDetailsFormPanel();
        
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
    }
    
    /**
     * Creates the east panel containing observations and contact details.
     */
    private JPanel createEastPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        // Create vertical split pane for observations (top) and details (bottom)
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setTopComponent(createObservationsSection());
        verticalSplit.setBottomComponent(createContactDetailsSection());
        verticalSplit.setResizeWeight(0.4); // Give 40% to observations, 60% to details
        verticalSplit.setContinuousLayout(true);
        
        panel.add(verticalSplit, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * Creates the observations section with a titled border.
     */
    private JPanel createObservationsSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
        
        JScrollPane scrollPane = new JScrollPane(observationsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * Creates the contact details section with a titled border.
     */
    private JPanel createContactDetailsSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));        
        
        JScrollPane scrollPane = new JScrollPane(contactDetailsPanel);
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
    public void setContact(Contact contact) {
        contactDetailsPanel.setContact(contact);
    }
    
    /**
     * Clears all observations from the observations panel.
     */
    public void clearObservations() {
        observationsPanel.clear();
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
        }
        catch (Exception e) {
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
        log.info("MapViewerLayout closed");
    }
    
    /**
     * Main method for testing the layout.
     */
    public static void main(String[] args) {
        // Check license
        try {
            LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
        } catch (Exception e) {
            log.error("License check failed", e);
            System.exit(1);
        }
        
        GuiUtils.setTheme("light");
        GuiUtils.setLookAndFeel();
        
        // Create test time range (last 7 days)
        Instant minTime = Instant.now().minusSeconds(7 * 86400);
        Instant maxTime = Instant.now();
        
        TargetManager layout = new TargetManager(minTime, maxTime);
        
        // Create frame manually to set menu bar before making visible
        JFrame frame = new JFrame("Target Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 900);
        frame.setJMenuBar(createMenuBar(frame));
        frame.add(layout);
        GuiUtils.centerOnScreen(frame);
        frame.setVisible(true);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    layout.close();
                } catch (Exception ex) {
                    log.error("Error closing layout", ex);
                }
            }
        });
    }
}
