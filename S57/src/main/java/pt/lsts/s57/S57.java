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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;

import pt.lsts.s57.entities.S57Map;
import pt.lsts.s57.painters.S57Painter;
import pt.lsts.s57.resources.Resources;

/**
 * Main S57 map management class.
 * Modernized with Java 21 features: virtual threads, var, Stream API.
 * 
 * @author Hugo Dias
 */
public class S57 {
    
    private static volatile boolean loadingFolder = false;

    private final Resources resources;
    private final Map<String, S57Map> maps = Collections.synchronizedMap(new TreeMap<>());
    private final Map<String, List<File>> folders = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<S57Painter> painters = new CopyOnWriteArrayList<>();

    /**
     * Static factory method
     * 
     * @param resources the resources instance
     * @return S57 instance
     */
    public static S57 build(Resources resources) {
        return new S57(resources);
    }

    private S57(Resources resources) {
        this.resources = resources;
    }

    /**
     * Add painter for S57 maps
     * 
     * @param painter the painter to add
     * @return true if added successfully
     */
    public boolean addPainter(S57Painter painter) {
        return painters.add(painter);
    }

    /**
     * Removes S57 painter
     * 
     * @param painter the painter to remove
     * @return true if removed successfully
     */
    public boolean removePainter(S57Painter painter) {
        return painters.remove(painter);
    }

    /**
     * Load folders. This will load all S57 maps within those directories.
     * 
     * @param dirs list of directories to load
     * @param listener progress listener
     * @return true if loading completed
     */
    public boolean loadFolders(List<File> dirs, S57Listener listener) {
        long start = System.nanoTime();
        List<File> files = null;
        setLoadingFolder(true);

        for (var dir : dirs) {
            if (dir.exists() && dir.isDirectory() && !folders.containsKey(dir.toString())) {
                @SuppressWarnings("unchecked")
                var foundFiles = (List<File>) FileUtils.listFiles(dir, new String[] { "000" }, true);
                if (foundFiles != null && !foundFiles.isEmpty()) {
                    files = foundFiles;
                    preloadMaps(files, listener);
                    folders.put(dir.toString(), files);
                } else {
                    listener.setMessage("No S57 files found.");
                }
            } else {
                listener.setMessage("Already loaded.");
            }
        }

        refreshPainters();
        if (S57Factory.DEBUG && files != null) {
            System.out.println(files.size() + " Maps loaded in " + ((System.nanoTime() - start) / 1E9) + "s");
        }
        setLoadingFolder(false);
        S57Utils.sessionSaveProperty(this, "s57Folders", this.getFolders());
        return true;
    }

    /**
     * Removes folders and unload all maps within
     * 
     * @param foldersToRemove folders to remove
     * @param deleteCache true to delete cache files
     */
    public void removeFolders(List<String> foldersToRemove, boolean deleteCache) {
        for (var folder : foldersToRemove) {
            if (this.folders.containsKey(folder)) {
                var list = this.folders.remove(folder);
                removeMaps(list, deleteCache);
            }
        }
        System.gc();
        refreshPainters();
        S57Utils.sessionSaveProperty(this, "s57Folders", this.getFolders());
    }

    /**
     * Remove maps
     * 
     * @param mapsToRemove maps to remove
     * @param deleteCache true to delete cache files
     */
    public void removeMaps(List<File> mapsToRemove, boolean deleteCache) {
        synchronized (this.maps) {
            mapsToRemove.forEach(map -> this.maps.remove(map.getName()));
            if (deleteCache) {
                S57Utils.deleteFromCache(this, mapsToRemove);
            }
        }
    }

    /**
     * Concurrently lazy loads map objects using virtual threads.
     * 
     * @param mapsToLoad maps to load
     */
    public void loadMapObjects(List<S57Map> mapsToLoad) {
        Thread.startVirtualThread(() -> {
            for (var map : mapsToLoad) {
                if (map.loadMapObjects(null)) {
                    refreshPainters();
                }
            }
        });
    }

    /**
     * Concurrently unloads map objects using virtual threads.
     * 
     * @param mapsToUnload maps to unload
     */
    public void unloadMapObjects(List<S57Map> mapsToUnload) {
        var inUseMaps = painters.stream()
            .flatMap(painter -> painter.getCurrentMaps().stream())
            .distinct()
            .toList();
        
        Thread.startVirtualThread(() -> {
            for (var map : mapsToUnload) {
                if (!inUseMaps.contains(map) && map.unloadMapObjects()) {
                    refreshPainters();
                }
            }
        });
    }

    /**
     * Preload maps from files
     * 
     * @param files files to preload
     * @param listener progress listener
     */
    public void preloadMaps(List<File> files, S57Listener listener) {
        int size = files.size();
        for (int i = 0; i < size; i++) {
            preloadMap(files.get(i), listener);
            if (listener != null) {
                listener.publishResult((i + 1) * 100 / size);
            }
        }
    }

    /**
     * Preload a single map
     * 
     * @param file file to preload
     * @param listener progress listener
     */
    public void preloadMap(File file, S57Listener listener) {
        var fileName = file.getName();
        if (!maps.containsKey(fileName)) {
            try {
                var map = S57Map.forge(resources, file.toString(), false);
                maps.put(fileName, map);
            } catch (IOException e) {
                var message = "File " + fileName + " isn't a S57 Map or is corrupted.";
                if (S57Factory.DEBUG) {
                    System.out.println(message);
                }
                if (listener != null) {
                    listener.setMessage(message);
                }
            }
        }
    }

    /**
     * Add maps from files
     * 
     * @param files files to add
     * @param listener progress listener
     */
    public void addMaps(List<File> files, S57Listener listener) {
        int size = files.size();
        for (int i = 0; i < size; i++) {
            addMap(files.get(i));
            if (listener != null) {
                listener.publishResult((i + 1) * 100 / size);
            }
        }
    }

    /**
     * Add a single map
     * 
     * @param file file to add
     */
    public void addMap(File file) {
        var fileName = file.getName();
        if (!maps.containsKey(fileName)) {
            try {
                var map = S57Map.forge(resources, file.toString(), true);
                maps.put(fileName, map);
            } catch (IOException e) {
                if (S57Factory.DEBUG) {
                    System.out.println("File " + fileName + " isn't a S57 Map or is corrupted.");
                }
            }
        }
    }

    /**
     * Preload maps only if cache exists
     * 
     * @param files files to check
     * @param listener progress listener
     */
    public void preloadIfCacheExists(List<File> files, S57Listener listener) {
        int size = files.size();
        for (int i = 0; i < size; i++) {
            preloadIfCacheExists(files.get(i), listener);
            listener.publishResult((i + 1) * 100 / size);
        }
    }

    /**
     * Preload a single map if cache exists
     * 
     * @param file file to check
     * @param listener progress listener
     */
    public void preloadIfCacheExists(File file, S57Listener listener) {
        var fileName = file.getName();
        var cacheFolder = this.getConfig().getString("s57.cache.folder");
        var cacheMeta = new File(cacheFolder + "/" + fileName + ".dat");
        var cacheObjs = new File(cacheFolder + "/" + fileName + ".obj");
        
        if (cacheMeta.exists() && cacheObjs.exists() && !maps.containsKey(fileName)) {
            System.out.println(file);
            try {
                var map = S57Map.forge(resources, file.toString(), false);
                maps.put(fileName, map);
            } catch (IOException e) {
                System.out.println("Cache file not found. " + e.getMessage());
            }
        }
    }

    /**
     * Refresh all painters. Force them to rebuild cache.
     * This method is already called when a new map is loaded.
     */
    public void refreshPainters() {
        painters.forEach(painter -> painter.setCacheValid(false));
    }

    // Accessors

    /**
     * Gets list of loaded folder paths
     * 
     * @return list of folder paths
     */
    public List<String> getFolders() {
        return List.copyOf(folders.keySet());
    }

    /**
     * List map names
     * 
     * @return set of map names
     */
    public Set<String> list() {
        return maps.keySet();
    }

    /**
     * Get map by name
     * 
     * @param name map name
     * @return the map or null if not found
     */
    public S57Map getMap(String name) {
        return maps.get(name);
    }

    /**
     * Get readable map metadata
     * 
     * @param name map name
     * @return map metadata as key-value pairs
     * @throws IllegalArgumentException if map doesn't exist
     */
    public Map<String, String> getMapMeta(String name) {
        var map = getMap(name);
        if (map != null) {
            return map.getMapInfoReadable();
        }
        throw new IllegalArgumentException("Map " + name + " doesn't exist or couldn't be loaded!");
    }

    /**
     * Check if a folder of S57 maps is being loaded
     * 
     * @return true if loading is in progress
     */
    public static boolean isLoadingFolder() {
        return loadingFolder;
    }

    /**
     * Set the isLoadingFolder flag
     * 
     * @param loading the loading state
     */
    public static void setLoadingFolder(boolean loading) {
        loadingFolder = loading;
    }

    /**
     * Get the maps collection
     * 
     * @return the maps
     */
    public Map<String, S57Map> getMaps() {
        return maps;
    }

    /**
     * Get maps from a single folder
     * 
     * @param folderPath the folder path
     * @return sorted list of map names
     */
    public List<String> getMapsFromFolder(String folderPath) {
        var folderMaps = folders.get(folderPath);
        if (folderMaps == null) {
            return List.of();
        }
        return folderMaps.stream()
            .map(File::getName)
            .sorted()
            .toList();
    }

    /**
     * Gets the configuration
     * 
     * @return the configuration
     */
    public Configuration getConfig() {
        return resources.getConfig();
    }

    /**
     * Gets the resources
     * 
     * @return the resources
     */
    public Resources getResources() {
        return resources;
    }
}
