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

public enum InstructionType {
    TX, // "show text" command word
    TE, // For numeric text, an alphanumeric prefix or suffix is needed to
        // avoid confusion between the numbers of the text and the numbers
        // representing soundings. For this purpose the C format TE
        // command is used
    SY, // show symbol command word
    LS, // show simple line style command word
    LC, // "complex line style" command word
    AC, // "CFILL 'colour fill' command word"
    AP, // "pattern fill" command word
    CS; // SPROC "symbology procedure call" command word:
}
