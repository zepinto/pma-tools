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
package pt.lsts.s57.resources.entities.s52;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import pt.lsts.s57.entities.S57Attribute;

public class LookupTable implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -9164912560494526779L;
    private final LookupTableType type;
    private final List<LookupTableRecord> records = new ArrayList<LookupTableRecord>();

    public static LookupTable forge(Configuration config, LookupTableType type) {
        return new LookupTable(config, type);
    }

    private LookupTable(Configuration config, LookupTableType type) {
        this.type = type;
        String filePath;
        switch (type) {
            case LINES:
                filePath = config.getString("s52.lines");
                break;
            case PAPER_CHART:
                filePath = config.getString("s52.paper_chart");
                break;
            case SIMPLIFIED:
                filePath = config.getString("s52.simplified");
                break;
            case PLAIN_BOUNDARIES:
                filePath = config.getString("s52.plain_boundaries");
                break;
            case SYMBOLIZED_BOUNDARIES:
                filePath = config.getString("s52.symbolized_boundaries");
                break;
            default:
                throw new IllegalArgumentException("Invalid lup type");
        }
        try {
            read(filePath);
        }
        catch (IOException e) {
            System.out.println("cant open file");
        }
    }

    private void read(String filepath) throws IOException {
        File file = new File(filepath);
        LineIterator it = FileUtils.lineIterator(file, "UTF-8");
        try {
            while (it.hasNext()) {
                String line = it.nextLine();
                line = line.substring(1);
                int size = line.length();
                line = line.substring(0, size - 1);
                List<String> tokens = Arrays.asList(line.split("\",\"", -1));
                records.add(LookupTableRecord.forge(tokens));
            }
        }
        finally {
            LineIterator.closeQuietly(it);
        }
    }

    public LookupTableType getType() {
        return type;
    }

    public List<LookupTableRecord> getRecords() {
        return records;
    }

    /**
     * Finds the correct look up table line for a given object
     * 
     * @param acronym
     * @param attributes
     * @return
     */
    public LookupTableRecord get(String acronym, Map<String, S57Attribute> attributes) {
        List<LookupTableRecord> matching = new ArrayList<LookupTableRecord>();
        Iterator<LookupTableRecord> it = records.listIterator();
        while (it.hasNext()) {
            LookupTableRecord record = it.next();
            if (record.getAcronym().equals(acronym)) {
                matching.add(record);
            }
        }
        // Matches found
        if (!matching.isEmpty()) {
            // Found one match
            if (matching.size() == 1) {
                if (!matching.get(0).getAttributes().isEmpty()) {
                    throw new IllegalStateException(
                            "When only 1 match is found that line must have the attributes fields empty");
                }
                return matching.get(0);
            }
            // Found more than one
            else {
                Integer[] attribMatches = new Integer[matching.size() - 1];
                // loop through records except the last one that is the backup
                for (int i = 0; i < matching.size() - 1; i++) {
                    LookupTableRecord record = matching.get(i);
                    int match_count = 0;
                    boolean flag = true;
                    // loop through attributes
                    for (Entry<String, List<String>> attribute : record.getAttributes().entrySet()) {
                        // values from the object
                        List<String> values = attributes.get(attribute.getKey()) == null ? new ArrayList<String>()
                                : attributes.get(attribute.getKey()).getValue();
                        // values from lup record
                        List<String> lupValues = attribute.getValue();
                        // when the record has more than 1 attribute we need an exact match
                        if (record.getAttributes().size() > 1) {
                            if (lupValues.equals(values) && flag) {
                                match_count = match_count + lupValues.size();
                                attribMatches[i] = match_count;
                                continue;
                            }
                            flag = false;
                            attribMatches[i] = 0;
                            continue;
                        }

                        for (int j = 0; j < lupValues.size(); j++) {
                            if (j >= values.size()) {
                                attribMatches[i] = match_count;
                                continue;
                            }
                            if (lupValues.get(j).equals(values.get(j)) || lupValues.get(j).equals(" ")) {
                                if (j == 0)
                                    attribMatches[i] = ++match_count;
                                else if (attribMatches[i] == j) {
                                    attribMatches[i] = ++match_count;
                                }
                            }
                            else {
                                attribMatches[i] = match_count;
                            }

                        }
                    }
                } // end loop through records
                int more = 0;
                int index = 0;
                for (int y = 0; y < attribMatches.length; y++) {
                    if (attribMatches[y] > more) {
                        if (matching.get(y).getAttributes().size() > 1 && attribMatches[y] == 1)
                            continue;
                        more = attribMatches[y];
                        index = y;
                    }
                }
                // when we use the backup better to check if the field 2 is empty
                return more == 0 ? matching.get(matching.size() - 1) : matching.get(index);
            }
        }
        else
        // No match found use ######
        {
            return records.get(0);
        }
    }
    // TODO still missing special cases ORIENT_ and CURVEL(w/o space) and ACRONYM followed by ? @see preslib part 1
    // 8.3.3.4
}
