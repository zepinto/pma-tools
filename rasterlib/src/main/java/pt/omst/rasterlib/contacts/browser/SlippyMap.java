package pt.omst.rasterlib.contacts.browser;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import pt.omst.neptus.util.GuiUtils;

public class SlippyMap extends JPanel {
    private int z = 2;              // Initial zoom level
    private double cx = 512.0;      // Center x at z=2 (mapWidth = 1024)
    private double cy = 512.0;      // Center y at z=2
    private final Map<String, BufferedImage> memoryCache = new HashMap<>(); // In-memory cache
    private final Set<String> pendingTiles = new HashSet<>();
    private final BlockingQueue<String> downloadQueue = new LinkedBlockingQueue<>();
    private double mouseLat = 0.0;  // Current mouse latitude
    private double mouseLon = 0.0;  // Current mouse longitude
    private final File cacheDir;    // Directory for disk cache
    private int clickedPointIndex = -1; // Index of the clicked point (-1 if none)
    private boolean darkMode = false; // Dark mode flag
    private int mouseHoverIndex = -1; // Index of the hovered point (-1 if none)
    private final ArrayList<ContactObject> points = new ArrayList<>(); // List of points to display
    private ContactBrowser browser; // Reference to the ContactBrowser
    public SlippyMap(List<? extends ContactObject> points, ContactBrowser browser) {
        this.points.addAll(points);
        this.browser = browser;
        setDarkMode(GuiUtils.isDarkTheme());
        // Initialize disk cache directory
        cacheDir = new File(System.getProperty("user.home"), "osm_tile_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // Start the tile downloader thread
        Thread downloader = new Thread(() -> {
            while (true) {
                try {
                    String tileKey = downloadQueue.take();
                    String[] parts = tileKey.split("/");
                    int tz = Integer.parseInt(parts[0]);
                    int tx = Integer.parseInt(parts[1]);
                    int ty = Integer.parseInt(parts[2]);

                    // Check disk cache first
                    File tileFile = new File(cacheDir, tileKey + ".png");
                    BufferedImage image = null;
                    if (tileFile.exists()) {
                        image = ImageIO.read(tileFile);
                    } else {
                        // Download tile if not cached
                        String url = "http://tile.openstreetmap.org/" + tz + "/" + tx + "/" + ty + ".png";
                        URL tileUrl = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) tileUrl.openConnection();
                        conn.setRequestProperty("User-Agent", "SlippyMapWithCoords/1.0 (your.email@example.com)");
                        image = ImageIO.read(conn.getInputStream());
                        conn.disconnect();

                        // Save to disk cache
                        tileFile.getParentFile().mkdirs(); // Ensure directory exists
                        ImageIO.write(image, "png", tileFile);
                    }

                    if (darkMode)
                        image = applyDarkModeFilter(image);

                    memoryCache.put(tileKey, image);
                    pendingTiles.remove(tileKey);
                    SwingUtilities.invokeLater(this::repaint);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        downloader.setDaemon(true);
        downloader.start();

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
                repaint();
            } else if (rotation > 0 && z > 0) { // Zoom out
                double newCx = cx / 2.0 + width / 4.0 - mouseX / 2.0;
                double newCy = cy / 2.0 + height / 4.0 - mouseY / 2.0;
                z--;
                cx = newCx;
                cy = newCy;
                repaint();
            }
        });

        // Pan with mouse drag and update coordinates
        final int[] lastPos = new int[2];

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
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
                        if (browser != null) {
                            browser.setSelectedContact(point.getLabel());
                        }
                        //JOptionPane.showMessageDialog(SlippyMap.this, message,
                        //        "Point Clicked", JOptionPane.INFORMATION_MESSAGE);
                        repaint();
                        break;
                    }
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

    public void focus(ContactObject obj, int zoom) {
        double[] xy = latLonToPixel(obj.getLatitude(), obj.getLongitude(), zoom);
        cx = xy[0];
        cy = xy[1];
        z = zoom;
        repaint();
        mouseHoverIndex = points.indexOf(obj);
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
                    String tileKey = z + "/" + wrappedTx + "/" + ty;
                    BufferedImage tile = memoryCache.get(tileKey);
                    double screenX = wrappedTx * 256 - px;
                    double screenY = ty * 256 - py;
                    if (tile != null) {
                        g2d.drawImage(tile, (int) screenX, (int) screenY, null);
                    } else if (!pendingTiles.contains(tileKey)) {
                        downloadQueue.add(tileKey);
                        pendingTiles.add(tileKey);
                        g2d.setColor(Color.GRAY);
                        g2d.fillRect((int) screenX, (int) screenY, 256, 256);
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
        String coordText = String.format("Lat: %.5f, Lon: %.5f", mouseLat, mouseLon);
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

    public static void main(String[] args) {

        List<ContactObject.Impl> examplePoints = Arrays.asList(
                new ContactObject.Impl(51.5074, -0.1278, "Central London"),
                new ContactObject.Impl(51.5550, -0.1086, "Camden"),
                new ContactObject.Impl(51.4545, -0.9731, "Reading")
        );

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Slippy Map with Coordinates and Disk Cache");

            SlippyMap mapPanel = new SlippyMap(examplePoints, null);
            //mapPanel.setDarkMode(true); // Enable dark mode
            frame.add(mapPanel);
            frame.setSize(800, 600);
            frame.setVisible(true);
            mapPanel.initializeMapCenterAndZoom();
        });
    }
}