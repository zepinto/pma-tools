//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.StreamUtil;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.sidescan.ISidescanLine;
import pt.omst.sidescan.SidescanHistogramNormalizer;
import pt.omst.sidescan.SidescanLine;
import pt.omst.sidescan.SidescanParameters;
import pt.omst.sidescan.SidescanParser;
import pt.omst.sidescan.SidescanParserFactory;

@Slf4j
public class IndexedRasterUtils {

    private static ExecutorService executor = null;

    private static final ArrayList<Future<?>> runningTasks = new ArrayList<>();

    public synchronized static CompletableFuture<?> background(Runnable task) {
        if (executor == null) {
            int totalProcessors = Runtime.getRuntime().availableProcessors();
            if (totalProcessors > 4) {
                totalProcessors -= 2; // leave some CPU for other tasks
            }
            executor = Executors.newFixedThreadPool(totalProcessors);
        }
        CompletableFuture<?> newTask = CompletableFuture.runAsync(task, executor);
        runningTasks.add(newTask);
        return newTask;
    }

    public static double getHeight(double slantRangeShadowStart, double slantRangeShadowEnd, double altitude) {
        double S = Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude) -
                Math.sqrt(slantRangeShadowEnd * slantRangeShadowEnd - altitude * altitude);
        S = Math.abs(S);
        return (altitude * S) / Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude);
    }

    public static void stopAll() {
        for (Future<?> task : runningTasks) {
            if (!task.isDone())
                task.cancel(true);
        }
        executor.shutdownNow();
        executor = null;
        runningTasks.clear();
    }

    public static Point2D.Double getCenter(IndexedRaster raster) {
        SampleDescription center = raster.getSamples().get(raster.getSamples().size() / 2);
        return new Point2D.Double(center.getPose().getLatitude(), center.getPose().getLongitude());
    }

    public static LocationType getSampleLocation(IndexedRaster raster, int index) {
        SampleDescription center = raster.getSamples().get(index);
        return new LocationType(center.getPose().getLatitude(), center.getPose().getLongitude());
    }

    public static Point2D.Double[] getLinePosition(IndexedRaster raster, int index) {
        SampleDescription sdesc = raster.getSamples().get(index);
        LocationType loc = new LocationType(sdesc.getPose().getLatitude(), sdesc.getPose().getLongitude());
        LocationType portLoc = new LocationType(loc);
        LocationType stbdLoc = new LocationType(loc);

        portLoc.setAzimuth(sdesc.getPose().getPsi() - 90);
        portLoc.setOffsetDistance(raster.getSensorInfo().getMinRange() * -1);
        portLoc.convertToAbsoluteLatLonDepth();

        stbdLoc.setAzimuth(sdesc.getPose().getPsi() + 90);
        stbdLoc.setOffsetDistance(raster.getSensorInfo().getMaxRange());
        stbdLoc.convertToAbsoluteLatLonDepth();

        return new Point2D.Double[] {
                new Point2D.Double(portLoc.getLatitudeDegs(),
                        portLoc.getLongitudeDegs()),
                new Point2D.Double(stbdLoc.getLatitudeDegs(),
                        stbdLoc.getLongitudeDegs()) };
    }

    public static Point2D.Double[] getShape(IndexedRaster raster) {
        ArrayList<Point2D.Double> points = new ArrayList<>();

        Point2D.Double[] pos = getLinePosition(raster, 0);
        points.add(pos[0]);
        points.add(pos[1]);
        pos = getLinePosition(raster, raster.getSamples().size() - 1);
        points.add(pos[1]);
        points.add(pos[0]);
        List<Point2D.Double> chull = computeConvexHull(points);
        return chull.toArray(new Point2D.Double[0]);
    }

    public static Rectangle2D.Double getBounds(IndexedRaster raster) {
        int numSamples = raster.getSamples().size();
        if (numSamples == 0) {
            return new Rectangle2D.Double(0, 0, 0, 0);
        }
        
        // Initialize with first line position
        Point2D.Double[] firstPos = getLinePosition(raster, 0);
        double minLat = Math.min(firstPos[0].x, firstPos[1].x);
        double maxLat = Math.max(firstPos[0].x, firstPos[1].x);
        double minLon = Math.min(firstPos[0].y, firstPos[1].y);
        double maxLon = Math.max(firstPos[0].y, firstPos[1].y);

        int step = Math.max(1, numSamples / 100); // At most 100 samples
        for (int i = step; i < numSamples; i += step) {
            Point2D.Double[] pos = getLinePosition(raster, i);
            minLat = Math.min(minLat, Math.min(pos[0].x, pos[1].x));
            maxLat = Math.max(maxLat, Math.max(pos[0].x, pos[1].x));
            minLon = Math.min(minLon, Math.min(pos[0].y, pos[1].y));
            maxLon = Math.max(maxLon, Math.max(pos[0].y, pos[1].y));
        }
        
        // Always include last line
        Point2D.Double[] lastPos = getLinePosition(raster, numSamples - 1);
        minLat = Math.min(minLat, Math.min(lastPos[0].x, lastPos[1].x));
        maxLat = Math.max(maxLat, Math.max(lastPos[0].x, lastPos[1].x));
        minLon = Math.min(minLon, Math.min(lastPos[0].y, lastPos[1].y));
        maxLon = Math.max(maxLon, Math.max(lastPos[0].y, lastPos[1].y));

        return new Rectangle2D.Double(minLon, minLat, maxLon - minLon, maxLat - minLat);
    }

    public static java.util.List<Point2D.Double> computeConvexHull(List<Point2D.Double> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("Convex hull requires at least 3 points");
        }

        // Find the point with the lowest y-coordinate, break ties by x-coordinate
        Point2D.Double start = points.stream()
                .min(Comparator.comparingDouble((Point2D.Double p) -> p.y).thenComparingDouble(p -> p.x)).orElse(null);

        // Sort the points by polar angle with respect to the start point
        points.sort((p1, p2) -> {
            double angle1 = Math.atan2(p1.y - start.y, p1.x - start.x);
            double angle2 = Math.atan2(p2.y - start.y, p2.x - start.x);
            if (angle1 != angle2) {
                return Double.compare(angle1, angle2);
            }
            return Double.compare(start.distanceSq(p1), start.distanceSq(p2));
        });

        // Use a stack to determine the convex hull
        Stack<Point2D.Double> hull = new Stack<>();
        for (Point2D.Double point : points) {
            while (hull.size() >= 2 && !isCounterClockwise(hull.get(hull.size() - 2), hull.peek(), point)) {
                hull.pop();
            }
            hull.push(point);
        }

        return new ArrayList<>(hull);
    }

    // Check if three points make a counter-clockwise turn
    private static boolean isCounterClockwise(Point2D.Double a, Point2D.Double b, Point2D.Double c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) > 0;
    }

    static final LinkedHashMap<Float, String> SENSOR_TYPES = new LinkedHashMap<>();

    static {
        SENSOR_TYPES.put(0.0f, "Unknown");
        SENSOR_TYPES.put(900000f, "Klein 3500 UUV HF");
        SENSOR_TYPES.put(450000f, "Klein 3500 UUV LF");
    }

    public static String getSidescanModel(SidescanParser ssparser) {
        switch (ssparser.getClass().getSimpleName()) {
            case "SdfSidescanParser":
                return "Klein UUV 3500";
            case "JsfSidescanParser":
                return "Edgetech 2200";
            case "SdsParser":
                return "Marine Sonic";
            default:
                return "Unknown";
        }
    }

    public static String exportSidescan(File folder, Consumer<String> progressCallback) {
        return exportSidescan(folder, -1, 500, progressCallback);
    }

    public static SensorInfo getSensorInfo(SidescanParser parser) {
        SensorInfo sensorInfo = new SensorInfo();
        SidescanParameters params = parser.getDefaultParams();
        ISidescanLine line = null;
        
        // Check if parser has any subsystems
        if (parser.getSubsystemList().isEmpty()) {
            return null;
        }
        
        int subsystem = parser.getSubsystemList().getLast();
        var lines = parser.getLinesBetween(parser.firstPingTimestamp(), parser.firstPingTimestamp() + 1000, subsystem, params);
        
        // Check if any lines were retrieved
        if (lines.isEmpty()) {
            log.warn("No sidescan lines found, returning empty sensor info");
            return null;
        }
        
        line = lines.getFirst();
        sensorInfo.setSystemName("rasterlib");
        sensorInfo.setFrequency(line.getFrequency() / 1000d);
        if (SENSOR_TYPES.containsKey(line.getFrequency()))
            sensorInfo.setSensorModel(SENSOR_TYPES.get(line.getFrequency()));
        else
            sensorInfo.setSensorModel("Sidescan " + line.getFrequency() / 1000 + "kHz");
        sensorInfo.setMinRange((double) -line.getRange());
        sensorInfo.setMaxRange((double) line.getRange());
        return sensorInfo;
    }

    public static String exportSidescan(File folder, int sub, int maxLines, Consumer<String> progressCallback) {
        if (!new File(folder, "mra").exists()) {
            new File(folder, "mra").mkdirs();
        }

        SidescanParser ssparser = SidescanParserFactory.build(folder);
        SidescanParameters params = ssparser.getDefaultParams();
        SidescanHistogramNormalizer egnCorrection = SidescanHistogramNormalizer.create(ssparser, folder);
        final int subsystem = sub > 0? sub : ssparser.getSubsystemList().getLast();
        
        long start = ssparser.firstPingTimestamp();
        long end = ssparser.lastPingTimestamp();

        ISidescanLine line = null;

        var lines = ssparser.getLinesBetween(start, start + 1000, subsystem, params);
        while (lines.isEmpty() && start < end) {
            start += 1000;
            lines = ssparser.getLinesBetween(start, start + 1000, subsystem, params);
        }

        line = lines.getFirst();

        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setSensorModel(getSidescanModel(ssparser));
        sensorInfo.setSystemName("unknown");
        sensorInfo.setFrequency(line.getFrequency() / 1000d);
        if (SENSOR_TYPES.containsKey(line.getFrequency()))
            sensorInfo.setSensorModel(SENSOR_TYPES.get(line.getFrequency()));
        else
            sensorInfo.setSensorModel("Sidescan " + line.getFrequency() / 1000 + "kHz");
        sensorInfo.setMinRange((double) -line.getRange());
        sensorInfo.setMaxRange((double) line.getRange());
        long lastTime = ssparser.lastPingTimestamp();

        LinkedList<SidescanLine> lineQueue = new LinkedList<>();
        List<Future<?>> tasks = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            File outputDir = new File(folder, "rasterIndex");
            if (outputDir.exists())
                if (!outputDir.delete()) {
                    log.error("Could not delete output directory {}", outputDir.getAbsolutePath());
                }
            if (!outputDir.mkdirs()) {
                log.error("Could not create output directory {}", outputDir.getAbsolutePath());
            }
            for (long time = ssparser.firstPingTimestamp(); time <= lastTime; time += 1000) {
                lines = ssparser.getLinesBetween(time, time + 1000, subsystem, params);
                if (lines.isEmpty())
                    continue;
                for (SidescanLine line2 : lines) {
                    line2.applyEGN(egnCorrection, subsystem);
                }
                
                lineQueue.addAll(lines);
                if (lineQueue.size() >= maxLines) {
                    int timestamp = (int) (lineQueue.peek().getTimestampMillis() / 1000);
                    ArrayList<SidescanLine> firstLines = new ArrayList<>(maxLines);
                    for (int i = 0; i < maxLines; i++) {
                        firstLines.add(lineQueue.poll());
                    }
                    Future<?> task = executor.submit(() -> {
                        File f = new File(outputDir, "sss_" + subsystem + "_" + timestamp + ".json");
                        new IndexedRasterCreator(f, sensorInfo).export(firstLines);
                        if (progressCallback != null) {
                            progressCallback.accept("Exported " + f.getAbsolutePath());
                        }
                        log.info("Exported " + f.getAbsolutePath());                        
                    });
                    tasks.add(task);
                }
            }

            // Process any remaining lines
            if (!lineQueue.isEmpty()) {
                int timestamp = (int) (lineQueue.peek().getTimestampMillis() / 1000);
                ArrayList<SidescanLine> remainingLines = new ArrayList<>(lineQueue);
                Future<?> task = executor.submit(() -> {
                    File f = new File(outputDir, "sss_" + subsystem + "_" + timestamp + ".json");
                    new IndexedRasterCreator(f, sensorInfo).export(remainingLines);
                    if (progressCallback != null) {
                        progressCallback.accept("Exported " + f.getAbsolutePath());
                    }
                    log.info("Exported " + f.getAbsolutePath());
                });
                tasks.add(task);
                lineQueue.clear();
            }

            // Wait for all tasks to complete
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error waiting for export task to complete", e);
                }
            }
        } finally {
            log.info("Exported all lines.");
        }
        if (progressCallback != null) {
            progressCallback.accept("Export completed successfully.");
        }
        return "Exported " + folder.getAbsolutePath();
    }

   
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

    public static List<IndexedRaster> loadRasters(File folder) {
        List<File> rasterFiles = findRasterFiles(folder);
        List<IndexedRaster> rasters = new ArrayList<>();
        for (File file : rasterFiles) {
            try {
                String json = Files.readString(file.toPath());
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(json);
                rasters.add(raster);
            } catch (IOException e) {
                log.error("Error loading raster file: " + file.getAbsolutePath(), e);
            }
        }
        return rasters;
    }

    @Data
    public static class RasterContactInfo {
        private String label;
        private Annotation boxAnnotation = null;
        private Annotation positionAnnotation = null;
        private Annotation classification = null;
        private SensorInfo sensorInfo = null;
        @Getter
        @Setter
        private LocationType center;
        private double minRange;
        private double maxRange;
        private long startTimeStamp;
        private long endTimeStamp;        
        private CompressedContact contact;
        
        /**
         * Returns true if this contact has a customized position measurement.
         */
        public boolean hasCustomPosition() {
            return positionAnnotation != null;
        }
        
        /**
         * Gets the customized position latitude if available, otherwise returns null.
         */
        public Double getCustomLatitude() {
            if (positionAnnotation != null && contact != null) {
                Optional<Observation> obs = contact.getContact().getObservations().stream()
                    .filter(o -> o.getRasterFilename() != null)
                    .findFirst();
                if (obs.isPresent()) {
                    return obs.get().getLatitude();
                }
            }
            return null;
        }
        
        /**
         * Gets the customized position longitude if available, otherwise returns null.
         */
        public Double getCustomLongitude() {
            if (positionAnnotation != null && contact != null) {
                Optional<Observation> obs = contact.getContact().getObservations().stream()
                    .filter(o -> o.getRasterFilename() != null)
                    .findFirst();
                if (obs.isPresent()) {
                    return obs.get().getLongitude();
                }
            }
            return null;
        }
    }

    public static RasterContactInfo getContactInfo(CompressedContact contact) {
        Contact c = contact.getContact();
        RasterContactInfo info = new RasterContactInfo();
        info.center = new LocationType(c.getLatitude(), c.getLongitude());
        info.label = c.getLabel();
        info.setContact(contact);
        Observation sssObservation = null;
        Annotation boxAnnotation = null;
        SensorInfo sensorInfo = null;
        IndexedRaster raster = null;

        Optional<Observation> optionalObs = contact.getContact().getObservations().stream()
                .filter(obs -> obs.getRasterFilename() != null)
                .findFirst();
        if (optionalObs.isPresent()) {
            sssObservation = optionalObs.get();
            Optional<Annotation> classificationOptional = sssObservation.getAnnotations().stream()
                    .filter(ann -> ann.getAnnotationType() == AnnotationType.CLASSIFICATION)
                    .findFirst();
            if (classificationOptional.isPresent()) {
                info.classification = classificationOptional.get();
            }
            Optional<Annotation> boxOptional = sssObservation.getAnnotations().stream()
                    .filter(ann -> ann.getAnnotationType() == AnnotationType.MEASUREMENT
                            && ann.getMeasurementType() == MeasurementType.BOX)
                    .findFirst();
            if (boxOptional.isPresent()) {
                boxAnnotation = boxOptional.get();
            }
            // Check for position measurement annotation
            Optional<Annotation> positionOptional = sssObservation.getAnnotations().stream()
                    .filter(ann -> ann.getAnnotationType() == AnnotationType.MEASUREMENT
                            && ann.getMeasurementType() == MeasurementType.POSITION)
                    .findFirst();
            if (positionOptional.isPresent()) {
                info.positionAnnotation = positionOptional.get();
            }
            try {
                InputStream is = ZipUtils.getFileInZip(contact.getZctFile().getAbsolutePath(),
                        sssObservation.getRasterFilename());
                String rasterJson = StreamUtil.copyStreamToString(is);
                raster = Converter.IndexedRasterFromJsonString(rasterJson);
                //raster.getSamples().sort( (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
                sensorInfo = raster.getSensorInfo();
                info.minRange = sensorInfo.getMinRange();
                info.maxRange = sensorInfo.getMaxRange();
                
                info.startTimeStamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
                info.endTimeStamp = raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli();
                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                //log.info("Contact {} is from {} to {}", info.label,
                //        sdf.format(info.startTimeStamp), sdf.format(info.endTimeStamp));
                //System.out.println(info.label+" is from "+sdf.format(new Date(info.startTimeStamp))+" to "+sdf.format(new Date(info.endTimeStamp)));
                if (boxAnnotation != null) {
                    double newMinRange = info.minRange + boxAnnotation.getNormalizedX() * (info.maxRange - info.minRange);
                    double newMaxRange = info.minRange + boxAnnotation.getNormalizedX2() * (info.maxRange - info.minRange);
                    double newStartTimestamp = info.startTimeStamp + boxAnnotation.getNormalizedY() * (info.endTimeStamp - info.startTimeStamp);
                    double newEndTimestamp = info.startTimeStamp + boxAnnotation.getNormalizedY2() * (info.endTimeStamp - info.startTimeStamp);
                    info.boxAnnotation = boxAnnotation;
                    // Ensure minRange is always <= maxRange (box coordinates might be drawn in any direction)
                    info.minRange = Math.min(newMinRange, newMaxRange);
                    info.maxRange = Math.max(newMinRange, newMaxRange);
                    info.startTimeStamp = (long) Math.min(newStartTimestamp, newEndTimestamp);
                    info.endTimeStamp = (long) Math.max(newStartTimestamp, newEndTimestamp);
                }
            }
            catch (IOException e) {
                log.error("Error reading raster file: " + e.getMessage());
            }
        } else {
            info.minRange = info.maxRange = 0;
            info.startTimeStamp = info.endTimeStamp = contact.getTimestamp();
        }
        return info;
    }

}
