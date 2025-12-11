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
 * Nov 3, 2011
 */
package pt.lsts.s57.entities;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.s52.DisplayCategory;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.s52.SymbologyProcess;
import pt.omst.mapview.StateRenderer2D;

/**
 * Objects of this class have already processed the mariner controls
 * 
 * @author Hugo Dias
 */
public class S57ObjectPainter {

    public static double SCAMIN_INFINITE = 1499999; // 809999
    private Resources resources;
    private S57Object object;
    private int priority;
    private boolean overRadar;
    private DisplayCategory displayCategory;
    private String viewingGroup;
    private List<Instruction> instructions = new ArrayList<>();
    private double scamin;

    public static S57ObjectPainter forge(S57Object object, MarinerControls mc, Resources resources) {
        return new S57ObjectPainter(object, mc, resources);

    }

    private S57ObjectPainter(S57Object object, MarinerControls mc, Resources resources) {
        this.object = object;
        this.priority = object.getPriority(mc);
        this.overRadar = object.isOverRadar(mc);
        this.displayCategory = object.getDisplayCategory(mc);
        this.viewingGroup = object.getViewingGroup(mc);
        this.instructions = object.getInstructions(mc);
        this.resources = resources;
        var scaminAttr = object.getAttribute("SCAMIN");
        this.scamin = (scaminAttr == null || scaminAttr.isEmpty())
                ? SCAMIN_INFINITE 
                : Double.parseDouble(scaminAttr.getValue().get(0));
    }

    public void paint(Graphics g, StateRenderer2D render, MarinerControls mc) {
        SymbologyProcess.process(instructions, this, (Graphics2D) g, render, resources, mc);

    }

    public boolean hasConditional() {
        return instructions.stream().anyMatch(Instruction::isConditional);
    }

    /**
     * Accessors
     */

    public S57Attribute getAttribute(String acronym) {
        return object.getAttributes().get(acronym);
    }

    /**
     * Checks if attribute is given and not empty
     * 
     * @param acronym
     * @return
     */
    public boolean isAttribute(String acronym) {
        return object.isAttribute(acronym);
    }

    /**
     * Check if the attribute is given (may be empty)
     * 
     * @param acronym
     * @return
     */
    public boolean hasAttribute(String acronym) {
        return object.hasAttribute(acronym);
    }

    /**
     * @return the object
     */
    public S57Object getObject() {
        return object;
    }

    /**
     * @param object the object to set
     */
    public void setObject(S57Object object) {
        this.object = object;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param priority the priority to set
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return the overRadar
     */
    public boolean isOverRadar() {
        return overRadar;
    }

    /**
     * @param overRadar the overRadar to set
     */
    public void setOverRadar(boolean overRadar) {
        this.overRadar = overRadar;
    }

    /**
     * @return the displayCategory
     */
    public DisplayCategory getDisplayCategory() {
        return displayCategory;
    }

    /**
     * @param displayCategory the displayCategory to set
     */
    public void setDisplayCategory(DisplayCategory displayCategory) {
        this.displayCategory = displayCategory;
    }

    /**
     * @return the viewingGroup
     */
    public String getViewingGroup() {
        return viewingGroup;
    }

    /**
     * @param viewingGroup the viewingGroup to set
     */
    public void setViewingGroup(String viewingGroup) {
        this.viewingGroup = viewingGroup;
    }

    /**
     * @return the instructions
     */
    public List<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * @param instructions the instructions to set
     */
    public void setInstructions(List<Instruction> instructions) {
        this.instructions = instructions;
    }

    /**
     * @return the scamin
     */
    public double getScamin() {
        return scamin;
    }

    /**
     * @param scamin the scamin to set
     */
    public void setScamin(double scamin) {
        this.scamin = scamin;
    }

}
