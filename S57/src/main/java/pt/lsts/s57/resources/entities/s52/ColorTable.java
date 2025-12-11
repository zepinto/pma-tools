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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

public class ColorTable implements Serializable {

    private static final long serialVersionUID = -6124952754662032293L;
    private final Map<String, S52Color> records = new LinkedHashMap<String, S52Color>();

    public static ColorTable forge(Configuration config) {
        return new ColorTable(config);
    }

    private ColorTable(Configuration config) {
        String filePath = config.getString("s52.colors");
        try {
            read(filePath);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void read(String filePath) throws IOException {
        File file = new File(filePath);
        Map<String, Map<ColorScheme, String[]>> map = new LinkedHashMap<String, Map<ColorScheme, String[]>>();
        ColorScheme colorScheme = null;

        LineIterator it = FileUtils.lineIterator(file, "UTF-8");
        try {
            while (it.hasNext()) {
                String line = it.nextLine();
                line = prepare(line);
                // process line
                if (line == null) {
                    break;
                }
                if (Character.isDigit(line.charAt(0)) || line.contains("*")) {
                    continue;
                }
                // Color scheme processing
                if (line.substring(0, 4).equals("COLS")) {
                    String tokens[] = line.split(" ");
                    Pattern pattern = Pattern.compile("([0-9A-Za-z]*)NIL([A-Z_]*)");
                    Matcher matcher = pattern.matcher(tokens[1]);
                    matcher.find();
                    colorScheme = ColorScheme.valueOf(matcher.group(2));
                    // System.out.println(matcher.group(2));
                }
                else
                // Color
                {
                    Pattern pattern = Pattern.compile(
                            "[A-Z]{4}\\s[0-9]*([A-Z0-9]{5})([0-9\\.]*)\\s([0-9\\.]*)\\s([0-9\\.]*)\\s([a-z]*)");
                    Matcher matcher = pattern.matcher(line);

                    matcher.find();
                    String code = matcher.group(1);
                    // System.out.println(matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) + " " +
                    // matcher.group(4) + " " + matcher.group(5));
                    String[] attr = { matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5) };
                    // Color color = new Color(
                    // Float.valueOf(matcher.group(2)),
                    // Float.valueOf(matcher.group(3)),
                    // Float.valueOf(matcher.group(4)),
                    // matcher.group(5));
                    if (map.containsKey(code)) {
                        map.get(code).put(colorScheme, attr);
                    }
                    else {
                        Map<ColorScheme, String[]> temp = new HashMap<ColorScheme, String[]>();
                        temp.put(colorScheme, attr);
                        map.put(code, temp);
                    }
                }
            }
        }
        finally {
            LineIterator.closeQuietly(it);
        }

        // Create final hashmap
        for (Entry<String, Map<ColorScheme, String[]>> entry : map.entrySet()) {
            records.put(entry.getKey(), S52Color.forge(entry.getKey(), entry.getValue()));
        }
    }

    private String prepare(String s) {
        s = s.replace(String.valueOf((char) 0x1f), " ");
        s = s.replace("   ", " ");
        return s;
    }

    /**
     * Get a S52Color by acronym
     * 
     * @param acronym
     * @return
     */
    public S52Color get(String acronym) {
        return records.get(acronym);
    }

    /**
     * @return the records
     */
    public Map<String, S52Color> getRecords() {
        return records;
    }
}
