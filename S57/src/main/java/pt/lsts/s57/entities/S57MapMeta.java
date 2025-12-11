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
 * Dec 27, 2011
 */
package pt.lsts.s57.entities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Layer;

import pt.lsts.s57.S57Utils;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.Agency;

/**
 * @author Hugo Dias
 */
public class S57MapMeta implements Serializable {

    private static final long serialVersionUID = 1326239967438064940L;

    // Static lookup maps using Map.of()
    private static final Map<String, String> NAV_PURPOSE = Map.of("1", "Overview", "2", "General", "3", "Coastal", "4",
            "Approach", "5", "Harbour", "6", "Berthing");

    private static final Map<String, String> COORD_UNIT_TRANSLATE = Map.of("1", "Latitude/Longitude", "2",
            "Easting/Northing", "3", "Units on Chart/Map");

    // Properties
    transient private Resources resources;
    private final Agency agency;
    /*
     * @see s57 main document chapter 7.2 Data Set Identification DSID prefix , etc
     */
    private final Map<String, String> mapMeta = new HashMap<>();
    private final Map<String, String> mapMetaReadable = new LinkedHashMap<>();
    private final File absolutePath;
    private final String name;

    private List<S57Object> coverage = new ArrayList<>();

    /**
     * Static factory method
     * 
     * @param resources
     * @param filePath
     * @return S57Map
     * @throws IOException
     */
    public static S57MapMeta forge(Resources resources, String filePath, DataSource gdal) throws IOException {
        var name = new File(filePath).getName();
        var cacheFolder = resources.getConfig().getString("s57.cache.folder");
        var file = Path.of(cacheFolder, name + ".dat");
        if (Files.exists(file)) {
            return deserialize(file, resources);
        }

        // This is a special feature Data Set Identifier with info about the entire map
        var dsid = gdal.GetLayer("DSID");
        // This is a special feature with info where data exist in the map
        var cov = gdal.GetLayer("M_COVR");
        if (dsid != null && cov != null) {
            var meta = new S57MapMeta(resources, filePath, dsid, cov);
            serialize(cacheFolder, name, meta);
            return meta;
        }
        else {
            throw new IOException("Error preloading meta and coverage");
        }
    }

    public static S57MapMeta forge(Resources resources, String filePath) throws IOException {
        var name = new File(filePath).getName();
        var cacheFolder = resources.getConfig().getString("s57.cache.folder");
        var file = Path.of(cacheFolder + name + ".dat");

        if (Files.exists(file)) {
            return deserialize(file, resources);
        }
        else {
            var gdal = S57Utils.loadDataSource(filePath);
            // This is a special feature Data Set Identifier with info about the entire map
            var dsid = gdal.GetLayer("DSID");
            // This is a special feature with info where data exist in the map
            var cov = gdal.GetLayer("M_COVR");

            var meta = new S57MapMeta(resources, filePath, dsid, cov);
            serialize(cacheFolder, name, meta);
            return meta;
        }
    }

    /**
     * Constructor
     * 
     * @throws IOException
     */
    private S57MapMeta(Resources resources, String filePath, Layer dsid, Layer cov) throws IOException {
        this.resources = resources;
        this.absolutePath = new File(filePath).getAbsoluteFile();
        this.name = this.absolutePath.getName();
        loadMapInfo(dsid);
        loadCoverage(cov);
        agency = resources.getAgency(mapMeta.get("DSID_AGEN"));
        // buildMetaDataReadable();
    }

    /**
     * Loads the first layer/object from the ogr datasource that contains general info about the map
     * 
     * @see s57 main document chapter 7.2 Data Set Identification DSID prefix , etc
     * @param feature
     */
    private void loadMapInfo(Layer dsid) {
        Feature feature;
        while ((feature = dsid.GetNextFeature()) != null) {
            var fieldCount = feature.GetFieldCount();
            for (int i = 0; i < fieldCount; i++) {
                var attribAcronym = feature.GetFieldDefnRef(i).GetNameRef();
                // fields that aren't set, have empty string ""
                var value = feature.IsFieldSet(i) ? feature.GetFieldAsString(i) : "";
                mapMeta.put(attribAcronym, value);
            }
        }
    }

    /**
     * Load the layer M_COVR where the map has data to paint the boundaries
     * 
     * @param feature
     */
    private void loadCoverage(Layer cov) {
        Feature feature;
        while ((feature = cov.GetNextFeature()) != null) {
            if ("M_COVR".equals(feature.GetDefnRef().GetName())) {
                var object = S57Object.forge(resources, feature);
                if ("1".equals(object.getAttributes().get("CATCOV").getValue().get(0))) {
                    coverage.add(object);
                }
            }
        }
    }

    protected List<S57Object> getCoverage() {
        return coverage;
    }

    //
    // HELPERS
    //

    private void buildMetaDataReadable() {
        // Name
        mapMetaReadable.put("Path", absolutePath.toString());
        // Agency
        var agencyCode = mapMeta.get("DSID_AGEN");
        mapMetaReadable.put("Agency", resources.getAgency(agencyCode).getAgencyName());
        mapMetaReadable.put("Country", resources.getAgency(agencyCode).getCountry());
        mapMetaReadable.put("CC", resources.getAgency(agencyCode).getToken());
        // Navigation purpose
        var purpose = mapMeta.get("DSID_INTU");
        mapMetaReadable.put("Navigational Purpose", NAV_PURPOSE.get(purpose));
        // Coordinate Unit
        mapMetaReadable.put("Coordinate Unit", COORD_UNIT_TRANSLATE.get(mapMeta.get("DSPM_COUN")));
        mapMetaReadable.put("Edition Number", mapMeta.get("DSID_EDTN"));
        mapMetaReadable.put("Update Number", mapMeta.get("DSID_UPDN"));
        mapMetaReadable.put("Issue Date", mapMeta.get("DSID_ISDT"));
        mapMetaReadable.put("Update application date", mapMeta.get("DSID_UADT"));
    }

    private static void serialize(String cacheFolder, String name, S57MapMeta mapMeta) {
        var folder = Path.of(cacheFolder);
        try {
            Files.createDirectories(folder);
            var file = folder.resolve(name + ".dat");
            try (OutputStream fileStream = Files.newOutputStream(file);
                    OutputStream buffer = new BufferedOutputStream(fileStream);
                    ObjectOutput output = new ObjectOutputStream(buffer)) {
                output.writeObject(mapMeta);
            }
        }
        catch (IOException e) {
            System.err.println("Error serializing S57MapMeta: " + e.getMessage());
        }
    }

    private static S57MapMeta deserialize(Path file, Resources resources) {
        try (InputStream fileStream = Files.newInputStream(file);
                InputStream buffer = new BufferedInputStream(fileStream);
                ObjectInput input = new ObjectInputStream(buffer)) {
            // deserialize the metadata
            var metaInfo = (S57MapMeta) input.readObject();
            metaInfo.setResources(resources);
            return metaInfo;
        }
        catch (IOException | ClassNotFoundException e) {
            System.err.println("Error deserializing S57MapMeta: " + e.getMessage());
            return null;
        }
    }

    //
    // Accessors
    //

    /**
     * @return the mapMetaReadable
     */
    public Map<String, String> getMapMetaReadable() {
        if (mapMetaReadable.isEmpty()) {
            buildMetaDataReadable();
        }
        return mapMetaReadable;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the absolutePath
     */
    public File getAbsolutePath() {
        return absolutePath;
    }

    /**
     * @return the agency
     */
    public Agency getAgency() {
        return agency;
    }

    public String get(String name) {
        return mapMeta.get(name);
    }

    /**
     * Get map purpose
     * 
     * @return
     */
    public int getPurpose() {
        return Integer.valueOf(mapMeta.get("DSID_INTU"));
    }

    /**
     * @param resources the resources to set
     */
    public void setResources(Resources resources) {
        this.resources = resources;
    }
}
