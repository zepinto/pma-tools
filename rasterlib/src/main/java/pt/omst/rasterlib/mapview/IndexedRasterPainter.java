package pt.omst.rasterlib.mapview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.SampleDescription;

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

    public IndexedRasterPainter(File parentFolder, IndexedRaster raster)  throws IOException{
        this.raster = raster;
        this.parentFolder = parentFolder;
        //readPath();
        readShape();
        bounds = IndexedRasterUtils.getBounds(raster);
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
        shape.setShape(true);        
        shape.setOpaque(true);
    }

    private void simpleFill(Graphics2D g, SlippyMap renderer) {
        AffineTransform before = g.getTransform();
        for (int i = 0; i < raster.getSamples().size(); i += 3) {
            Point2D.Double[] pos = IndexedRasterUtils.getLinePosition(raster, i);
            double[] startPos = renderer.latLonToPixel(pos[0].x, pos[0].y);
            double[] endPos = renderer.latLonToPixel(pos[1].x, pos[1].y);
            g.drawLine((int) startPos[0], (int) startPos[1], (int) endPos[0], (int) endPos[1]);
        }
        g.setTransform(before);
    }

    private void paintMosaic(Graphics2D g, SlippyMap renderer) {
        AffineTransform before = g.getTransform();
        if (mosaicImage != null) {
            double[] nw = renderer.latLonToPixel(mosaicNWcorner.getLatitudeDegs(), mosaicNWcorner.getLongitudeDegs());
            Point2D.Double corner = new Point2D.Double(nw[0], nw[1]);

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
        double[] rendererBounds = renderer.getVisibleBounds();
        Rectangle2D.Double rBounds = new Rectangle2D.Double(
                rendererBounds[0], rendererBounds[1],
                rendererBounds[2] - rendererBounds[0],
                rendererBounds[3] - rendererBounds[1]);
        if (!rBounds.intersects(bounds)) {
            cancelMosaicTask();
            image = mosaicImage = null;
            mosaicResolution.set(0);
            return;
        }

        if (renderer.getZoom() < 2) {
            cancelMosaicTask();
            image = mosaicImage = null;
            mosaicResolution.set(0);
            simplePaint(g, renderer);
            return;
        }

        int resolution = Math.min(20, (int)renderer.getZoom());
        if (mosaicResolution.get() != resolution) {
            cancelMosaicTask();
            mosaicTask = IndexedRasterUtils.background(() -> createMosaic(resolution));
            simplePaint(g, renderer);
        }
        else {
            paintMosaic(g, renderer);
        }
    }


    public void simplePaint(Graphics2D g, SlippyMap renderer) {
        AffineTransform before = g.getTransform();
        shape.paint(g, renderer);
        g.setTransform(before);
    }
}
