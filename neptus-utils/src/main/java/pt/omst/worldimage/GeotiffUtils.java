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
import pt.omst.neptus.core.LocationType;

/**
 * Utility class for exporting BufferedImage objects as GeoTIFF files using
 * GDAL.
 */
@Slf4j
public class GeotiffUtils {

    static {
        // Initialize GDAL library
        gdal.AllRegister();
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
}
