package pt.omst.worldimage;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import pt.omst.neptus.colormap.ColorMap;
import pt.omst.neptus.core.LocationType;

/**
 * @author zp
 */
public class WorldImage {
    private DataDiscretizer dd;
    private ColorMap cmap;
    private LocationType sw = null, ne = null, ref = null;
    private int defaultWidth = 1024;
    private int defaultHeight = 768;

    private Double minVal = null, maxVal = null;

    public WorldImage(int cellWidth, ColorMap cmap) {
        this.cmap = cmap;
        this.dd = new DataDiscretizer(cellWidth);
    }

    public final void setMinVal(Double minVal) {
        this.minVal = minVal;
    }

    public final void setMaxVal(Double maxVal) {
        this.maxVal = maxVal;
    }

    public void addPoint(LocationType loc, double value) {
        if (ref == null)
            ref = new LocationType(loc);

        double[] offsets = loc.getOffsetFrom(ref);

        dd.addPoint(offsets[1], -offsets[0], value);
    }

    public BufferedImage processData() {
        if (getAmountDataPoints() == 0)
            return null;

        double maxX = dd.maxX + 5;
        double maxY = dd.maxY + 5;
        double minX = dd.minX - 5;
        double minY = dd.minY - 5;

        //width/height
        double dx = maxX - minX;
        double dy = maxY - minY;

        double ratio1 = (double) defaultWidth / (double) defaultHeight;
        double ratio2 = dx / dy;

        if (ratio2 < ratio1)
            dx = dy * ratio2;
        else
            dy = dx / ratio1;

        //center
        double cx = (maxX + minX) / 2;
        double cy = (maxY + minY) / 2;

        Rectangle2D bounds = new Rectangle2D.Double(cx - dx / 2, cy - dy / 2, dx, dy);

        BufferedImage img = new BufferedImage(1000, (int) (bounds.getHeight() / bounds.getWidth() * 1000.0), BufferedImage.TYPE_INT_ARGB);

        try {
            double max = getMaxValue();
            double min = getMinValue();
            ColorMapUtils.generateInterpolatedColorMap(bounds, dd.getDataPoints(), 0, img.createGraphics(), img.getWidth(), img.getHeight(), 255, cmap, min, max);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        ne = new LocationType(ref);
        ne.translatePosition(-maxY, maxX, 0);

        sw = new LocationType(ref);
        sw.translatePosition(-minY, minX, 0);

        return img;
    }

    public LocationType getSouthWest() {
        return sw;
    }

    public LocationType getNorthEast() {
        return ne;
    }

    public int getAmountDataPoints() {
        return dd.getAmountDataPoints();
    }

    public double getMaxValue() {
        return maxVal == null ? dd.maxVal[0] * 1.005 : maxVal;
    }

    public double getMinValue() {
        return minVal == null ? dd.minVal[0] * 0.995 : minVal;
    }

    /**
     * @return the cmap
     */
    public ColorMap getColormap() {
        return cmap;
    }

    /**
     * @param cmap the cmap to set
     */
    public void setColormap(ColorMap cmap) {
        this.cmap = cmap;
    }

    
}
