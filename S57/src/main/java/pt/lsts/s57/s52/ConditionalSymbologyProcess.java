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
package pt.lsts.s57.s52;

import java.util.ArrayList;
import java.util.List;

import pt.lsts.s57.S57Factory;
import pt.lsts.s57.entities.S57Attribute;
import pt.lsts.s57.entities.S57Map;
import pt.lsts.s57.entities.S57ObjectPainter;
import pt.lsts.s57.entities.geometry.S57GeometryType;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.resources.entities.s52.InstructionType;
import pt.lsts.s57.resources.entities.s52.LookupTableType;

/**
 * @see PresLib 3.4 part I chapter 12.1.2
 * @author Hugo Dias
 * @author Paulo Dias
 */
public class ConditionalSymbologyProcess {
    public static int UNKNOWN = 99999999;

    /**
     * Processes CS calls
     * 
     * @param instructions
     * @param obj
     * @param g
     * @param srend
     * @param resources
     * @param mc
     */
    public static void process(S57ObjectPainter obj, Resources resources, MarinerControls mc, S57Map map,
            List<S57ObjectPainter> painters) {
        var instructions = obj.getInstructions();
        var temp = new ArrayList<Instruction>();
        Instruction cs = null;

        for (var instruction : instructions) {
            if (!instruction.isConditional()) {
                temp.add(instruction);
            } else {
                cs = instruction;
            }
        }
        var csName = cs.getParams().get(0).substring(0, 6);
        var version = cs.getParams().get(0).substring(7, 8);
        switch (ConditionalSymbologyType.valueOf(csName)) {
            case DATCVR: // Not TODO for now
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
            case DEPARE: // PARTIAL, depends on RESCSP SEABED SAFCON
                DEPARE(version, temp, obj, resources, mc, painters);
                break;
            case DEPCNT: // DONE, depends on SAFCON
                DEPCNT(version, temp, obj, resources, mc);
                break;
            case DEPVAL: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case LIGHTS: // TODO, depends on LITDSN-
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
            case LITDSN: // TODO, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case OBSTRN: // DONE, continuation B (line) missing, depends on DEPVAL QUAPNT- SNDFRM? UDWHAZ~
                OBSTRN(version, temp, obj, resources, mc, map);
                break;
            case QUAPOS: // TODO, depends on QUALIN- QUAPNT-
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
            case QUALIN: // TODO, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case QUAPNT: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case RESARE: // DONE
                RESARE(version, temp, obj, resources, mc);
                break;
            case RESTRN: // DONE, depends RESCSP
                RESTRN(version, temp, obj, resources, mc);
                break;
            case RESCSP: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case SAFCON: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case SLCONS: // DONE 
                SLCONS(version, temp, obj, resources, mc, painters);
                break;
            case SEABED: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case SNDFRM: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case SOUNDG: // DONE but depends on SNDFRM
                SOUNDG(version, temp, obj, resources, mc);
                break;
            case SYMINS: // TODO ???
                if (S57Factory.DEBUG)
                    System.out.println("NEWOBJ");
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
            case TOPMAR: // TODO
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
            case UDWHAZ: // DONE, Is a sub-procedure, should not appear here
                addEmptyInstructionsToObject(csName, obj, temp, true);
                break;
            case WRECKS: // DONE, depends on DEPVAL QUAPNT SNDFRM UDWHAZ
                WRECKS(version, temp, obj, resources, mc, map);
                break;
            default:
                addEmptyInstructionsToObject(csName, obj, temp, false);
                break;
        }

    }

    private static void addEmptyInstructionsToObject(String csName, S57ObjectPainter obj, List<Instruction> temp,
            boolean isSubProcedure) {
        //System.out.println(csName + " not implemented yet" + (isSubProcedure ? " and is a sub-procedure" : ""));

        // DEBBUG TIRA ISTO DEPOIS DE IMPLEMTAR TUDO
        obj.setInstructions(temp);
    }
    
    /**
     * PARTIAL
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     * @param painters
     */
    public static void DEPARE(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc, List<S57ObjectPainter> painters) {
        /*
         * Required ECDIS startup values: The manufacturer is responsible for setting the SAFETY_CONTOUR to 30 metres.
         * This value should stay in operation until the mariner decides to select another safety contour.
         */
        double drval1, drval2;
        if (obj.getAttribute("DRVAL1").isEmpty()) {
            drval1 = -1;
        }
        else {
            drval1 = Double.valueOf(obj.getAttribute("DRVAL1").getValue().get(0));
        }
        if (obj.getAttribute("DRVAL2").isEmpty()) {
            drval2 = drval1 + 0.01;
        }
        else {
            drval2 = Double.valueOf(obj.getAttribute("DRVAL2").getValue().get(0));
        }
        List<Instruction> seabed = SubConditionalSymbology.SEABED(drval1, drval2, mc);
        for (Instruction instruction2 : seabed) {
            instructions.add(instruction2);
        }

        if (obj.getObject().getAcronym().equals("DRGARE")) {
            Instruction AP = Instruction.forge(InstructionType.AP, "DRGARE01");
            instructions.add(AP);
            Instruction LS = Instruction.forge(InstructionType.LS, "DASH,1,CHGRF");
            instructions.add(LS);
            if (!obj.getAttribute("RESTRN").isEmpty()) {
                List<Integer> values = obj.getAttribute("RESTRN").getValueAsInt();
                instructions.add(SubConditionalSymbology.RESCSP(values));
            }
        }
        // TODO check if we need to do the Continuation A and B ( page 143 preslib 3.4 part i )
        // continuation A
        // boolean safe = false;
        // boolean unsafe = false;
        // boolean loc_safety = false;
        // if (drval1 < mc.getSafetyContour())
        // {
        // unsafe = true;
        // }
        // else
        // {
        // safe = true;
        // }
        // // List<S57Object> depcnt = map.getObjects(new String[] { "DEPCNT" });
        // Double LOC_VALDCO = null;
        // S57ObjectPainter contour = null;
        // for (S57ObjectPainter painter : painters)
        // {
        // if (painter.getObject().getAcronym().equals("DEPCNT") && painter.getObject().intersect(o))
        // {
        // // System.out.println("INTERSECT");
        // // System.out.println(dep.toStringLup(mc));
        // // System.out.println("WITH");
        // // System.out.println(o.toStringLup(mc));
        // contour = painter;
        // if (painter.getAttribute("VALDCO").isEmpty())
        // {
        // LOC_VALDCO = 0.0;
        // }
        // else
        // {
        // LOC_VALDCO = Double.valueOf(painter.getAttribute("VALDCO").getValue().get(0));
        // }
        // }
        // else
        // {
        // LOC_VALDCO = null;
        // }
        // }
        // if (LOC_VALDCO != null && LOC_VALDCO == mc.getSafetyContour())
        // {
        // loc_safety = true;
        // }
        // else
        // {
        // // List<S57Object> depare_and_drgare = map.getObjects(new String[] { "DEPARE","DRGARE" });
        // double drval1_i;
        // boolean match = false;
        // for (S57ObjectPainter painter : painters)
        // {
        // if ((painter.getObject().getAcronym().equals("DEPARE") || painter.getObject().getAcronym().equals("DRGARE"))
        // && painter.getObject().intersect(o))
        // {
        // match = true;
        // if (painter.getAttribute("DRVAL1").isEmpty())
        // {
        // drval1_i = -1.0;
        // }
        // else
        // {
        // drval1_i = Double.valueOf(painter.getAttribute("DRVAL1").getValue().get(0));
        // if (drval1_i < mc.getSafetyContour())
        // {
        // unsafe = true;
        // }
        // else
        // {
        // safe = true;
        // }
        // }
        // }
        // }
        // if (!match)
        // {
        // // TODO
        // // List<S57Object> group1 = map.getGroupOneObjects();
        // // for (S57Object s57Object : group1)
        // // {
        // // if(s57Object.intersect(o) && (s57Object.getAcronym().equals("LNDARE") ||
        // s57Object.getAcronym().equals("UNSARE")))
        // // {
        // // //TODO
        // // }
        // // }
        // }
        // }
        // if (!loc_safety)
        // {
        // if (unsafe && safe)
        // {
        // loc_safety = true;
        // }
        // }
        //
        // // continuation B
        // if (loc_safety)
        // {
        // if (contour != null)
        // {
        // List<Instruction> i = new ArrayList<Instruction>();
        // i.add(Instruction.forge(InstructionType.LS, "SOLD,2,DEPSC"));
        // contour.setInstructions(i);
        //
        // }
        // // instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,DEPSC"));
        // }

        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     */
    public static void RESARE(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc) {
        List<Integer> restrn = !obj.getObject().isAttribute("RESTRN") ? null
                : obj.getAttribute("RESTRN").getValueAsInt();
        List<Integer> catrea = !obj.getObject().isAttribute("CATREA") ? null
                : obj.getAttribute("CATREA").getValueAsInt();
        if (restrn != null) {
            if (containsOneOf(new int[] { 7, 8, 14 }, restrn)) {
                // continuation A
                if (containsOneOf(new int[] { 1, 2, 3, 4, 5, 6, 13, 16, 17, 23, 24, 25, 26, 27 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ENTRES61"));
                }
                else if (catrea != null
                        && containsOneOf(new int[] { 1, 8, 9, 12, 14, 18, 19, 21, 24, 25, 26 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ENTRES61"));
                }
                else if (containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ENTRES71"));
                }
                else if (catrea != null && containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ENTRES71"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.SY, "ENTRES51"));
                }

                // end
                if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                    instructions.add(Instruction.forge(InstructionType.LC, "CTYARE51"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
                }
                obj.setPriority(6);
                obj.setInstructions(instructions);

                return;
            }
            if (containsOneOf(new int[] { 1, 2 }, restrn)) {
                // continuation B
                if (containsOneOf(new int[] { 3, 4, 5, 6, 13, 16, 17, 23, 24, 25, 26, 27 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ACHRES61"));
                }
                else if (catrea != null
                        && containsOneOf(new int[] { 1, 8, 9, 12, 14, 18, 19, 21, 24, 25, 26 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ACHRES61"));
                }
                else if (containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ACHRES71"));
                }
                else if (catrea != null && containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "ACHRES71"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.SY, "ACHRES51"));
                }

                // end
                if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                    instructions.add(Instruction.forge(InstructionType.LC, "ACHRES51"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
                }
                obj.setPriority(6);
                obj.setInstructions(instructions);
                return;
            }
            if (containsOneOf(new int[] { 3, 4, 5, 6, 24 }, restrn)) {
                // continuation C
                if (containsOneOf(new int[] { 13, 16, 17, 23, 24, 25, 26, 27 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "FSHRES61"));
                }
                else if (catrea != null
                        && containsOneOf(new int[] { 1, 8, 9, 12, 14, 18, 19, 21, 24, 25, 26 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "FSHRES61"));
                }
                else if (containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "FSHRES71"));
                }
                else if (catrea != null && containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "FSHRES71"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.SY, "FSHRES51"));
                }

                // end
                if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                    instructions.add(Instruction.forge(InstructionType.LC, "FSHRES51"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
                }
                obj.setPriority(6);
                obj.setInstructions(instructions);
                return;
            }
            if (containsOneOf(new int[] { 13, 16, 17, 23, 25, 26, 27 }, restrn)) {
                // continuation D
                if (containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "CTYARE71"));
                }
                else if (catrea != null && containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                    instructions.add(Instruction.forge(InstructionType.SY, "CTYARE71"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.SY, "CTYARE51"));
                }

                // end
                if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                    instructions.add(Instruction.forge(InstructionType.LC, "CTYARE51"));
                }
                else {
                    instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
                }
                obj.setPriority(6);
                obj.setInstructions(instructions);
                return;
            }
            if (containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
                instructions.add(Instruction.forge(InstructionType.SY, "INFARE51"));
            }
            else {
                instructions.add(Instruction.forge(InstructionType.SY, "RSRDEF51"));
            }

            if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                instructions.add(Instruction.forge(InstructionType.LC, "CTYARE51"));
            }
            else {
                instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
            }
        }
        else {// continuation E
            if (catrea != null) {
                if (containsOneOf(new int[] { 1, 8, 9, 12, 14, 18, 19, 21, 24, 25, 26 }, catrea)) {
                    if (containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                        instructions.add(Instruction.forge(InstructionType.SY, "CTYARE71"));
                    }
                    else {
                        instructions.add(Instruction.forge(InstructionType.SY, "CTYARE51"));
                    }
                }
                else {
                    if (containsOneOf(new int[] { 4, 5, 6, 7, 10, 20, 22, 23 }, catrea)) {
                        instructions.add(Instruction.forge(InstructionType.SY, "INFARE51"));
                    }
                    else {
                        instructions.add(Instruction.forge(InstructionType.SY, "RSRDEF51"));
                    }
                }
            }
            else {
                instructions.add(Instruction.forge(InstructionType.SY, "RSRDEF51"));
            }
            if (mc.getAreaStyle().equals(LookupTableType.SYMBOLIZED_BOUNDARIES)) {
                instructions.add(Instruction.forge(InstructionType.LC, "CTYARE51"));
            }
            else {
                instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHMGD"));
            }
        }

        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     */
    public static void SOUNDG(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc) {

        double depth = obj.getObject().getGeometry().getDepth();
        List<Instruction> i = SubConditionalSymbology.SNDFRM(depth, mc, obj);
        for (Instruction instruction2 : i) {
            instructions.add(instruction2);
        }
        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     * @param map
     */
    public static void WRECKS(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc, S57Map map) {
        Double depth = null;
        Double leastDepth = null;
        Double seabedDepth = null;
        List<Instruction> sndfrm = new ArrayList<Instruction>();

        Integer expsou = obj.isAttribute("EXPSOU") ? obj.getAttribute("EXPSOU").getValueAsInt().get(0)
                : UNKNOWN;
        Integer watlev = obj.isAttribute("WATLEV") ? obj.getAttribute("WATLEV").getValueAsInt().get(0)
                : UNKNOWN;
        Double valsou = obj.isAttribute("VALSOU")
                ? Double.valueOf(obj.getAttribute("VALSOU").getValue().get(0)) : Double.NaN;
        Integer catwrk = obj.isAttribute("CATWRK") ? obj.getAttribute("CATWRK").getValueAsInt().get(0)
                : UNKNOWN;

        if (!valsou.isNaN()) {
            depth = valsou;
            obj.setViewingGroup("34051");
            sndfrm = SubConditionalSymbology.SNDFRM(depth, mc, obj);
        }
        else {
            leastDepth = null;
            seabedDepth = null;

            Double[] values = SubConditionalSymbology.DEPVAL(expsou, watlev, map, obj);
            leastDepth = values[0];
            seabedDepth = values[1];
            if (leastDepth == null) {
                if (catwrk != UNKNOWN) {
                    if (catwrk == 1) {
                        depth = 20.1;
                        if (seabedDepth != null) {
                            leastDepth = seabedDepth - 66.0;
                            if (leastDepth < 20.1) {
                                depth = leastDepth;
                            }
                        }
                    }
                    else {
                        depth = -15.0;
                    }
                }
                else {
                    if (watlev != UNKNOWN) {
                        if (watlev == 3 || watlev == 5)
                            depth = 0.0;
                        else
                            depth = -15.0;
                    }
                    else {
                        depth = -15.0;
                    }
                }
            }
            else {
                depth = leastDepth;
            }
        }
        Instruction udwhaz = SubConditionalSymbology.UDWHAZ(depth, mc, map, obj, watlev);

        Instruction quapnt = SubConditionalSymbology.QUAPNT(obj, mc);
        
        if (obj.getObject().getGeoType() == S57GeometryType.POINT) {
            if (udwhaz != null) {
                instructions.add(udwhaz);
                obj.setInstructions(instructions);
                return;
            }
            else {
                // continuation A
                if (!valsou.isNaN()) {
                    if (valsou <= 20) {
                        instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                    }
                    else {
                        instructions.add(Instruction.forge(InstructionType.SY, "DANGER02"));
                    }

                    if (quapnt != null)
                        instructions.add(quapnt);
                    for (Instruction i : sndfrm) {
                        instructions.add(i);
                    }
                }
                else {
                    if (catwrk == 1 && watlev == 3)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS04"));
                    else if (catwrk == 2 && watlev == 3)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS05"));
                    else if (catwrk == 4)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else if (catwrk == 5)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else if (watlev == 1)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else if (watlev == 2)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else if (watlev == 5)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else if (watlev == 4)
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS01"));
                    else
                        instructions.add(Instruction.forge(InstructionType.SY, "WRECKS05"));
                }
                obj.setInstructions(instructions);
                return;
            }
        }
        else {
            // continuation B
            // Area objects
            
            S57Attribute quaposAtt = obj.getAttribute("QUAPOS");
            if (quaposAtt != null && !quaposAtt.isEmpty() 
                    && containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quaposAtt.getValueAsInt())) {
                instructions.add(Instruction.forge(InstructionType.LC, "LOWACC41"));
            }
            else { // Pos is accurate
                if (udwhaz != null) {
                    instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                }
                else {
                    if (!valsou.isNaN()) {
                        if (valsou <= 20)
                            instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                        else
                            instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHBLK"));
                    }
                    else {
                        switch (watlev) {
                            case 1:
                            case 2:
                                instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,CSTLN"));
                                break;
                            case 4:
                                instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CSTLN"));
                                break;
                            case 5:
                            case 3:
                            default:
                                instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CSTLN"));
                                break;
                        }
                    }
                }
            }
            
            if (!valsou.isNaN()) {
                if (udwhaz != null) {
                    instructions.add(udwhaz);
                }
                else {
                    if (sndfrm != null && !sndfrm.isEmpty())
                        instructions.addAll(sndfrm);
                }
                
                if (quapnt != null)
                    instructions.add(quapnt);
            }
            else {
                switch (watlev) {
                    case 1:
                    case 2:
                        instructions.add(Instruction.forge(InstructionType.AC, "CHGRN"));
                        break;
                    case 4:
                        instructions.add(Instruction.forge(InstructionType.AC, "DEPIT"));
                        break;
                    case 5:
                    case 3:
                    default:
                        instructions.add(Instruction.forge(InstructionType.AC, "DEPVS"));
                        break;
                }
                
                if (udwhaz != null)
                    instructions.add(udwhaz);
                
                if (quapnt != null)
                    instructions.add(quapnt);
            }
        }

        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     */
    public static void DEPCNT(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc) {

        S57Attribute quaposAtt = obj.getAttribute("QUAPOS");
        if (quaposAtt != null && !quaposAtt.isEmpty()) {
            List<Integer> quaposVal = quaposAtt.getValueAsInt();
            if (containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quaposVal))
                instructions.add(Instruction.forge(InstructionType.LS, "DASH,1,DEPCN"));
            else
                instructions.add(Instruction.forge(InstructionType.LS, "SOLD,1,DEPCN"));
        }
        else {
            instructions.add(Instruction.forge(InstructionType.LS, "SOLD,1,DEPCN"));
        }

        if (mc.isContourLabels()) { // contour labels on
            double valdcoVal = obj.getAttribute("VALDCO") == null || obj.getAttribute("VALDCO").isEmpty() ? 0.0
                    : Double.valueOf(obj.getAttribute("VALDCO").getValue().get(0));
            List<Instruction> list = SubConditionalSymbology.SAFCON(valdcoVal);
            instructions.addAll(list);
        }

        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     */
    public static void RESTRN(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc) {
        if (!obj.getAttribute("RESTRN").isEmpty()) {
            List<Integer> values = obj.getAttribute("RESTRN").getValueAsInt();
            instructions.add(SubConditionalSymbology.RESCSP(values));
        }
        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     * @param map
     */
    public static void OBSTRN(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc, S57Map map) {
        Double valsou = !obj.getObject().isAttribute("VALSOU") ? null
                : Double.valueOf(obj.getAttribute("VALSOU").getValue().get(0));
        Integer watlev = !obj.getObject().isAttribute("WATLEV") ? UNKNOWN
                : obj.getAttribute("WATLEV").getValueAsInt().get(0);
        Integer expsou = !obj.getObject().isAttribute("EXPSOU") ? UNKNOWN
                : obj.getAttribute("EXPSOU").getValueAsInt().get(0);
        Integer catobs = !obj.getObject().isAttribute("CATOBS") ? null
                : obj.getAttribute("CATOBS").getValueAsInt().get(0);

        Double depthValue = null;
        List<Instruction> sndfrm = null;
        if (valsou != null) {
            depthValue = valsou;
            obj.setViewingGroup("34051");
            sndfrm = SubConditionalSymbology.SNDFRM(depthValue, mc, obj);
        }
        else {
            Double[] values = SubConditionalSymbology.DEPVAL(expsou, watlev, map, obj);
            if (values[0] == null) {
                if (watlev == UNKNOWN) {
                    depthValue = -15.0;
                }
                else {
                    switch (watlev) {
                        case 5:
                            depthValue = 0.0;
                            break;
                        case 3:
                            depthValue = 0.01;
                            break;
                        case 4:
                            depthValue = -15.0;
                            break;
                        case 1:
                            depthValue = -15.0;
                            break;
                        case 2:
                            depthValue = -15.0;
                            break;
                        default:
                            depthValue = -15.0;
                            break;
                    }
                    if (catobs != null && catobs == 6)
                        depthValue = 0.01;
                }
            }
            else {
                depthValue = values[0];
            }
        }
        Instruction udwhaz = SubConditionalSymbology.UDWHAZ(depthValue, mc, map, obj, watlev);
        S57GeometryType type = obj.getObject().getGeoType();
        switch (type) {
            case POINT:
                // continuation A
                if (udwhaz != null) {
                    instructions.add(udwhaz);
                    obj.setInstructions(instructions);
                    return;
                }
                else {
                    boolean sounding = false;
                    if (valsou != null) {
                        if (valsou <= 20) {
                            if (obj.getObject().getAcronym().equals("UWTROC")) {
                                switch (watlev) {
                                    case 3:
                                        instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                                        sounding = true;
                                        break;
                                    case 4:
                                        instructions.add(Instruction.forge(InstructionType.SY, "UWTROC04"));
                                        sounding = false;
                                        break;
                                    case 5:
                                        instructions.add(Instruction.forge(InstructionType.SY, "UWTROC04"));
                                        sounding = false;
                                        break;
                                    default:
                                        instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                                        sounding = true;
                                        break;
                                }
                            }
                            else {
                                if (!obj.getObject().getAcronym().equals("OBSTRN"))
                                    throw new IllegalArgumentException(
                                            "must be OBSTRN and is " + obj.getObject().getAcronym());
                                if (catobs != null && catobs == 6) {
                                    instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                                    sounding = true;
                                }
                                else {
                                    switch (watlev) {
                                        case 1:
                                            instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN11"));
                                            sounding = false;
                                            break;
                                        case 2:
                                            instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN11"));
                                            sounding = false;
                                            break;
                                        case 3:
                                            instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                                            sounding = true;
                                            break;
                                        case 4:
                                            instructions.add(Instruction.forge(InstructionType.SY, "DANGER03"));
                                            sounding = true;
                                            break;
                                        default:
                                            instructions.add(Instruction.forge(InstructionType.SY, "DANGER01"));
                                            sounding = true;
                                            break;
                                    }
                                }
                            }
                        }
                        else {
                            instructions.add(Instruction.forge(InstructionType.SY, "DANGER02"));
                            sounding = true;
                        }
                    }
                    else {
                        if (obj.getObject().getAcronym().equals("UWTROC")) {
                            switch (watlev) {
                                case 3:
                                    instructions.add(Instruction.forge(InstructionType.SY, "UWTROC03"));
                                    break;
                                default:
                                    instructions.add(Instruction.forge(InstructionType.SY, "UWTROC04"));
                                    break;
                            }
                        }
                        else {
                            if (!obj.getObject().getAcronym().equals("OBSTRN"))
                                throw new IllegalArgumentException(
                                        "must be OBSTRN and is " + obj.getObject().getAcronym());
                            if (catobs != null && catobs == 6) {
                                instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN01"));
                            }
                            else {
                                switch (watlev) {
                                    case 1:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN11"));
                                        break;
                                    case 2:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN11"));
                                        break;
                                    case 3:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN01"));
                                        break;
                                    case 4:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN03"));
                                        break;
                                    case 5:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN03"));
                                        break;
                                    default:
                                        instructions.add(Instruction.forge(InstructionType.SY, "OBSTRN01"));
                                        break;
                                }
                            }
                        }
                    }

                    if (sounding == true) {
                        for (Instruction instruction : sndfrm) {
                            instructions.add(instruction);
                        }
                    }
                    obj.setInstructions(instructions);
                    return;
                }
            case LINE:
                // continuation B
                List<Integer> quapos = !obj.getObject().isAttribute("QUAPOS") ? null
                        : obj.getAttribute("QUAPOS").getValueAsInt();
                if (quapos != null && !quapos.isEmpty() 
                        && containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quapos)) {
                    if (udwhaz != null)
                        instructions.add(Instruction.forge(InstructionType.LC, "LOWACC41"));
                    else
                        instructions.add(Instruction.forge(InstructionType.LC, "LOWACC31"));
                }
                else {
                    if (udwhaz != null) {
                        instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                    }
                    else {
                        if (valsou != null) {
                            if (valsou <= 20)
                                instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                            else
                                instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHBLK"));
                        }
                        else {
                            instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                        }
                    }
                }
                
                if (udwhaz != null) {
                    instructions.add(udwhaz);
                }
                else {
                    if (valsou != null)
                        instructions.addAll(sndfrm);
                }
                break;
            case AREA:
                // continuation C
                if (udwhaz != null) {
                    instructions.add(Instruction.forge(InstructionType.AC, "DEPVS"));
                    instructions.add(Instruction.forge(InstructionType.AP, "FOULAR01"));
                    instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                    instructions.add(udwhaz);
                    obj.setInstructions(instructions);
                    return;
                }
                if (valsou != null) {
                    if (valsou <= 20) {
                        instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                    }
                    else {
                        instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CHGRD"));
                    }
                    for (Instruction instruction : sndfrm) {
                        instructions.add(instruction);
                    }
                }
                else {
                    if (catobs != null && catobs == 6) {
                        instructions.add(Instruction.forge(InstructionType.AP, "FOULAR01"));
                        instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                    }
                    else {
                        switch (watlev) {
                            case 1:
                                instructions.add(Instruction.forge(InstructionType.AC, "CHBRN"));
                                instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,CSTLN"));
                                break;
                            case 2:
                                instructions.add(Instruction.forge(InstructionType.AC, "CHBRN"));
                                instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,CSTLN"));
                                break;
                            case 3:
                                instructions.add(Instruction.forge(InstructionType.AC, "DEPVS"));
                                instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                                break;
                            case 4:
                                instructions.add(Instruction.forge(InstructionType.AC, "DEPIT"));
                                instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CSTLN"));
                                break;
                            case 5:
                                instructions.add(Instruction.forge(InstructionType.AC, "DEPVS"));
                                instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                                break;
                            default:
                                instructions.add(Instruction.forge(InstructionType.AC, "DEPVS"));
                                instructions.add(Instruction.forge(InstructionType.LS, "DOTT,2,CHBLK"));
                                break;
                        }
                    }
                }
                obj.setInstructions(instructions);
                return;

            default:
                break;
        }
        obj.setInstructions(instructions);
    }

    /**
     * COMPLETE IMPLEMENTATION
     * 
     * @param version
     * @param instructions
     * @param obj
     * @param resources
     * @param mc
     * @param painters
     */
    public static void SLCONS(String version, List<Instruction> instructions, S57ObjectPainter obj, Resources resources,
            MarinerControls mc, List<S57ObjectPainter> painters) {
        try {
            switch (obj.getObject().getGeoType()) {
                case POINT:
                    S57Attribute quaposAtt = obj.getAttribute("QUAPOS");
                    if (!quaposAtt.isEmpty()) {
                        List<Integer> quaposVal = quaposAtt.getValueAsInt();
                        if (containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quaposVal))
                            instructions.add(Instruction.forge(InstructionType.SY, "LOWACC01"));
                    }
                    break;
                    
                case LINE:
                case AREA: // Continuation A is equal to Line 
    //                obj.getObject().getGeometry();
                    // For each of spacial Geom!
                    quaposAtt = obj.getAttribute("QUAPOS");
                    if (quaposAtt != null && !quaposAtt.isEmpty()) {
                        List<Integer> quaposVal = quaposAtt.getValueAsInt();
                        if (containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quaposVal))
                            instructions.add(Instruction.forge(InstructionType.LC, "LOWACC21")); // Not sure if this overide the bottom lines??
                    }
                    // end for
                    S57Attribute attTmp = obj.getObject().getAttribute("CONDTN");
                    int condtnVal = attTmp == null || attTmp.isEmpty() ? Integer.MIN_VALUE : attTmp.getValueAsInt().get(0);
                    attTmp = obj.getObject().getAttribute("CATSLC");
                    int catslcVal = attTmp == null || attTmp.isEmpty() ? Integer.MIN_VALUE : attTmp.getValueAsInt().get(0);
                    attTmp = obj.getObject().getAttribute("WATLEV");
                    int watlevVal = attTmp == null || attTmp.isEmpty() ? Integer.MIN_VALUE : attTmp.getValueAsInt().get(0);
    
                    if (condtnVal == 1)
                        instructions.add(Instruction.forge(InstructionType.LS, "DASH,1,CSTLN"));
                    else if (condtnVal == 2)
                        instructions.add(Instruction.forge(InstructionType.LS, "DASH,1,CSTLN"));
                    else if (catslcVal == 6)
                        instructions.add(Instruction.forge(InstructionType.LS, "SOLD,4,CSTLN"));
                    else if (catslcVal == 15)
                        instructions.add(Instruction.forge(InstructionType.LS, "SOLD,4,CSTLN"));
                    else if (catslcVal == 16)
                        instructions.add(Instruction.forge(InstructionType.LS, "SOLD,4,CSTLN"));
                    else if (watlevVal == 2)
                        instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,CSTLN"));
                    else if (watlevVal == 3)
                        instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CSTLN"));
                    else if (watlevVal == 4)
                        instructions.add(Instruction.forge(InstructionType.LS, "DASH,2,CSTLN"));
                    else
                        instructions.add(Instruction.forge(InstructionType.LS, "SOLD,2,CSTLN"));
                    break;
                    
                default:
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        obj.setInstructions(instructions);                
    }
    
    // helpers
    public static boolean containsOneOf(int[] values, List<Integer> list) {
        return java.util.Arrays.stream(values).anyMatch(list::contains);
    }
}
