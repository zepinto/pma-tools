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
 * 
 */
package pt.lsts.s57;

import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.gdal.gdal.gdal;
import org.gdal.ogr.ogr;

import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.ResourcesFactory;

/**
 * @author Hugo Dias
 * @author Paulo Dias
 *
 */
public class S57Factory {
    public static boolean DEBUG = false;
    // public static File userHomeFolder = new File(System.getProperty("user.home"));

    private S57Factory() {
    }

    /**
     * Default static factory method
     * 
     * @param debub mode
     * @return
     */
    public static S57 build(File folderToExtractResources, File gdalFolder) {
        try {
            S57Utils.extractResources(folderToExtractResources);
            S57Utils.loadNative(gdalFolder);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        File resourcesFolder = new File(folderToExtractResources, "resources");
        File sessionAndCacheFolder = folderToExtractResources;

        PropertiesConfiguration config = S57Factory.config(resourcesFolder, sessionAndCacheFolder);
        Resources resources = S57Factory.resources(resourcesFolder, config);
        setupCacheAndSession(config);

        // GDAL/OGR Settings
        gdal.SetConfigOption("S57_CSV", config.getString("s57.resources"));
        gdal.SetConfigOption("OGR_S57_OPTIONS", "SPLIT_MULTIPOINT=ON,ADD_SOUNDG_DEPTH=ON");
        ogr.RegisterAll();
        return S57.build(resources);
    }

    private static PropertiesConfiguration config(File resourcesFolder, File sessionAndCacheFolder) {
        PropertiesConfiguration config = null;
        try {
            File configFile = new File(resourcesFolder, "config.properties");
            System.out.println("Loading config from: " + configFile.getCanonicalPath());
            config = new PropertiesConfiguration(configFile.getCanonicalPath());
            config.setAutoSave(false);

            System.out.println("s57.resources = " + config.getString("s57.resources"));
            System.out.println("s57.attributes = " + config.getString("s57.attributes"));
            
            config.setProperty("s57.resources",
                    new File(resourcesFolder, config.getString("s57.resources")).getCanonicalPath());
            config.setProperty("s57.svg", new File(resourcesFolder, config.getString("s57.svg")).getCanonicalPath());
            config.setProperty("s52.resources",
                    new File(resourcesFolder, config.getString("s52.resources")).getCanonicalPath());
            config.setProperty("s57.cache.folder",
                    new File(sessionAndCacheFolder.getCanonicalPath(), "cache/s57/").toString()); // userHomeFolder +
                                                                                                  // ".s57
            config.setProperty("s63.cache.folder",
                    new File(sessionAndCacheFolder.getCanonicalPath(), "cache/s63/").toString()); // userHomeFolder +
                                                                                                  // ".s57
            config.setProperty("s57.session.file",
                    new File(sessionAndCacheFolder.getCanonicalPath(), "s57.session").toString()); // userHomeFolder +
                                                                                                   // ".s57

        }
        catch (ConfigurationException e) {
            System.err.println("Error loading config.properties file .. " + e);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return config;
    }

    private static Resources resources(File resourcesFolder, PropertiesConfiguration config) {
        return ResourcesFactory.build(config);
    }

    private static void setupCacheAndSession(PropertiesConfiguration config) {
        // setup cache folders
        File s57 = new File(config.getString("s57.cache.folder"));
        File s63 = new File(config.getString("s63.cache.folder"));
        if (!s57.exists())
            s57.mkdirs();
        if (!s63.exists())
            s63.mkdirs();

        // clean s63 cache folder
        try {
            FileUtils.cleanDirectory(s63);
        }
        catch (IOException e1) {
            System.out.println("Problem cleaning s63 cache folder at startup");
        }

        // setup session file
        File session = new File(config.getString("s57.session.file"));
        if (!session.exists()) {
            try {
                session.createNewFile();
            }
            catch (IOException e) {
                System.out.println("Problem creating session file at startup");
            }
        }
    }

}
