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
package pt.lsts.s57.resources.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.commons.configuration.Configuration;

import pt.lsts.s57.resources.CSVType;

import com.csvreader.CsvReader;

public final class ObjectClasses implements Serializable {

    @Serial
    private static final long serialVersionUID = 7957702892300635364L;
    private final CSVType type;
    private final String filePath;
    private final Map<String, ObjectClass> instances = new HashMap<String, ObjectClass>();

    public static ObjectClasses forge(Configuration config) {
        try {
            return new ObjectClasses(CSVType.STANDARD, config);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public static ObjectClasses forge(Configuration config, CSVType type) {
        try {
            return new ObjectClasses(type, config);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    /**
     * Construct
     * 
     * @param type
     * @param config
     * @throws Exception
     */
    private ObjectClasses(CSVType type, Configuration config) throws Exception {
        this.type = type;

        filePath = switch (type) {
            case STANDARD -> config.getString("s57.objects");
            case AML -> config.getString("s57.objects_aml");
            case IW -> config.getString("s57.objects_iw");
        };
        read(filePath);
    }

    /**
     * Reads csv file
     * 
     * @param file
     * @throws Exception
     */
    private void read(String file) throws Exception {
        var reader = new CsvReader(file);
        try {
            reader.readHeaders();
            while (reader.readRecord()) {
                var record = new ObjectClass(
                        Integer.parseInt(reader.get(0)),
                        reader.get(1), reader.get(2), reader.get(3),
                        reader.get(4), reader.get(5), reader.get(6), reader.get(7));
                instances.put(record.acronym, record);
            }
        } catch (Exception e) {
            throw new Exception("Couldn't load csv file on path: " + filePath, e);
        } finally {
            reader.close();
        }
    }

    /**
     * Returns class object by acronym
     * 
     * @param acronym
     * @return ObjectClass
     */
    public ObjectClass get(String acronym) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        return instances.get(acronym);
    }

    /**
     * Returns how many classes are loaded
     * 
     * @return int
     */
    public int getSize() {
        return instances.size();
    }

    /**
     * Returns the type of classes loaded STANDARD , AML or IW
     * 
     * @return
     */
    public CSVType getType() {
        return type;
    }

    @Override
    public String toString() {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        var joiner = new StringJoiner("\n");
        for (var record : instances.values()) {
            joiner.add(record.toString());
        }
        return joiner.toString();
    }
}
