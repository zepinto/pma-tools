//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import lombok.Getter;
import lombok.extern.java.Log;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.IndexedRasterUtils.RasterContactInfo;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

@Log
public class RasterfallTiles extends JPanel implements Closeable {

    @Getter
    private final ArrayList<IndexedRaster> rasters = new ArrayList<>();
    @Getter
    private final ArrayList<RasterfallTile> tiles = new ArrayList<>();
    @Getter
    private final ContactCollection contacts;
    @Getter
    private final File rastersFolder;
    @Getter
    private double zoom = 1.0;
    @Getter
    private final double heightProportion;

    @Getter
    private final File contactsFolder;

    // Cache for RasterContactInfo to avoid recalculation
    private final Map<CompressedContact, RasterContactInfo> contactInfoCache = new HashMap<>();

    public record TilesPosition(Instant timestamp, double range, LocationType location, Pose pose) {}

    public static List<File> findRasterFiles(File parentFolder) {
        List<File> files = new ArrayList<>();
        for (File file : parentFolder.listFiles()) {
            if (file.isDirectory()) {
                files.addAll(findRasterFiles(file));
            } else if (file.getName().endsWith(".json")
                    && file.getParentFile().getName().equalsIgnoreCase("rasterIndex")) {
                files.add(file);                
            }
        }
        return files;
    }


    public RasterfallTiles(File folder, Consumer<String> progressCallback) {
        contactsFolder = new File(folder.getParentFile(), "contacts");
        if (!contactsFolder.exists()) {
            contactsFolder.mkdirs();
        }
        if (progressCallback != null) {
            progressCallback.accept("Loading contacts...");
        }
        contacts = ContactCollection.fromFolder(new File(folder.getParentFile(), "contacts"));
        if (progressCallback != null) {
            progressCallback.accept(String.format("Loaded %d contacts.", contacts.getAllContacts().size()));
        }
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        double verticalSize = 0;
        rastersFolder = folder;
        findRasterFiles(folder).forEach(index -> {
            try {
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(index.toPath()));
                rasters.add(raster);
                RasterfallTile tile = new RasterfallTile(index.getParentFile(), raster);
                tiles.add(tile);
                rasters.sort((r1, r2) -> r2.getSamples().get(0).getTimestamp().compareTo(r1.getSamples().get(0).getTimestamp()));
            } catch (IOException e) {
                log.warning("Error reading raster file: " + e.getMessage());
            }
        });

        tiles.sort(Comparator.naturalOrder());
        if (progressCallback != null) {
            progressCallback.accept("Loading tiles...");
        }
        for (int i = tiles.size() - 1; i >= 0; i--) {
            if (progressCallback != null) {
                progressCallback.accept(String.format("Loading tile %d/%d", tiles.size() - i, tiles.size()));
            }
            RasterfallTile tile = tiles.get(i);
            add(tile);
            verticalSize += tile.getPreferredSize().getHeight();
        }

        if (progressCallback != null) {
            progressCallback.accept(String.format("%d Tiles loaded.", tiles.size()));
        }

        double width = tiles.getFirst().getPreferredSize().getWidth();
        setPreferredSize(new Dimension((int) width, (int) verticalSize));
        setMinimumSize(new Dimension((int) width, (int) verticalSize));
        heightProportion = verticalSize / width;
    }

    // private LinkedHashMap<CompressedContact, RasterContactInfo> infos = new LinkedHashMap<>();

    // public List<IndexedRasterUtils.RasterContactInfo> getVisibleContactInfos() {
    //     List<IndexedRasterUtils.RasterContactInfo> contactInfos = new ArrayList<>();
    //     List<CompressedContact> cts = contacts.getContactsBetween(getBottomTimestamp(), getTopTimestamp());
    //     for (CompressedContact contact : cts) {
    //         if (!infos.containsKey(contact)) {
    //             RasterContactInfo info = IndexedRasterUtils.getContactInfo(contact);
    //             infos.put(contact, info);
    //         }
    //         RasterContactInfo info = infos.get(contact);
    //         if (info != null) {
    //             contactInfos.add(info);
    //         }
    //     }
    //     return contactInfos;
    // }

    public Point2D.Double getSlantedScreenPosition(Instant timestamp, double range) {
        for (RasterfallTile tile : tiles) {
            if (tile.containstTime(timestamp)) {
                Point2D.Double relativePosition = tile.getSlantedRangePosition(timestamp, range);
                if (relativePosition == null) {
                    log.warning("Failed to get slanted range position for timestamp " + timestamp + " and range " + range);
                    return null;
                }
                Point2D.Double absolutePosition = new Point2D.Double(tile.getBounds().x + relativePosition.x, tile.getBounds().y + relativePosition.y);
                return absolutePosition;
            }
        }
        log.warning("No tile contains timestamp " + timestamp);
        return null;
    }

    public Point2D.Double getScreenPosition(Instant timestamp, double range) {
        for (RasterfallTile tile : tiles) {
            if (tile.containstTime(timestamp)) {
                Point2D.Double relativePosition = tile.getGroundPosition(timestamp, range);
                Point2D.Double absolutePosition = new Point2D.Double(tile.getBounds().x + relativePosition.x, tile.getBounds().y + relativePosition.y);
                return absolutePosition;
            }
        }
        return null;
    }

    public TilesPosition getPosition(Point2D point) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(point)) {
                int x = (int)(point.getX() - tile.getBounds().x);
                int y = (int)(point.getY() - tile.getBounds().y);
                return new TilesPosition(Instant.ofEpochMilli(tile.getTimestamp(x, y)), tile.getRange(x), tile.getWorldPosition(x, y), tile.getPose(x, y));
            }
        }
        return null;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        
        // Batch update all tiles without triggering individual revalidations
        for (RasterfallTile tile : tiles) {
            tile.setZoomQuiet(zoom);
        }
        
        // Single layout update
        revalidate();
        repaint();
    }

    public long getTimestamp() {
        double x = getVisibleRect().getX()+getVisibleRect().getWidth()/2;
        double y = getVisibleRect().getY()+getVisibleRect().getHeight()/2;
        return getTimestamp(new Point2D.Double(x, y));
    }

    public long getBottomTimestamp() {
        double x = getVisibleRect().getX()+getVisibleRect().getWidth()/2;
        double y = getVisibleRect().getY()+getVisibleRect().getHeight();
        return getTimestamp(new Point2D.Double(x, y));
    }

    public long getTopTimestamp() {
        double x = getVisibleRect().getX()+getVisibleRect().getWidth()/2;
        double y = getVisibleRect().getY();
        return getTimestamp(new Point2D.Double(x, y));
    }

    public long getTimestamp(Point2D point) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(point)) {
                int x = (int)(point.getX() - tile.getBounds().x);
                int y = (int)(point.getY() - tile.getBounds().y);
                return tile.getTimestamp(x, y);
            }
        }
        return -1;
    }

    public double getRange(Point2D point) {
            for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(point)) {
                int x = (int)(point.getX() - tile.getBounds().x);
                return tile.getRange(x);
            }
        }
        return Double.NaN;
    }

    public Point2D.Double getScreenPosition(TilesPosition position)  {
        for (RasterfallTile tile : tiles) {
            if (tile.containstTime(position.timestamp())) {
                Point2D.Double relativePosition = tile.getSlantedRangePosition(position.range());
                return new Point2D.Double(tile.getBounds().x + relativePosition.x, tile.getBounds().y + relativePosition.y);
            }
        }
        return null;
    }

    public LocationType getWorldPosition(Point2D point) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(point)) {
                int x = (int)(point.getX() - tile.getBounds().x);
                int y = (int)(point.getY() - tile.getBounds().y);
                return tile.getWorldPosition(x, y);
            }
        }
        return null;
    }


    @Override
    public void doLayout() {
        if (tiles.isEmpty())
            return;
        
        // Calculate all tile positions in one pass
        int yPosition = 0;
        
        for (RasterfallTile tile : tiles) {
            Dimension tileSize = tile.getPreferredSize();
            tile.setBounds(0, yPosition, tileSize.width, tileSize.height);
            yPosition += tileSize.height;
        }
        
        // Update container size
        int width = tiles.getFirst().getPreferredSize().width;
        setPreferredSize(new Dimension(width, yPosition));
    }

    public long getStartTime() {
        long startTime = tiles.getLast().getStartTime().toInstant().toEpochMilli();
        return startTime;
    }

    public long getEndTime() {
        long endTime = tiles.getFirst().getEndTime().toInstant().toEpochMilli();        
        return endTime;
    }

    public BufferedImage getScrollImage(int width, Consumer<Void> loadingCallback) {
        double verticalSize = 0;
        for (RasterfallTile tile : tiles) {
            verticalSize += tile.getSamplesCount();
        }
        BufferedImage firstImage = tiles.getFirst().getImageSync();
        if (firstImage == null)
            return null;
        double scale = width / (double) firstImage.getWidth();

        verticalSize *= scale;
        final double height = verticalSize;
        BufferedImage image = new BufferedImage(width, (int) verticalSize, BufferedImage.TYPE_INT_ARGB);
        IndexedRasterUtils.background(() -> {
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double y = height;
            for (int i = tiles.size()-1; i >= 0; i--) {
                RasterfallTile tile = tiles.get(i);
                BufferedImage tileImage = tile.getImageSync();
                if (tileImage == null)
                    continue;
                y -= tileImage.getHeight() * scale;
                g.drawImage(tileImage, 0, (int) Math.round(y), width, (int) Math.round(tileImage.getHeight() * scale), null);

                loadingCallback.accept(null);
            }
            for (RasterfallTile tile : tiles) {
                BufferedImage tileImage = tile.getImageSync();
                if (tileImage == null)
                    continue;
                g.drawImage(tileImage, 0, (int) Math.round(y), width, (int) Math.round(tileImage.getHeight() * scale), null);
                y += tileImage.getHeight() * scale;
                loadingCallback.accept(null);
            }
            g.dispose();
        });

        return image;
    }

    @Override
    public void close() throws IOException {
        for (RasterfallTile tile : tiles)
            tile.close();
        tiles.clear();
        rasters.clear();
        contactInfoCache.clear();
    }
    
    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        
        if (RasterfallDebug.debug) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw tile boundaries
            Color[] tileColors = {
                new Color(255, 0, 0),
                new Color(0, 255, 0),
                new Color(0, 0, 255),
                new Color(255, 255, 0),
                new Color(255, 0, 255),
                new Color(0, 255, 255)
            };
            
            int tileIndex = 0;
            for (RasterfallTile tile : tiles) {
                Color color = tileColors[tileIndex % tileColors.length];
                
                // Draw tile boundary
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                g2.drawRect(tile.getBounds().x, tile.getBounds().y, 
                           tile.getBounds().width - 1, tile.getBounds().height - 1);
                
                // Draw tile info label
                g2.setColor(color);
                g2.setFont(new Font("Monospaced", Font.BOLD, 11));
                String label = String.format("Tile #%d [%d x %d]", 
                    tileIndex, tile.getWidth(), tile.getHeight());
                g2.drawString(label, tile.getBounds().x + 5, tile.getBounds().y + 15);
                
                String timeRange = String.format("%s - %s",
                    RasterfallDebug.formatTimestamp(tile.getStartTime().toInstant().toEpochMilli()),
                    RasterfallDebug.formatTimestamp(tile.getEndTime().toInstant().toEpochMilli()));
                g2.drawString(timeRange, tile.getBounds().x + 5, tile.getBounds().y + 30);
                
                tileIndex++;
            }
            
            g2.dispose();
        }
    }

    public double getRangeAtScreenX(int screenX) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(screenX, 0)) {
                int x = screenX - tile.getBounds().x;
                return tile.getRange(x);
            }
        }
        return Double.NaN;
    }

    public Instant getTimeAtScreenY(int screenY) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(0, screenY)) {
                int y = screenY - tile.getBounds().y;
                return Instant.ofEpochMilli(tile.getTimestamp(0, y));
            }
        }
        return null;
    }

    public RasterfallTile getTileUnder(double x, double y) {
        for (RasterfallTile tile : tiles) {
            if (tile.getBounds().contains(x, y)) {
                return tile;
            }
        }
        return null;
    }

    public List<RasterContactInfo> getVisibleContacts() {
        List<CompressedContact> allContacts = contacts.getAllContacts();
        ArrayList<RasterContactInfo> contactInfos = new ArrayList<>();
        for (CompressedContact contact : allContacts) {
            // Check cache first
            RasterContactInfo cinfo = contactInfoCache.get(contact);
            if (cinfo == null) {
                // Calculate and cache if not present
                cinfo = IndexedRasterUtils.getContactInfo(contact);
                if (cinfo == null)
                    continue;
                contactInfoCache.put(contact, cinfo);
            }
            long contactStart = cinfo.getStartTimeStamp();
            long contactEnd = cinfo.getEndTimeStamp();
            long visibleStart = getBottomTimestamp();
            long visibleEnd = getTopTimestamp();
            if (contactEnd < visibleStart || contactStart > visibleEnd)
                continue;

            contactInfos.add(cinfo);
        }
        return contactInfos;
    }

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        JFrame frame = new JFrame("Index Raster Waterfall");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        JScrollPane scroll = new JScrollPane(new RasterfallTiles(new File("/LOGS/REP/REP24/lauv-omst-2/20240913/075900_mwm-omst2/"), null));
        scroll.setBackground(Color.BLACK);
        frame.setContentPane(scroll);
        frame.setVisible(true);
    }

}
