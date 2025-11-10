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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
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
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.sidescan.ISidescanLine;
import pt.omst.neptus.sidescan.SidescanHistogramNormalizer;
import pt.omst.neptus.sidescan.SidescanLine;
import pt.omst.neptus.sidescan.SidescanParameters;
import pt.omst.neptus.sidescan.SidescanParser;
import pt.omst.neptus.sidescan.SidescanParserFactory;
import pt.omst.neptus.util.StreamUtil;
import pt.omst.neptus.util.ZipUtils;
import pt.omst.rasterlib.contacts.CompressedContact;

@Slf4j
public class IndexedRasterUtils {

    private static ExecutorService executor = null;

    private static final ArrayList<Future<?>> runningTasks = new ArrayList<>();

    public synchronized static CompletableFuture<?> background(Runnable task) {
        if (executor == null)
            executor = Executors.newFixedThreadPool(5);

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
        Point2D.Double[] shape = getShape(raster);
        double minLat = shape[0].x;
        double maxLat = shape[0].x;
        double minLon = shape[0].y;
        double maxLon = shape[0].y;

        for (Point2D.Double point : shape) {
            minLat = Math.min(minLat, point.x);
            maxLat = Math.max(maxLat, point.x);
            minLon = Math.min(minLon, point.y);
            maxLon = Math.max(maxLon, point.y);
        }

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

    @Data
    public static class RasterContactInfo {
        private String label;
        private Annotation boxAnnotation = null;
        private Annotation classification = null;
        private SensorInfo sensorInfo = null;
        private LocationType center;
        private double minRange;
        private double maxRange;
        private long startTimeStamp;
        private long endTimeStamp;        
    }

    public static RasterContactInfo getContactInfo(CompressedContact contact) {
        Contact c = contact.getContact();
        RasterContactInfo info = new RasterContactInfo();
        info.center = new LocationType(c.getLatitude(), c.getLongitude());
        info.label = c.getLabel();

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
            try {
                InputStream is = ZipUtils.getFileInZip(contact.getZctFile().getAbsolutePath(),
                        sssObservation.getRasterFilename());
                String rasterJson = StreamUtil.copyStreamToString(is);
                raster = Converter.IndexedRasterFromJsonString(rasterJson);
                raster.getSamples().sort( (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));
                sensorInfo = raster.getSensorInfo();
                info.minRange = sensorInfo.getMinRange();
                info.maxRange = sensorInfo.getMaxRange();
                
                info.startTimeStamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
                info.endTimeStamp = raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                //System.out.println(info.label+" is from "+sdf.format(new Date(info.startTimeStamp))+" to "+sdf.format(new Date(info.endTimeStamp)));
                if (boxAnnotation != null) {
                    double newMinRange = info.minRange + boxAnnotation.getNormalizedX() * (info.maxRange - info.minRange);
                    double newMaxRange = info.minRange + boxAnnotation.getNormalizedX2() * (info.maxRange - info.minRange);
                    double newStartTimestamp = info.startTimeStamp + boxAnnotation.getNormalizedY() * (info.endTimeStamp - info.startTimeStamp);
                    double newEndTimestamp = info.startTimeStamp + boxAnnotation.getNormalizedY2() * (info.endTimeStamp - info.startTimeStamp);
                    info.boxAnnotation = boxAnnotation;
                    info.minRange = newMinRange;
                    info.maxRange = newMaxRange;
                    info.startTimeStamp = (long) newStartTimestamp;
                    info.endTimeStamp = (long) newEndTimestamp;
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
