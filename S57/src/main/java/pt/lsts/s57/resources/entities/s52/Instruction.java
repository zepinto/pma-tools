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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class Instruction implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 2581432604148279260L;
    private final InstructionType type;
    private final List<String> params;

    private Instruction(InstructionType type, String params) {
        this.type = type;
        this.params = Arrays.asList(params.split(","));
    }

    public static Instruction forge(InstructionType type, String params) {
        return new Instruction(type, params);
    }

    public boolean isConditional() {
        return type.equals(InstructionType.CS) ? true : false;
    }

    // Accessors
    /**
     * @return the type
     */
    public InstructionType getType() {
        return type;
    }

    /**
     * @return the params
     */
    public List<String> getParams() {
        return params;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((params == null) ? 0 : params.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Instruction other = (Instruction) obj;
        if (params == null) {
            if (other.params != null)
                return false;
        }
        else if (!params.equals(other.params))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Instruction [type=" + type + ", params=" + params + "]";
    }
}
