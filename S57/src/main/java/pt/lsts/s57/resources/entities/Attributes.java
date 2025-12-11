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

import org.apache.commons.configuration.Configuration;

import pt.lsts.s57.resources.CSVType;

import com.csvreader.CsvReader;

public final class Attributes implements Serializable {

    @Serial
    private static final long serialVersionUID = -2023427511433612419L;
    private final CSVType type;
    private final String filePath;
    private final Map<Integer, Attribute> instances = new HashMap<Integer, Attribute>();
    private final Map<String, Integer> acronymToCode = new HashMap<String, Integer>();

    public static Attributes forge(Configuration config) {
        try {
            return new Attributes(CSVType.STANDARD, config);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static Attributes forge(Configuration config, CSVType type) {
        try {
            return new Attributes(type, config);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private Attributes(CSVType type, Configuration config) throws Exception {
        this.type = type;

        filePath = switch (type) {
            case STANDARD -> config.getString("s57.attributes");
            case AML -> config.getString("s57.attributes_aml");
            case IW -> config.getString("s57.attributes_iw");
        };
        read(filePath);
    }

    private void read(String file) throws Exception {
        var reader = new CsvReader(file);
        try {
            reader.readHeaders();
            while (reader.readRecord()) {
                var record = new Attribute(
                        Integer.parseInt(reader.get(0)),
                        reader.get(1), reader.get(2), reader.get(3), reader.get(4));
                instances.put(record.code, record);
                acronymToCode.put(record.acronym, record.code);
            }
        } catch (Exception e) {
            throw new Exception("Couldn't load csv file on path: " + filePath, e);
        } finally {
            reader.close();
        }
    }

    /**
     * Returns attribute by acronym
     * 
     * @param acronym
     * @return Attribute
     */
    public Attribute get(String acronym) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.get(getcode(acronym));
    }

    /**
     * Returns class object by code
     * 
     * @param acronym
     * @return Attribute
     */
    public Attribute get(Integer code) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.get(code);
    }

    /**
     * Get code by acronym
     * 
     * @param String acronym
     * @return code
     */
    public Integer getcode(String acronym) {
        return acronymToCode.get(acronym);
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
     * @return the type
     */
    public CSVType getType() {
        return type;
    }
}
