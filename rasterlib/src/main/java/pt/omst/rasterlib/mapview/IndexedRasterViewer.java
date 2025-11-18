//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * A Swing component that displays an IndexedRaster image with interactive sample information.
 * When the user moves the mouse over the image, the corresponding sample details are displayed
 * in a side panel, and a crosshair line highlights the active sample row.
 */
@Slf4j
public class IndexedRasterViewer extends JPanel {

    @Getter
    private final IndexedRaster raster;
    @Getter
    private final BufferedImage image;
    
    private final ImageDisplayPanel imagePanel;
    private final JTextArea detailTextArea;
    private final JLabel statusLabel;
    
    private final Map<Integer, String> sampleTextCache = new HashMap<>();
    private int currentSampleIndex = -1;
    
    private static final DecimalFormat COORD_FORMAT = new DecimalFormat("#.######");
    private static final DecimalFormat METER_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat DEGREE_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat VELOCITY_FORMAT = new DecimalFormat("#.###");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /**
     * Creates a new IndexedRasterViewer.
     * 
     * @param raster the IndexedRaster to display
     * @param image the companion image file
     */
    public IndexedRasterViewer(IndexedRaster raster, BufferedImage image) {
        this.raster = raster;
        this.image = image;
        
        setLayout(new BorderLayout());
        
        // Create image display panel
        imagePanel = new ImageDisplayPanel();
        add(imagePanel, BorderLayout.CENTER);
        
        // Create detail panel on the right
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setPreferredSize(new Dimension(350, 0));
        detailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 5),
            BorderFactory.createTitledBorder("Sample Details")
        ));
        
        detailTextArea = new JTextArea();
        detailTextArea.setEditable(false);
        detailTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        detailTextArea.setText("Move mouse over image to see sample details...");
        
        JScrollPane scrollPane = new JScrollPane(detailTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(detailPanel, BorderLayout.EAST);
        
        // Create status bar at the bottom
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        add(statusLabel, BorderLayout.SOUTH);
        
        updateStatusBar();
    }
    
    /**
     * Updates the status bar with image and sample information.
     */
    private void updateStatusBar() {
        String status = String.format("Image: %dx%d pixels | Samples: %d | Sensor: %s",
            image.getWidth(), image.getHeight(),
            raster.getSamples().size(),
            raster.getSensorInfo().getSensorModel() != null ? 
                raster.getSensorInfo().getSensorModel() : "Unknown");
        
        if (currentSampleIndex >= 0) {
            status = String.format("Sample %d/%d | %s",
                currentSampleIndex + 1,
                raster.getSamples().size(),
                status);
        }
        
        statusLabel.setText(status);
    }
    
    /**
     * Formats sample details into a human-readable string.
     * Uses caching to avoid redundant formatting.
     */
    private String formatSampleDetails(int sampleIndex) {
        if (sampleTextCache.containsKey(sampleIndex)) {
            return sampleTextCache.get(sampleIndex);
        }
        
        SampleDescription sample = raster.getSamples().get(sampleIndex);
        Pose pose = sample.getPose();
        
        StringBuilder sb = new StringBuilder();
        
        // Basic sample info
        sb.append("═══════════════════════════════════\n");
        sb.append("SAMPLE INFORMATION\n");
        sb.append("═══════════════════════════════════\n\n");
        
        if (sample.getIndex() != null) {
            sb.append("Index:       ").append(sample.getIndex()).append("\n");
        }
        
        if (sample.getOffset() != null) {
            sb.append("Offset:      ").append(sample.getOffset()).append(" bytes\n");
        }
        
        if (sample.getTimestamp() != null) {
            sb.append("Timestamp:   ").append(sample.getTimestamp().format(TIME_FORMAT)).append("\n");
        }
        
        sb.append("\n");
        sb.append("───────────────────────────────────\n");
        sb.append("POSITION\n");
        sb.append("───────────────────────────────────\n\n");
        
        // Position
        sb.append("Latitude:    ").append(COORD_FORMAT.format(pose.getLatitude())).append("°\n");
        sb.append("Longitude:   ").append(COORD_FORMAT.format(pose.getLongitude())).append("°\n");
        
        if (pose.getDepth() != null) {
            sb.append("Depth:       ").append(METER_FORMAT.format(pose.getDepth())).append(" m\n");
        }
        
        if (pose.getAltitude() != null) {
            sb.append("Altitude:    ").append(METER_FORMAT.format(pose.getAltitude())).append(" m\n");
        }
        
        if (pose.getHeight() != null) {
            sb.append("Height:      ").append(METER_FORMAT.format(pose.getHeight())).append(" m\n");
        }
        
        if (pose.getHacc() != null) {
            sb.append("H. Accuracy: ").append(METER_FORMAT.format(pose.getHacc())).append(" m\n");
        }
        
        // Orientation
        if (pose.getPhi() != null || pose.getTheta() != null || pose.getPsi() != null) {
            sb.append("\n");
            sb.append("───────────────────────────────────\n");
            sb.append("ORIENTATION (Euler Angles)\n");
            sb.append("───────────────────────────────────\n\n");
            
            if (pose.getPhi() != null) {
                sb.append("Roll (φ):    ").append(DEGREE_FORMAT.format(pose.getPhi())).append("°\n");
            }
            
            if (pose.getTheta() != null) {
                sb.append("Pitch (θ):   ").append(DEGREE_FORMAT.format(pose.getTheta())).append("°\n");
            }
            
            if (pose.getPsi() != null) {
                sb.append("Yaw (ψ):     ").append(DEGREE_FORMAT.format(pose.getPsi())).append("°\n");
            }
        }
        
        // Angular rates
        if (pose.getP() != null || pose.getQ() != null || pose.getR() != null) {
            sb.append("\n");
            sb.append("───────────────────────────────────\n");
            sb.append("ANGULAR RATES\n");
            sb.append("───────────────────────────────────\n\n");
            
            if (pose.getP() != null) {
                sb.append("Roll Rate:   ").append(DEGREE_FORMAT.format(pose.getP())).append("°/s\n");
            }
            
            if (pose.getQ() != null) {
                sb.append("Pitch Rate:  ").append(DEGREE_FORMAT.format(pose.getQ())).append("°/s\n");
            }
            
            if (pose.getR() != null) {
                sb.append("Yaw Rate:    ").append(DEGREE_FORMAT.format(pose.getR())).append("°/s\n");
            }
        }
        
        // Linear velocities
        if (pose.getU() != null || pose.getV() != null || pose.getW() != null) {
            sb.append("\n");
            sb.append("───────────────────────────────────\n");
            sb.append("LINEAR VELOCITIES\n");
            sb.append("───────────────────────────────────\n\n");
            
            if (pose.getU() != null) {
                sb.append("Forward (u): ").append(VELOCITY_FORMAT.format(pose.getU())).append(" m/s\n");
            }
            
            if (pose.getV() != null) {
                sb.append("Lateral (v): ").append(VELOCITY_FORMAT.format(pose.getV())).append(" m/s\n");
            }
            
            if (pose.getW() != null) {
                sb.append("Vertical (w):").append(VELOCITY_FORMAT.format(pose.getW())).append(" m/s\n");
            }
        }
        
        String result = sb.toString();
        sampleTextCache.put(sampleIndex, result);
        return result;
    }
    
    /**
     * Inner class that handles image display with zoom, pan, and mouse interaction.
     */
    private class ImageDisplayPanel extends JPanel {
        
        @Getter @Setter
        private double zoomFactor = 1.0;
        @Getter @Setter
        private int offsetX = 0;
        @Getter @Setter
        private int offsetY = 0;
        
        private Point mouseDragPosition = null;
        private Point mousePosition = null;
        private int crosshairY = -1;
        
        public ImageDisplayPanel() {
            setBackground(Color.DARK_GRAY);
            
            // Mouse wheel for zooming
            addMouseWheelListener(new MouseAdapter() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double delta = 0.1 * e.getPreciseWheelRotation();
                    zoomFactor = Math.max(0.1, zoomFactor - delta);
                    repaint();
                }
            });
            
            // Mouse drag for panning
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    mouseDragPosition = e.getPoint();
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    mousePosition = null;
                    crosshairY = -1;
                    currentSampleIndex = -1;
                    detailTextArea.setText("Move mouse over image to see sample details...");
                    updateStatusBar();
                    repaint();
                }
            });
            
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (mouseDragPosition != null) {
                        int dx = e.getX() - mouseDragPosition.x;
                        int dy = e.getY() - mouseDragPosition.y;
                        offsetX += dx;
                        offsetY += dy;
                        mouseDragPosition = e.getPoint();
                        mousePosition = e.getPoint();
                        updateMousePosition(e.getPoint());
                        repaint();
                    }
                }
                
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePosition = e.getPoint();
                    updateMousePosition(e.getPoint());
                    repaint();
                }
            });
        }
        
        /**
         * Updates the display based on mouse position.
         */
        private void updateMousePosition(Point mousePoint) {
            Point2D.Double imageCoords = screenToImageCoords(new Point2D.Double(mousePoint.x, mousePoint.y));
            
            // Check if mouse is over the image
            if (imageCoords.y < 0 || imageCoords.y > 1.0 || 
                imageCoords.x < 0 || imageCoords.x > 1.0) {
                crosshairY = -1;
                currentSampleIndex = -1;
                detailTextArea.setText("Move mouse over image to see sample details...");
                updateStatusBar();
                return;
            }
            
            // Calculate sample index from Y coordinate
            int sampleIndex = (int) (imageCoords.y * raster.getSamples().size());
            sampleIndex = Math.max(0, Math.min(sampleIndex, raster.getSamples().size() - 1));
            
            if (sampleIndex != currentSampleIndex) {
                currentSampleIndex = sampleIndex;
                detailTextArea.setText(formatSampleDetails(sampleIndex));
                detailTextArea.setCaretPosition(0); // Scroll to top
                updateStatusBar();
            }
            
            // Update crosshair position
            Point2D.Double sampleScreenCoords = imageToScreenCoords(
                new Point2D.Double(0.5, (double) sampleIndex / raster.getSamples().size())
            );
            crosshairY = (int) sampleScreenCoords.y;
        }
        
        /**
         * Converts screen coordinates to normalized image coordinates [0,1].
         */
        private Point2D.Double screenToImageCoords(Point2D.Double screenCoords) {
            int newWidth = (int) (image.getWidth() * zoomFactor);
            int newHeight = (int) (image.getHeight() * zoomFactor);
            
            int x = (getWidth() - newWidth) / 2 + offsetX;
            int y = (getHeight() - newHeight) / 2 + offsetY;
            
            double imgX = (screenCoords.x - x) / (newWidth);
            double imgY = (screenCoords.y - y) / (newHeight);
            
            return new Point2D.Double(imgX, imgY);
        }
        
        /**
         * Converts normalized image coordinates [0,1] to screen coordinates.
         */
        private Point2D.Double imageToScreenCoords(Point2D.Double imageCoords) {
            int newWidth = (int) (image.getWidth() * zoomFactor);
            int newHeight = (int) (image.getHeight() * zoomFactor);
            
            int x = (getWidth() - newWidth) / 2 + offsetX;
            int y = (getHeight() - newHeight) / 2 + offsetY;
            
            double screenX = imageCoords.x * newWidth + x;
            double screenY = imageCoords.y * newHeight + y;
            
            return new Point2D.Double(screenX, screenY);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            
            // Enable high-quality rendering
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Calculate image position and size
            int newWidth = (int) (image.getWidth() * zoomFactor);
            int newHeight = (int) (image.getHeight() * zoomFactor);
            int x = (getWidth() - newWidth) / 2 + offsetX;
            int y = (getHeight() - newHeight) / 2 + offsetY;
            
            // Draw the image
            g2d.drawImage(image, x, y, newWidth, newHeight, null);
            
            // Draw crosshair line at current sample
            if (crosshairY >= 0) {
                g2d.setColor(new Color(255, 0, 0, 128)); // Semi-transparent red
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawLine(x, crosshairY, x + newWidth, crosshairY);
                
                // Draw small indicators at the edges
                g2d.fillRect(x - 5, crosshairY - 2, 5, 5);
                g2d.fillRect(x + newWidth, crosshairY - 2, 5, 5);
            }
            
            // Draw zoom level indicator
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String zoomText = String.format("Zoom: %.0f%%", zoomFactor * 100);
            g2d.drawString(zoomText, 10, getHeight() - 10);
        }
    }
    
    /**
     * Main method for testing the component.
     */
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            // Create file chooser for selecting IndexedRaster JSON file
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select IndexedRaster JSON file");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
                }
                
                @Override
                public String getDescription() {
                    return "IndexedRaster JSON files (*.json)";
                }
            });
            
            int result = fileChooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) {
                System.exit(0);
                return;
            }
            
            File jsonFile = fileChooser.getSelectedFile();
            
            try {
                // Load IndexedRaster from JSON
                log.info("Loading IndexedRaster from {}", jsonFile.getAbsolutePath());
                String jsonContent = Files.readString(jsonFile.toPath());
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(jsonContent);
                
                // Load companion image
                File imageFile = new File(jsonFile.getParentFile(), raster.getFilename());
                log.info("Loading image from {}", imageFile.getAbsolutePath());
                BufferedImage image = ImageIO.read(imageFile);
                
                if (image == null) {
                    JOptionPane.showMessageDialog(null,
                        "Could not load image: " + imageFile.getName(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                    return;
                }
                
                // Create and display viewer
                IndexedRasterViewer viewer = new IndexedRasterViewer(raster, image);
                
                JFrame frame = new JFrame("IndexedRaster Viewer - " + jsonFile.getName());
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(1200, 800);
                frame.setContentPane(viewer);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                
                log.info("IndexedRaster loaded successfully: {} samples, {}x{} pixels",
                    raster.getSamples().size(), image.getWidth(), image.getHeight());
                
            } catch (Exception e) {
                log.error("Error loading IndexedRaster", e);
                JOptionPane.showMessageDialog(null,
                    "Error loading IndexedRaster: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
