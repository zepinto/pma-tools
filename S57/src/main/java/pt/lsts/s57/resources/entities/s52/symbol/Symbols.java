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
 * Nov 11, 2011
 */
package pt.lsts.s57.resources.entities.s52.symbol;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.text.StrBuilder;

import pt.lsts.s57.resources.entities.s52.ColorTable;

/**
 * @author Hugo Dias
 * @author Paulo Dias
 */
public class Symbols implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, S52Pattern> pattern = new HashMap<String, S52Pattern>();
    private final Map<String, S52Pattern> vPattern = new HashMap<String, S52Pattern>();
    private final Map<String, S52Symbol> raster = new HashMap<String, S52Symbol>();
    private final Map<String, S52Symbol> symbol = new HashMap<String, S52Symbol>();
    private final Map<String, S52Symbol> lines = new HashMap<String, S52Symbol>();
    private final ColorTable colorTable;

    public static Symbols forge(Configuration config, ColorTable colorTable) {
        return new Symbols(config, colorTable);
    }

    private Symbols(Configuration config, ColorTable colorTable) {
        this.colorTable = colorTable;
        String rasterPath = config.getString("s52.symbols.raster");
        String vectorPath = config.getString("s52.symbols.vector");
        String[] paths = { rasterPath, vectorPath };
        try {
            read(paths);
        }
        catch (IOException e) {
            System.out.println("IOException : " + e.getMessage());
        }
    }

    public S52Symbol getSymbol(String code) {
        if (raster.get(code) != null) {
            return raster.get(code);
        }
        if (symbol.get(code) != null) {
            return symbol.get(code);
        }
        else {
            throw new IllegalArgumentException("Symbol with code " + code + " doesnt exist!");
        }
    }

    public S52Pattern getPattern(String code) {
        if (pattern.get(code) != null) {
            return pattern.get(code);
        }
        if (vPattern.get(code) != null) {
            return vPattern.get(code);
        }
        else {
            throw new IllegalArgumentException("Pattern with code " + code + " doesnt exist!");
        }
    }

    public S52Symbol getLine(String code) {
        S52Symbol line = lines.get(code);
        if (line != null)
            return line;
        else
            throw new IllegalArgumentException("Line with code " + code + " doesnt exist!");
    }

    private void read(String[] paths) throws IOException {
        for (String filePath : paths) {
            File file = new File(filePath);
            LineIterator it = FileUtils.lineIterator(file, "UTF-8");
            StrBuilder sb = new StrBuilder();
            try {
                while (it.hasNext()) {
                    String line = it.nextLine();
                    line = prepare(line);
                    // process line
                    if (!line.trim().isEmpty()) {
                        if (line.startsWith("0001")) {
                            sb.clear();
                        }
                        sb.appendln(line);
                        if (line.startsWith("****")) {
                            if (isBitmap(sb.toString())) {
                                mergeBimaps(sb.toString());
                            }
                            if (isVector(sb.toString())) {
                                mergeVectors(sb.toString());
                            }
                        }
                    }
                }
            }
            finally {
                LineIterator.closeQuietly(it);
            }
        }
    }

    private void mergeBimaps(String bitmapText) {
        String[] split = bitmapText.split("0001\\s+.+?" + System.getProperty("line.separator"));
        String bitmapString = split[1].trim();
        if (isBitmapSymbol(bitmapString)) {
            RasterSymbol bs = RasterSymbol.forge(bitmapString, colorTable);
            raster.put(bs.getCode(), bs);
        }
        if (isBitmapPattern(bitmapString)) {
            RasterPattern bs = RasterPattern.forge(bitmapString, colorTable);
            pattern.put(bs.getCode(), bs);
        }
    }

    private void mergeVectors(String vectorText) {
        String[] split = vectorText.split("0001\\s+.+?" + System.getProperty("line.separator"));
        String vector = split[1].trim();
        if (isVectorSymbol(vector)) {
            VectorSymbol vs = VectorSymbol.forge(vector, colorTable);
            if (vs.isValid()) {
                symbol.put(vs.getCode(), vs);
            }
        }
        if (isVectorPattern(vector)) {
            VectorPattern vs = VectorPattern.forge(vector, colorTable);
            if (vs.isValid()) {
                vPattern.put(vs.getCode(), vs);
            }
        }
        if (isVectorLine(vector)) {
            VectorLine vs = VectorLine.forge(vector, colorTable);
            if (vs.isValid()) {
                lines.put(vs.getCode(), vs);
            }
        }
    }

    private String prepare(String s) {
        s = s.replace(String.valueOf((char) 0x1f), " ");
        // s = s.replace(" ", " ");
        return s;
    }

    private boolean isBitmap(String component) {
        return (component.contains("SBTM") || component.contains("PBTM"));
    }

    private boolean isBitmapSymbol(String component) {
        return component.contains("SBTM");
    }

    private boolean isBitmapPattern(String component) {
        return component.contains("PBTM");
    }

    private boolean isVector(String component) {
        return (component.contains("LVCT") || component.contains("PVCT") || component.contains("SVCT"));
    }

    private boolean isVectorSymbol(String component) {
        return component.contains("SVCT");
    }

    private boolean isVectorPattern(String component) {
        return component.contains("PVCT");
    }

    private boolean isVectorLine(String component) {
        return component.contains("LVCT");
    }
}
