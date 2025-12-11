/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 *
 * Created by Hugo Dias
 * May 14, 2012
 */
package pt.lsts.s57;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.ogr;

import pt.lsts.s57.ui.LoadS57FolderTask;

/**
 * Utility class for S57 operations.
 * Modernized with Java 21 features: NIO.2, try-with-resources, HexFormat, var.
 * 
 * @author Hugo Dias
 */
public final class S57Utils {

    private static final String DRIVER_NAME = "S57";
    
    private static final String[] RESOURCES_LIST = {
        "resources/s52/lookup_tables/PAPER_CHART.dic",
        "resources/s52/lookup_tables/LINES.dic",
        "resources/s52/lookup_tables/PLAIN_BOUNDARIES.dic",
        "resources/s52/lookup_tables/SIMPLIFIED.dic",
        "resources/s52/lookup_tables/SYMBOLIZED_BOUNDARIES.dic",
        "resources/s52/symbols/allbitmaps.rah",
        "resources/s52/symbols/allvectors.sym",
        "resources/s52/color_tables/allcolors.cta",
        "resources/s57/s57agencies.csv",
        "resources/s57/s57attributes_aml.csv",
        "resources/s57/s57expectedinput.csv",
        "resources/s57/s57objectclasses_aml.csv",
        "resources/s57/s57attributes.csv",
        "resources/s57/s57attributes_iw.csv",
        "resources/s57/s57objectclasses.csv",
        "resources/s57/s57objectclasses_iw.csv",
        "resources/config.properties",
        "resources/map4.svg"
    };

    private S57Utils() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract resources to the given folder
     * 
     * @param baseFolder destination folder
     * @throws IOException if extraction fails
     */
    public static void extractResources(File baseFolder) throws IOException {
        Collection<String> files = new ArrayList<>(Arrays.asList(RESOURCES_LIST));

        var jarConn = getJarConnection(S57Utils.class);
        var jarTime = getTime(jarConn);
        
        for (var filePath : files) {
            var file = new File(baseFolder, filePath);
            boolean extractAnyway = false;
            
            if (file.exists() && jarConn != null) {
                if (jarTime == null || jarTime > file.lastModified()) {
                    extractAnyway = true;
                }
            }
            
            if (extractAnyway || !file.exists()) {
                try (var in = S57Utils.class.getResourceAsStream("/" + filePath);
                     var out = FileUtils.openOutputStream(file)) {
                    if (in != null) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }

    /**
     * Get JAR connection for the given class
     */
    public static JarURLConnection getJarConnection(Class<?> cl) {
        try {
            var resourceName = cl.getName().replace('.', '/') + ".class";
            return (JarURLConnection) ClassLoader.getSystemResource(resourceName).openConnection();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get modification time from JAR connection
     */
    public static Long getTime(JarURLConnection jarConn) {
        try {
            return jarConn.getJarFile().getEntry("META-INF/MANIFEST.MF").getTime();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load GDAL native libraries
     * 
     * @param nativeLibsFolder folder containing native libraries
     * @throws IOException if loading fails
     */
    public static void loadNative(File nativeLibsFolder) throws IOException {
        String[] libs = {"gdal", "gdalconstjni", "gdaljni", "ogrjni", "osrjni"};
        var platform = getPlatformPath();

        for (var name : libs) {
            var filename = new StringBuilder();
            if (platform.contains("win")) {
                filename.append(name);
                if ("gdal".equals(name)) {
                    filename.append("19");
                }
                filename.append(".dll");
            } else {
                filename.append("lib").append(name).append(".so");
            }

            try {
                var path = new File(nativeLibsFolder.getCanonicalFile(), filename.toString());
                System.load(path.getAbsolutePath());
            } catch (IOException | UnsatisfiedLinkError | SecurityException | IllegalArgumentException e) {
                System.err.println("Native code library failed to load: " + e.getMessage());
            }
        }
    }

    /**
     * Get platform identifier (e.g., "win/x86", "linux/x64")
     * 
     * @return platform path string
     */
    public static String getPlatformPath() {
        var osName = System.getProperty("os.name").toLowerCase();
        var archName = System.getProperty("os.arch").toLowerCase();
        
        var os = switch (osName) {
            case String s when s.contains("windows") -> "win";
            case String s when s.contains("linux") -> "linux";
            default -> throw new IllegalArgumentException("OS not supported: " + osName);
        };
        
        var arch = switch (archName) {
            case String a when a.contains("86") -> "x86";
            case String a when a.contains("64") -> "x64";
            default -> throw new IllegalArgumentException("Architecture not supported: " + archName);
        };
        
        return os + "/" + arch;
    }

    /**
     * Load a GDAL data source from file
     * 
     * @param filePath path to the file
     * @return DataSource instance
     * @throws IOException if loading fails
     */
    public static DataSource loadDataSource(String filePath) throws IOException {
        var dataSource = ogr.Open(filePath, false);

        if (dataSource == null) {
            throw new IOException("Could not open file: " + filePath);
        }

        Driver driver = dataSource.GetDriver();
        if (!DRIVER_NAME.equals(driver.getName())) {
            dataSource.delete();
            throw new IOException("Data source is not recognised as S-57 format: " + driver.getName());
        }
        return dataSource;
    }

    /**
     * Get file extension
     * 
     * @param file the file
     * @return extension in lowercase, or null
     */
    public static String getExtension(File file) {
        var name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Calculate CRC32 checksum
     * 
     * @param text input text
     * @return checksum value
     */
    public static int checksum(String text) {
        byte[] bytes = text.getBytes();
        int crc = 0xFFFFFFFF;
        int poly = 0xEDB88320;

        for (byte b : bytes) {
            int temp = (crc ^ b) & 0xFF;
            for (int i = 0; i < 8; i++) {
                temp = (temp & 1) == 1 ? (temp >>> 1) ^ poly : temp >>> 1;
            }
            crc = (crc >>> 8) ^ temp;
        }

        return crc ^ 0xFFFFFFFF;
    }

    /**
     * Encrypt string with Blowfish
     */
    public static String encryptBlowfish(String toEncrypt, String key) {
        try {
            var secretKey = new SecretKeySpec(key.getBytes(), "Blowfish");
            var cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return new String(cipher.doFinal(toEncrypt.getBytes()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Encrypt integer with Blowfish
     */
    public static String encryptBlowfish(int toEncrypt, String key) {
        try {
            var secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "Blowfish");
            var cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            try (var bos = new ByteArrayOutputStream();
                 var dos = new DataOutputStream(bos)) {
                dos.writeInt(toEncrypt);
                dos.flush();
                return HexFormat.of().formatHex(cipher.doFinal(bos.toByteArray())).toUpperCase();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypt Blowfish encrypted string
     */
    public static byte[] decryptBlowfish(String toDecrypt, String key) {
        try {
            var secretKey = new SecretKeySpec(key.getBytes(), "Blowfish");
            var cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(HexFormat.of().parseHex(toDecrypt));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypt file with Blowfish
     */
    public static InputStream decryptBlowfish(File toDecrypt, byte[] key) {
        try {
            var secretKey = new SecretKeySpec(key, "Blowfish");
            var cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            var fileStream = new FileInputStream(toDecrypt);
            var buffer = new BufferedInputStream(fileStream);
            return new CipherInputStream(buffer, cipher);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Unzip stream to folder
     * 
     * @param stream input stream
     * @param folder destination folder
     */
    public static void unzipToFile(InputStream stream, String folder) {
        final int bufferSize = 2048;
        
        try (var zis = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                var destPath = Path.of(folder, entry.getName());
                try (var dest = new BufferedOutputStream(Files.newOutputStream(destPath), bufferSize)) {
                    byte[] data = new byte[bufferSize];
                    int count;
                    while ((count = zis.read(data, 0, bufferSize)) != -1) {
                        dest.write(data, 0, count);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert hex string to byte array using Java 21 HexFormat
     */
    public static byte[] hexStringToByteArray(String s) {
        return HexFormat.of().parseHex(s);
    }

    /**
     * Convert byte array to hex string using Java 21 HexFormat
     */
    public static String byteArrayToHexString(byte[] bytes) {
        return HexFormat.of().formatHex(bytes).toUpperCase();
    }

    /**
     * Save list property to session file
     */
    public static void sessionSaveProperty(S57 s57, String key, List<String> value) {
        var sessionFile = new File(s57.getConfig().getString("s57.session.file"));
        try {
            var session = new PropertiesConfiguration(sessionFile);
            session.setProperty(key, value);
            session.save();
        } catch (ConfigurationException e) {
            System.err.println("Problem saving property to session file: " + e.getMessage());
        }
    }

    /**
     * Save string property to session file
     */
    public static void sessionSaveProperty(S57 s57, String key, String value) {
        var sessionFile = new File(s57.getConfig().getString("s57.session.file"));
        try {
            var session = new PropertiesConfiguration(sessionFile);
            session.setProperty(key, value);
            session.save();
        } catch (ConfigurationException e) {
            System.err.println("Problem saving property to session file: " + e.getMessage());
        }
    }

    /**
     * Get string property from session
     */
    public static String sessionGet(S57 s57, String key) {
        var sessionFile = new File(s57.getConfig().getString("s57.session.file"));
        try {
            var session = new PropertiesConfiguration(sessionFile);
            return session.getString(key);
        } catch (ConfigurationException e) {
            System.err.println("Problem getting session property string: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get list property from session
     */
    public static List<Object> sessionGetList(S57 s57, String key) {
        var sessionFile = new File(s57.getConfig().getString("s57.session.file"));
        try {
            var session = new PropertiesConfiguration(sessionFile);
            return session.getList(key);
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Load session and preload folders
     */
    public static void loadSession(S57 s57) {
        var sessionFile = new File(s57.getConfig().getString("s57.session.file"));
        try {
            var session = new PropertiesConfiguration(sessionFile);
            var preloaded = session.getList("s57Folders");
            if (preloaded != null && !preloaded.isEmpty()) {
                var loadTask = new LoadS57FolderTask(preloaded, s57);
                loadTask.execute();
            }
        } catch (ConfigurationException e) {
            System.err.println("Dialog config file error: " + e.getMessage());
        }
    }

    /**
     * Delete maps from cache
     */
    public static void deleteFromCache(S57 s57, List<File> mapFiles) {
        mapFiles.forEach(map -> deleteFromCache(s57, map));
    }

    /**
     * Delete single map from cache
     */
    public static void deleteFromCache(S57 s57, File mapFile) {
        var cache = s57.getConfig().getString("s57.cache.folder");
        var meta = Path.of(cache, mapFile.getName() + ".dat");
        var objs = Path.of(cache, mapFile.getName() + ".obj");
        
        try {
            Files.deleteIfExists(meta);
            Files.deleteIfExists(objs);
        } catch (IOException e) {
            System.err.println("Failed to delete cache file: " + e.getMessage());
        }
    }

    /**
     * Clean S63 temporary files
     */
    public static void cleanS63TempFiles(S57 s57) {
        var cache = s57.getConfig().getString("s63.cache.folder");
        System.out.println(cache);
        
        var cacheDir = new File(cache);
        var files = cacheDir.listFiles();
        if (files != null) {
            for (var file : files) {
                file.delete();
            }
        }
    }
}
