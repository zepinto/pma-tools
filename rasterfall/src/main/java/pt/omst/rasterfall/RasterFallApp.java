package pt.omst.rasterfall;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import javax0.license3j.License;
import pt.omst.licences.LicenseChecker;
import pt.omst.licences.LicensePanel;
import pt.omst.licences.NeptusLicense;
import pt.omst.neptus.util.GuiUtils;

public class RasterFallApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private RasterfallPanel rasterfallPanel;

    public RasterFallApp() {
        setTitle("Sidescan RasterFall");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        setupMenubar();
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
        
        // Exit menu item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        menuBar.add(fileMenu);        
        
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        licenseFrame.add(panel);
        licenseFrame.pack();
        licenseFrame.setLocationRelativeTo(this);
        licenseFrame.setVisible(true);
    }
    
    private void openFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Raster Folder");

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
    
    private void loadRasterFolder(File folder) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Close existing panel if one is open
                if (rasterfallPanel != null) {
                    remove(rasterfallPanel);
                    rasterfallPanel.close();
                }
                
                // Create progress callback
                Consumer<String> progressCallback = (String message) -> {
                    // You can add a progress dialog here if needed
                    System.out.println("Loading: " + message);
                };
                
                // Create new rasterfall panel with selected folder
                rasterfallPanel = new RasterfallPanel(folder, progressCallback);
                add(rasterfallPanel, BorderLayout.CENTER);
                
                // Update window title
                setTitle("Sidescan RasterFall - " + folder.getName());
                
                // Refresh the display
                revalidate();
                repaint();
                
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error loading raster folder: " + ex.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        try {
            LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "License check failed: " + ex.getMessage(), 
                "License Error", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }        
        RasterFallApp app = new RasterFallApp();        
        app.setVisible(true);
    }
    
}
