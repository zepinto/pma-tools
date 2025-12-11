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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LookupTableRecord implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -7304729963342211315L;
    private final String acronym;
    private final Map<String, List<String>> attributes = new HashMap<String, List<String>>();
    private final List<Instruction> intructions = new ArrayList<Instruction>();
    private final int priority;
    private final boolean radarFlag;
    private final DisplayCategory category;
    private final String group;
    private final boolean conditional;
    private final boolean empty;

    private LookupTableRecord(List<String> tokens) {
        acronym = tokens.get(0);
        // attributes combos
        if (!tokens.get(1).isEmpty()) {
            List<String> temp = Arrays.asList(tokens.get(1).split("[|]"));
            Iterator<String> iterator = temp.iterator();
            while (iterator.hasNext()) {
                String item = iterator.next();
                if (!item.isEmpty()) {
                    String name = item.substring(0, 6);
                    List<String> values = Arrays.asList(item.substring(6).split(",", -1));
                    attributes.put(name, values);
                }
            }
        }

        // instructions
        boolean temp = false;
        if (!tokens.get(2).isEmpty()) {
            List<String> insts = Arrays.asList(tokens.get(2).split(";"));
            Iterator<String> iterator = insts.iterator();
            while (iterator.hasNext()) {
                String item = iterator.next();
                if (!item.isEmpty()) {
                    InstructionType type;
                    try {
                        type = InstructionType.valueOf(item.substring(0, 2));
                        if (!temp)
                            temp = type.equals(InstructionType.CS) ? true : false;
                        String params = item.substring(2).replace("(", "");
                        params = params.replace(")", "");
                        intructions.add(Instruction.forge(type, params));
                    }
                    catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
            empty = false;
        }
        else {
            empty = true;
        }
        conditional = temp;
        priority = Integer.valueOf(tokens.get(3));
        radarFlag = tokens.get(4).equals("O") ? true : false;
        category = tokens.get(5).isEmpty() ? null : DisplayCategory.valueOf(tokens.get(5).replace(" ", "_"));

        group = tokens.size() == 7 ? tokens.get(6) : "";

    }

    public static LookupTableRecord forge(List<String> tokens) {
        return new LookupTableRecord(tokens);
    }

    // TODO isEmpty

    // Accessors

    public List<String> getAttributeValues(String acronym) {
        return attributes.get(acronym);
    }

    /**
     * @return the acronym
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * @return the attributes
     */
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    /**
     * @return the intructions
     */
    public List<Instruction> getIntructions() {
        return intructions;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * OVERRADAR flag. It classifies whether objects are shown on top of the raw radar picture. Two different values can
     * occur in this field: <br>
     * 'O' (true) which puts the object's presentation over radar; and <br>
     * 'S' (false) which means that presentation is suppressed by radar
     * 
     * @return the radarFlag
     */
    public boolean getRadarFlag() {
        return radarFlag;
    }

    /**
     * @return the category
     */
    public DisplayCategory getCategory() {
        return category;
    }

    /**
     * @return the category in string format
     */
    public String getCategoryString() {
        if (category != null) {
            return category.toString();
        }
        return "";
    }

    /**
     * @return the group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Check if this records has a conditional symbology
     * 
     * @return
     */
    public boolean hasConditional() {
        return conditional;
    }

    /**
     * @return the empty
     */
    public boolean isEmpty() {
        return empty;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "LookupTableRecord [acronym=" + acronym + ", attributes=" + attributes + ", intructions=" + intructions
                + ", priority=" + priority + ", radarFlag=" + radarFlag + ", category=" + category + ", group=" + group
                + "]";
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
        result = prime * result + ((acronym == null) ? 0 : acronym.hashCode());
        result = prime * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((category == null) ? 0 : category.hashCode());
        result = prime * result + (conditional ? 1231 : 1237);
        result = prime * result + ((group == null) ? 0 : group.hashCode());
        result = prime * result + ((intructions == null) ? 0 : intructions.hashCode());
        result = prime * result + priority;
        result = prime * result + (radarFlag ? 1231 : 1237);
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
        LookupTableRecord other = (LookupTableRecord) obj;
        if (acronym == null) {
            if (other.acronym != null)
                return false;
        }
        else if (!acronym.equals(other.acronym))
            return false;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        }
        else if (!attributes.equals(other.attributes))
            return false;
        if (category != other.category)
            return false;
        if (conditional != other.conditional)
            return false;
        if (group == null) {
            if (other.group != null)
                return false;
        }
        else if (!group.equals(other.group))
            return false;
        if (intructions == null) {
            if (other.intructions != null)
                return false;
        }
        else if (!intructions.equals(other.intructions))
            return false;
        if (priority != other.priority)
            return false;
        if (radarFlag != other.radarFlag)
            return false;
        return true;
    }

}
