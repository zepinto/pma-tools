package pt.omst.worldimage;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.core.LocationType;

/**
 * @author zp
 */
public class ImageLayer implements Serializable, MapPainter {
    private static final Logger LOG = LoggerFactory.getLogger(ImageLayer.class);
    private static final long serialVersionUID = -3596078283131291222L;
    private String name;
    private LocationType topLeft;
    private double zoom, transparency = 0.6;
    transient private BufferedImage image;
    
    @Setter
    private int layerPriority = 100;
    

    public ImageLayer(String name, BufferedImage img, LocationType topLeft, LocationType bottomRight) {
        this.name = name;
        this.topLeft = new LocationType(topLeft);
        this.image = img;
        this.zoom = topLeft.getOffsetFrom(bottomRight)[0] / img.getHeight();
    }

    public static ImageLayer fromWorldFile(File f) throws Exception {
        String name = f.getName().replaceAll(".pgw", "");
        BufferedImage img = ImageIO.read(new File(f.getParent(), name + ".png"));
        if (img == null)
            throw new IOException("Could not read image from " + f.getAbsolutePath());
        List<String> lines = Files.readAllLines(f.toPath());
        if (lines.size() < 6)
            throw new IOException("Invalid world file " + f.getAbsolutePath() + ", expected at least 6 lines, got " + lines.size());

        
        double resolution1 = Double.parseDouble(lines.get(0));
        double resolution2 = Double.parseDouble(lines.get(3));
        if (resolution1 == 0 || resolution2 == 0)
            throw new IOException("Invalid world file " + f.getAbsolutePath() + ", resolutions cannot be zero.");
        double lon1 = Double.parseDouble(lines.get(4));
        double lat1 = Double.parseDouble(lines.get(5));        
        double lat2 = lat1 + resolution2 * img.getHeight();
        double lon2 = lon1 - resolution1 * img.getWidth();
        if (lat2 < -90 || lat2 > 90 || lon2 < -180 || lon2 > 180)
            throw new IOException("Invalid world file " + f.getAbsolutePath() + ", coordinates out of bounds: lat2=" + lat2 + ", lon2=" + lon2);
        
        LocationType topLeft = new LocationType(lat1, lon1);
        LocationType bottomRight = new LocationType(lat2, lon2);
        LOG.info("Creating ImageLayer from world file: {}, topLeft={}, bottomRight={}", f.getAbsolutePath(), topLeft, bottomRight);
        if (topLeft.getLatitudeDegs() < -90 || topLeft.getLatitudeDegs() > 90 || 
            topLeft.getLongitudeDegs() < -180 || topLeft.getLongitudeDegs() > 180 ||
            bottomRight.getLatitudeDegs() < -90 || bottomRight.getLatitudeDegs() > 90 ||
            bottomRight.getLongitudeDegs() < -180 || bottomRight.getLongitudeDegs() > 180) {
            throw new IOException("Invalid coordinates in world file " + f.getAbsolutePath());
        }
        return new ImageLayer(name, img, topLeft, bottomRight);
    }

    public static ImageLayer read(File f) throws Exception {
        ObjectInputStream is = new ObjectInputStream(new FileInputStream(f));
        ImageLayer imgLayer = (ImageLayer) is.readObject();
        is.close();
        return imgLayer;
    }

    @Override
    public void paint(Graphics2D g, SlippyMap renderer) {
        Point2D tl = renderer.getScreenPosition(topLeft);
        g.translate(tl.getX(), tl.getY());
        g.draw(new Line2D.Double(-3, -3, 3, 3));
        g.draw(new Line2D.Double(-3, 3, 3, -3));
        g.scale(renderer.getZoom() * zoom, renderer.getZoom() * zoom);
        if (transparency < 1.0)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) transparency));
        g.drawImage(image, 0, 0, null);
    }

    /**
     * @return the image
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * @return the topLeft
     */
    public LocationType getTopLeft() {
        return new LocationType(topLeft);
    }

    public LocationType getBottomRight() {
        LocationType loc = new LocationType(topLeft);
        loc.translatePosition(-image.getHeight() * zoom, image.getWidth() * zoom, 0);
        return loc;
    }

    public void saveToFile(File f) throws Exception {
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(f));
        os.writeObject(this);
        os.close();
    }

    public void saveAsPng(File f, boolean writeWorldFile) throws Exception {

        if (writeWorldFile) {
            File out = new File(f.getParent(), f.getName().replaceAll(".png", ".pgw"));
            BufferedWriter writer = new BufferedWriter(new FileWriter(out));
            LocationType bottomRight = new LocationType(topLeft);
            bottomRight.translatePosition(-image.getHeight() * zoom, image.getWidth() * zoom, 0)
                    .convertToAbsoluteLatLonDepth();
            double degsPerPixel = (topLeft.getLatitudeDegs() - bottomRight.getLatitudeDegs()) / image.getHeight();
            double degsPerPixel2 = (bottomRight.getLongitudeDegs() - topLeft.getLongitudeDegs()) / image.getWidth();
            writer.write(String.format("%.10f\n", degsPerPixel2));
            writer.write("0\n");
            writer.write("0\n");
            writer.write(String.format("-%.10f\n", degsPerPixel));
            writer.write(topLeft.getLongitudeDegs() + "\n");
            writer.write(topLeft.getLatitudeDegs() + "\n");
            writer.close();
            LOG.info("Created World image in " + out.getAbsolutePath());
        }

        ImageIO.write(image, "PNG", f);
    }

    /**
     * @return the transparency
     */
    public double getTransparency() {
        return transparency;
    }

    /**
     * @param transparency the transparency to set
     */
    public void setTransparency(double transparency) {
        this.transparency = transparency;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        image = ImageIO.read(in);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        ImageIO.write(image, "PNG", out);
    }   
}
