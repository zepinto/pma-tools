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

public final class Inputs implements Serializable {

    @Serial
    private static final long serialVersionUID = 1494955584728918794L;
    private final Map<String, Input> instances = new HashMap<String, Input>();

    public static Inputs forge(Configuration config) {
        try {
            return new Inputs(config);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private Inputs(Configuration config) throws Exception {
        String filePath = config.getString("s57.inputs");
        read(filePath);
    }

    private void read(String file) throws Exception {
        var reader = new CsvReader(file);
        try {
            reader.readHeaders();
            while (reader.readRecord()) {
                var record = new Input(
                        Integer.parseInt(reader.get(0)),
                        Integer.parseInt(reader.get(1)),
                        reader.get(2));
                instances.put(record.getCodeId(), record);
            }
        } catch (Exception e) {
            throw new Exception("Couldn't load csv file on path: " + file, e);
        } finally {
            reader.close();
        }
    }

    /**
     * Get the expected input for an attribute
     * 
     * @param attributeCode
     * @param inputID
     * @return Input expected input
     */
    public Input get(int attributeCode, int inputID) {
        return instances.get(Integer.toString(attributeCode) + Integer.toString(inputID));
    }

    /**
     * Returns how many expected inputs are loaded
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
        return "Inputs [instances=" + joiner + "]";
    }
}
