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

import pt.lsts.s57.entities.S57Map;
import pt.lsts.s57.entities.S57Object;
import pt.lsts.s57.entities.S57ObjectPainter;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.DisplayCategory;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.resources.entities.s52.InstructionType;

/**
 * @author Hugo Dias
 * @author Paulo Dias
 */
public class SubConditionalSymbology {

    /**
     * Symbolization of areas that form the seabed COMPLETE IMPLEMENTATION
     * 
     * @param drval1
     * @param drval2
     * @param mc
     * @return
     */
    protected static List<Instruction> SEABED(double drval1, double drval2, MarinerControls mc) {
        List<Instruction> list = new ArrayList<Instruction>();
        String color = "DEPIT";
        boolean shallow = true;

        if (drval1 >= 0.0 && drval2 > 0) {
            color = "DEPVS";
        }

        if (mc.isTwoShades()) {
            if (drval1 >= mc.getSafetyContour() && drval2 > mc.getSafetyContour()) {
                color = "DEPDW";
                shallow = false;
            }
        }
        else {
            if (drval1 >= mc.getShallowContour() && drval2 > mc.getShallowContour()) {
                color = "DEPMS";
            }
            if (drval1 >= mc.getSafetyContour() && drval2 > mc.getSafetyContour()) {
                color = "DEPMD";
                shallow = false;
            }
            if (drval1 >= mc.getDeepContour() && drval2 > mc.getDeepContour()) {
                color = "DEPDW";
                shallow = false;
            }
        }
        list.add(Instruction.forge(InstructionType.AC, color));
        if (mc.isShallowPattern() && shallow) {
            list.add(Instruction.forge(InstructionType.AP, "DIAMOND1"));
        }
        return list;
    }

    /**
     *  RESCSP sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param restrn
     * @return
     */
    protected static Instruction RESCSP(List<Integer> restrn) {
        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 7, 8, 14 }, restrn)) {
            // continuation A
            if (restrn.contains(1) || restrn.contains(2) || restrn.contains(3) || restrn.contains(4)
                    || restrn.contains(5) || restrn.contains(6) || restrn.contains(13) || restrn.contains(16)
                    || restrn.contains(17) || restrn.contains(23) || restrn.contains(24) || restrn.contains(25)
                    || restrn.contains(26) || restrn.contains(27)) {
                return Instruction.forge(InstructionType.SY, "ENTRES61");
            }
            if (restrn.contains(9) || restrn.contains(10) || restrn.contains(11) || restrn.contains(12)
                    || restrn.contains(15) || restrn.contains(18) || restrn.contains(19) || restrn.contains(20)
                    || restrn.contains(21) || restrn.contains(22)) {
                return Instruction.forge(InstructionType.SY, "ENTRES71");
            }
            return Instruction.forge(InstructionType.SY, "ENTRES51");
        }

        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 1, 2 }, restrn)) {
            // continuation B
            if (restrn.contains(3) || restrn.contains(4) || restrn.contains(5) || restrn.contains(6)
                    || restrn.contains(13) || restrn.contains(16) || restrn.contains(17) || restrn.contains(23)
                    || restrn.contains(24) || restrn.contains(25) || restrn.contains(26) || restrn.contains(27)) {
                return Instruction.forge(InstructionType.SY, "ACHRES61");
            }
            if (restrn.contains(9) || restrn.contains(10) || restrn.contains(11) || restrn.contains(12)
                    || restrn.contains(15) || restrn.contains(18) || restrn.contains(19) || restrn.contains(20)
                    || restrn.contains(21) || restrn.contains(22)) {
                return Instruction.forge(InstructionType.SY, "ACHRES71");
            }
            return Instruction.forge(InstructionType.SY, "ACHRES51");
        }

        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 3, 4, 5, 6, 24 }, restrn)) {
            // continuation C
            if (restrn.contains(13) || restrn.contains(16) || restrn.contains(17) || restrn.contains(23)
                    || restrn.contains(25) || restrn.contains(26) || restrn.contains(27)) {
                return Instruction.forge(InstructionType.SY, "FSHRES61");
            }
            if (restrn.contains(9) || restrn.contains(10) || restrn.contains(11) || restrn.contains(12)
                    || restrn.contains(15) || restrn.contains(18) || restrn.contains(19) || restrn.contains(20)
                    || restrn.contains(21) || restrn.contains(22)) {
                return Instruction.forge(InstructionType.SY, "FSHRES71");
            }
            return Instruction.forge(InstructionType.SY, "FSHRES51");
        }

        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 13, 16, 17, 23, 25, 26, 27 }, restrn)) {
            // continuation D
            if (restrn.contains(9) || restrn.contains(10) || restrn.contains(11) || restrn.contains(12)
                    || restrn.contains(15) || restrn.contains(18) || restrn.contains(19) || restrn.contains(20)
                    || restrn.contains(21) || restrn.contains(22)) {
                return Instruction.forge(InstructionType.SY, "CTYARE71");
            }
            return Instruction.forge(InstructionType.SY, "CTYARE51");
        }

        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 9, 10, 11, 12, 15, 18, 19, 20, 21, 22 }, restrn)) {
            return Instruction.forge(InstructionType.SY, "INFARE51");
        }
        return Instruction.forge(InstructionType.SY, "RSRDEF51");
    }

    /**
     * SNDFRM sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param depth
     * @param mc
     * @param obj
     * @return
     */
    protected static List<Instruction> SNDFRM(double depth, MarinerControls mc, S57ObjectPainter obj) {
        /*
         * Required ECDIS startup values: The manufacturer is responsible for setting the SAFETY_DEPTH to 30 meters (see
         * also conditional symbology procedures "DEPAREnn" and "DEPCNTnn"). This value should stay in operation until
         * the mariner decides to select another safety depth.
         */
        List<Instruction> list = new ArrayList<Instruction>();
        List<String> names = new ArrayList<String>();
        S57Object o = obj.getObject();
        String prefix = "";
        if (depth <= mc.getSafetyContour()) {
            prefix = "SOUNDS";
        }
        else {
            prefix = "SOUNDG";
        }
        if (!o.getAttribute("TECSOU").isEmpty()) {
            if (o.getAttribute("TECSOU").getValueAsInt().contains(6)) {
                names.add(prefix.concat("B1"));
            }
        }
        boolean test = false;
        if (o.isAttribute("QUASOU") || o.isAttribute("STATUS")) {
            if (o.isAttribute("STATUS")) {
                List<Integer> status = o.getAttribute("STATUS").getValueAsInt();
                if (status.contains(18) && !test) {
                    list.add(Instruction.forge(InstructionType.SY, prefix.concat("C2")));
                    test = true;
                }
            }

            if (o.isAttribute("QUASOU")) {
                List<Integer> quasou = o.getAttribute("QUASOU").getValueAsInt();
                if (!test && (quasou.contains(3) || quasou.contains(4) || quasou.contains(5) || quasou.contains(8)
                        || quasou.contains(9))) {
                    list.add(Instruction.forge(InstructionType.SY, prefix.concat("C2")));
                    test = true;
                }
            }
        }
        if (!test && o.isAttribute("QUAPOS")) {
            List<Integer> quapos = o.getAttribute("QUAPOS").getValueAsInt();
            if (quapos.contains(2) || quapos.contains(3) || quapos.contains(4) || quapos.contains(5)
                    || quapos.contains(6) || quapos.contains(7) || quapos.contains(8) || quapos.contains(9)) {
                list.add(Instruction.forge(InstructionType.SY, prefix.concat("C2")));
            }
        }

        // continuation A
        if (depth < 0) {
            list.add(Instruction.forge(InstructionType.SY, prefix.concat("A1")));
        }

        if (depth < 10) {
            String leadingString = String.valueOf(depth).replace("-", "");
            int leading = Integer.valueOf(leadingString.substring(0, 1));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 10))));

            int fraction = Integer.valueOf(leadingString.substring(2));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(fraction + 50))));
            return list;
        }
        if (depth < 31 && String.valueOf(depth).contains(".") && !String.valueOf(depth).substring(3, 4).equals("0")) {
            int leading = Integer.valueOf(String.valueOf(depth).substring(0, 1));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 20))));

            int second = Integer.valueOf(String.valueOf(depth).substring(1, 2));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(second + 10))));

            int fraction = Integer.valueOf(String.valueOf(depth).substring(3));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(fraction + 50))));
            return list;
        }

        // continuation B
        depth = Integer.valueOf(String.valueOf(depth).substring(0, String.valueOf(depth).indexOf(".")));
        if (depth < 100) {
            int leading = Integer.valueOf(String.valueOf(depth).substring(0, 1));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 10))));

            int second = Integer.valueOf(String.valueOf(depth).substring(1, 2));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat("0".concat(String.valueOf(second)))));
            return list;
        }
        if (depth < 1000) {
            int leading = Integer.valueOf(String.valueOf(depth).substring(0, 1));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 20))));

            int second = Integer.valueOf(String.valueOf(depth).substring(1, 2));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(second + 10))));

            int third = Integer.valueOf(String.valueOf(depth).substring(2, 3));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat("0".concat(String.valueOf(third)))));
            return list;
        }
        if (depth < 10000) {
            int leading = Integer.valueOf(String.valueOf(depth).substring(0, 1));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 20))));

            int second = Integer.valueOf(String.valueOf(depth).substring(1, 2));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(second + 10))));

            int third = Integer.valueOf(String.valueOf(depth).substring(2, 3));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat("0".concat(String.valueOf(third)))));

            int fourth = Integer.valueOf(String.valueOf(depth).substring(3, 4));
            list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(fourth + 40))));

            return list;
        }

        // continuation C
        int leading = Integer.valueOf(String.valueOf(depth).substring(0, 1));
        list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(leading + 30))));

        int second = Integer.valueOf(String.valueOf(depth).substring(1, 2));
        list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(second + 20))));

        int third = Integer.valueOf(String.valueOf(depth).substring(2, 3));
        list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(third + 10))));

        int fourth = Integer.valueOf(String.valueOf(depth).substring(3, 4));
        list.add(Instruction.forge(InstructionType.SY, prefix.concat("0".concat(String.valueOf(fourth)))));

        int fifth = Integer.valueOf(String.valueOf(depth).substring(4, 5));
        list.add(Instruction.forge(InstructionType.SY, prefix.concat(String.valueOf(fifth + 40))));
        return list;
    }

    /**
     * DEPVAL sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param expsou
     * @param watlev
     * @param map
     * @param objP
     * @return double[] [0] = least_depth and [1] = seabed_depth
     */
    protected static Double[] DEPVAL(Integer expsou, Integer watlev, S57Map map, S57ObjectPainter objP) {
        Double least_depth = null;
        Double seabed_depth = null;
        S57Object calling = objP.getObject();
        for (S57Object obj : map.getGroupOneObjects()) {
            if (calling.intersects(obj)) {
                if (obj.getAcronym().equals("UNSARE")) {
                    least_depth = 0.0;
                    break;
                }
                else if (obj.getAcronym().equals("DEPARE") || obj.getAcronym().equals("DRGARE")) {
                    if (!obj.getAttribute("DRVAL1").isEmpty()) {
                        double drval1 = Double.valueOf(obj.getAttribute("DRVAL1").getValue().get(0));
                        if (least_depth == null) {
                            least_depth = drval1;
                        }
                        else if (least_depth > drval1) {
                            least_depth = drval1;
                        }
                    }
                }
            }

        }
        if (least_depth != null) {

            if (watlev == 3 && (expsou == 1 || expsou == 3)) {
                seabed_depth = least_depth;
            }
            else {
                seabed_depth = least_depth;
                least_depth = null;
            }
        }
        return new Double[] { least_depth, seabed_depth };
    }

    /**
     * UDWHAZ sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param depth
     * @param mc
     * @param map
     * @param objP
     * @param watlev
     * @return
     */
    protected static Instruction UDWHAZ(Double depth, MarinerControls mc, S57Map map, S57ObjectPainter objP,
            Integer watlev) {
        boolean danger = false;
        S57Object calling = objP.getObject();
        if (depth == null)
            return null;
        
        if (depth <= mc.getSafetyContour()) {
            for (S57Object obj : map.getGroupOneObjects()) {
                if ((obj.getAcronym().equals("DEPARE") || obj.getAcronym().equals("DRGARE"))) {
                    if (obj.getGeometry().containsPoint(calling.getGeometry())) {
                        if (!obj.getAttribute("DRVAL1").isEmpty()) {
                            if (Double.valueOf(obj.getAttribute("DRVAL1").getValue().get(0)) >= mc.getSafetyContour()) {
                                danger = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (danger) {
                if (watlev == 1 || watlev == 2) {
                    objP.setDisplayCategory(DisplayCategory.DISPLAYBASE);
                    objP.setViewingGroup("14050");
                    return null;
                }
                objP.setDisplayCategory(DisplayCategory.DISPLAYBASE);
                objP.setPriority(8);
                objP.setOverRadar(true);
                objP.setViewingGroup("14010");
                objP.setScamin(S57ObjectPainter.SCAMIN_INFINITE);
                return Instruction.forge(InstructionType.SY, "ISODGR01");
            }
            else {
                // continuation A
                if (!mc.isShowIsolatedDangerInShallowWater())
                    return null;
                
                for (S57Object obj : map.getGroupOneObjects()) {
                    if ((obj.getAcronym().equals("DEPARE") || obj.getAcronym().equals("DRGARE"))) {
                        if (obj.getGeometry().containsPoint(calling.getGeometry())) {
                            if (obj.isAttribute("DRVAL1")) {
                                double drval1Val = Double.valueOf(obj.getAttribute("DRVAL1").getValue().get(0));
                                if (drval1Val >= 0 && drval1Val < mc.getSafetyContour()) {
                                    danger = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (danger) {
                    if (watlev == 1 || watlev == 2) {
                        objP.setDisplayCategory(DisplayCategory.STANDARD);
                        objP.setViewingGroup("24050");
                        return null;
                    }
                    objP.setDisplayCategory(DisplayCategory.DISPLAYBASE);
                    objP.setPriority(8);
                    objP.setOverRadar(true);
                    objP.setViewingGroup("24020");
                    return Instruction.forge(InstructionType.SY, "ISODGR01");
                }
            }
        }
        
        return null;
    }
    
    /**
     * SAFCON sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param depth
     * @return
     */
    @SuppressWarnings("unused")
    protected static List<Instruction> SAFCON(double depth) {
        List<Instruction> list = new ArrayList<Instruction>();
        String symbPrefix = "SAFCON";
        
        if (depth < 0 || depth > 99999)
            return list; // Symbols could not be determined
        
        if (depth < 10) {
            int leading = (int) depth;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + "0" + leading));
            
            if (depth % 1 != 0) {
                int fraction = (int) ((depth % 1) * 10);
                fraction += 60;
                list.add(Instruction.forge(InstructionType.SY, symbPrefix + fraction));
            }

            return list;
        }
        
        if (depth < 100) {
            int leading = (int) depth;
            int fLeading = leading / 10;
            fLeading += 20;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + fLeading));

            int sLeading = (int) Math.round(((leading / 10.) % 1) * 10);
            sLeading += 10;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + sLeading));

            if (depth < 31 && depth % 1 != 0) {
                int fraction = (int) ((depth % 1) * 10);
                fraction += 50;
                list.add(Instruction.forge(InstructionType.SY, symbPrefix + fraction));
            }

            return list;
        }

        // Continuation A is manufacturer-optional
        
        // We don't have the symbols
        if (true)
            return list;
        
        if (depth < 1000) {
            int leading = (int) depth;
            int fLeading = leading / 100;
            fLeading += 80;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + fLeading));

            int sLeading = (int) Math.round(((leading / 100.) % 1) * 10);
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + "0" + sLeading));
            
            int tLeading = (int) Math.round(((leading / 10.) % 1) * 10);
            tLeading += 90;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + tLeading));

            return list;
        }

        if (depth < 10000) {
            int leading = (int) depth;
            int fLeading = leading / 1000;
            fLeading += 30;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + fLeading));

            int sLeading = (int) Math.round(((leading / 1000.) % 1) * 10);
            sLeading += 20;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + sLeading));
            
            int tLeading = (int) Math.round(((leading / 100.) % 1) * 10);
            tLeading += 10;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + tLeading));

            int frLeading = (int) Math.round(((leading / 10.) % 1) * 10);
            frLeading += 70;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + frLeading));

            return list;
        }

        if (depth < 100000) {
            int leading = (int) depth;
            int fLeading = leading / 10000;
            fLeading += 40;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + fLeading));

            int sLeading = (int) Math.round(((leading / 10000.) % 1) * 10);
            sLeading += 30;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + sLeading));
            
            int tLeading = (int) Math.round(((leading / 1000.) % 1) * 10);
            tLeading += 20;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + tLeading));

            int foLeading = (int) Math.round(((leading / 100.) % 1) * 10);
            foLeading += 10;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + foLeading));

            int fiLeading = (int) Math.round(((leading / 100.) % 1) * 10);
            fiLeading += 70;
            list.add(Instruction.forge(InstructionType.SY, symbPrefix + fiLeading));

            return list;
        }

        return list;
    }

    /**
     * QUAPNT sub conditional instruction. COMPLETE IMPLEMENTATION
     * 
     * @param obj
     * @param mc
     * @return
     */
    protected static Instruction QUAPNT(S57ObjectPainter obj, MarinerControls mc) {
        boolean accurate = true;
        
        if (!mc.isLowAccurateSymbols() || !obj.getObject().isAttribute("QUAPOS"))
            return null;
        
        List<Integer> quaposAttLst = obj.getObject().getAttribute("QUAPOS").getValueAsInt();
        if (ConditionalSymbologyProcess.containsOneOf(new int[] { 2, 3, 4, 5, 6, 7, 8, 9 }, quaposAttLst))
            accurate = false;
        
        if (!accurate)
            return Instruction.forge(InstructionType.SY, "LOWACC01");
        
        return null;
    }
}
