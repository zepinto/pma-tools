//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.licences.util;

import java.io.File;

/**
 * Utility class for configuration path resolution
 */
public class ConfigFetch {
    
    /**
     * Resolves a configuration path relative to the application directory
     * First looks in the current folder, then in the user's home .omst folder
     * @param path the relative path to resolve
     * @return the absolute path as a string
     */
    public static String resolvePath(String path) {
        File resolved = new File(path);
        if (!resolved.exists()) {
            File homeDir = new File(System.getProperty("user.home"), ".omst");
            resolved = new File(homeDir, path);
        }
        return resolved.getAbsolutePath();
    }
}
