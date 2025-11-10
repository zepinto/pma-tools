//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.colormap.ColorMap;
import pt.omst.neptus.colormap.ColorMapFactory;
import pt.omst.neptus.sidescan.SidescanLine;
import pt.omst.neptus.sidescan.SidescanLogMarker;
import pt.omst.neptus.sidescan.SidescanMarkerUtils;
import pt.omst.neptus.sidescan.SidescanParser;
import pt.omst.neptus.util.ImageUtils;

@Slf4j
public class IndexedRasterCreator {

    private final IndexedRaster raster;
    @Getter
    private final File jsonFile;
    @Getter
    private final File imgFile;
    @Getter
    private BufferedImage image;

    @Setter
    private ColorMap colorMap = ColorMapFactory.createBronzeColormap();

    /**
     * Creates a new IndexedRasterCreator with the given output file and sensor info
     * @param outputFile The output file. Both the image and the json file will be created with the same name but different extensions
     * @param info The sensor info. This will be used to populate the sensor info field in the json file
     */
    public IndexedRasterCreator(File outputFile, SensorInfo info) {
        raster = new IndexedRaster();
        raster.setSensorInfo(info);
        raster.setRasterType(RasterType.SCANLINE);
        raster.setSamples(new ArrayList<>());
        jsonFile = outputFile;
        imgFile = new File(outputFile.getAbsolutePath().replace(".json", ".png"));
        raster.setFilename(imgFile.getName());
    }

    /**
     * Exports the given marker to the output file, using the given parser and subsystem
     * @param marker The marker to export
     * @param parser The parser to use
     * @param subsystem The subsystem to use
     * @param margin The margin to use, this is the number of pixels to include on each side of the marker
     */
    public IndexedRaster export(SidescanLogMarker marker, SidescanParser parser, int subsystem, int margin) {
        ArrayList<SidescanLine> lines = SidescanMarkerUtils.getNormalizedLines(marker, parser, subsystem, margin, margin);
        SampleDescription lastSample = null;
        for (int i = 0; i < lines.size(); i++) {
            SidescanLine line = (SidescanLine) lines.toArray()[i];
            SampleDescription sample = getSample(lastSample, line, i);
            lastSample = sample;
            raster.getSamples().add(sample);
        }
        image = getSidescanMarkerImage(marker, lines, margin);

        SidescanLine firstLine = lines.stream().findFirst().get();
        if (raster.getSensorInfo() == null)
            raster.setSensorInfo(new SensorInfo());
        raster.getSensorInfo().setFrequency((double) (firstLine.getFrequency()/1000.0f));
        raster.getSensorInfo().setSensorModel(IndexedRasterUtils.getSidescanModel(parser));
        try {
            boolean result = write(image, "JPG", imgFile);
            if (!result)
                log.error("Failed to write image to file");
            String json = Converter.IndexedRasterToJsonString(raster);
            Files.write(jsonFile.toPath(), json.getBytes());
            return raster;
        } catch (Exception e) {
            log.error("error", e);
        }
        return null;
    }

    private BufferedImage getSidescanMarkerImage(SidescanLogMarker marker, List<SidescanLine> lines, int margin) {

        SidescanLine middleLine = lines.get(lines.size()/2);
        double slantedX = SidescanMarkerUtils.getSlantDistance(middleLine.getState().getAltitude(), marker.getX());
        int sssPixelWidth = lines.getFirst().getData().length;
        double sssWorldWidth = SidescanMarkerUtils.getSlantDistance(middleLine.getState().getAltitude(), middleLine.getRange()*2);
        double sssPixelResolution = sssWorldWidth / sssPixelWidth;
        double markWorldWidth = marker.getFullRange();
        double markWorldPos = lines.getFirst().getRange() + slantedX;
        // System.out.println(marker.getLabel()+":");
        // System.out.println("  - Mark world pos: " + markWorldPos);
        // System.out.println("  - Mark world width: " + markWorldWidth);
        // System.out.println("  - SSS world width: " + sssWorldWidth);
        // System.out.println("  - SSS pixel width: " + sssPixelWidth);
        // System.out.println("  - SSS pixel resolution: " + sssPixelResolution);
        // System.out.println("  - Marker position: " + marker.getX()+", "+marker.getY());
        double rangeStart = (marker.getX() - markWorldWidth/2d);
        double rangeEnd = (marker.getX() + markWorldWidth/2d);

        raster.getSensorInfo().setMaxRange(rangeEnd);
        raster.getSensorInfo().setMinRange(rangeStart);

        // System.out.println("  - Marker start: " +(markWorldPos - markWorldWidth/2));
        // System.out.println("  - Marker end: " +(markWorldPos + markWorldWidth/2));

        int markerStartPos = (int) ((markWorldPos - markWorldWidth/2) / sssPixelResolution);
        int markerEndPos = (int) ((markWorldPos + markWorldWidth/2) / sssPixelResolution);


        markerStartPos = Math.max(0, markerStartPos - margin);
        markerEndPos = Math.min(sssPixelWidth, markerEndPos + margin);

        // System.out.println("  - Marker start pos: " + markerStartPos);
        // System.out.println("  - Marker end pos: " + markerEndPos);
        BufferedImage myImage = new BufferedImage(markerEndPos - markerStartPos, lines.size(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < lines.size(); y++) {
            SidescanLine line = lines.get(y);
            line.drawSlantedImage(ColorMapFactory.createBronzeColormap());
            int imgx = markerStartPos;
            for (int x = 0; x < line.getData().length && imgx < markerEndPos; x++, imgx++) {
                myImage.setRGB(x, y, line.getImage().getRGB(imgx, 0));
            }
        }
        return myImage;
    }

    /**
     * Exports the given lines to an output file (both image and json file)
     * @param lines The full lines to export to the output file
     */
    public void export(Collection<SidescanLine> lines) {
        if (lines.isEmpty())
            return;
        SidescanLine firstLine = lines.stream().findFirst().get();
        int width = firstLine.getXSize();
        BufferedImage img = new BufferedImage(width, lines.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        BufferedImage lineImage = new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB);
        SampleDescription lastSample = null;
        for (int i = 0; i < lines.size(); i++) {
            SidescanLine line = (SidescanLine) lines.toArray()[i];
            SampleDescription sample = getSample(lastSample, line, i);
            lastSample = sample;
            line.drawSlantedImage(colorMap, lineImage);
            java.awt.Image im2 = ImageUtils.getScaledImage(lineImage, width, 1, true);
            g2d.drawImage(im2, 0, lines.size()-i-1, null);
            raster.getSamples().add(sample);
        }
        g2d.dispose();
        try {
            write(img, "PNG", imgFile);
            String json = Converter.IndexedRasterToJsonString(raster);
            Files.write(jsonFile.toPath(), json.getBytes());
        } catch (Exception e) {
            log.error("error", e);
        }
    }

    boolean write(BufferedImage img, String formatName, File output) {

        ImageWriter writer = ImageIO.getImageWritersByFormatName(formatName).next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); // Needed see javadoc
        param.setCompressionQuality(1.0F); // Highest quality

        try {
            return ImageIO.write(img, formatName, output);
        } catch (Exception e) {
            log.error("error", e);
            return false;
        }
    }

    private static SampleDescription getSample(SampleDescription lastSample, SidescanLine l, long count) {
        SampleDescription sample = new SampleDescription();
        Instant instant = Instant.ofEpochMilli(l.getTimestampMillis());
        sample.setTimestamp(OffsetDateTime.ofInstant(instant, ZoneId.of("UTC")));
        sample.setIndex(count);
        Pose pose = new Pose();
        pose.setLatitude(l.getState().getPosition().getLatitudeDegs());
        pose.setLongitude(l.getState().getPosition().getLongitudeDegs());
        pose.setDepth(l.getState().getDepth());
        pose.setAltitude(l.getState().getAltitude());
        pose.setPhi(Math.toDegrees(l.getState().getRoll()));
        pose.setTheta(Math.toDegrees(l.getState().getPitch()));
        pose.setPsi(Math.toDegrees(l.getState().getYaw()));
        double deltaT = 1;
        if (lastSample != null)
            deltaT = (sample.getTimestamp().toInstant().toEpochMilli() / 1000d - lastSample.getTimestamp().toInstant().toEpochMilli() / 1000d);

        if (l.getState().getP() != 0 || lastSample == null)
            pose.setP(Math.toDegrees(l.getState().getP()));
        else {
            double lastRoll = lastSample.getPose().getPhi();
            pose.setP((Math.toDegrees(l.getState().getRoll()) - lastRoll) / deltaT);
        }
        if (l.getState().getQ() != 0 || lastSample == null)
            pose.setQ(Math.toDegrees(l.getState().getQ()));
        else {
            double lastPitch = lastSample.getPose().getTheta();
            pose.setQ((Math.toDegrees(l.getState().getPitch()) - lastPitch) / deltaT);
        }
        if (l.getState().getR() != 0 || lastSample == null)
            pose.setR(Math.toDegrees(l.getState().getR()));
        else {
            double lastYaw = lastSample.getPose().getPsi();
            pose.setR((Math.toDegrees(l.getState().getYaw()) - lastYaw) / deltaT);
        }
        pose.setU(l.getState().getU());
        pose.setV(l.getState().getV());
        pose.setW(l.getState().getW());
        sample.setPose(pose);
        return sample;
    }
}
