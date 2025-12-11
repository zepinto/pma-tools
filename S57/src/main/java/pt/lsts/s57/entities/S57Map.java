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
package pt.lsts.s57.entities;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.gdal.ogr.DataSource;

import pt.lsts.s57.S57Factory;
import pt.lsts.s57.S57Listener;
import pt.lsts.s57.S57Utils;
import pt.lsts.s57.resources.Resources;

public class S57Map {

    // Properties
    private final Resources resources;
    private String name;
    private S57MapMeta metaInfo;
    private S57MapObjects objects;

    private boolean loaded = false;
    private boolean loading = false;

    /**
     * Static factory method
     * 
     * @param resources
     * @param filePath
     * @param load      (true will load the objects, false only loads the metadata)
     * @return S57Map
     * @throws IOException
     */
    public static S57Map forge(Resources resources, String filePath, boolean load) throws IOException {
        return new S57Map(resources, filePath, load);
    }

    /**
     * Constructor
     * 
     * @throws IOException
     */
    private S57Map(Resources resources, String filePath, boolean load) throws IOException {
        this.resources = resources;
        this.name = new File(filePath).getName();

        DataSource dataSource = null;

        // preload
        var cacheFile = new File(resources.getConfig().getString("s57.cache.folder") + name + ".dat");
        if (cacheFile.exists()) {
            metaInfo = S57MapMeta.forge(resources, filePath);
        }
        else {
            // opening data source in read-only mode
            dataSource = S57Utils.loadDataSource(filePath);
            metaInfo = S57MapMeta.forge(resources, filePath, dataSource);
        }

        // Loading layers
        if (load) {
            if (dataSource == null) {
                dataSource = S57Utils.loadDataSource(filePath);
            }
            objects = S57MapObjects.forge(dataSource, resources, name);
            loaded = true;
        }
        // cleanup external source
        if (dataSource != null) {
            dataSource.delete();
        }
    }

    public boolean loadMapObjects(S57Listener listener) {
        if (loaded || loading) {
            return false;
        }

        var startMap = System.nanoTime();
        loading = true;
        objects = S57MapObjects.forge(metaInfo.getAbsolutePath().toString(), resources, name);

        loaded = true;
        loading = false;
        if (S57Factory.DEBUG) {
            System.out.println(name + " concurrent load done !  " + ((System.nanoTime() - startMap) / 1E9) + "s");
        }
        return true;
    }

    public boolean unloadMapObjects() {
        if (!loaded || loading) {
            return false;
        }
        loaded = false;
        objects = null;
        return true;
    }

    /**
     * Accessors
     */

    /**
     * @return the objects
     * @throws Exception
     */
    public List<S57Object> getObjects() {
        if (!loaded) {
            this.loadMapObjects(null);
        }
        return objects.getObjects();
    }

    public List<S57Object> getObjects(String[] acronyms) {
        if (!loaded) {
            this.loadMapObjects(null);
        }
        var names = List.of(acronyms);
        return objects.getObjects().stream().filter(obj -> names.contains(obj.getAcronym())).toList();
    }

    public List<S57Object> getGroupOneObjects() {
        if (!loaded) {
            this.loadMapObjects(null);
        }
        return objects.getObjectsGroupOne();
    }

    public Double getScaleMin() {
        return getGroupOneObjects().stream().filter(object -> "DEPARE".equals(object.getAcronym())).findFirst()
                .map(S57Object::getScamin).orElse(null);
    }

    public List<S57Object> getCoverage() {
        return metaInfo.getCoverage();
    }

    public String getName() {
        return metaInfo.getName();
    }

    /**
     * @return the meta
     */
    public List<S57Object> getMeta() {
        return objects.getMeta();
    }

    /**
     * Gets map meta data in human readable form
     * 
     * @return the mapInfo
     */
    public Map<String, String> getMapInfoReadable() {
        return metaInfo.getMapMetaReadable();
    }

    /**
     * Gets map meta data object
     * 
     * @return the mapInfo
     */
    public S57MapMeta getMapInfo() {
        return metaInfo;
    }

    /**
     * @return the loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * @param loaded the loaded to set
     */
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * @return the loading
     */
    public boolean isLoading() {
        return loading;
    }

    /**
     * @param loading the loading to set
     */
    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "S57Map [name=" + name + "]";
    }
}
