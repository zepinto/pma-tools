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

public class Input implements Serializable {

    private static final long serialVersionUID = -6849865962699340976L;
    protected final int code;
    protected final int id;
    protected final String meaning;

    protected Input(int code, int id, String meaning) {
        this.code = code;
        this.id = id;
        this.meaning = meaning;
    }

    /**
     * @return the id
     */
    public String getCodeId() {
        return Integer.toString(code) + Integer.toString(id);
    }

    /**
     * @return the code
     */
    public int getCode() {
        return code;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @return the meaning
     */
    public String getMeaning() {
        if (meaning == null)
            return "";
        return meaning;
    }

    @Override
    public String toString() {
        return code + "," + id;
    }
}
