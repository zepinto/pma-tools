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

import java.io.Serializable;

public class Attribute implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -3943903273662326342L;
    protected final Integer code;
    protected final String name;
    protected final String acronym;
    protected final String attributeType;
    protected final String classAbbrev;

    protected Attribute(Integer code, String name, String acronym, String attributeType, String classAbbrev) {
        this.code = code;
        this.name = name;
        this.acronym = acronym;
        this.attributeType = attributeType;
        this.classAbbrev = classAbbrev;
    }

    /**
     * Attribute code
     * 
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * Human readable attribute name
     * 
     * @return name
     */
    public String getName() {
        return name;
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
     * Attribute type categories:
     * <ul>
     * <li>E : enumerated 1 value selected from the expected input list
     * <li>L : List - 1 or more values selected from the expected input list
     * <li>F : Floating point number - range, resolution, units, format given ??
     * <li>I : Integer value - range, units and format is given ???
     * <li>A : Coded string - format is given
     * <li>S : Free format string
     * </ul>
     * 
     * @return the attributeType
     */
    public String getAttributeType() {
        return attributeType;
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
        return classAbbrev;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Attribute [code=" + code + ", name=" + name + ", acronym=" + acronym + ", attributeType="
                + attributeType + ", classAbbrev=" + classAbbrev + "]";
    }

}
