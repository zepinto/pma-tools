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
package pt.lsts.s57.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.Attribute;

public class S57Attribute implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -2287913572573190345L;

    // static fields
    public static boolean verbose = false;

    /*
     * Attribute acronym 6 char codes to identify an attribute
     */
    private final String acronym;

    /*
     * Attribute values Need to be casted to the specific type and values not setted have "" value
     */
    private final List<String> value = new ArrayList<>();

    /**
     * attribute info {@link Attribute} has acronym , code , name , attribute type, class
     */
    private final Attribute attributeInfo;

    private final String meaning;

    /**
     * static factory method
     * 
     * @param resources
     * @param acronym
     * @param value
     * @return
     */
    public static S57Attribute forge(Resources resources, String acronym, String value) {
        return new S57Attribute(resources, acronym, value);
    }

    /**
     * Construct
     * 
     * @param resources
     * @param acronym
     * @param value
     */
    private S57Attribute(Resources resources, String acronym, String value) {
        this.acronym = acronym;
        var temp = value.split(",");
        for (var v : temp) {
            var valueTrimmed = v.trim();
            if (valueTrimmed.contains("/")) {
                this.value.addAll(Arrays.asList(valueTrimmed.split("/")));
            } else {
                this.value.add(valueTrimmed);
            }
        }
        this.attributeInfo = resources.getAttribute(acronym);
        this.meaning = setMeaning(resources);
    }

    private String setMeaning(Resources resources) {
        if (value.isEmpty() || value.get(0).isEmpty()) {
            return "";
        }
        if ("E".equals(attributeInfo.getAttributeType())) {
            var test = resources.getInput(attributeInfo.getCode(), Integer.parseInt(value.get(0)));
            return (test != null) ? test.getMeaning() : "";
        }
        if ("L".equals(attributeInfo.getAttributeType())) {
            var sb = new StringBuilder();
            for (var item : value) {
                if (item.isEmpty()) continue;
                var input = resources.getInput(attributeInfo.getCode(), Integer.parseInt(item));
                if (input != null) {
                    sb.append(" ").append(input.getMeaning());
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Attribute acronym 6 char codes to identify an attribute
     * 
     * @return acronym
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * Attribute values Need to be casted to the specific type and values not setted have "" value
     * 
     * @return value if type E returns meaning, if type L returns list of meanings joined by "," otherwise value
     */
    public List<String> getValue() {
        return value;
    }

    public boolean isEmpty() {
        if (value.isEmpty()) return true;
        return value.size() == 1 && value.get(0).isEmpty();
    }

    /**
     * Gets the meaning ( human readable value ) for E and L types. Other type return null or empty if the attribute has
     * no value
     * 
     * @return
     */
    public String getMeaning() {
        return meaning;
    }

    /**
     * Gets values as a list of integer for types E and L. Others types returns null
     * 
     * @return
     */
    public List<Integer> getValueAsInt() {
        var type = attributeInfo.getAttributeType();
        if ("E".equals(type) || "L".equals(type)) {
            return value.stream()
                    .filter(v -> !v.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        }
        return null;
    }

    /**
     * @return Attribute code
     */
    public Integer getCode() {
        return attributeInfo.getCode();
    }

    /**
     * @return Human readable attribute name
     */
    public String getName() {
        return attributeInfo.getName();
    }

    /**
     * Attribute type categories:
     * <ul>
     * <li>E : enumerated 1 value selected from the expected input list
     * <li>L : List - 1 or more values selected from the expected input list
     * <li>F : Floating point number - range, resolution, units, format given ??
     * <li>I : Integer value - range, units and format is given ???
     * <li>A : Coded string - format is given
     * <li>S : Free format string
     * <ul>
     * 
     * @return the attributeType
     */
    public String getAttributeType() {
        return attributeInfo.getAttributeType();
    }

    /**
     * Attribute class
     * <ul>
     * <li>"F" Feature attribute
     * <li>"N" Feature national attribute
     * <li>"S" Spatial attribute
     * </ul>
     * 
     * @return the classAbbrev
     */
    public String getClassAbbrev() {
        return attributeInfo.getClassAbbrev();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "S57Attribute [acronym=" + acronym + ", value=" + value + ", attributeInfo=" + attributeInfo + "]";
    }

    /**
     * @return the verbose
     */
    public static boolean isVerbose() {
        return verbose;
    }

    /**
     * @param verbose the verbose to set
     */
    public static void setVerbose(boolean verbose) {
        S57Attribute.verbose = verbose;
    }

}
