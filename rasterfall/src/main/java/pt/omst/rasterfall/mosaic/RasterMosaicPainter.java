package pt.omst.rasterfall.mosaic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.MultiPointGeometry;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.SampleDescription;
import pt.omst.rasterlib.SensorInfo;

@Slf4j
public class RasterMosaicPainter implements MapPainter, AutoCloseable {

    // The raster being painted
    private final IndexedRaster raster;
    // The image associated with the raster
    //private BufferedImage image = null;
    // The generated mosaic image at the current resolution
    private BufferedImage mosaicImage = null;
    // The northwest corner location of the mosaic
    private LocationType mosaicNWcorner = null;

    // The shape representing the raster, painted when zoomed out
    private final MultiPointGeometry shape = new MultiPointGeometry();
    // The parent folder where raster images are stored
    private final File parentFolder;
    // The currently built resolution of the mosaic (pixels per meter)
    private final AtomicInteger mosaicResolution = new AtomicInteger(0);
    // The geographic bounds of the raster
    private final Rectangle2D.Double bounds;

    
    private Future<?> mosaicTask = null;

    public RasterMosaicPainter(File parentFolder, IndexedRaster raster) throws IOException {
        this.raster = raster;
        this.parentFolder = parentFolder;
        this.bounds = IndexedRasterUtils.getBounds(raster);
        readShape();
    }


    public BufferedImage readImage() {
       // if (image != null)
        //    return image;
        try {
            return ImageIO.read(new File(parentFolder, raster.getFilename()));
        } catch (IOException e) {
            //image = null;
            log.error("Error reading image {}/{}", parentFolder, raster.getFilename(), e);
        }
        return null;
    }


    public void setAlphaChannel(BufferedImage img, SampleDescription sample, SensorInfo sensor) {
        int width = img.getWidth();
        double maxRange = sensor.getMaxRange();
        double altitude = sample.getPose().getAltitude();
        double pixelSize = (maxRange) / (double) (width/2);
        int maxAlpha = 255;
        double targetAltitude = maxRange / 10.0; // 10% of max range
        // if targetAltitude is bigger than altitude, decreate maxAlpha
        if (altitude > targetAltitude+1) {
            maxAlpha = (int) (200 * (targetAltitude / altitude));
        }
        if (sample.getPose().getU() < 0.5) {
            // Low speed, set low alpha
            maxAlpha = 200;
        }
        
        if (sample.getPose().getR() > 3 || sample.getPose().getR() < -3) {
            // High rotation, set low alpha
            maxAlpha = 220;
        }

        for (int x = width / 2; x < width; x++) {
            int rgb = img.getRGB(x, 0);
            Color color = new Color(rgb, true);
            int alpha = maxAlpha;
            // Calculate range for this pixel
            double range = (x - width / 2) * pixelSize;
            if (range <= altitude)
                alpha = 0;
            else {
                double peak = altitude * 2;
                if (range <= peak) {
                    double t = (range - altitude) / altitude; // 0 to 1
                    alpha = (int) (maxAlpha * (2 * t - t * t)); // Parabola: peaks at t=1
                } else {
                    // Descending from (peak, 255) to (range, 128)
                    double t = (range - peak) / (range - peak); // 0 to 1
                    t = Math.log(1 + 9 * t) / Math.log(10); // Logarithmic scale for smoother falloff
                    alpha = (int) (maxAlpha - 127 * t); // Linear descent: 255 → 128
                    
                }

                // Set alpha based on range
                alpha = (int) (maxAlpha * (1.0 - (range - altitude) / (maxRange)));
                alpha = Math.max(0, Math.min(maxAlpha, alpha));
            }
            
            alpha = Math.max(0, Math.min(maxAlpha, alpha));
            Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
            img.setRGB(x, 0, newColor.getRGB());
        }
    }

    public void createMosaic(int resolution) {
        if (mosaicResolution.get() == resolution)
            return;
        
        mosaicResolution.set(resolution);
        mosaicImage = null;
        BufferedImage img = readImage();
            
        
        // Check if image was successfully loaded
        if (img == null) {
            log.warn("Image not available for raster {}", raster.getFilename());
            return;
        }

        LocationType topLeft = new LocationType(bounds.getY() + bounds.getHeight(), bounds.getX());
        LocationType bottomRight = new LocationType(bounds.getY(), bounds.getX() + bounds.getWidth());
        
        // Calculate dimensions in meters
        double widthMeters = topLeft.getDistanceInMeters(
                new LocationType(topLeft.getLatitudeDegs(), bottomRight.getLongitudeDegs()));
        double heightMeters = topLeft.getDistanceInMeters(
                new LocationType(bottomRight.getLatitudeDegs(), topLeft.getLongitudeDegs()));
        
        int imgWidth = Math.max(1, (int) (widthMeters * resolution));
        int imgHeight = Math.max(1, (int) (heightMeters * resolution));

        if (imgHeight <= 0 || imgWidth <= 0) {
            return;
        }        

        // Store topLeft for painting
        mosaicNWcorner = topLeft;

        log.info("Generating raster mosaic: {}x{} pixels at resolution {} px/m", imgWidth, imgHeight,
                resolution);
        mosaicImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = mosaicImage.createGraphics();
        //g2.setComposite(MaxAlphaComposite.INSTANCE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        AffineTransform identity = g2.getTransform();
        try {
            SensorInfo si = raster.getSensorInfo();
            double range = si.getMaxRange() - si.getMinRange();

            
            int inc = 1;
            switch (resolution) {
                case 8:
                    inc = 2;                    
                    break;
                case 7:
                    inc = 3;                    
                    break;
                case 6:
                    inc = 4;
                    break;
                case 5:
                    inc = 5;
                    break;
                case 4:
                    inc = 8;
                    break;
                case 3:
                    inc = 12;
                    break;
                case 2:
                    inc = 20;
                    break;
                case 1:
                    inc = 30;
                    break;            
                default:
                    break;
            }
            if (resolution <= 4)
                inc = 4;
            if (resolution <= 2)
                inc = 10;
            else if (resolution <= 1)
                inc = 30;

            for (int y = 0; y < img.getHeight(); y += inc) {
                if (resolution != mosaicResolution.get()) {
                    log.info("Resolution changed, stopping mosaic creation");
                    return;
                }

                SampleDescription sd = raster.getSamples().get(y);

                // Skip samples with excessive roll
                if (Math.abs(sd.getPose().getR()) > 5)
                    continue;

                LocationType samplePos = new LocationType(sd.getPose().getLatitude(), sd.getPose().getLongitude());
                double[] nedOffsets = samplePos.getOffsetFrom(topLeft);

                // Transform to image coordinates (NED to screen: east=right, north=up->down)
                g2.setTransform(identity);
                g2.translate(nedOffsets[1] * resolution, -nedOffsets[0] * resolution);
                g2.rotate(Math.toRadians(sd.getPose().getPsi()));
                BufferedImage swath = new BufferedImage(img.getWidth(), 1, BufferedImage.TYPE_INT_ARGB);
                swath = Scalr.apply(img.getSubimage(0, img.getHeight() - 1 - y, img.getWidth(), 1),
                        new SlantRangeImageFilter(sd.getPose().getAltitude(), range/2-1,
                                swath.getWidth()));
                setAlphaChannel(swath, sd, si);
                // Draw the swath line
                int swathWidthPx = (int) (range * resolution);
                g2.drawImage(swath, -swathWidthPx / 2, -1, swathWidthPx / 2, 1,
                        0, 0, swath.getWidth(), 1, null);
            }
        } catch (Exception e) {
            log.error("Error generating sidescan mosaic", e);
            e.printStackTrace();
        } finally {
            g2.dispose();
        }

        // Fill gaps in the mosaic (transparent lines between scanlines)
        if (mosaicImage != null) {
            GapFillFilter.fillGaps(mosaicImage, 5); // Fill gaps up to 10 pixels
        }
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
            double[] cornerPos = renderer.latLonToScreen(mosaicNWcorner.getLatitudeDegs(),
                    mosaicNWcorner.getLongitudeDegs());
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
    public void close() {
        cancelMosaicTask();
        mosaicImage = null;
        mosaicNWcorner = null;
        mosaicResolution.set(0);
        log.debug("Closed RasterMosaicPainter for {}", raster.getFilename());
    }

    @Override
    public void paint(Graphics2D g, SlippyMap renderer) {
        //g.setRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY));
        g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
        g.setRenderingHints(new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR));
        boolean visible = renderer.getVisibleCoordinates().intersects(bounds);
        if (!visible) {
            cancelMosaicTask();
            //mosaicImage = null;
            //mosaicResolution.set(0);
            return;
        }

        // Cap resolution between 1 and 21 - zoom < 1 scales down LOD 1, zoom > 21 scales up LOD 21
        int resolution = Math.max(1, Math.min(20, (int) renderer.getZoom()));
        if (mosaicResolution.get() != resolution) {
            cancelMosaicTask();
            IndexedRasterUtils.background(() -> createMosaic(resolution));            
        }
        // Always paint the existing mosaic (scaled) while new resolution is being generated
        if (mosaicImage != null) {
            paintMosaic(g, renderer);
        } else {
            shape.paint(g, renderer);
        }
    }

    public static void main(String[] args) {
        SlippyMap renderer = new SlippyMap();
        GuiUtils.testFrame(renderer, "Indexed Raster Painter");

        File folder = new File("/LOGS/QUARTEIRA/20250930/082605_A-12/rasterIndex");
        LocationType center = null;

        // Use an executor for parallel processing of raster files
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (File index : IndexedRasterUtils.findRasterFiles(folder)) {
            System.out.println("Processing: " + index);
            File indexFile = index;

            executor.submit(() -> {
                try {
                    IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(indexFile.toPath()));
                    RasterMosaicPainter painter = new RasterMosaicPainter(indexFile.getParentFile(), raster);
                    renderer.addRasterPainter(painter);
                    // renderer.repaint();
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
