package pt.omst.rasterlib;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.MultiPointGeometry;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.GuiUtils;

@Slf4j
public class IndexedRasterPainter implements MapPainter {

    private final IndexedRaster raster;
    private Image image = null;
    private BufferedImage mosaicImage = null;
    LocationType mosaicNWcorner = null;
    private final MultiPointGeometry path = new MultiPointGeometry();
    private final MultiPointGeometry shape = new MultiPointGeometry();
    private final File parentFolder;
    private final AtomicInteger mosaicResolution = new AtomicInteger(0);
    private final Rectangle2D.Double bounds;
    private Future<?> mosaicTask = null;

    public IndexedRasterPainter(File parentFolder, IndexedRaster raster) throws IOException {
        this.raster = raster;
        this.parentFolder = parentFolder;
        readPath();
        readShape();
        bounds = IndexedRasterUtils.getBounds(raster);
    }

    public long getStartTimestamp() {
        if (raster.getSamples() != null && !raster.getSamples().isEmpty()) {
            return raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
        }
        return 0;
    }

    public void readPath() {
        path.setFilled(false);
        LocationType start = new LocationType(raster.getSamples().getFirst().getPose().getLatitude(), raster.getSamples().getFirst().getPose().getLongitude());

        path.setCenterLocation(start);
        //random color
        path.setColor(new Color((int) (Math.random() * 0x1000000)));
        for (SampleDescription sample : raster.getSamples()) {
            LocationType loc = new LocationType(sample.getPose().getLatitude(), sample.getPose().getLongitude());
            path.addPoint(loc);
        }
        path.setShape(false);        
    }

    public void readImage() {
        try {
            image = ImageIO.read(new File(parentFolder, raster.getFilename()));
            double swathWidth = raster.getSensorInfo().getMaxRange() - raster.getSensorInfo().getMinRange();
            double hScale = swathWidth / image.getWidth(null);
            LocationType start = new LocationType(
                    raster.getSamples().getFirst().getPose().getLatitude(),
                    raster.getSamples().getFirst().getPose().getLongitude());

            LocationType end = new LocationType(
                    raster.getSamples().getLast().getPose().getLatitude(),
                    raster.getSamples().getLast().getPose().getLongitude());

            double distance = start.getDistanceInMeters(end);
            double vScale = distance / image.getHeight(null);
        } catch (IOException e) {
            image = null;
            log.error("Error reading image {}/{}", parentFolder, raster.getFilename(), e);
        }
    }

    public void createMosaic(int resolution) {
        if (mosaicResolution.get() == resolution)
            return;
        mosaicResolution.set(resolution);
        mosaicImage = null;
        if (image == null)
            readImage();

        Point2D.Double[] shape = IndexedRasterUtils.getShape(raster);
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

        LocationType nw = new LocationType(maxLat, minLon);
        LocationType ne = new LocationType(maxLat, maxLon);
        LocationType sw = new LocationType(minLat, minLon);

        mosaicNWcorner = nw;

        double imgWidth = nw.getDistanceInMeters(ne) * resolution;
        double imgHeight = nw.getDistanceInMeters(sw) * resolution;
        BufferedImage tmp = new BufferedImage((int)imgWidth, (int)imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        AffineTransform before = g.getTransform();
        int step = 1;
        for (int i = 0; i < raster.getSamples().size(); i+=step) {
            if (resolution != mosaicResolution.get()) {
                System.out.println("Resolution changed, stopped mosaic creation");
                return;
            }
            SampleDescription sampleDescription = raster.getSamples().get(i);
            if (Math.abs(sampleDescription.getPose().getR()) > 5)
                continue;
            LocationType center = IndexedRasterUtils.getSampleLocation(raster, i);
            double[] nedOffsets = center.getOffsetFrom(nw);
            double range = raster.getSensorInfo().getMaxRange();
            g.translate(nedOffsets[1] * resolution, -nedOffsets[0] * resolution);
            g.rotate(Math.toRadians(sampleDescription.getPose().getPsi()));
            g.drawImage(image,(int)-(range * resolution), -step-1, (int)(range*resolution), step+1,
                    0, i, image.getWidth(null), i + step, null);
            g.setTransform(before);
        }
        g.dispose();
        mosaicImage = tmp;
        image = null;
    }

    public void readShape() {
        shape.setFilled(false);
        Point2D.Double[] ptShape = IndexedRasterUtils.getShape(raster);
        shape.setCenterLocation(new LocationType(ptShape[0].x, ptShape[0].y));
        shape.setColor(new Color(128, 64, 0));
        shape.setFilled(true);
        for (Point2D.Double point : ptShape) {
            LocationType loc = new LocationType(point.x, point.y);
            shape.addPoint(loc);
        }
        shape.addPoint(new LocationType(ptShape[0].x, ptShape[0].y));
        shape.setFilled(true);
        shape.setShape(true);
        shape.setOpaque(true);        
    }

    
    private void paintMosaic(Graphics2D g, SlippyMap renderer) {
        AffineTransform before = g.getTransform();
        if (mosaicImage != null) {
            double[] cornerPos = renderer.latLonToScreen(mosaicNWcorner.getLatitudeDegs(), mosaicNWcorner.getLongitudeDegs());
            Point2D corner = new Point2D.Double(cornerPos[0], cornerPos[1]);
            g.translate(corner.getX(), corner.getY());
            g.scale(renderer.getZoom() / mosaicResolution.get(), renderer.getZoom() / mosaicResolution.get());
            g.drawImage(mosaicImage, 0, 0, null);
            g.setTransform(before);
        }
    }

    private void cancelMosaicTask() {
        if (mosaicTask != null) {
            mosaicTask.cancel(true);
            mosaicTask = null;
        }
    }

    @Override
    public void paint(Graphics2D g, SlippyMap renderer) {
        g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF));
        g.setRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED));
        boolean visible = renderer.getVisibleCoordinates().intersects(bounds);
        if (!visible) {
            cancelMosaicTask();
            image = mosaicImage = null;
            mosaicResolution.set(0);
            return;
        }
        

        if (renderer.getZoom() < 2) {
            shape.paint(g, renderer);
            return;
        }

        int resolution = Math.min(20, (int) renderer.getZoom());
        if (mosaicResolution.get() != resolution) {
            cancelMosaicTask();
            mosaicTask = IndexedRasterUtils.background(() -> createMosaic(resolution));
            shape.paint(g, renderer);
        } else {
            paintMosaic(g, renderer);
        }
    }


    public void simplePaint(Graphics2D g, SlippyMap renderer) {
        LocationType center = new LocationType(
            raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLatitude(),
            raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLongitude());
        
        Point2D position = renderer.getScreenPosition(center);
        g.setColor(Color.RED);
        g.fill(new Rectangle2D.Double(position.getX() - 3, position.getY() - 3, 6, 6));
    }



    public static void main(String[] args) {
        SlippyMap renderer = new SlippyMap(new ArrayList<>());
        GuiUtils.testFrame(renderer, "Indexed Raster Painter");

        File folder = new File("/LOGS/REP/");
        LocationType center = null;
        
        // Use an executor for parallel processing of raster files
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        for (File index : IndexedRasterUtils.findRasterFiles(folder)) {
            System.out.println("Processing: " + index);
            File indexFile = index;
            
            executor.submit(() -> {
                try {
                    IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(indexFile.toPath()));
                    IndexedRasterPainter painter = new IndexedRasterPainter(indexFile.getParentFile(), raster);
                    renderer.addRasterPainter(painter);
                    //renderer.repaint();
                } catch (IOException e) {
                    log.error("Error loading raster: " + indexFile, e);
                }
            });
            
            // Set center from first raster
            if (center == null) {
                try {
                    IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(index.toPath()));
                    center = new LocationType(
                        raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLatitude(), 
                        raster.getSamples().get(raster.getSamples().size() / 2).getPose().getLongitude());
                    renderer.focus(center);
                } catch (IOException e) {
                    log.error("Error reading center from first raster", e);
                }
            }
        }
        
        executor.shutdown();
    }
}
