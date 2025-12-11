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
package pt.lsts.s57.resources;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;

import pt.lsts.s57.entities.S57Attribute;
import pt.lsts.s57.entities.geometry.S57GeometryType;
import pt.lsts.s57.resources.entities.Agencies;
import pt.lsts.s57.resources.entities.Agency;
import pt.lsts.s57.resources.entities.Attribute;
import pt.lsts.s57.resources.entities.Attributes;
import pt.lsts.s57.resources.entities.Input;
import pt.lsts.s57.resources.entities.Inputs;
import pt.lsts.s57.resources.entities.ObjectClass;
import pt.lsts.s57.resources.entities.ObjectClasses;
import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.lsts.s57.resources.entities.s52.LookupTable;
import pt.lsts.s57.resources.entities.s52.LookupTableRecord;
import pt.lsts.s57.resources.entities.s52.LookupTableType;
import pt.lsts.s57.resources.entities.s52.symbol.S52Pattern;
import pt.lsts.s57.resources.entities.s52.symbol.S52Symbol;
import pt.lsts.s57.resources.entities.s52.symbol.Symbols;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;

public class Resources implements Serializable {

    private static final long serialVersionUID = 6617883540259537424L;
    private final Map<CSVType, ObjectClasses> objectClassesMap;
    private final Map<CSVType, Attributes> attributesMap;
    private final Agencies agencies;
    private final Inputs inputs;
    private final Map<LookupTableType, LookupTable> lupMap;
    private final ColorTable colorTable;
    private final Symbols symbols;
    transient private SVGDiagram diagram;
    transient private Configuration config;

    /**
     * Static factory method
     * 
     * @param objectClassesMap
     * @param attributesMap
     * @param agencies
     * @param inputs
     * @param lupMap
     * @param colorTable
     * @return
     */
    public static Resources forge(Configuration config, Map<CSVType, ObjectClasses> objectClassesMap,
            Map<CSVType, Attributes> attributesMap, Agencies agencies, Inputs inputs,
            Map<LookupTableType, LookupTable> lupMap, ColorTable colorTable, Symbols symbols) {
        return new Resources(config, objectClassesMap, attributesMap, agencies, inputs, lupMap, colorTable, symbols);
    }

    /**
     * Construct
     * 
     * @param objectClassesMap
     * @param attributesMap
     * @param agencies
     * @param inputs
     * @param lupMap
     * @param colorTable
     */
    private Resources(Configuration config, Map<CSVType, ObjectClasses> objectClassesMap,
            Map<CSVType, Attributes> attributesMap, Agencies agencies, Inputs inputs,
            Map<LookupTableType, LookupTable> lupMap, ColorTable colorTable, Symbols symbols) {
        this.config = config;
        this.objectClassesMap = objectClassesMap;
        this.attributesMap = attributesMap;
        this.agencies = agencies;
        this.inputs = inputs;
        this.lupMap = lupMap;
        this.colorTable = colorTable;
        this.symbols = symbols;
        this.diagram = null;
    }

    public Configuration getConfig() {
        return config;
    }

    /**
     * Render the SVG
     * 
     * @param g
     * @throws SVGException
     */
    public void renderSVG(Graphics2D g) throws SVGException {
        // Lazy load svg
        if (diagram == null) {
            lazyLoadSVG();
        }
        this.diagram.render(g);
    }

    @SuppressWarnings("deprecation")
    public void lazyLoadSVG() {
        File svg = new File(config.getString("s57.svg"));
        SVGUniverse universe = new SVGUniverse();

        try {
            diagram = universe.getDiagram(universe.loadSVG(svg.toURL()));
        }
        catch (MalformedURLException e) {
            System.out.println("error loading svg!");
        }
    }

    /**
     * @param acronym
     * @return
     */
    public Attribute getAttribute(String acronym) {
        return attributesMap.get(CSVType.STANDARD).get(acronym);
    }

    /**
     * @param acronym
     * @param type
     * @return
     */
    public Attribute getAttribute(String acronym, CSVType type) {
        return attributesMap.get(type).get(acronym);
    }

    /**
     * @param acronym
     * @return
     */
    public ObjectClass getObjectClass(String acronym) {
        return objectClassesMap.get(CSVType.STANDARD).get(acronym);
    }

    /**
     * @param acronym
     * @param type
     * @return
     */
    public ObjectClass getObjectClass(String acronym, CSVType type) {
        return objectClassesMap.get(type).get(acronym);
    }

    /**
     * Gets agency by id
     * 
     * @param id
     * @return Agency
     */
    public Agency getAgency(String id) {
        return agencies.get(id);
    }

    /**
     * Gets the expected input
     * 
     * @param attributeCode
     * @param inputID
     * @return Input
     */
    public Input getInput(int attributeCode, int inputID) {
        return inputs.get(attributeCode, inputID);
    }

    public LookupTable getLookupTable(LookupTableType type) {
        return lupMap.get(type);
    }

    public Map<LookupTableType, LookupTableRecord> getLupRecord(S57GeometryType type, String acronym,
            Map<String, S57Attribute> attributes) {
        Map<LookupTableType, LookupTableRecord> records = new HashMap<LookupTableType, LookupTableRecord>();
        switch (type) {
            case LINE:
                records.put(LookupTableType.LINES, this.getLookupTable(LookupTableType.LINES).get(acronym, attributes));
                break;
            case AREA:
                records.put(LookupTableType.PLAIN_BOUNDARIES,
                        this.getLookupTable(LookupTableType.PLAIN_BOUNDARIES).get(acronym, attributes));
                records.put(LookupTableType.SYMBOLIZED_BOUNDARIES,
                        this.getLookupTable(LookupTableType.SYMBOLIZED_BOUNDARIES).get(acronym, attributes));
                break;
            case POINT:
                records.put(LookupTableType.PAPER_CHART,
                        this.getLookupTable(LookupTableType.PAPER_CHART).get(acronym, attributes));
                records.put(LookupTableType.SIMPLIFIED,
                        this.getLookupTable(LookupTableType.SIMPLIFIED).get(acronym, attributes));
                break;
            case NONE:
                break;
            default:
                break;
        }
        return records;
    }

    public Color getColor(String name, ColorScheme scheme) {
        return colorTable.get(name).get(scheme);
    }

    public S52Symbol getSymbol(String code) {
        return symbols.getSymbol(code);
    }

    public S52Pattern getPattern(String code) {
        return symbols.getPattern(code);
    }

    public S52Symbol getLine(String code) {
        return symbols.getLine(code);
    }

    /**
     * @param config the config to set
     */
    public void setConfig(Configuration config) {
        this.config = config;
    }

}
