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
 * Jun 4, 2012
 */
package pt.lsts.s57.entities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Layer;

import pt.lsts.s57.S57Utils;
import pt.lsts.s57.resources.Resources;

/**
 * @author Hugo Dias
 */
public class S57MapObjects implements Serializable {

    private static final long serialVersionUID = 1549955050463007910L;
    private List<S57Object> objects = new ArrayList<S57Object>();
    private List<S57Object> objectsGroupOne = new ArrayList<S57Object>();
    private List<S57Object> meta = new ArrayList<S57Object>();
    private List<S57Object> collection = new ArrayList<S57Object>();

    public static S57MapObjects forge(DataSource dataSource, Resources resources, String name) {
        S57MapObjects obj;
        String cacheFolder = resources.getConfig().getString("s57.cache.folder");
        File file = new File(cacheFolder, name + ".obj");
        if (file.exists()) {
            return deserialize(file);
        }

        obj = new S57MapObjects(dataSource, resources);
        serialize(cacheFolder, name, obj);
        return obj;
    }

    public static S57MapObjects forge(String dataSource, Resources resources, String name) {
        DataSource source = null;

        try {
            source = S57Utils.loadDataSource(dataSource);
        }
        catch (IOException e) {
            System.out.println("error opening map file with gdal " + e.getMessage());
        }
        return S57MapObjects.forge(source, resources, name);
    }

    private S57MapObjects(DataSource dataSource, Resources resources) {
        int layerCount = dataSource.GetLayerCount();

        for (int iLayer = 0; iLayer < layerCount; iLayer++) {
            Layer layer = dataSource.GetLayer(iLayer);
            String layerName = layer.GetLayerDefn().GetName();
            // continue if DSID or M_COVR because with already have that with the preload process
            if (layerName.equals("DSID") || layerName.equals("M_COVR")) {
                continue;
            }
            // Loading actual objects
            Feature feature;
            while ((feature = layer.GetNextFeature()) != null) {
                loadObject(feature, resources);
            }
        }
        dataSource.delete();
    }

    private void loadObject(Feature feature, Resources resources) {

        S57Object object = S57Object.forge(resources, feature);
        if (object.getObjectClass().getType().equals("M")) {
            meta.add(object);
        }
        else if (object.getObjectClass().getType().equals("C")) {
            collection.add(object);
        }
        else if (object.getObjectClass().getType().equals("G")) {
            // otherwise object goes to the geographic list of objects
            if (object.isGroupOne()) {
                objectsGroupOne.add(object);
            }
            objects.add(object);
        }
        else {
            throw new IllegalArgumentException("Invalid feature type");
        }
    }

    private static void serialize(String cacheFolder, String name, S57MapObjects obj) {
        File folder = new File(cacheFolder);
        File file = new File(cacheFolder, name + ".obj");
        if (file.exists())
            return;

        if (!folder.exists()) {
            folder.mkdirs();
        }

        // use buffering
        OutputStream fileStream;
        OutputStream buffer;
        ObjectOutput output;
        try {
            fileStream = new FileOutputStream(new File(cacheFolder, name + ".obj"));
            buffer = new BufferedOutputStream(fileStream);
            output = new ObjectOutputStream(buffer);
            output.writeObject(obj);
            output.flush();
            output.close();
            buffer.close();
            fileStream.close();
        }
        catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static S57MapObjects deserialize(File file) {
        // use buffering
        InputStream fileStream;
        InputStream buffer;
        ObjectInput input;

        try {
            fileStream = new FileInputStream(file);
            buffer = new BufferedInputStream(fileStream);
            input = new ObjectInputStream(buffer);
            S57MapObjects objs = (S57MapObjects) input.readObject();
            input.close();
            buffer.close();
            fileStream.close();

            return objs;
        }
        catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    /**
     * ACCESSORS
     */

    /**
     * @return the objects
     */
    public List<S57Object> getObjects() {
        return objects;
    }

    /**
     * @return the objectsGroupOne
     */
    public List<S57Object> getObjectsGroupOne() {
        return objectsGroupOne;
    }

    /**
     * @return the meta
     */
    public List<S57Object> getMeta() {
        return meta;
    }
}
