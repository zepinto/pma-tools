//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.prefs.Preferences;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.rasterlib.contacts.ContactObject;

@Slf4j
public class SlippyMap extends JPanel implements AutoCloseable {
    
    private int z = 2;              // Initial zoom level
    private double cx = 512.0;      // Center x at z=2 (mapWidth = 1024)
    private double cy = 512.0;      // Center y at z=2
    private final Map<String, BufferedImage> memoryCache = new HashMap<>(); // In-memory cache
    private final Set<String> pendingTiles = new HashSet<>();
    private final BlockingQueue<String> downloadQueue = new LinkedBlockingQueue<>();
    private double mouseLat = 0.0;  // Current mouse latitude
    private double mouseLon = 0.0;  // Current mouse longitude
    private final File baseCacheDir;    // Base directory for disk cache
    private int clickedPointIndex = -1; // Index of the clicked point (-1 if none)
    private boolean darkMode = false; // Dark mode flag
    private int mouseHoverIndex = -1; // Index of the hovered point (-1 if none)
    private final ArrayList<ContactObject> points = new ArrayList<>(); // List of points to display
    private final BaseMapManager baseMapManager; // Manages tile source selection
    
    public SlippyMap(List<? extends ContactObject> points) {
        this.points.addAll(points);
        setDarkMode(GuiUtils.isDarkTheme());
        
        // Get preferences for zoom and center
        Preferences prefs = Preferences.userNodeForPackage(SlippyMap.class);
        
        // Initialize base map manager
        baseMapManager = new BaseMapManager(newSource -> {
            memoryCache.clear(); // Clear memory cache to reload with new source
            repaint();
        });
        
        // Initialize base cache directory
        baseCacheDir = new File(System.getProperty("user.home"), "tile_cache");
        if (!baseCacheDir.exists()) {
            baseCacheDir.mkdirs();
        }
        
        // Create subdirectories for each tile source
        for (TileSource source : TileSource.values()) {
            File sourceDir = new File(baseCacheDir, source.getCacheName());
            if (!sourceDir.exists()) {
                sourceDir.mkdirs();
            }
        }

        // Start the tile downloader thread
        Thread downloader = new Thread(() -> {
            while (true) {
                String tileKey = null;
                try {
                    tileKey = downloadQueue.take();
                    String[] parts = tileKey.split("/");
                    // tileKey format: "tilesource/z/x/y"
                    String tileSourceName = parts[0];
                    int tz = Integer.parseInt(parts[1]);
                    int tx = Integer.parseInt(parts[2]);
                    int ty = Integer.parseInt(parts[3]);

                    // Check disk cache first
                    File tileFile = new File(baseCacheDir, tileKey + ".png");
                    BufferedImage image = null;
                    if (tileFile.exists()) {
                        image = ImageIO.read(tileFile);
                    } else {
                        // Download tile if not cached
                        String url = baseMapManager.getCurrentTileSource().getTileUrl(tz, tx, ty);
                        URL tileUrl = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) tileUrl.openConnection();
                        conn.setRequestProperty("User-Agent", "SlippyMapWithCoords/1.0 (your.email@example.com)");
                        conn.setInstanceFollowRedirects(true);
                        
                        // Check response code before reading
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            image = ImageIO.read(conn.getInputStream());
                            
                            // Only save to disk cache if image was successfully loaded
                            if (image != null) {
                                tileFile.getParentFile().mkdirs(); // Ensure directory exists
                                ImageIO.write(image, "png", tileFile);
                            }
                        } else {
                            log.warn("Failed to download tile {}: HTTP {}", tileKey, responseCode);
                        }
                        conn.disconnect();
                    }

                    if (image != null) {
                        if (darkMode)
                            image = applyDarkModeFilter(image);
                        memoryCache.put(tileKey, image);
                        SwingUtilities.invokeLater(this::repaint);
                    }
                    
                    pendingTiles.remove(tileKey);
                } catch (IOException | InterruptedException e) {
                    if (tileKey != null) {
                        log.error("Error downloading tile {}: {}", tileKey, e.getMessage());
                        pendingTiles.remove(tileKey);
                    } else {
                        log.error("Error in tile downloader: {}", e.getMessage());
                    }
                }
            }
        });
        downloader.setDaemon(true);
        downloader.start();
        
        // Load saved zoom and center preferences, or initialize from points
        double savedCx = prefs.getDouble("centerX", Double.NaN);
        double savedCy = prefs.getDouble("centerY", Double.NaN);
        int savedZ = prefs.getInt("zoom", -1);
        
        if (!Double.isNaN(savedCx) && !Double.isNaN(savedCy) && savedZ >= 0 && savedZ <= 19) {
            // Use saved preferences
            cx = savedCx;
            cy = savedCy;
            z = savedZ;
            log.info("Loaded saved map view: zoom={}, cx={}, cy={}", z, cx, cy);
        } else {
            // Initialize from points
            initializeMapCenterAndZoom();
            log.info("Initialized map view from points: zoom={}, cx={}, cy={}", z, cx, cy);
        }
        
        // Setup popup menu for map options
        setupPopupMenu();

        // Zoom with mouse wheel
        addMouseWheelListener(e -> {
            int rotation = e.getWheelRotation();
            int width = getWidth();
            int height = getHeight();
            int mouseX = e.getX();
            int mouseY = e.getY();
            if (rotation < 0 && z < 19) { // Zoom in
                double newCx = 2 * cx - (width / 2.0) + mouseX;
                double newCy = 2 * cy - (height / 2.0) + mouseY;
                z++;
                cx = newCx;
                cy = newCy;
                saveViewPreferences();
                repaint();
            } else if (rotation > 0 && z > 0) { // Zoom out
                double newCx = cx / 2.0 + width / 4.0 - mouseX / 2.0;
                double newCy = cy / 2.0 + height / 4.0 - mouseY / 2.0;
                z--;
                cx = newCx;
                cy = newCy;
                saveViewPreferences();
                repaint();
            }
        });

        // Pan with mouse drag and update coordinates
        final int[] lastPos = new int[2];

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Don't handle left clicks if it's a popup trigger
                if (e.isPopupTrigger()) {
                    return;
                }
                lastPos[0] = e.getX();
                lastPos[1] = e.getY();

                // Check for point click
                clickedPointIndex = -1;
                double px = cx - getWidth() / 2.0;
                double py = cy - getHeight() / 2.0;
                for (int i = 0; i < points.size(); i++) {
                    ContactObject point = points.get(i);
                    double[] xy = latLonToPixel(point.getLatitude(), point.getLongitude(), z);
                    int screenX = (int) (xy[0] - px);
                    int screenY = (int) (xy[1] - py);
                    double dist = Math.sqrt(Math.pow(e.getX() - screenX, 2) + Math.pow(e.getY() - screenY, 2));
                    if (dist <= 10) { // Click radius of 10 pixels
                        clickedPointIndex = i;
                        String message = String.format("Point %d: %s\nLat %.5f, Lon %.5f",
                                i, point.getLabel(), point.getLatitude(), point.getLongitude());
                        System.out.println(message);
                        //JOptionPane.showMessageDialog(SlippyMap.this, message,
                        //        "Point Clicked", JOptionPane.INFORMATION_MESSAGE);
                        repaint();
                        break;
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                // Save preferences after drag is complete
                if (!e.isPopupTrigger()) {
                    saveViewPreferences();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                cx -= (x - lastPos[0]);
                cy -= (y - lastPos[1]);
                lastPos[0] = x;
                lastPos[1] = y;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                double[] latLon = pixelToLatLon(cx - getWidth() / 2.0 + e.getX(), cy - getHeight() / 2.0 + e.getY(), z);
                mouseLat = latLon[0];
                mouseLon = latLon[1];
                repaint();

                // Check for point click
                mouseHoverIndex = -1;
                double px = cx - getWidth() / 2.0;
                double py = cy - getHeight() / 2.0;
                for (int i = 0; i < points.size(); i++) {
                    ContactObject point = points.get(i);
                    double[] xy = latLonToPixel(point.getLatitude(), point.getLongitude(), z);
                    int screenX = (int) (xy[0] - px);
                    int screenY = (int) (xy[1] - py);
                    double dist = Math.sqrt(Math.pow(e.getX() - screenX, 2) + Math.pow(e.getY() - screenY, 2));
                    if (dist <= 10) { // Click radius of 10 pixels
                        mouseHoverIndex = i;
                        repaint();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * Setup the popup menu for map options.
     */
    private void setupPopupMenu() {
        baseMapManager.setupPopupMenu(this);
    }
    
    /**
     * Set the tile source and reload tiles.
     */
    public void setTileSource(TileSource source) {
        baseMapManager.setTileSource(source);
    }
    
    /**
     * Get the current tile source.
     */
    public TileSource getTileSource() {
        return baseMapManager.getCurrentTileSource();
    }
    
    /**
     * Save current view (zoom and center) preferences.
     */
    private void saveViewPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(SlippyMap.class);
        prefs.putDouble("centerX", cx);
        prefs.putDouble("centerY", cy);
        prefs.putInt("zoom", z);
    }

    public void focus(double lat, double lon, int zoom) {
        double[] xy = latLonToPixel(lat, lon, zoom);
        cx = xy[0];
        cy = xy[1];
        z = zoom;
        repaint();
        mouseHoverIndex = -1; // Reset hover index
    }

    public void focus(LocationType loc) {
        double lld[] = loc.getAbsoluteLatLonDepth();
        focus(lld[0], lld[1], z);        
    }

    public void addPainter(MapPainter painter) {
        painter.paint((Graphics2D) getGraphics(), this);
    }

    public void focus(ContactObject obj, int zoom) {
        double[] xy = latLonToPixel(obj.getLatitude(), obj.getLongitude(), zoom);
        focus(xy[0], xy[1], zoom);
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        memoryCache.clear(); // Clear cache to reload tiles with new filter
        if (darkMode)
            setBackground(Color.BLACK);
        else
            setBackground(Color.WHITE);
        repaint();

    }

    private void initializeMapCenterAndZoom() {
        if (points.isEmpty()) {
            z = 2;
            cx = 512.0;
            cy = 512.0;
            return;
        }

        // Calculate centroid and bounding box
        double sumLat = 0, sumLon = 0;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (ContactObject point : points) {
            double lat = point.getLatitude();
            double lon = point.getLongitude();
            sumLat += lat;
            sumLon += lon;
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }
        double centroidLat = sumLat / points.size();
        double centroidLon = sumLon / points.size();

        // Convert centroid to pixel coordinates
        double[] centroidXY = latLonToPixel(centroidLat, centroidLon, 2);
        cx = centroidXY[0];
        cy = centroidXY[1];
        z = 2;

        // Adjust zoom to fit all points (assuming 800x600 viewport initially)
        double[] minXY = latLonToPixel(maxLat, minLon, z); // Top-left corner
        double[] maxXY = latLonToPixel(minLat, maxLon, z); // Bottom-right corner
        while (z < 19) {
            double pixelWidth = (maxXY[0] - minXY[0]) / Math.pow(2, z - 2);
            double pixelHeight = (maxXY[1] - minXY[1]) / Math.pow(2, z - 2);
            System.out.println("Zoom " + z + ": " + pixelWidth + " x " + pixelHeight + ", " + getWidth() + " x " + getHeight());
            if (pixelWidth <= getWidth() && pixelHeight <= getHeight()) break;
            z++;
            cx *= 2;
            cy *= 2;
            minXY = latLonToPixel(maxLat, minLon, z);
            maxXY = latLonToPixel(minLat, maxLon, z);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        double px = cx - width / 2.0; // Top-left x in map pixels
        double py = cy - height / 2.0; // Top-left y in map pixels
        int numTiles = 1 << z;

        // Calculate visible tile range
        int txMin = (int) Math.floor(px / 256);
        int txMax = (int) Math.floor((px + width - 1) / 256);
        int tyMin = (int) Math.floor(py / 256);
        int tyMax = (int) Math.floor((py + height - 1) / 256);

        // Draw tiles
        for (int ty = tyMin; ty <= tyMax; ty++) {
            if (ty >= 0 && ty < numTiles) {
                for (int tx = txMin; tx <= txMax; tx++) {
                    int wrappedTx = ((tx % numTiles) + numTiles) % numTiles;
                    String tileKey = baseMapManager.getCurrentTileSource().getCacheName() + "/" + z + "/" + wrappedTx + "/" + ty;
                    BufferedImage tile = memoryCache.get(tileKey);
                    double screenX = wrappedTx * 256 - px;
                    double screenY = ty * 256 - py;
                    if (tile != null) {
                        g2d.drawImage(tile, (int) screenX, (int) screenY, null);
                    } else {
                        // Try to find a fallback tile from lower or higher zoom levels
                        BufferedImage fallbackTile = findFallbackTile(wrappedTx, ty, z);
                        if (fallbackTile != null) {
                            g2d.drawImage(fallbackTile, (int) screenX, (int) screenY, 256, 256, null);
                        } else {
                            g2d.setColor(Color.GRAY);
                            g2d.fillRect((int) screenX, (int) screenY, 256, 256);
                        }
                        
                        // Queue the correct tile for download
                        if (!pendingTiles.contains(tileKey)) {
                            downloadQueue.add(tileKey);
                            pendingTiles.add(tileKey);
                        }
                    }
                }
            }
        }

        // Draw points with labels
        Font font = new Font("SansSerif", Font.PLAIN, 12);
        g2d.setFont(font);
        for (int i = 0; i < points.size(); i++) {
            ContactObject point = points.get(i);
            double[] xy = latLonToPixel(point.getLatitude(), point.getLongitude(), z);
            int screenX = (int) (xy[0] - px);
            int screenY = (int) (xy[1] - py);
            if (i == mouseHoverIndex) {
                g2d.setColor(Color.RED); // Highlight clicked point
                g2d.fillOval(screenX - 7, screenY - 7, 14, 14); // Larger dot
                g2d.setColor(Color.BLACK); // Label outline
                g2d.drawString(point.getLabel(), screenX + 10, screenY - 2);
                g2d.setColor(Color.RED); // Label fill
                g2d.drawString(point.getLabel(), screenX + 9, screenY - 3);
            } else {
                g2d.setColor(Color.BLUE);
                g2d.fillOval(screenX - 5, screenY - 5, 10, 10); // Normal dot
                g2d.setColor(Color.BLACK); // Label outline
                g2d.drawString(point.getLabel(), screenX + 10, screenY - 2);
                g2d.setColor(Color.WHITE); // Label fill
                g2d.drawString(point.getLabel(), screenX + 9, screenY - 3);
            }
        }

        // Draw cursor coordinates
        LocationType cursorLoc = new LocationType(mouseLat, mouseLon);

        String coordText = cursorLoc.getLatitudeAsPrettyString()+ " / " + cursorLoc.getLongitudeAsPrettyString();
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(coordText) + 10;
        int textHeight = fm.getHeight() + 5;
        int boxX = width - textWidth - 10;
        int boxY = height - textHeight - 10;
        //g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black
        g2d.fillRect(boxX, boxY, textWidth, textHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawString(coordText, boxX + 5, boxY + fm.getAscent() + 2);
    }

    private BufferedImage applyDarkModeFilter(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage darkImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xff;
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                // Invert colors for dark mode
                r = 255 - r;
                g = 255 - g;
                b = 255 - b;

                // Adjust brightness/contrast for all channels
                r = (int) (r * 0.9);
                g = (int) (g * 0.6);
                b = (int) (b * 0.0);


                // Ensure values stay within 0-255 range
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                int newRgb = (alpha << 24) | (r << 16) | (g << 8) | b;
                darkImage.setRGB(x, y, newRgb);
            }
        }
        return darkImage;
    }
    
    /**
     * Find a fallback tile from nearby zoom levels when the exact tile is not available.
     * Tries lower zoom levels first (showing less detail but bigger area), then higher zoom levels.
     */
    private BufferedImage findFallbackTile(int tx, int ty, int zoom) {
        // Try lower zoom levels first (zoom out - each tile covers 4 tiles of the next zoom level)
        for (int deltaZ = 1; deltaZ <= 3 && zoom - deltaZ >= 0; deltaZ++) {
            int lowerZoom = zoom - deltaZ;
            int factor = (int) Math.pow(2, deltaZ);
            int lowerTx = tx / factor;
            int lowerTy = ty / factor;
            String lowerTileKey = baseMapManager.getCurrentTileSource().getCacheName() + "/" + lowerZoom + "/" + lowerTx + "/" + lowerTy;
            BufferedImage lowerTile = memoryCache.get(lowerTileKey);
            
            if (lowerTile != null) {
                // Extract the relevant portion of the lower zoom tile
                int subX = (tx % factor) * (256 / factor);
                int subY = (ty % factor) * (256 / factor);
                int subSize = 256 / factor;
                
                try {
                    BufferedImage subImage = lowerTile.getSubimage(subX, subY, subSize, subSize);
                    // Scale up to 256x256
                    BufferedImage scaledImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaledImage.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(subImage, 0, 0, 256, 256, null);
                    g.dispose();
                    return scaledImage;
                } catch (Exception e) {
                    // Continue to next fallback option
                }
            }
        }
        
        // Try higher zoom levels (zoom in - 4 tiles make up 1 tile of the lower zoom level)
        for (int deltaZ = 1; deltaZ <= 2 && zoom + deltaZ <= 19; deltaZ++) {
            int higherZoom = zoom + deltaZ;
            int factor = (int) Math.pow(2, deltaZ);
            
            // Check if any of the child tiles exist
            for (int dx = 0; dx < factor; dx++) {
                for (int dy = 0; dy < factor; dy++) {
                    int higherTx = tx * factor + dx;
                    int higherTy = ty * factor + dy;
                    String higherTileKey = baseMapManager.getCurrentTileSource().getCacheName() + "/" + higherZoom + "/" + higherTx + "/" + higherTy;
                    BufferedImage higherTile = memoryCache.get(higherTileKey);
                    
                    if (higherTile != null) {
                        // Composite all available child tiles
                        BufferedImage composite = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = composite.createGraphics();
                        g.setColor(Color.GRAY);
                        g.fillRect(0, 0, 256, 256);
                        
                        int tileSize = 256 / factor;
                        for (int cx = 0; cx < factor; cx++) {
                            for (int cy = 0; cy < factor; cy++) {
                                int childTx = tx * factor + cx;
                                int childTy = ty * factor + cy;
                                String childKey = baseMapManager.getCurrentTileSource().getCacheName() + "/" + higherZoom + "/" + childTx + "/" + childTy;
                                BufferedImage childTile = memoryCache.get(childKey);
                                if (childTile != null) {
                                    g.drawImage(childTile, cx * tileSize, cy * tileSize, tileSize, tileSize, null);
                                }
                            }
                        }
                        g.dispose();
                        return composite;
                    }
                }
            }
        }
        
        return null; // No fallback tile found
    }

    public double[] latLonToPixel(double lat, double lon) {
        return latLonToPixel(lat, lon, z);
    }

    public double[] pixelToLatLon(double x, double y) {
        return pixelToLatLon(x, y, z);
    }

    // Convert pixel coordinates to WGS84 (lat/lon)
    private double[] pixelToLatLon(double x, double y, int zoom) {
        double n = Math.pow(2, zoom);
        double lon = x / (n * 256) * 360.0 - 180.0;
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * y / (n * 256))));
        double lat = Math.toDegrees(latRad);
        return new double[]{lat, lon};
    }

    
    // Convert WGS84 (lat/lon) to pixel coordinates
    private double[] latLonToPixel(double lat, double lon, int zoom) {
        double n = Math.pow(2, zoom);
        double x = (lon + 180) / 360 * n * 256;
        double latRad = Math.toRadians(lat);
        double y = (1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n * 256;
        return new double[]{x, y};
    }

    public int getZoom() {
        return z;
    }
    
    /**
     * Get the visible bounds of the current map view.
     * Returns array: [minLat, minLon, maxLat, maxLon]
     */
    public double[] getVisibleBounds() {
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) {
            // Return default bounds if not yet displayed
            return new double[]{41.0, -9.0, 42.0, -8.0};
        }
        
        double px = cx - width / 2.0;
        double py = cy - height / 2.0;
        
        double[] topLeft = pixelToLatLon(px, py, z);
        double[] bottomRight = pixelToLatLon(px + width, py + height, z);
        
        // topLeft[0] is maxLat, bottomRight[0] is minLat (lat decreases going down)
        return new double[]{
            bottomRight[0],  // minLat
            topLeft[1],      // minLon
            topLeft[0],      // maxLat
            bottomRight[1]   // maxLon
        };
    }

    @Override
    public void close() {
        // Clear memory cache
        for (BufferedImage img : memoryCache.values()) {
            if (img != null) {
                img.flush();
            }
        }
        memoryCache.clear();
        // Clear pending tiles
        pendingTiles.clear();
        // Clear download queue
        downloadQueue.clear();
        // Clear points
        points.clear();        
    }

    public static void main(String[] args) {
        GuiUtils.setTheme("light");
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("Slippy Map with Coordinates");
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            GuiUtils.setLookAndFeel();
            SlippyMap map = new SlippyMap(new ArrayList<>());
            frame.add(map);
            frame.setSize(800, 600);
            frame.setVisible(true);
            // Note: map center and zoom are already initialized in constructor from preferences or points
        });
    }
}