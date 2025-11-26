//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;

@Slf4j
public class RasterfallTile extends JPanel implements Comparable<RasterfallTile>, Scrollable, Closeable {

    private final IndexedRaster raster;

    private BufferedImage image = null;
    private final double heightProportition;
    private final int screenWidth = 0;
    private final File folder;
    private Future<?> loadingTask = null;
    private double zoom = 1;
    @Setter
    private double leftMargin = 0;

    private BufferedImageOp imageFilter = null;

    private static final BufferedImage loadingImage = new BufferedImage(1000, 100, BufferedImage.TYPE_INT_ARGB);
    static {
        loadingImage.getGraphics().setColor(Color.BLACK);
        loadingImage.getGraphics().fillRect(0, 0, 1000, 100);
        loadingImage.getGraphics().setColor(Color.DARK_GRAY);
        loadingImage.getGraphics().drawString("Loading...", 10, 10);
    }

    public long getTimestamp(int x, int y) {
        int index = (int)(((float)y/getHeight()) * getSamplesCount());
        index = getSamplesCount()-index-1;
        return raster.getSamples().get(index).getTimestamp().toInstant().toEpochMilli();
    }

    public double getRange(double x) {
        double worldWidth = raster.getSensorInfo().getMaxRange() * 2;
        return ((x - getWidth()/2.0) / getWidth()) * worldWidth;
    }

    public void setFilter(BufferedImageOp filter) {
        this.imageFilter = filter;
        this.image = null;
        this.image = getImage();
        repaint();
    }

    public Point2D.Double getSlantedRangePosition(double slantRange) {
        double xx = slantRange += getRange();
        xx = (xx / (getRange()*2)) * getWidth();
        if (xx < 0)
            xx = 0;
        if (xx > getWidth())
            xx = getWidth();
        double yy = (getSamplesCount() - 1) / (double)getSamplesCount() * getHeight();
        return new Point2D.Double(xx, yy);
    }

    public Point2D.Double getSlantedRangePosition(Instant timestamp, double slantRange) {
        if (timestamp.isBefore(getStartTime().toInstant()))
            return null;
        if (timestamp.isAfter(getEndTime().toInstant()))
            return null;
        
        // Find the sample index for this timestamp
        int index = 0;
        for (SampleDescription sample : raster.getSamples()) {
            if (sample.getTimestamp().toInstant().isAfter(timestamp))
                break;
            index++;
        }
        
        double xx = slantRange + getRange();

        xx = (xx / (getRange()*2)) * getWidth();
        
        if (xx < 0)
            xx = 0;
        if (xx > getWidth())
            xx = getWidth();
        
        // Calculate Y position based on timestamp/index
        double yy = (getSamplesCount() - index) / (double)getSamplesCount() * getHeight();
        
        return new Point2D.Double(xx, yy);
    }

    public Point2D.Double getGroundPosition(Instant timestamp, double slantRange) {
        if (timestamp.isBefore(getStartTime().toInstant()))
            return null;
        if (timestamp.isAfter(getEndTime().toInstant()))
            return null;
        int index = 0;
        for (SampleDescription sample : raster.getSamples()) {
            if (sample.getTimestamp().toInstant().isAfter(timestamp))
                break;
            index++;
        }
        Pose pose = getPose(index);
        double altitude = pose.getAltitude();
        double groundRange = Math.sqrt(slantRange*slantRange - altitude*altitude);
        double xx = (groundRange + getRange()) / (getRange()*2) * getWidth();
        double yy = (getSamplesCount() - index) / (double)getSamplesCount() * getHeight();
        return new Point2D.Double(xx, yy);
    }

    public RasterfallTile(File folder, IndexedRaster raster) {
        this.folder = folder;
        this.raster = raster;
        double worldWidth = raster.getSensorInfo().getMaxRange() * 2;
        double worldHeight = 0;
        double speed = raster.getSamples().stream().collect(Collectors.averagingDouble(sample -> sample.getPose().getU()));
        double startTime = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli()/1000.0;
        double endTime = raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli()/1000.0;
        worldHeight = speed * (endTime - startTime);
        //worldHeight *= 2;
        log.info("World height: "+worldHeight+", width: "+worldWidth);
        heightProportition = worldHeight / worldWidth;
        setBackground(Color.BLACK);
    }

    public boolean containsTime(Instant time) {
        return time.isAfter(getStartTime().toInstant()) && time.isBefore(getEndTime().toInstant());
    }

    public OffsetDateTime getStartTime() {
        return raster.getSamples().getFirst().getTimestamp();
    }

    public OffsetDateTime getEndTime() {
        return raster.getSamples().getLast().getTimestamp();
    }

    public double getRange() {
        return raster.getSensorInfo().getMaxRange();
    }

    public Pose getPose(int index) {
        return raster.getSamples().get(index).getPose();
    }

    @Override
    public Dimension getPreferredSize() {
        // During construction, parent hierarchy may not be complete yet
        if (getParent() == null || getParent().getParent() == null || getParent().getParent().getParent() == null) {
            // Return a reasonable default size based on image dimensions
            return getFullResolutionSize();
        }
        int viewportWidth = getParent().getParent().getParent().getWidth();
        int width = (int) (viewportWidth * zoom);
        int height = (int) (width * heightProportition);
        return new Dimension(width, height);
    }

    public double getHorizontalResolution() {
        double totalRange = raster.getSensorInfo().getMaxRange() - raster.getSensorInfo().getMinRange();
        return totalRange / getWidth();
    }

    public double getVerticalResolution() {

        LocationType locStart = getLocation(0, 0);
        LocationType locEnd = getLocation(getSamplesCount() - 1, 0);
        double distanceTravelled = locEnd.getDistanceInMeters(locStart);
        return distanceTravelled / getHeight();
    }

    public Dimension getFullResolutionSize() {
        if (image == null)
            return new Dimension(300, getSamplesCount());
        return new Dimension(image.getWidth(), (int) (image.getWidth()*heightProportition));
    }

    public LocationType getWorldPosition(int x, int y) {
        double slantRange = (x / (double)getWidth()) * getRange() * 2 - getRange();
        int index = (getSamplesCount() - (int)(((float)y/getHeight()) * getSamplesCount()))-1;
        return getLocation(index, slantRange);
    }

    public Pose getPose(int x, int y) {
        int index = (getSamplesCount() - (int)(((float)y/getHeight()) * getSamplesCount()))-1;
        return getPose(index);
    }

    public LocationType getLocation(Instant instant, double slantRange) {

        if (instant.isAfter(getEndTime().toInstant()))
            return null;

        if (instant.isBefore(getStartTime().toInstant()))
            return null;

        int index = 0;
        for (SampleDescription sample : raster.getSamples()) {
            if (sample.getTimestamp().toInstant().isAfter(instant))
                break;
            index++;
        }
        return getLocation(index, slantRange);
    }

    public LocationType getLocation(int index, double slantRange) {
        Pose pose = getPose(index);
        LocationType loc = new LocationType(pose.getLatitude(), pose.getLongitude());
        double altitude = pose.getAltitude();
        double groundRange = Math.sqrt(slantRange*slantRange - altitude*altitude);
        if (Double.isNaN(groundRange))
            groundRange = 0;
        if (slantRange >= 0)
            loc.setAzimuth(pose.getPhi()+90);
        else
            loc.setAzimuth(pose.getPhi()-90);

        loc.setOffsetDistance(groundRange);
        loc.convertToAbsoluteLatLonDepth();
        return loc;
    }

    public int getSamplesCount() {
        return raster.getSamples().size();
    }

    public BufferedImage getImageSync() {
        if (image != null)
            return image;
        try {
            return ImageIO.read(new File(folder, raster.getFilename()));
        }
        catch (Exception e) {
            return null;
        }
    }

    public synchronized BufferedImage getImage() {
        if (image != null)
            return image;
        if (raster.getFilename() == null || !new File(folder, raster.getFilename()).exists()) {
            System.out.println("File "+new File(folder, raster.getFilename()+" does not exist"));
            return null;
        }
        if (loadingTask == null) {
            loadingTask = IndexedRasterUtils.background(() -> {
                try {
                    image = ImageIO.read(new File(folder, raster.getFilename()));
                    if (imageFilter != null)
                        image = imageFilter.filter(image, null);
                    //this.screenWidth = screenWidth;
                } catch (Exception e) {
                    image = new BufferedImage(300, getSamplesCount(), BufferedImage.TYPE_INT_RGB);
                    image.getGraphics().setColor(Color.ORANGE);
                    image.getGraphics().fillRect(0, 0, screenWidth, getSamplesCount());
                    image.getGraphics().setColor(Color.white);
                    image.getGraphics().drawString("Loading " + raster.getFilename(), 10, 10);
                } finally {
                    loadingTask = null;                    
                    repaint();
                }
            });
        }
        return image;
    }

    public void cancelLoading() {
        if (loadingTask != null)
            loadingTask.cancel(true);
        loadingTask = null;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        revalidate();
    }

    /**
     * Sets zoom without triggering revalidation - for batch updates.
     * Parent container will handle layout after all tiles are updated.
     */
    public void setZoomQuiet(double zoom) {
        this.zoom = zoom;
        invalidate();
    }

    @Override
    public int compareTo(RasterfallTile o) {
        // inverted order
        return o.getStartTime().compareTo(getStartTime());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 1000;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ((orientation == SwingConstants.VERTICAL) ? 10 : visibleRect.width) - 10;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    @Override
    public void paint(Graphics g) {
        BufferedImage image = getImage();
        Graphics2D g2d = (Graphics2D) g;
        if (image == null) {
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.RED);
            g2d.drawString("Error loading "+raster.getFilename(), 10, 10);
        }
        else {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (RasterfallPreferences.isRenderQuality()) {
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            } else {
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            }
            g2d.drawImage(image, (int)leftMargin, 0, getWidth(), getHeight(), null);
        }
        // paint index of the tile
        //g2d.setColor(Color.YELLOW);
        //g2d.drawString("Tile: " + raster.getFilename(), 10, 20);
    }

    @Override
    public void close() throws IOException {
        if (loadingTask != null)
            loadingTask.cancel(true);
        if (image != null)
            image.flush();
        image = null;
    }

    public int getSampleIndex(Instant timestamp) {
        if (timestamp.isBefore(getStartTime().toInstant()))
            return 0;
        if (timestamp.isAfter(getEndTime().toInstant()))
            return getSamplesCount() - 1;
        
        int index = 0;
        for (SampleDescription sample : raster.getSamples()) {
            if (sample.getTimestamp().toInstant().isAfter(timestamp))
                break;
            index++;
        }
        return Math.min(index, getSamplesCount() - 1);
    }

    public IndexedRaster getRaster() {
        return raster;
    }

    public double getResolution() {
        double totalRange = raster.getSensorInfo().getMaxRange() - raster.getSensorInfo().getMinRange();
        return image.getWidth() / totalRange;
    }

    public Pose getPoseAtTime(Instant timestamp) {
        int index = getSampleIndex(timestamp);
        return getPose(index);
    }
}
