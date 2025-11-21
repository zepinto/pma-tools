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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.colormap.ColorMap;
import pt.lsts.neptus.colormap.ColorMapFactory;
import pt.lsts.neptus.mra.SidescanLogMarker;
import pt.lsts.neptus.util.ImageUtils;
import pt.omst.sidescan.ISidescanLine;
import pt.omst.sidescan.SidescanHistogramNormalizer;
import pt.omst.sidescan.SidescanLine;
import pt.omst.sidescan.SidescanMarkerUtils;
import pt.omst.sidescan.SidescanParameters;
import pt.omst.sidescan.SidescanParser;
import pt.omst.sidescan.SidescanParserFactory;

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
     * 
     * @param outputFile The output file. Both the image and the json file will be
     *                   created with the same name but different extensions
     * @param info       The sensor info. This will be used to populate the sensor
     *                   info field in the json file
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
     * Exports the given marker to the output file, using the given parser and
     * subsystem
     * 
     * @param marker    The marker to export
     * @param parser    The parser to use
     * @param subsystem The subsystem to use
     * @param margin    The margin to use, this is the number of pixels to include
     *                  on each side of the marker
     */
    public IndexedRaster export(SidescanLogMarker marker, SidescanParser parser, SidescanHistogramNormalizer normalizer, int subsystem, int margin) {
        ArrayList<SidescanLine> lines = SidescanMarkerUtils.getNormalizedLines(marker, parser, subsystem, margin,
                margin);       
        if (normalizer != null) {
            for (SidescanLine l : lines) {
                normalizer.normalize(l, subsystem);
            }
        }
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
        raster.getSensorInfo().setFrequency((double) (firstLine.getFrequency() / 1000.0f));
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

        SidescanLine middleLine = lines.get(lines.size() / 2);
        double slantedX = SidescanMarkerUtils.getSlantDistance(middleLine.getState().getAltitude(), marker.getX());
        int sssPixelWidth = lines.getFirst().getData().length;
        double sssWorldWidth = SidescanMarkerUtils.getSlantDistance(middleLine.getState().getAltitude(),
                middleLine.getRange() * 2);
        double sssPixelResolution = sssWorldWidth / sssPixelWidth;
        double markWorldWidth = marker.getFullRange();
        double markWorldPos = lines.getFirst().getRange() + slantedX;
        // System.out.println(marker.getLabel()+":");
        // System.out.println(" - Mark world pos: " + markWorldPos);
        // System.out.println(" - Mark world width: " + markWorldWidth);
        // System.out.println(" - SSS world width: " + sssWorldWidth);
        // System.out.println(" - SSS pixel width: " + sssPixelWidth);
        // System.out.println(" - SSS pixel resolution: " + sssPixelResolution);
        // System.out.println(" - Marker position: " + marker.getX()+",
        // "+marker.getY());
        double rangeStart = (marker.getX() - markWorldWidth / 2d);
        double rangeEnd = (marker.getX() + markWorldWidth / 2d);

        raster.getSensorInfo().setMaxRange(rangeEnd);
        raster.getSensorInfo().setMinRange(rangeStart);

        // System.out.println(" - Marker start: " +(markWorldPos - markWorldWidth/2));
        // System.out.println(" - Marker end: " +(markWorldPos + markWorldWidth/2));

        int markerStartPos = (int) ((markWorldPos - markWorldWidth / 2) / sssPixelResolution);
        int markerEndPos = (int) ((markWorldPos + markWorldWidth / 2) / sssPixelResolution);

        markerStartPos = Math.max(0, markerStartPos - margin);
        markerEndPos = Math.min(sssPixelWidth, markerEndPos + margin);

        // System.out.println(" - Marker start pos: " + markerStartPos);
        // System.out.println(" - Marker end pos: " + markerEndPos);
        BufferedImage myImage = new BufferedImage(markerEndPos - markerStartPos, lines.size(),
                BufferedImage.TYPE_INT_RGB);

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
     * 
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
            g2d.drawImage(im2, 0, lines.size() - i - 1, null);
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
            deltaT = (sample.getTimestamp().toInstant().toEpochMilli() / 1000d
                    - lastSample.getTimestamp().toInstant().toEpochMilli() / 1000d);

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

    static final LinkedHashMap<Float, String> SENSOR_TYPES = new LinkedHashMap<>();
    static {
        SENSOR_TYPES.put(0.0f, "Unknown");
        SENSOR_TYPES.put(900000f, "Klein 3500 UUV HF");
        SENSOR_TYPES.put(450000f, "Klein 3500 UUV LF");
    }

    static final int MaxWidth = 5000;
    static final int MaxHeight = 500;

    public static void exportRasters(File folder, int subsystem, Consumer<String> progress) {
        SidescanParser ssparser = null;
        ExecutorService executor = null;
        
        try {
            ssparser = SidescanParserFactory.build(folder);
           
            if (ssparser.getSubsystemList().isEmpty()) {
                log.error("No subsystems found in sidescan folder {}", folder.getAbsolutePath());
                return;
            }
            

            SidescanParameters params = ssparser.getDefaultParams();
            final int sub = subsystem > 0 ? subsystem : ssparser.getSubsystemList().getLast();
            System.out.println("Using subsystem: " + sub);
            
            if (progress == null)
                progress = (s) -> {
                };
            progress.accept("Starting raster export for folder " + folder.getAbsolutePath());
            SidescanHistogramNormalizer normalizer = SidescanHistogramNormalizer.create(ssparser, folder);
        
        long start = ssparser.firstPingTimestamp();

        ISidescanLine line = null;

        progress.accept("Reading first lines to get sensor info...");
        var lines = ssparser.getLinesBetween(start, start + 1000, sub, params);
        if (lines.isEmpty()) {
            log.error("No sidescan lines found in folder {} for subsystem {}", folder.getAbsolutePath(), sub);
            return;
        }
        line = lines.getFirst();

        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setSystemName("rasterlib");
        sensorInfo.setFrequency(line.getFrequency() / 1000d);
        if (SENSOR_TYPES.containsKey(line.getFrequency()))
            sensorInfo.setSensorModel(SENSOR_TYPES.get(line.getFrequency()));
        else
            sensorInfo.setSensorModel("Sidescan " + line.getFrequency() / 1000 + "kHz");
        sensorInfo.setMinRange((double) -line.getRange());
        sensorInfo.setMaxRange((double) line.getRange());
        long lastTime = ssparser.lastPingTimestamp();

        LinkedList<SidescanLine> lineQueue = new LinkedList<>();

        executor = Executors.newVirtualThreadPerTaskExecutor();

        progress.accept("Processing lines...");
        File outputDir = new File(folder, "rasterIndex");
        if (outputDir.exists())
            if (!outputDir.delete()) {
                log.error("Could not delete output directory {}", outputDir.getAbsolutePath());
            }
        if (!outputDir.mkdirs()) {
            log.error("Could not create output directory {}", outputDir.getAbsolutePath());
        }
        for (long time = ssparser.firstPingTimestamp(); time <= lastTime; time += 1000) {
            lines = ssparser.getLinesBetween(time, time + 1000, sub, params);
            if (lines.isEmpty())
                continue;
            lineQueue.addAll(lines);
            if (lineQueue.size() >= MaxHeight) {
                int timestamp = (int) (lineQueue.peek().getTimestampMillis() / 1000);
                ArrayList<SidescanLine> firstLines = new ArrayList<>(MaxHeight);
                for (int i = 0; i < MaxHeight; i++) {
                    firstLines.add(lineQueue.poll());
                }
                executor.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    IndexedRasterCreator creator = new IndexedRasterCreator(
                            new File(outputDir, "sss_" + sub + "_" + timestamp + ".json"), sensorInfo);
                    for (SidescanLine l : firstLines)
                        normalizer.normalize(l, sub);                    
                    creator.export(firstLines);
                    log.debug("Processed " + firstLines.size() + " lines in "
                            + (System.currentTimeMillis() - startTime) + "ms");
                });
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS)) {
                log.error("Timeout while processing sidescan data");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while processing sidescan data", e);
        }
        progress.accept("Raster export completed.");
        
        } finally {
            // Cleanup resources
            if (ssparser != null) {
                try {
                    ssparser.cleanup();
                } catch (Exception e) {
                    log.warn("Error during parser cleanup: {}", e.getMessage());
                }
            }
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
        }
    }

    public static void main(String[] args) {
        File folder = new File("/home/zp/Desktop/data-samples/153320_03-fat-sidescan-depth/");
        exportRasters(folder, -1, (s) -> {
            System.out.println(s);
        });
    }
}
