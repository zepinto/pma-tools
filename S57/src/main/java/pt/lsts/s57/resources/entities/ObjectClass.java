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
import java.util.Arrays;
import java.util.List;

public class ObjectClass implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 2126694117199091048L;
    protected final Integer code;
    protected final String name;
    protected final String acronym;
    protected final List<String> attrib_A;
    protected final List<String> attrib_B;
    protected final List<String> attrib_C;
    protected final String type;
    protected final List<String> primitives;

    protected ObjectClass(Integer code, String name, String acronym, String attribA, String attribB, String attribC,
            String type, String primitives) {
        this.code = code;
        this.name = name;
        this.acronym = acronym;
        this.attrib_A = Arrays.asList(attribA.split(";"));
        this.attrib_B = Arrays.asList(attribB.split(";"));
        this.attrib_C = Arrays.asList(attribC.split(";"));
        this.type = type;
        this.primitives = Arrays.asList(primitives.split(";"));
    }

    /**
     * Object code
     * 
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * Human readable object name
     * 
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 6 Char code
     * 
     * @return acronym
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * Type A list of attributes for this object
     * 
     * @return
     */
    public List<String> getAttrib_A() {
        return attrib_A;
    }

    /**
     * Type B list of attributes for this object
     * 
     * @return
     */
    public List<String> getAttrib_B() {
        return attrib_B;
    }

    /**
     * Type C list of attributes for this object
     * 
     * @return
     */
    public List<String> getAttrib_C() {
        return attrib_C;
    }

    /**
     * Feature object type
     * <ul>
     * <li>G : Geografic
     * <li>M : Meta
     * <li>C : Collection
     * <li>$ : Cartografic
     * </ul>
     * 
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * List of geometric primitives
     * 
     * @return
     */
    public List<String> getPrimitives() {
        return primitives;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ObjectClass [code=" + code + ", objectClass=" + name + ", acronym=" + acronym + ", attrib_A=" + attrib_A
                + ", attrib_B=" + attrib_B + ", attrib_C=" + attrib_C + ", classAbbrev=" + type + ", primitives="
                + primitives + "]";
    }

}
