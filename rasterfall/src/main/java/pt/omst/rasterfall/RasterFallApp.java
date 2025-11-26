//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import javax0.license3j.License;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.gui.LoadingPanel;
import pt.omst.gui.datasource.RasterfallDataSource;
import pt.omst.gui.jobs.TaskStatusIndicator;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.rasterfall.map.MapViewer;
import pt.omst.rasterlib.IndexedRasterCreator;
import pt.omst.rasterlib.contacts.ContactCollection;
import pt.omst.rasterlib.gui.RasterFolderChooser;

@Slf4j
public class RasterFallApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private RasterfallPanel rasterfallPanel;
    private WelcomePanel welcomePanel;
    private final TaskStatusIndicator statusIndicator;
    // Singleton ContactManager tracking
    private static JFrame mapViewerFrame;
    private static MapViewer mapViewer;
    private JMenuItem openLogFolderItem;

    public RasterFallApp() {
        setTitle("Sidescan RasterFall");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Set application icon (fixes Windows Task Manager icon)
        setApplicationIcon();

        // Show welcome panel initially
        welcomePanel = new WelcomePanel();
        add(welcomePanel, BorderLayout.CENTER);
        statusIndicator = new TaskStatusIndicator(this);
        setupMenubar();
    }
    
    /**
     * Sets the application icon for the window and task manager.
     * Loads multiple icon sizes for optimal display quality.
     */
    private void setApplicationIcon() {
        try {
            List<Image> icons = new ArrayList<>();
            // Load multiple sizes for better quality at different scales
            String[] sizes = {"16", "32", "48", "64", "128", "256"};
            
            for (String size : sizes) {
                InputStream is = getClass().getResourceAsStream("/icons/rasterfall-" + size + ".png");
                if (is != null) {
                    Image img = ImageIO.read(is);
                    if (img != null) {
                        icons.add(img);
                    }
                    is.close();
                } else {
                    log.warn("Icon resource not found: /icons/rasterfall-{}.png", size);
                }
            }
            
            if (!icons.isEmpty()) {
                setIconImages(icons);
                log.info("Loaded {} application icon sizes", icons.size());
            } else {
                log.warn("No application icons found in resources");
            }
        } catch (Exception e) {
            log.error("Failed to load application icons", e);
        }
    }

    private void setupMenubar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        // Open folder menu item
        JMenuItem openFolderItem = new JMenuItem("Open Folder...");
        openFolderItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFolder();
            }
        });

        fileMenu.add(openFolderItem);
        fileMenu.addSeparator();

        // Preferences submenu
        JMenu preferencesMenu = new JMenu("Preferences");

        // Mission Preferences menu item
        JMenuItem missionPrefsItem = new JMenuItem("Mission Preferences...");
        missionPrefsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editMissionPrefs();
            }
        });
        preferencesMenu.add(missionPrefsItem);
        preferencesMenu.addSeparator();

        // Dark Mode toggle
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.setSelected(GuiUtils.isDarkTheme());
        darkModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean isDark = darkModeItem.isSelected();
                GuiUtils.setTheme(isDark ? "dark" : "light");
            }
        });
        preferencesMenu.add(darkModeItem);
        preferencesMenu.addSeparator();

        // Rendering Quality toggle
        JCheckBoxMenuItem renderQualityItem = new JCheckBoxMenuItem("High Quality Rendering");
        renderQualityItem.setSelected(RasterfallPreferences.isRenderQuality());
        renderQualityItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean quality = renderQualityItem.isSelected();
                RasterfallPreferences.setRenderQuality(quality);
                // Repaint the waterfall panel if it exists
                if (rasterfallPanel != null) {
                    rasterfallPanel.repaint();
                }
            }
        });
        preferencesMenu.add(renderQualityItem);

        fileMenu.add(preferencesMenu);
        fileMenu.addSeparator();

        // Exit menu item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // Tools menu
        JMenu toolsMenu = new JMenu("Tools");

        // Map Viewer menu item
        JMenuItem mapViewerItem = new JMenuItem("Map Viewer...");
        mapViewerItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openMapViewer();
            }
        });

        toolsMenu.add(mapViewerItem);

        openLogFolderItem = new JMenuItem("Browse Folder");
        openLogFolderItem.setEnabled(false); // Disabled until folder is loaded
        openLogFolderItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openCurrentLogFolder();
            }
        });
        toolsMenu.add(openLogFolderItem);

        menuBar.add(toolsMenu);
        // Help menu
        JMenu helpMenu = new JMenu("Help");

        // Software License menu item
        JMenuItem licenseItem = new JMenuItem("Software License");
        licenseItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showLicenseDialog();
            }
        });

        helpMenu.add(licenseItem);

        menuBar.add(helpMenu);

        // Add horizontal glue to push status indicator to the right
        menuBar.add(Box.createHorizontalGlue());

        // Ensure the status indicator has proper size
        statusIndicator.setPreferredSize(new java.awt.Dimension(30, 20));
        statusIndicator.setMinimumSize(new java.awt.Dimension(30, 20));
        statusIndicator.setMaximumSize(new java.awt.Dimension(30, 20));
        statusIndicator.setVisible(true);
        menuBar.add(statusIndicator);

        setJMenuBar(menuBar);
    }

    private void showLicenseDialog() {
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
            e.printStackTrace();
        }

        licenseFrame.add(panel);
        licenseFrame.pack();
        licenseFrame.setLocationRelativeTo(this);
        licenseFrame.setVisible(true);
    }

    private void openCurrentLogFolder() {
        if (rasterfallPanel == null) {
            return;
        }

        try {
            File logFolder = rasterfallPanel.getWaterfall().getRastersFolder();
            if (logFolder != null && logFolder.exists() && logFolder.isDirectory()) {
                java.awt.Desktop.getDesktop().open(logFolder);
            } else {
                GuiUtils.errorMessage(this, "Error", "Log folder not found or invalid.");
            }
        } catch (Exception ex) {
            GuiUtils.errorMessage(this, "Error", "Failed to open log folder: " + ex.getMessage());
        }
    }

    private void openMapViewer() {
        // Check if MapViewer is already open (singleton pattern)
        if (mapViewerFrame != null && mapViewerFrame.isDisplayable()) {
            // Bring existing window to front
            mapViewerFrame.toFront();
            mapViewerFrame.requestFocus();
            return;
        }

        try {
            // Get or create ContactCollection
            ContactCollection contacts;
            RasterfallTiles waterfall = null;

            if (rasterfallPanel != null) {
                try {
                    waterfall = rasterfallPanel.getWaterfall();
                    contacts = waterfall.getContacts();
                } catch (Exception ex) {
                    System.err.println("Warning: Could not load contacts from waterfall: " + ex.getMessage());
                    contacts = new ContactCollection();
                    rasterfallPanel.addInteractionListener(mapViewer);
                }
            } else {
                // No folder loaded yet, create empty collection
                contacts = new ContactCollection();
            }

            // Create SimpleContactViewer
            mapViewer = new MapViewer(contacts);
            if (waterfall != null) {
                mapViewer.loadWaterfall(waterfall);
            }

            // Create and setup frame
            mapViewerFrame = new JFrame("Map Viewer");
            mapViewerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            mapViewerFrame.setSize(1400, 900);
            mapViewerFrame.setJMenuBar(MapViewer.createMenuBar(mapViewerFrame, mapViewer));
            mapViewerFrame.add(mapViewer);

            // Load preferences
            SwingUtilities.invokeLater(() -> {
                mapViewer.loadPreferences();
            });

            // Cleanup when window closes
            mapViewerFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    try {
                        mapViewer.savePreferences();
                        mapViewer.close();
                    } catch (Exception ex) {
                        System.err.println("Error closing Map Viewer: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }

                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    // Clear singleton references to allow reopening
                    mapViewerFrame = null;
                    mapViewer = null;
                }
            });

            // Center and show
            GuiUtils.centerOnScreen(mapViewerFrame);
            mapViewerFrame.setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            GuiUtils.errorMessage(this, "Error",
                    "Failed to open Map Viewer: " + ex.getMessage());
        }
    }

    private void openFolder() {
        RasterFolderChooser fileChooser = new RasterFolderChooser();

        Preferences prefs = Preferences.userNodeForPackage(RasterFallApp.class);
        String lastDir = prefs.get("lastRasterFolder", null);
        if (lastDir != null) {
            File lastFolder = new File(lastDir);
            if (lastFolder.exists() && lastFolder.isDirectory()) {
                fileChooser.setCurrentDirectory(lastFolder);
            }
        }

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            prefs.put("lastRasterFolder", selectedFolder.getAbsolutePath());
            loadRasterFolder(selectedFolder);
        }
    }

    private boolean editMissionPrefs() {
        return MissionPreferencesDialog.showDialog(this);
    }

    private void loadRasterFolder(File folder) {
        
         final JWindow splash = LoadingPanel.showSplashScreen("Loading raster data...", this);
        final LoadingPanel loadingPanel = LoadingPanel.getLoadingPanel(splash);

       
        
        // Show loading splash screen centered on this window
       

        // Perform loading in background thread to keep UI responsive
        new Thread(() -> {
            try {
                // Give splash screen time to render and start animation
                Thread.sleep(150);

                // Close existing panel if one is open
                if (rasterfallPanel != null) {
                    if (loadingPanel != null) {
                        SwingUtilities.invokeLater(() -> loadingPanel.setStatus("Closing previous session..."));
                    }
                    SwingUtilities.invokeLater(() -> {
                        remove(rasterfallPanel);
                        try {
                            rasterfallPanel.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    Thread.sleep(100);
                }

                // Remove welcome panel if it's showing
                if (welcomePanel != null) {
                    SwingUtilities.invokeLater(() -> remove(welcomePanel));
                }

                // Create progress callback that updates splash screen
                Consumer<String> progressCallback = (String message) -> {
                    if (loadingPanel != null) {
                        SwingUtilities.invokeLater(() -> loadingPanel.setStatus(message));
                    }
                    System.out.println("Loading: " + message);
                };

                List<File> files = RasterfallTiles.findRasterFiles(folder);
                File openedFile = folder;
                if (files.isEmpty()) {
                    log.warn("No raster files found in folder: " + folder.getAbsolutePath());
                    progressCallback.accept("Exporting rasters from " + folder.getAbsolutePath());
                    IndexedRasterCreator.exportRasters(folder, 0,
                            progressCallback == null ? null : msg -> {
                                progressCallback.accept(msg);
                            });
                    files = RasterfallTiles.findRasterFiles(openedFile);
                }
                
                progressCallback.accept("Found " + files.size() + " raster files.");

                if (!openedFile.getName().equals("rasterIndex"))
                    openedFile = new File(folder, "rasterIndex");
                // Create new rasterfall panel with selected folder (in background)
                if (loadingPanel != null) {
                    SwingUtilities.invokeLater(() -> loadingPanel.setStatus("Initializing rasterfall panel..."));
                }
                log.info("Loading raster folder: " + openedFile.getAbsolutePath());

                RasterfallPanel newPanel = new RasterfallPanel(openedFile, progressCallback);

                // Add panel to UI on EDT
                SwingUtilities.invokeLater(() -> {
                    rasterfallPanel = newPanel;
                    add(rasterfallPanel, BorderLayout.CENTER);

                    // Update window title
                    setTitle("Sidescan RasterFall - " + folder.getName());

                    // Enable "Open Log Folder" menu item
                    if (openLogFolderItem != null) {
                        openLogFolderItem.setEnabled(true);
                    }

                    // Sync contacts to open MapViewer if it exists
                    syncContactsToViewer();

                    // Refresh the display
                    if (loadingPanel != null)
                        loadingPanel.setStatus("Finalizing...");
                    revalidate();
                    repaint();

                    // Close splash screen after a brief delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            SwingUtilities.invokeLater(() -> LoadingPanel.hideSplashScreen(splash));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    LoadingPanel.hideSplashScreen(splash);
                    JOptionPane.showMessageDialog(this,
                            "Error loading raster folder: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();

        if (!editMissionPrefs()) {
            return; // User cancelled mission preferences
        }

         // Force the splash screen to render before starting heavy work
        splash.toFront();
        splash.repaint();


    }

    /**
     * Syncs the contacts from the currently loaded folder to the open MapViewer
     * viewer. If no
     * MapViewer is open or no contacts are loaded, this method does nothing.
     */
    private void syncContactsToViewer() {
        if (mapViewer != null && rasterfallPanel != null) {
            try {
                log.info("Syncing contacts and waterfall to MapViewer...");
                // Load the waterfall path into the map viewer
                mapViewer.loadWaterfall(rasterfallPanel.getWaterfall());
                mapViewer.setRasterfallDataSource(new RasterfallDataSource(rasterfallPanel.getWaterfall().getContactsFolder()));
                mapViewer.refresh();
                System.out.println("Synced contacts and waterfall to MapViewer");
            } catch (Exception e) {
                System.err.println("Error syncing contacts to viewer: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the currently open MapViewer viewer instance, if any.
     * 
     * @return the MapViewer instance, or null if not open
     */
    public static MapViewer getMapViewer() {
        return mapViewer;
    }

    /**
     * Checks if the MapViewer is currently open.
     * 
     * @return true if the MapViewer window is open
     */
    public static boolean isMapViewerOpen() {
        return mapViewerFrame != null && mapViewerFrame.isDisplayable();
    }

    public static void main(String[] args) {
        // Show splash screen during startup
        JWindow splash = LoadingPanel.showSplashScreen("Starting Sidescan RasterFall...");
        LoadingPanel loadingPanel = LoadingPanel.getLoadingPanel(splash);

        // Initialize application in background
        new Thread(() -> {
            try {
                if (loadingPanel != null)
                    loadingPanel.setStatus("Setting look and feel...");
                GuiUtils.setLookAndFeel();

                if (loadingPanel != null)
                    loadingPanel.setStatus("Checking license...");
                try {
                    LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    LoadingPanel.hideSplashScreen(splash);
                    try {
                        SwingUtilities.invokeAndWait(() -> {
                            GuiUtils.errorMessage(null, "License Check Failed",
                                    "The application license is invalid or missing.\n\n" +
                                            "Error: " + ex.getMessage() + "\n\n" +
                                            "Please contact support or check your license activation.");
                        });
                    } catch (Exception dialogEx) {
                        dialogEx.printStackTrace();
                    }
                    System.exit(1);
                    return;
                }

                if (loadingPanel != null)
                    loadingPanel.setStatus("Initializing application...");
                Thread.sleep(500); // Brief delay for smoother experience

                SwingUtilities.invokeLater(() -> {
                    RasterFallApp app = new RasterFallApp();
                    app.setVisible(true);

                    // Close splash screen
                    LoadingPanel.hideSplashScreen(splash);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    LoadingPanel.hideSplashScreen(splash);
                    JOptionPane.showMessageDialog(null,
                            "Application initialization failed: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
                System.exit(1);
            }
        }).start();
    }

}
