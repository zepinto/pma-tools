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

public enum DisplayCategory {
    DISPLAYBASE(0),
    STANDARD(1),
    OTHER(2),
    MARINERS_OTHER(3),
    MARINERS_STANDARD(4);

    private int code;

    /**
     * 
     */
    private DisplayCategory(int value) {
        code = value;
    }

    public int getCode() {
        return code;
    }
}
