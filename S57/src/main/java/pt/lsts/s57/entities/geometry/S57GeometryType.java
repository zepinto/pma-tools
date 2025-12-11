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
 * 16 de Set de 2011
 */
package pt.lsts.s57.entities.geometry;

/**
 * Enum representing S57 geometry types with their code values.
 * 
 * @author Hugo Dias
 */
public enum S57GeometryType {
    POINT("1"),
    AREA("3"),
    LINE("2"),
    NONE("255");

    private final String code;

    S57GeometryType(String code) {
        this.code = code;
    }

    /**
     * Gets the code value for this geometry type.
     * 
     * @return the code string
     */
    public String getCode() {
        return code;
    }
    
    /**
     * @deprecated Use {@link #getCode()} instead
     */
    @Deprecated(forRemoval = true)
    public String getName() {
        return code;
    }
}
