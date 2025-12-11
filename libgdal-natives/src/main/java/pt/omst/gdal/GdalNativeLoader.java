//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.gdal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading GDAL native libraries from the JAR.
 * Extracts platform-specific native libraries to a temporary directory
 * and loads them in the correct order.
 */
public class GdalNativeLoader {

    private static final Logger log = LoggerFactory.getLogger(GdalNativeLoader.class);
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static Path nativeLibDir;

    // Libraries must be loaded in dependency order
    private static final String[] LINUX_LIBS = {
        "libgdal.so",
        "libgdalconstjni.so",
        "libgdaljni.so",
        "libogrjni.so",
        "libosrjni.so"
    };

    private static final String[] WINDOWS_LIBS = {
        "gdal19.dll",
        "gdalconstjni.dll",
        "gdaljni.dll",
        "ogrjni.dll",
        "osrjni.dll"
    };

    /**
     * Load GDAL native libraries. This method is idempotent - calling it
     * multiple times has no effect after the first successful load.
     * 
     * <p><b>Important:</b> On Linux, the native library directory must be in 
     * LD_LIBRARY_PATH before the JVM starts. Use {@link #extractNatives()} first
     * to get the path, then restart with LD_LIBRARY_PATH set.
     * 
     * @throws RuntimeException if libraries cannot be loaded
     */
    public static synchronized void load() {
        if (loaded.get()) {
            return;
        }

        try {
            String os = detectOS();
            String arch = detectArch();
            
            log.info("Loading GDAL natives for {}-{}", os, arch);
            
            // Extract natives if not already done
            if (nativeLibDir == null) {
                extractNatives();
            }
            
            // Add the native library directory to java.library.path
            addLibraryPath(nativeLibDir.toString());
            
            // On Linux, check if LD_LIBRARY_PATH includes our directory
            if (os.equals("linux")) {
                String ldPath = System.getenv("LD_LIBRARY_PATH");
                if (ldPath == null || !ldPath.contains(nativeLibDir.toString())) {
                    // Try the reflection hack as a last resort
                    setLinuxLibraryPath(nativeLibDir.toString());
                }
                
                // Load all libraries - libgdal.so first, then JNI bindings
                System.load(nativeLibDir.resolve("libgdal.so").toString());
                System.load(nativeLibDir.resolve("libgdalconstjni.so").toString());
                System.load(nativeLibDir.resolve("libgdaljni.so").toString());
                System.load(nativeLibDir.resolve("libogrjni.so").toString());
                System.load(nativeLibDir.resolve("libosrjni.so").toString());
            } else {
                // On Windows, load in dependency order
                System.load(nativeLibDir.resolve("gdal19.dll").toString());
                System.load(nativeLibDir.resolve("gdalconstjni.dll").toString());
                System.load(nativeLibDir.resolve("gdaljni.dll").toString());
                System.load(nativeLibDir.resolve("ogrjni.dll").toString());
                System.load(nativeLibDir.resolve("osrjni.dll").toString());
            }
            
            loaded.set(true);
            log.info("GDAL natives loaded successfully from {}", nativeLibDir);
            
        } catch (Exception e) {
            String message = "Failed to load GDAL native libraries.";
            if (detectOS().equals("linux")) {
                message += " On Linux, set LD_LIBRARY_PATH before starting the JVM: " +
                    "LD_LIBRARY_PATH=" + (nativeLibDir != null ? nativeLibDir : extractNativesPath());
            }
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Extract native libraries to a directory and return the path.
     * Uses a fixed location under user's home directory for persistence across JVM restarts.
     * This method can be used to get the path needed for LD_LIBRARY_PATH on Linux.
     * 
     * @return Path to the directory containing the extracted native libraries
     */
    public static synchronized Path extractNatives() {
        if (nativeLibDir != null) {
            return nativeLibDir;
        }
        
        try {
            String os = detectOS();
            String arch = detectArch();
            
            // Use a fixed location under user home for persistence
            // This ensures the same path is used across JVM restarts
            Path userHome = Path.of(System.getProperty("user.home"));
            nativeLibDir = userHome.resolve(".gdal-natives").resolve(os).resolve(arch);
            Files.createDirectories(nativeLibDir);
            
            String[] libs = os.equals("linux") ? LINUX_LIBS : WINDOWS_LIBS;
            String resourcePath = String.format("/%s/%s/", os, arch);
            
            // Extract all libraries
            for (String lib : libs) {
                extractLibrary(resourcePath + lib, lib);
            }
            
            log.info("Extracted GDAL natives to: {}", nativeLibDir);
            return nativeLibDir;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract GDAL native libraries", e);
        }
    }

    /**
     * Get the path where natives will be extracted (or were extracted).
     * Returns an existing extracted path, or creates a new extraction.
     * 
     * @return String path to native library directory
     */
    public static String extractNativesPath() {
        return extractNatives().toString();
    }

    /**
     * Check if GDAL natives have been loaded.
     */
    public static boolean isLoaded() {
        return loaded.get();
    }

    /**
     * Get the directory where native libraries were extracted.
     */
    public static Path getNativeLibDir() {
        return nativeLibDir;
    }

    private static String detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            return "linux";
        } else if (os.contains("windows")) {
            return "win";
        } else if (os.contains("mac")) {
            throw new UnsupportedOperationException("macOS is not currently supported");
        }
        throw new UnsupportedOperationException("Unsupported operating system: " + os);
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("64")) {
            return "x64";
        } else if (arch.contains("86") || arch.contains("32")) {
            return "x86";
        }
        throw new UnsupportedOperationException("Unsupported architecture: " + arch);
    }

    private static void extractLibrary(String resourcePath, String libName) throws IOException {
        Path targetPath = nativeLibDir.resolve(libName);
        
        // Skip extraction if file already exists and has content
        if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
            log.debug("Library {} already exists at {}", libName, targetPath);
            return;
        }
        
        try (InputStream is = GdalNativeLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Native library not found in JAR: " + resourcePath);
            }
            
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Make executable on Linux
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                targetPath.toFile().setExecutable(true);
            }
            
            log.debug("Extracted {} to {}", libName, targetPath);
        }
    }

    /**
     * Attempt to modify LD_LIBRARY_PATH at runtime using reflection.
     * This is a hack that may not work on all JVMs, but it's worth trying.
     */
    @SuppressWarnings("unchecked")
    private static void setLinuxLibraryPath(String path) {
        try {
            // Try to modify the environment variable map directly
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            
            String currentPath = System.getenv("LD_LIBRARY_PATH");
            String newPath = currentPath != null && !currentPath.isEmpty() 
                ? path + ":" + currentPath 
                : path;
            writableEnv.put("LD_LIBRARY_PATH", newPath);
            
            log.debug("Set LD_LIBRARY_PATH to: {}", newPath);
        } catch (Exception e) {
            log.warn("Could not modify LD_LIBRARY_PATH at runtime. " +
                "You may need to set it before starting the JVM: LD_LIBRARY_PATH={}", path);
        }
    }

    /**
     * Dynamically add a path to java.library.path.
     * This uses reflection to modify the sys_paths field, forcing the ClassLoader
     * to re-read java.library.path on the next loadLibrary call.
     */
    private static void addLibraryPath(String path) throws Exception {
        String currentPath = System.getProperty("java.library.path");
        String separator = System.getProperty("path.separator");
        
        if (currentPath == null || currentPath.isEmpty()) {
            System.setProperty("java.library.path", path);
        } else {
            System.setProperty("java.library.path", path + separator + currentPath);
        }
        
        // Force the ClassLoader to re-read the java.library.path
        // This is necessary because the ClassLoader caches the path
        try {
            var sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
            sysPathsField.setAccessible(true);
            sysPathsField.set(null, null);
        } catch (NoSuchFieldException e) {
            // Java 9+ may not have this field, but System.load with absolute paths works
            log.debug("Could not reset sys_paths (expected on Java 9+), using absolute paths");
        }
    }

    /**
     * Main method for testing and extracting natives.
     * Prints the native library path for use with LD_LIBRARY_PATH.
     */
    public static void main(String[] args) {
        Path path = extractNatives();
        System.out.println(path);
    }
}
