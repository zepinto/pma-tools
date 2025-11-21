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

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.rasterfall.RasterfallTile;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.RasterType;
import pt.omst.rasterlib.SampleDescription;
import pt.omst.rasterlib.SensorInfo;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.util.UserPreferences;

@Slf4j
public class MarkOverlay extends AbstractOverlay {

    private static final double EXPANSION_FACTOR = 0.5; // 50% expansion on each side

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

        // Create new list with renumbered indexes from 0 to N
        List<SampleDescription> renumberedSamples = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            SampleDescription original = samples.get(i);
            SampleDescription copy = new SampleDescription();
            copy.setIndex((long) i);
            copy.setTimestamp(original.getTimestamp());
            copy.setPose(original.getPose());
            renumberedSamples.add(copy);
        }

        return renumberedSamples;
    }

    public BufferedImage getContactImage(double minRange, double maxRange, Instant startTime, Instant endTime) {
        if (waterfall == null || waterfall.getTiles().isEmpty()) {
            return null;
        }

        Point2D.Double startPoint = waterfall.getScreenPosition(startTime, minRange);
        Point2D.Double endPoint = waterfall.getScreenPosition(endTime, maxRange);

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
            int endIdx = tile.getSampleIndex(overlapEnd); // Newest sample in overlap

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
                if (srcY < 0)
                    srcY = 0;

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

    private IndexedRaster generateRaster(File destinationFile, List<SampleDescription> samples, BufferedImage image,
            double minRange, double maxRange) {
        if (waterfall == null || waterfall.getTiles().isEmpty()) {
            return null;
        }

        try {
            // 1. Determine image filename
            String jsonPath = destinationFile.getAbsolutePath();
            String imagePath = jsonPath.replace(".json", ".png");
            File imageFile = new File(imagePath);

            
            ImageIO.write(image, "png", imageFile);
            
            // 2. Create IndexedRaster
            IndexedRaster raster = new IndexedRaster();
            raster.setFilename(imageFile.getName());
            raster.setRasterType(RasterType.SCANLINE);
            raster.setSamples(samples);

            // 3. Create SensorInfo
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

            // 4. Save JSON
            String json = Converter.IndexedRasterToJsonString(raster);
            Files.write(destinationFile.toPath(), json.getBytes());

            return raster;

        } catch (Exception e) {
            log.error("Error generating raster", e);
            return null;
        }
    }

    public static File zipFolderAndDelete(File folder) {
        File zipFile = new File(folder.getParentFile(), folder.getName() + ".zct");
        try {
            ZipUtils.zipDir(zipFile.getAbsolutePath(), folder.getAbsolutePath());
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            folder.delete();
            return zipFile;
        } catch (Exception e) {
            log.error("Error zipping folder", e);
            return null;
        }
    }

    private String generateNextLabel() {
        List<String> existingLabels = new ArrayList<>();
        String lastContact = null;
        long lastTimestamp = 0;
        int numContacts = waterfall.getContacts().getAllContacts().size();
        log.info("Generating label for new contact, existing contacts: {}", numContacts);
        for (CompressedContact c : waterfall.getContacts().getAllContacts()) {
            existingLabels.add(c.getLabel());
            if (lastContact == null || c.getTimestamp() > lastTimestamp) {
                lastContact = c.getLabel();
                lastTimestamp = c.getTimestamp();
            }
        }

        if (lastContact == null) {
            return "contact01";
        } else {
            // Try to increment numeric suffix
            String base = lastContact;
            String numericSuffix = "";

            // Find where numeric suffix starts (from the end)
            int i = lastContact.length() - 1;
            while (i >= 0 && Character.isDigit(lastContact.charAt(i))) {
                numericSuffix = lastContact.charAt(i) + numericSuffix;
                i--;
            }
            base = lastContact.substring(0, i + 1);

            if (!numericSuffix.isEmpty()) {
                int number = Integer.parseInt(numericSuffix);
                number++;
                String newLabel;
                do {
                    newLabel = String.format("%s%02d", base, number);
                    number++;
                } while (existingLabels.contains(newLabel));
                return newLabel;
            } else {
                // No numeric suffix, just append 01
                String newLabel = lastContact + "01";
                int suffix = 1;
                while (existingLabels.contains(newLabel)) {
                    suffix++;
                    newLabel = lastContact + String.format("%02d", suffix);
                }
                return newLabel;
            }
        }
    }

    private void createContact(Point2D.Double topLeft, Point2D.Double bottomRight) {
        String label = generateNextLabel();
        log.info("Creating new contact with label {}", label);
        // Calculate top-left and bottom-right coordinates
        int topLeftX = (int) Math.min(firstPoint.getX(), lastPoint.getX());
        int topLeftY = (int) Math.min(firstPoint.getY(), lastPoint.getY());
        
        int bottomRightX = (int) Math.max(firstPoint.getX(), lastPoint.getX());
        int bottomRightY = (int) Math.max(firstPoint.getY(), lastPoint.getY());
        int centerX = (topLeftX + bottomRightX) / 2;
        int centerY = (topLeftY + bottomRightY) / 2;

        double minRange = waterfall.getRangeAtScreenX(topLeftX);
        double maxRange = waterfall.getRangeAtScreenX(bottomRightX);
        Instant startTime = waterfall.getTimeAtScreenY(bottomRightY);
        Instant endTime = waterfall.getTimeAtScreenY(topLeftY);

        double width = maxRange - minRange;
        double height = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000.0; // in seconds

        // expand by EXPANSION_FACTOR on each side
        double expandX = width * EXPANSION_FACTOR;
        double expandY = height * EXPANSION_FACTOR;
        double expandedMinRange = Math.max(0, minRange - expandX);
        double expandedMaxRange = maxRange + expandX;
        Instant expandedStartTime = startTime.minusMillis((long) (expandY * 1000));
        Instant expandedEndTime = endTime.plusMillis((long) (expandY * 1000));

        pt.lsts.neptus.core.LocationType centerLocation = waterfall.getWorldPosition(
                new Point2D.Double(centerX, centerY));
        RasterfallTiles.TilesPosition pos = waterfall.getPosition(
                new Point2D.Double(centerX, centerY));
        Pose pose = pos.pose();

        Contact contact = new Contact();
        contact.setLatitude(centerLocation.getLatitudeDegs());
        contact.setLongitude(centerLocation.getLongitudeDegs());
        contact.setDepth(pose.getDepth());
        contact.setUuid(UUID.randomUUID());
        contact.setLabel(label);

        Observation obs = new Observation();
        obs.setDepth(pose.getDepth());
        obs.setLatitude(centerLocation.getLatitudeDegs());
        obs.setLongitude(centerLocation.getLongitudeDegs());
        obs.setTimestamp(OffsetDateTime.now());

        // Calculate normalized coordinates of original box within expanded image
        // Original box is centered in expanded image with EXPANSION_FACTOR padding on each side
        double normalizedPadding = EXPANSION_FACTOR / (1.0 + 2 * EXPANSION_FACTOR);
        double boxStartX = normalizedPadding;
        double boxStartY = normalizedPadding;
        double boxEndX = 1.0 - normalizedPadding;
        double boxEndY = 1.0 - normalizedPadding;

        Annotation annotation = new Annotation();
        annotation.setNormalizedX(boxStartX);
        annotation.setNormalizedY(boxStartY);
        annotation.setNormalizedX2(boxEndX);
        annotation.setNormalizedY2(boxEndY);
        annotation.setAnnotationType(AnnotationType.MEASUREMENT);
        annotation.setMeasurementType(MeasurementType.BOX);
        obs.setAnnotations(new ArrayList<>());
        annotation.setTimestamp(obs.getTimestamp());
        annotation.setUserName(UserPreferences.getUsername());
        obs.getAnnotations().add(annotation);
        obs.setUserName(UserPreferences.getUsername());
        contact.getObservations().add(obs);
        
        

        BufferedImage image = getContactImage(expandedMinRange, expandedMaxRange, expandedStartTime, expandedEndTime);
        List<SampleDescription> samples = getSamplesBetween(expandedStartTime, expandedEndTime);
        if (image != null) {
            try {
                // Create a temp directory for the contact files
                File tempDir = Files.createTempDirectory("rasterfall_contact").toFile();

                // Save raster (JSON + Image) inside the temp directory
                File rasterFile = new File(tempDir, "raster_" + label + ".json");
                generateRaster(rasterFile, samples, image, expandedMinRange, expandedMaxRange);
                obs.setRasterFilename(rasterFile.getName()); // Relative path inside the zip
                // Debug: List files in tempDir
                if (tempDir.exists() && tempDir.isDirectory()) {
                    File[] files = tempDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            log.info("File in temp dir: {} (size: {})", f.getName(), f.length());
                        }
                    } else {
                        log.warn("Temp dir is empty or I/O error");
                    }
                }

                // Save the contact metadata itself
                File contactFile = new File(tempDir, "contact.json"); // Or .json depending on format
                Files.write(contactFile.toPath(),
                        Converter.ContactToJsonString(contact).getBytes());

                log.info(label + " saved to {}", contactFile.getAbsolutePath());

                // Zip the directory containing the files
                File zctFile = zipFolderAndDelete(tempDir);

                // Move/Copy the zip to the final destination
                File finalZct = new File(label + ".zct");
                Files.copy(zctFile.toPath(), finalZct.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                waterfall.getContacts().addContact(finalZct);
                // Cleanup the temporary zip file
                zctFile.delete();

                log.info("Contact saved to {}", finalZct.getAbsolutePath());

            } catch (Exception ex) {
                log.error("Error saving rectangle selection", ex);
            }
        } else {
            log.error("Failed to generate contact image for the selected area.");
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
                createContact(firstPoint, lastPoint);
            }
        }
        waterfall.repaint();
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