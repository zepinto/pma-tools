package pt.omst.rasterfall;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Consumer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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
        setJMenuBar(menuBar);
    }
    
    private void openFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Raster Folder");
        
        // Set default directory if available
        String userHome = System.getProperty("user.home");
        fileChooser.setCurrentDirectory(new File(userHome));
        
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
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
        RasterFallApp app = new RasterFallApp();
        app.setVisible(true);
    }
    
}
