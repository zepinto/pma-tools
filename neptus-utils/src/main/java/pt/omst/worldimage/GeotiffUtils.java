package pt.omst.worldimage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.colormap.ColorMap;
import pt.lsts.neptus.core.LocationType;
import pt.omst.gdal.GdalNativeLoader;

/**
 * Utility class for exporting BufferedImage objects as GeoTIFF files using
 * GDAL.
 */
@Slf4j
public class GeotiffUtils {

    static {
        // Load native libraries first, then initialize GDAL
        GdalNativeLoader.load();
        gdal.AllRegister();
    }

    /**
     * Reads a GeoTIFF file from disk and creates a WorldImage.
     * The GeoTIFF is read as a raster and sampled to populate the WorldImage with data points.
     * 
     * @param filePath The path to the GeoTIFF file
     * @param colorMap The ColorMap to use for the WorldImage
     * @param cellWidth The cell width for discretization (in meters)
     * @param sampleStep Step size for sampling the raster (1 = every pixel, 2 = every other pixel, etc.)
     * @return A WorldImage containing the sampled raster data and geographic information
     * @throws IOException If there's an error reading the file
     */
    public static WorldImage readGeoTiff(String filePath, pt.lsts.neptus.colormap.ColorMap colorMap, 
                                         int cellWidth, int sampleStep) throws IOException {
        // Open the GeoTIFF file
        Dataset dataset = gdal.Open(filePath, gdalconst.GA_ReadOnly);
        if (dataset == null) {
            throw new IOException("Failed to open GeoTIFF file: " + filePath);
        }

        try {
            // Get image dimensions
            int width = dataset.getRasterXSize();
            int height = dataset.getRasterYSize();
            int bandCount = dataset.getRasterCount();

            // Get geotransform (geographic coordinates)
            double[] geoTransform = dataset.GetGeoTransform();
            if (geoTransform == null || geoTransform.length != 6) {
                throw new IOException("Invalid or missing geotransform in GeoTIFF");
            }

            // Extract geographic parameters from geotransform
            // geoTransform[0] = top left x (longitude)
            // geoTransform[1] = pixel width
            // geoTransform[2] = rotation (usually 0)
            // geoTransform[3] = top left y (latitude)
            // geoTransform[4] = rotation (usually 0)
            // geoTransform[5] = pixel height (negative for north-up)
            double topLeftLon = geoTransform[0];
            double topLeftLat = geoTransform[3];
            double pixelWidth = geoTransform[1];
            double pixelHeight = geoTransform[5]; // negative for north-up images

            // Create WorldImage
            WorldImage worldImage = new WorldImage(cellWidth, colorMap);

            // Read first band for intensity values (can be extended to handle all bands)
            byte[] band1Data = new byte[width * height];
            dataset.GetRasterBand(1).ReadRaster(0, 0, width, height, width, height, 
                gdalconst.GDT_Byte, band1Data);

            // Sample the raster and add points to WorldImage
            int pointCount = 0;
            for (int y = 0; y < height; y += sampleStep) {
                for (int x = 0; x < width; x += sampleStep) {
                    int idx = y * width + x;
                    
                    // Calculate geographic coordinates for this pixel
                    double lon = topLeftLon + (x + 0.5) * pixelWidth;
                    double lat = topLeftLat + (y + 0.5) * pixelHeight;
                    
                    // Get pixel value (0-255 range)
                    double value = band1Data[idx] & 0xff;
                    
                    // Skip fully transparent pixels if alpha channel exists
                    if (bandCount >= 4) {
                        byte[] alphaData = new byte[1];
                        dataset.GetRasterBand(4).ReadRaster(x, y, 1, 1, 1, 1, 
                            gdalconst.GDT_Byte, alphaData);
                        int alpha = alphaData[0] & 0xff;
                        if (alpha == 0) {
                            continue; // Skip transparent pixels
                        }
                    }
                    
                    // Create location and add point to WorldImage
                    LocationType loc = new LocationType(lat, lon);
                    worldImage.addPoint(loc, value);
                    pointCount++;
                }
            }

            log.info("Successfully read GeoTIFF from: {}", filePath);
            log.info("Image dimensions: {}x{}, sampled {} points with step={}", 
                width, height, pointCount, sampleStep);

            return worldImage;

        } finally {
            // Close and release the dataset
            dataset.delete();
        }
    }

    /**
     * Reads a GeoTIFF file from disk and creates a WorldImage with default parameters.
     * Uses a sample step of 1 (every pixel) and cell width of 5 meters.
     * 
     * @param filePath The path to the GeoTIFF file
     * @param colorMap The ColorMap to use for the WorldImage
     * @return A WorldImage containing the raster data and geographic information
     * @throws IOException If there's an error reading the file
     */
    public static WorldImage readGeoTiff(String filePath, pt.lsts.neptus.colormap.ColorMap colorMap) throws IOException {
        return readGeoTiff(filePath, colorMap, 5, 1);
    }

    public static void exportTif(BufferedImage bufferedImage, LocationType ne, LocationType sw, String outputFilePath)
            throws IOException {
        double[] swCoords = sw.getAbsoluteLatLonDepth();
        double[] neCoords = ne.getAbsoluteLatLonDepth();

        // Get min and max coordinates directly
        double minLon = Math.min(swCoords[1], neCoords[1]);
        double maxLon = Math.max(swCoords[1], neCoords[1]);
        double minLat = Math.min(swCoords[0], neCoords[0]);
        double maxLat = Math.max(swCoords[0], neCoords[0]);

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // Calculate pixel sizes (always positive)
        double pixelSizeX = (maxLon - minLon) / width;
        double pixelSizeY = (maxLat - minLat) / height;

        // Create a GDAL driver for GeoTIFF format
        Driver driver = gdal.GetDriverByName("GTiff");
        if (driver == null) {
            throw new RuntimeException("Failed to get GDAL GeoTIFF driver");
        }
        // Create the output dataset with 4 bands (RGBA)
        String[] options = new String[] { "ALPHA=YES" };
        Dataset dataset = driver.Create(outputFilePath, width, height, 4, gdalconst.GDT_Byte, options);
        if (dataset == null) {
            throw new RuntimeException("Failed to create GeoTIFF file: " + outputFilePath);
        }

        // Create the geotransform array
        double[] geoTransform = new double[] {
                minLon, // top left longitude (west/minimum)
                pixelSizeX, // pixel width (always positive)
                0.0, // rotation (0)
                maxLat, // top left latitude (north/maximum)
                0.0, // rotation (0)
                -pixelSizeY // pixel height (negative for north-up images)
        };

        dataset.SetGeoTransform(geoTransform);

        // Set spatial reference system to WGS84
        dataset.SetProjection(
                "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]");

        try {
            // Extract RGBA data from BufferedImage
            int[] pixels = new int[width * height];
            bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);

            // Create byte arrays for each band (R, G, B, A)
            byte[] redBand = new byte[width * height];
            byte[] greenBand = new byte[width * height];
            byte[] blueBand = new byte[width * height];
            byte[] alphaBand = new byte[width * height];

            // Fill the band arrays
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int alpha = (pixel >> 24) & 0xff;

                // Set RGB values exactly as they are in the source image
                redBand[i] = (byte) ((pixel >> 16) & 0xff);
                greenBand[i] = (byte) ((pixel >> 8) & 0xff);
                blueBand[i] = (byte) (pixel & 0xff);

                // Set alpha value
                alphaBand[i] = (byte) alpha;
            }

            // Write all bands to the dataset
            dataset.GetRasterBand(1).WriteRaster(0, 0, width, height, width, height, gdalconst.GDT_Byte, redBand);
            dataset.GetRasterBand(2).WriteRaster(0, 0, width, height, width, height, gdalconst.GDT_Byte, greenBand);
            dataset.GetRasterBand(3).WriteRaster(0, 0, width, height, width, height, gdalconst.GDT_Byte, blueBand);
            dataset.GetRasterBand(4).WriteRaster(0, 0, width, height, width, height, gdalconst.GDT_Byte, alphaBand);

            // No need to set NoData value as we're using an alpha channel

            // Flush cached data to disk
            dataset.FlushCache();

            log.info("Successfully exported GeoTIFF to: {}", outputFilePath);
        } finally {
            // Close and release the dataset
            dataset.delete();
        }
    }

    /**
     * Exports a WorldImage as a GeoTIFF file using GDAL, preserving geographic
     * information
     * 
     * @param image          The WorldImage to export
     * @param outputFilePath The path where the GeoTIFF will be saved
     */
    public static void exportTif(WorldImage image, String outputFilePath) throws IOException {
        // Process the data to get the BufferedImage
        BufferedImage bufferedImage = image.processData();
        if (bufferedImage == null) {
            throw new RuntimeException("Failed to process WorldImage data");
        }

        // Get geographic coordinates
        LocationType sw = image.getSouthWest();
        LocationType ne = image.getNorthEast();

        exportTif(bufferedImage, ne, sw, outputFilePath);
    }

    /**
     * Alternative export method that uses a temporary file and gdal_translate
     * command line
     * This method can be used as a fallback if the JNI approach doesn't work
     * 
     * @param image          The WorldImage to export
     * @param outputFilePath The path where the GeoTIFF will be saved
     * @return true if successful, false otherwise
     */
    public static boolean exportTifUsingCommandLine(WorldImage image, String outputFilePath) {
        // Process the data to get the BufferedImage
        BufferedImage bufferedImage = image.processData();
        if (bufferedImage == null) {
            System.err.println("Failed to process WorldImage data");
            return false;
        }
        File tempPng = null;
        try {
            // Create a temporary PNG file (lossless format for better quality)
            tempPng = File.createTempFile("temp_for_tiff", ".png");
            ImageIO.write(bufferedImage, "png", tempPng);

            // Get geographic coordinates for world file
            LocationType sw = image.getSouthWest();
            LocationType ne = image.getNorthEast();
            double[] swCoords = sw.getAbsoluteLatLonDepth();
            double[] neCoords = ne.getAbsoluteLatLonDepth();

            // Calculate pixel size
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();

            // Get min and max coordinates directly to ensure correct orientation
            double minLon = Math.min(swCoords[1], neCoords[1]);
            double maxLon = Math.max(swCoords[1], neCoords[1]);
            double minLat = Math.min(swCoords[0], neCoords[0]);
            double maxLat = Math.max(swCoords[0], neCoords[0]);

            // Calculate pixel sizes (always positive)
            double pixelSizeX = (maxLon - minLon) / width;
            double pixelSizeY = (maxLat - minLat) / height;

            // Create a world file (.wld) for the PNG
            File worldFile = new File(tempPng.getAbsolutePath().replace(".png", ".wld"));
            try (java.io.PrintWriter writer = new java.io.PrintWriter(worldFile)) {
                writer.println(pixelSizeX); // pixel width (positive)
                writer.println(0.0); // rotation term
                writer.println(0.0); // rotation term
                writer.println(-pixelSizeY); // pixel height (negative)
                writer.println(minLon); // x-coordinate (longitude) of upper-left pixel center
                writer.println(maxLat); // y-coordinate (latitude) of upper-left pixel center
            }

            // Use gdal_translate command to convert PNG to GeoTIFF with georeferencing and
            // transparency
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "gdal_translate",
                    "-of", "GTiff",
                    "-a_srs", "EPSG:4326", // WGS84
                    "-co", "ALPHA=YES", // Include alpha channel
                    tempPng.getAbsolutePath(),
                    outputFilePath);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("Error exporting to GeoTIFF using command line: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Clean up temporary files
            if (tempPng != null && tempPng.exists()) {
                tempPng.delete();
            }

            // Also delete the world file if it exists
            File worldFile = new File(tempPng.getAbsolutePath().replace(".png", ".wld"));
            if (worldFile.exists()) {
                worldFile.delete();
            }
        }
    }

    public static void main(String[] args) {
        try {
            String inputTif = "input_geotiff.tif";
            String outputTif = "output_geotiff.tif";
            // Read GeoTIFF
            pt.lsts.neptus.colormap.ColorMap colorMap = pt.lsts.neptus.colormap.ColorMapFactory.createJetColorMap();
            WorldImage worldImage = readGeoTiff(inputTif, colorMap, 5, 2);
    
            // Export GeoTIFF
            exportTif(worldImage, outputTif);
    
            System.out.println("GeoTIFF read and exported successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
