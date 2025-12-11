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

import com.csvreader.CsvReader;

public final class Agencies implements Serializable {

    @Serial
    private static final long serialVersionUID = 6342359091955138765L;
    private final Map<Integer, Agency> instances = new HashMap<Integer, Agency>();

    public static Agencies forge(Configuration config) {
        try {
            return new Agencies(config);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Agencies(Configuration config) throws Exception {

        String filePath = config.getString("s57.agencies");
        read(filePath);
    }

    private void read(String file) throws Exception {
        var reader = new CsvReader(file);
        try {
            reader.readHeaders();
            while (reader.readRecord()) {
                var record = new Agency(
                        Integer.parseInt(reader.get(0)),
                        reader.get(1),
                        Integer.parseInt(reader.get(2)),
                        reader.get(3), reader.get(4));
                instances.put(record.agencyID, record);
            }
        } catch (Exception e) {
            throw new Exception("Couldn't load csv file on path: " + file, e);
        } finally {
            reader.close();
        }
    }

    /**
     * Gets Agency by id
     * 
     * @param id
     * @return Agency
     */
    public Agency get(Integer id) {
        return instances.get(id);
    }

    /**
     * Gets Agency by id
     * 
     * @param id
     * @return Agency
     */
    public Agency get(String id) {
        return instances.get(Integer.valueOf(id));
    }

    /**
     * Returns how many classes are loaded
     * 
     * @return int
     */
    public int getSize() {
        return instances.size();
    }

    @Override
    public String toString() {
        var joiner = new StringJoiner("\n");
        for (var record : instances.values()) {
            joiner.add(record.toString());
        }
        return "Agencies [instances=" + joiner + "]";
    }
}
