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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;

import pt.lsts.s57.S57Factory;
import pt.lsts.s57.resources.entities.Agencies;
import pt.lsts.s57.resources.entities.Attributes;
import pt.lsts.s57.resources.entities.Inputs;
import pt.lsts.s57.resources.entities.ObjectClasses;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.lsts.s57.resources.entities.s52.LookupTable;
import pt.lsts.s57.resources.entities.s52.LookupTableType;
import pt.lsts.s57.resources.entities.s52.symbol.Symbols;

/**
 * ResourcesFactory build the Resources class instances and handles cache serialization
 * 
 * @author Hugo Dias
 */
public class ResourcesFactory {

    private ResourcesFactory() {
    }

    public static Resources build(Configuration config) {
        long start = System.nanoTime();

        // Object Classes
        ObjectClasses std = ObjectClasses.forge(config);
        ObjectClasses aml = ObjectClasses.forge(config, CSVType.AML);
        ObjectClasses iw = ObjectClasses.forge(config, CSVType.IW);

        Map<CSVType, ObjectClasses> objectClassesMap = new HashMap<CSVType, ObjectClasses>();
        objectClassesMap.put(CSVType.STANDARD, std);
        objectClassesMap.put(CSVType.AML, aml);
        objectClassesMap.put(CSVType.IW, iw);

        // Attributes
        Attributes stdAttrib = Attributes.forge(config);
        Attributes amlAttrib = Attributes.forge(config, CSVType.AML);
        Attributes iwAttrib = Attributes.forge(config, CSVType.IW);

        Map<CSVType, Attributes> attributesMap = new HashMap<CSVType, Attributes>();
        attributesMap.put(CSVType.STANDARD, stdAttrib);
        attributesMap.put(CSVType.AML, amlAttrib);
        attributesMap.put(CSVType.IW, iwAttrib);

        // Agencies
        Agencies agencies = Agencies.forge(config);

        // Inputs
        Inputs inputs = Inputs.forge(config);

        // LookUpTables
        LookupTable table1 = LookupTable.forge(config, LookupTableType.PAPER_CHART);
        LookupTable table2 = LookupTable.forge(config, LookupTableType.LINES);
        LookupTable table3 = LookupTable.forge(config, LookupTableType.PLAIN_BOUNDARIES);
        LookupTable table4 = LookupTable.forge(config, LookupTableType.SIMPLIFIED);
        LookupTable table5 = LookupTable.forge(config, LookupTableType.SYMBOLIZED_BOUNDARIES);

        Map<LookupTableType, LookupTable> lupMap = new HashMap<LookupTableType, LookupTable>();
        lupMap.put(LookupTableType.PAPER_CHART, table1);
        lupMap.put(LookupTableType.LINES, table2);
        lupMap.put(LookupTableType.PLAIN_BOUNDARIES, table3);
        lupMap.put(LookupTableType.SIMPLIFIED, table4);
        lupMap.put(LookupTableType.SYMBOLIZED_BOUNDARIES, table5);

        // Colors
        ColorTable colorTable = ColorTable.forge(config);

        // Symbols
        Symbols symbols = Symbols.forge(config, colorTable);

        if (S57Factory.DEBUG)
            System.out.println("Resources finished in " + ((System.nanoTime() - start) / 1E9) + "s");
        Resources resources = Resources.forge(config, objectClassesMap, attributesMap, agencies, inputs, lupMap,
                colorTable, symbols);
        return resources;

    }

}
