//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JOptionPane;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTile;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.RasterType;
import pt.omst.rasterlib.SampleDescription;
import pt.omst.rasterlib.SensorInfo;

@Slf4j
public class MarkOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private Point2D.Double firstPoint = null;
    private Point2D.Double lastPoint = null;
    private Point2D.Double currentPoint = null;

    private RasterfallTiles.TilesPosition firstPosition = null, lastPosition = null;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        firstPoint = lastPoint = currentPoint = null;
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        firstPoint = lastPoint = currentPoint = null;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);

        // Draw rectangle when we have two points
        Point2D.Double drawPoint = (lastPoint != null) ? lastPoint : currentPoint;

        if (firstPoint != null && drawPoint != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(255, 255, 255, 200));

            // Calculate rectangle bounds
            int x = (int) Math.min(firstPoint.getX(), drawPoint.getX());
            int y = (int) Math.min(firstPoint.getY(), drawPoint.getY());
            int width = (int) Math.abs(drawPoint.getX() - firstPoint.getX());
            int height = (int) Math.abs(drawPoint.getY() - firstPoint.getY());

            // Draw rectangle
            g2.drawRect(x, y, width, height);

            // Fill with semi-transparent white
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRect(x, y, width, height);

            g2.dispose();
        }
    }

    public List<SampleDescription> getSamplesBetween(Instant startTime, Instant endTime) {
        List<SampleDescription> samples = new ArrayList<>();
        if (waterfall == null || waterfall.getTiles().isEmpty()) {
            return samples;
        }

        for (RasterfallTile tile : waterfall.getTiles()) {
            Instant tileStart = tile.getStartTime().toInstant();
            Instant tileEnd = tile.getEndTime().toInstant();

            if (tileEnd.isBefore(startTime) || tileStart.isAfter(endTime)) {
                continue;
            }
            
            Instant overlapStart = tileStart.isAfter(startTime) ? tileStart : startTime;
            Instant overlapEnd = tileEnd.isBefore(endTime) ? tileEnd : endTime;
            
            int startIdx = tile.getSampleIndex(overlapStart);
            int endIdx = tile.getSampleIndex(overlapEnd);
            
            int minIdx = Math.min(startIdx, endIdx);
            int maxIdx = Math.max(startIdx, endIdx);
            
            List<SampleDescription> tileSamples = tile.getRaster().getSamples();
            for (int i = minIdx; i <= maxIdx; i++) {
                if (i >= 0 && i < tileSamples.size()) {
                    SampleDescription s = tileSamples.get(i);
                    Instant t = s.getTimestamp().toInstant();
                    if (!t.isBefore(startTime) && !t.isAfter(endTime)) {
                        samples.add(s);
                    }
                }
            }
        }
        
        samples.sort((s1, s2) -> s1.getTimestamp().compareTo(s2.getTimestamp()));
        
        return samples;
    }
    public BufferedImage getContactImage(double minRange, double maxRange, Instant startTime, Instant endTime) {
        if (waterfall == null || waterfall.getTiles().isEmpty()) {
            return null;
        }

        // Calculate dimensions
        int totalHeight = 0;
        int width = 0;
        double pixelsPerMeter = 0;

        // Find relevant tiles and calculate height
        java.util.List<RasterfallTile> relevantTiles = new java.util.ArrayList<>();
        
        // Iterate tiles (they are sorted descending by time - newest first)
        for (RasterfallTile tile : waterfall.getTiles()) {
            Instant tileStart = tile.getStartTime().toInstant();
            Instant tileEnd = tile.getEndTime().toInstant();

            if (tileEnd.isBefore(startTime) || tileStart.isAfter(endTime)) {
                continue;
            }
            
            relevantTiles.add(tile);
            
            // Calculate overlap
            Instant overlapStart = tileStart.isAfter(startTime) ? tileStart : startTime;
            Instant overlapEnd = tileEnd.isBefore(endTime) ? tileEnd : endTime;
            
            int startIdx = tile.getSampleIndex(overlapStart);
            int endIdx = tile.getSampleIndex(overlapEnd);
            
            totalHeight += Math.abs(endIdx - startIdx);
            
            if (width == 0) {
                BufferedImage tileImg = tile.getImageSync();
                if (tileImg != null) {
                    double tileRange = tile.getRange();
                    pixelsPerMeter = tileImg.getWidth() / (2.0 * tileRange);
                    width = (int) ((maxRange - minRange) * pixelsPerMeter);
                }
            }
        }

        if (totalHeight == 0 || width == 0) {
            return null;
        }

        BufferedImage result = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        
        int currentY = 0; // Start from top (newest)
        
        // relevantTiles are sorted newest first.
        // We want to draw from top (newest) to bottom (oldest).
        
        for (RasterfallTile tile : relevantTiles) {
            Instant tileStart = tile.getStartTime().toInstant();
            Instant tileEnd = tile.getEndTime().toInstant();
            
            Instant overlapStart = tileStart.isAfter(startTime) ? tileStart : startTime;
            Instant overlapEnd = tileEnd.isBefore(endTime) ? tileEnd : endTime;
            
            int startIdx = tile.getSampleIndex(overlapStart); // Oldest sample in overlap
            int endIdx = tile.getSampleIndex(overlapEnd);     // Newest sample in overlap
            
            int h = Math.abs(endIdx - startIdx);
            
            BufferedImage tileImg = tile.getImageSync();
            if (tileImg != null) {
                // Calculate source coordinates
                // Image Y: 0 is newest (last sample), Height is oldest (first sample)
                // sample index: 0 is oldest, Count-1 is newest.
                // So y = (Count - 1) - index.
                
                int tileSamples = tile.getSamplesCount();
                // y for endIdx (newest) -> top of the slice in source image
                int srcY = (tileSamples - 1) - endIdx; 
                if (srcY < 0) srcY = 0;
                
                // x coordinates
                double tileRange = tile.getRange();
                double tilePixelsPerMeter = tileImg.getWidth() / (2.0 * tileRange);
                
                int srcX = (int) ((minRange + tileRange) * tilePixelsPerMeter);
                int srcW = (int) ((maxRange - minRange) * tilePixelsPerMeter);
                
                // Draw
                g2.drawImage(tileImg, 
                    0, currentY, width, currentY + h, // dest
                    srcX, srcY, srcX + srcW, srcY + h, // source
                    null);
            }
            
            currentY += h;
        }
        
        g2.dispose();
        return result;
    }

    private IndexedRaster generateRaster(File destinationFile, List<SampleDescription> samples, BufferedImage image, double minRange, double maxRange) {
        if (waterfall == null || waterfall.getTiles().isEmpty()) {
            return null;
        }
        
        try {
            // 1. Determine image filename
            String jsonPath = destinationFile.getAbsolutePath();
            String imagePath = jsonPath.replace(".json", ".jpg");
            File imageFile = new File(imagePath);
            
            // 2. Save image
            ImageIO.write(image, "jpg", imageFile);
            
            // 3. Create IndexedRaster
            IndexedRaster raster = new IndexedRaster();
            raster.setFilename(imageFile.getName());
            raster.setRasterType(RasterType.SCANLINE);
            raster.setSamples(samples);
            
            // 4. Create SensorInfo
            SensorInfo info = new SensorInfo();
            // Copy from first tile if available
            SensorInfo sourceInfo = waterfall.getTiles().getFirst().getRaster().getSensorInfo();
            if (sourceInfo != null) {
                info.setColorMode(sourceInfo.getColorMode());
                info.setFilters(sourceInfo.getFilters());
                info.setFrequency(sourceInfo.getFrequency());
                info.setHfov(sourceInfo.getHfov());
                info.setSensorModel(sourceInfo.getSensorModel());
                info.setSystemName(sourceInfo.getSystemName());
                info.setVfov(sourceInfo.getVfov());
            }
            info.setMinRange(minRange);
            info.setMaxRange(maxRange);
            
            raster.setSensorInfo(info);
            
            // 5. Save JSON
            String json = Converter.IndexedRasterToJsonString(raster);
            Files.write(destinationFile.toPath(), json.getBytes());
            
            return raster;
            
        } catch (Exception e) {
            log.error("Error generating raster", e);
            return null;
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
            if (lastPoint != null) {
                // Reset for new rectangle
                lastPoint = firstPoint = null;
                firstPosition = lastPosition = null;
            } else if (firstPoint == null) {
                // First click - set starting point
                firstPoint = new Point2D.Double(e.getX(), e.getY());
                firstPosition = waterfall.getPosition(firstPoint);
                currentPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            } else {
                // Second click - complete rectangle and show dialog
                lastPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(lastPoint);
                currentPoint = null;

                // Calculate top-left and bottom-right coordinates
                int topLeftX = (int) Math.min(firstPoint.getX(), lastPoint.getX());
                int topLeftY = (int) Math.min(firstPoint.getY(), lastPoint.getY());
                int bottomRightX = (int) Math.max(firstPoint.getX(), lastPoint.getX());
                int bottomRightY = (int) Math.max(firstPoint.getY(), lastPoint.getY());

                double minRange = waterfall.getRangeAtScreenX(topLeftX);
                double maxRange = waterfall.getRangeAtScreenX(bottomRightX);
                Instant startTime = waterfall.getTimeAtScreenY(bottomRightY);
                Instant endTime = waterfall.getTimeAtScreenY(topLeftY);
                
                BufferedImage image = getContactImage(minRange, maxRange, startTime, endTime);                
                List<SampleDescription> samples = getSamplesBetween(startTime, endTime);

                
                
                if (image != null) {
                    // Generate raster
                    File destFile = new File("rectangle_selection.json");
                    generateRaster(destFile, samples, image, minRange, maxRange);
                    log.info("Rectangle selection saved to {}", destFile.getAbsolutePath());
                }
                
                log.info("Found {} samples in selection", samples.size());
                    // Calculate center coordinates
                int centerX = (topLeftX + bottomRightX) / 2;
                int centerY = (topLeftY + bottomRightY) / 2;

                // Get world position (latitude/longitude) of center
                pt.lsts.neptus.core.LocationType centerLocation = waterfall.getWorldPosition(
                        new Point2D.Double(centerX, centerY));

                String centerLatLon = "N/A";
                if (centerLocation != null) {
                    centerLatLon = String.format("%.6f°, %.6f°",
                            centerLocation.getLatitudeDegs(),
                            centerLocation.getLongitudeDegs());
                }

                RasterfallTiles.TilesPosition pos = waterfall.getPosition(
                        new Point2D.Double(centerX, centerY));
                Pose pose = pos.pose();
                Contact contact = new Contact();
                contact.setLatitude(pose.getLatitude());
                contact.setLongitude(pose.getLongitude());
                contact.setDepth(pose.getDepth());
                contact.setUuid(UUID.randomUUID());

                Observation obs = new Observation();
                obs.setDepth(pose.getDepth());
                obs.setLatitude(pose.getLatitude());
                obs.setLongitude(pose.getLongitude());
                
                obs.setTimestamp(OffsetDateTime.now());
                contact.getObservations().add(obs);
                
                

                // Show dialog with coordinates
                String message = String.format(
                        "Rectangle Coordinates:\n\n" +
                                "Top-Left: (%d, %d)\n" +
                                "Bottom-Right: (%d, %d)\n\n" +
                                "Width: %d px\n" +
                                "Height: %d px\n\n" +
                                "Center (Lat, Lon): %s",
                        topLeftX, topLeftY,
                        bottomRightX, bottomRightY,
                        bottomRightX - topLeftX,
                        bottomRightY - topLeftY,
                        centerLatLon);

                JOptionPane.showMessageDialog(
                        waterfall,
                        message,
                        "Rectangle Selection",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            waterfall.repaint();
        }
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends RasterfallTiles> l) {
        if (firstPosition != null) {
            firstPoint = waterfall.getScreenPosition(firstPosition);
        }
        if (lastPoint != null) {
            lastPoint = waterfall.getScreenPosition(lastPosition);
        }
        waterfall.repaint();
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_MOVED && firstPoint != null) {
            if (lastPoint == null) {
                currentPoint = new Point2D.Double(e.getX(), e.getY());
                lastPosition = waterfall.getPosition(currentPoint);
            }
            waterfall.repaint();
        }
    }

}