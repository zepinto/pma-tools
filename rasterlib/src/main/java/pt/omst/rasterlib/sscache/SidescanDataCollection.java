package pt.omst.rasterlib.sscache;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.sidescan.SidescanParser;
import pt.omst.neptus.sidescan.SidescanParserFactory;
import pt.omst.rasterlib.IndexedRasterCreator;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.SensorInfo;

@Slf4j
public class SidescanDataCollection {
    public static List<File> findSidescanFolders(File parentFolder) {
        HashSet<File> files = new HashSet<>();
        for (File file : parentFolder.listFiles()) {
            if (file.isDirectory()) {
                findSidescanFolders(file).forEach(f -> files.add(f));
            } else if (file.getName().endsWith(".sdf") ||
                    file.getName().endsWith(".jsf") ||
                    file.getName().endsWith(".sds")) {
                files.add(parentFolder);
                break;
            }
        }
        ArrayList<File> filesList = new ArrayList<>(files);
        filesList.sort((f1, f2) -> f1.getAbsolutePath().compareTo(f2.getAbsolutePath()));
        return filesList;
    }

    public static boolean hasRasterIndex(File folder) {
        File rasterIndexFolder = new File(folder, "rasterIndex");
        if (!rasterIndexFolder.exists() || !rasterIndexFolder.isDirectory())
            return false;
        for (File file : rasterIndexFolder.listFiles()) {
            if (file.getName().endsWith(".json")) {
                return true;
            }
        }
        return false;
    }

    public static SensorInfo getSensorInfo(File sidescanFolder) {
        SidescanParser parser = null;
        try {
            parser = SidescanParserFactory.build(sidescanFolder);
            return IndexedRasterUtils.getSensorInfo(parser);
        } finally {
            if (parser != null) {
                try {
                    parser.cleanup();
                } catch (Exception e) {
                    log.warn("Error cleaning up parser: {}", e.getMessage());
                }
            }
        }
    }



    public static void main(String[] args) {
        List<File> allFolders = findSidescanFolders(new File("/home/zp/workspace/neptus/"));
        log.info("Found {} sidescan folders to process", allFolders.size());
        
        int processed = 0;
        int skipped = 0;
        int errors = 0;
        
        for (int i = 0; i < allFolders.size(); i++) {
            File f = allFolders.get(i);
            log.info("[{}/{}] Processing: {}", i + 1, allFolders.size(), f.getAbsolutePath());
            System.out.println("Sidescan folder: " + f.getAbsolutePath());
            
            try {
                SensorInfo sensorInfo = getSensorInfo(f);
                boolean hasIndex = hasRasterIndex(f);
                
                System.out.println("   " + sensorInfo);
                System.out.println("   Has raster index: " + hasIndex);
                
                if (!hasIndex) {
                    log.info("Creating raster index for {}", f.getName());
                    IndexedRasterCreator.exportRasters(f, -1, null);
                    processed++;
                } else {
                    log.debug("Skipping {} - already has raster index", f.getName());
                    skipped++;
                }
                
                // Force garbage collection between folders to prevent memory buildup
                System.gc();
                
            } catch (OutOfMemoryError e) {
                log.error("Out of memory processing: {}", f.getAbsolutePath());
                log.error("Attempting to recover memory and continue...");
                errors++;
                // Aggressive garbage collection
                System.gc();
                System.gc();
                
            } catch (Exception e) {
                log.error("Error processing {}: {}", f.getAbsolutePath(), e.getMessage(), e);
                errors++;
            }
            
            System.out.println("-----");
        }
        
        log.info("Processing complete: {} processed, {} skipped, {} errors", processed, skipped, errors);
    }

}
