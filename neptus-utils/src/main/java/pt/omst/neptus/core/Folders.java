//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.neptus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Folders {
    private final static Logger LOG = LoggerFactory.getLogger(Folders.class);
    private final static File FOLDER_ROOT = findRootFolder();
    private final static File FOLDER_USER_HOME =  new File(System.getProperty("user.home"));
    private final static File FOLDER_CFG = new File(FOLDER_ROOT, "conf");
    private final static File FOLDER_LOGS = new File(FOLDER_ROOT, "log");
    private final static File FOLDER_LOGS_DOWNLOADED = new File(FOLDER_LOGS, "downloaded");
    private final static File FOLDER_NATIVES = new File(FOLDER_ROOT, "natives");
    private final static File FOLDER_CFG_CONSOLES = new File(FOLDER_CFG, "consoles");
    private final static File FOLDER_CFG_VEHICLES = new File(FOLDER_CFG, "vehicles-defs");
    private final static File FOLDER_MISSIONS = new File(FOLDER_CFG, "missions");
    private final static File FOLDER_PLANS = new File(FOLDER_CFG, "plans");
    private final static File FOLDER_USER_CONFIG = findUserConfigFolder();
    private final static File FOLDER_USER_DATA = findUserDataFolder();
    private final static File FILE_ACOUSTIC_DEV = new File(FOLDER_CFG, "acoustic.txt");
    private final static File FILE_MANEUVERS = new File(FOLDER_CFG, "maneuvers.xml");    
    private final static File FILE_MRA_PROPERTIES = Folders.getUserConfigItem("Mra.properties");

    public static File getRoot() {
        return FOLDER_ROOT;
    }

    /**
     * Retrieves the folder where user configurations shall be stored.
     *
     * @return user configuration folder.
     */
    public static File getUserConfigFolder() {
        return FOLDER_USER_CONFIG;
    }

    public static File getUserConfigItem(String... components) {
        return Paths.get(getUserConfigFolder().getAbsolutePath(), components).toFile();
    }

    /**
     * Retrieves the folder where user data shall be stored.
     *
     * @return user data folder.
     */
    public static File getUserDataFolder() {
        return FOLDER_USER_DATA;
    }

    public static File getUserDataItem(String... components) {
        return Paths.get(getUserDataFolder().getAbsolutePath(), components).toFile();
    }

    public static File getNativesFolder() {
        return FOLDER_NATIVES;
    }

    public static File getVehicleConfigFolder() {
        return FOLDER_CFG_VEHICLES;
    }

    public static File getConfigItem(String... components) {
        return Paths.get(FOLDER_CFG.getAbsolutePath(), components).toFile();
    }

    public static File getMissionFolder() {
        return FOLDER_MISSIONS;
    }

    public static File getPlanFolder() {
        return FOLDER_PLANS;
    }

    public static File getConfigFolder() {
        return FOLDER_CFG;
    }

    public static File getLogsFolder() {
        return FOLDER_LOGS;
    }

    public static File getConsolesFolder() {
        return FOLDER_CFG_CONSOLES;
    }

    public static File getLogsDownloadedFolder() {
        return FOLDER_LOGS_DOWNLOADED;
    }

    public static File getFileAcousticDevices() {
        return FILE_ACOUSTIC_DEV;
    }

    public static File getFileManeuvers() {
        return FILE_MANEUVERS;
    }

    public static File getFileMraProperties() {
        return FILE_MRA_PROPERTIES;
    }
    /**
     * @return The user home folder.
     */
    public static File getUserHomeFolder() {
        return FOLDER_USER_HOME;
    }

    /**
     * Finds the path to the user configuration folder.
     *
     * @return path to the user configuration folder.
     */
    private static File findUserConfigFolder() {
        Path path = Paths.get(System.getProperty("user.home"), ".config", "Neptus");
        File file = path.toFile();

        // Folder can be used.
        if (Files.isDirectory(path) && Files.isWritable(path))
            return file;

        // Folder must be created.
        if (file.mkdirs())
            return file;

        throw new RuntimeException("failed to find user configuration folder");
    }

    /**
     * Finds the path to the user data folder.
     *
     * @return path to the user data folder.
     */
    private static File findUserDataFolder() {
        Path path = Paths.get(System.getProperty("user.home"), ".local", "share", "Neptus");
        File file = path.toFile();

        // Folder can be used.
        if (Files.isDirectory(path) && Files.isWritable(path))
            return file;

        // Folder must be created.
        if (file.mkdirs())
            return file;

        throw new RuntimeException("failed to find user data folder");
    }

    private static File findRootFolder() {
        // Try to use root folder from 'neptus.home' property.
        String homeProperty = System.getProperty("neptus.home");
        if (homeProperty != null) {
            LOG.info("home property: {}", homeProperty);

            try {
                File rootFolder = new File(URLDecoder.decode(homeProperty, "UTF-8"));
                if (rootFolder.isDirectory()) {
                    LOG.info("root folder via 'neptus.home': {}", rootFolder.getAbsolutePath());
                    return rootFolder;
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return new File(System.getProperty("user.dir"));

        /*// Try to find root folder via classpath probing.
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL[] urls = ((URLClassLoader) cl).getURLs();

        for (URL url : urls) {
            if (url.getPath().endsWith("conf/"))
                return new File(url.getPath()).getParentFile();
        }

        throw new RuntimeException("failed to find root folder");*/
    }

    public static void logInfo() {
        LOG.info("root folder: {}", getRoot());
    }
}


