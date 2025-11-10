//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.neptus.sidescan;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.colormap.ColorMapFactory;
import pt.omst.neptus.util.ImageUtils;

@Slf4j
public class SidescanMarkerUtils {

    public static List<BufferedImage> getImages(SidescanLogMarker marker, SidescanParser sidescanParser) {
        ArrayList<BufferedImage> images = new ArrayList<>();
        SidescanParameters sidescanParams = sidescanParser.getDefaultParams();
 
        for (int i = 0; i < sidescanParser.getSubsystemList().size(); i++) {
            try {
                images.add(getSidescanMarkImage(sidescanParser, sidescanParams, marker, i, false));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return images;  
    }

    // public static double getSlantedX(SidescanLogMarker marker, IMraLogGroup source) {
    //     double x = marker.getX();
    //     double t = marker.getTimestamp() / 1000.0;
    //     IMCMessage m = source.getLsfIndex().getMessageAt("EstimatedState", t);
    //     double altitude = m.getDouble("alt");
    //     int sign = x < 0 ? -1 : 1;
    //     return sign * Math.sqrt(x * x + altitude * altitude);
    // }

    /**
     * Get EGN normalized before and after a sidescan marker
     * @param marker The sidescan marker, which contains the timestamp and subsystem
     * @param parser The sidescan parser
     * @param subsystem The subsystem to use
     * @param linesBefore The number of lines to get before the marker
     * @param linesAfter The number of lines to get after the marker
     * @return The list of sidescan lines
     */
    public static ArrayList<SidescanLine> getNormalizedLines(SidescanLogMarker marker, SidescanParser parser, int subsystem, int linesBefore, int linesAfter) {
        ArrayList<SidescanLine> linesBeforeList = new ArrayList<>();
        ArrayList<SidescanLine> linesAfterList = new ArrayList<>();
        SidescanParameters params = parser.getDefaultParams();

        int height = marker.getHeight();
        
        int desiredLinesBefore = linesBefore + height/2;
        int desiredLinesAfter = linesAfter + height/2;

        int timestep = 1000;
        for (long time = (long)(marker.getTimestamp()); linesAfterList.size() < desiredLinesAfter;time += timestep) {
            List<SidescanLine> lines = parser.getLinesBetween(time, time + timestep, subsystem, params);
            for (SidescanLine line : lines) {
                if (linesAfterList.size() < desiredLinesAfter) {
                    linesAfterList.add(line);
                    continue;
                }
                break;
            }
        }
        for (long time = (long)(marker.getTimestamp()-1); linesBeforeList.size() < desiredLinesBefore;time -= timestep) {
            List<SidescanLine> lines = parser.getLinesBetween(time-timestep, time, subsystem, params);

            for (SidescanLine line : lines) {
                if (linesBeforeList.size() < desiredLinesBefore) {
                    linesBeforeList.add(line);
                    continue;
                }
                break;
            }
        }

        linesBeforeList.addAll(linesAfterList);

        linesBeforeList.sort((o2, o1) -> (int) (o1.getTimestampMillis() - o2.getTimestampMillis()));
        return linesBeforeList;
    }

    /**
     * Get the slant distance from the altitude and ground distance
     * @param altitude The altitude
     * @param groundDistance The ground distance
     * @return The slant distance, if the ground distance is negative, the slant distance will be negative
     */
    public static double getSlantDistance(double altitude, double groundDistance) {
        int sign = groundDistance < 0 ? -1 : 1;
        //slant^2 = ground^2 + altitude^2
        return sign * Math.sqrt(groundDistance * groundDistance + altitude * altitude);
    }

    public static ArrayList<SidescanLine> getLines(SidescanParser ssParser, int subSys,
            SidescanParameters sidescanParams, SidescanLogMarker mark) {
        long t = (long) mark.getTimestamp();
        int h = mark.getHeight();
        ArrayList<SidescanLine> list = new ArrayList<>();
        long firstTimestamp = ssParser.firstPingTimestamp();
        long lastTimestamp = ssParser.lastPingTimestamp();
        long t1, t2;
        int deviation = 0;

        int counter = 0;
        while (list.size() < h && counter < 10) {// get enough lines
            counter++;// infinte cicle protection
            deviation += 250;
            t1 = t - deviation * (h / 2);
            t2 = t + deviation * (h / 2);
            if (t1 < firstTimestamp) {
                t1 = firstTimestamp;
            }
            if (t2 > lastTimestamp) {
                t2 = lastTimestamp;
            }
            System.out.println("Retrieving lines between " + new Date(t1) + " and " + new Date(t2));
            list = ssParser.getLinesBetween(t1, t2, subSys, sidescanParams);
        }

        return list;
    }

    public static SidescanLogMarker adjustMark(SidescanLogMarker mark) {
        SidescanLogMarker newMark = new SidescanLogMarker(mark.getLabel(), mark.getTimestamp(), mark.getLatRads(),
                mark.getLonRads(),
                mark.getX(), mark.getY(), mark.getWidth(), mark.getHeight(), mark.getFullRange(), mark.getSubSys(),
                ColorMapFactory.createBronzeColormap());
        newMark.setPoint(mark.isPoint());
        int h = newMark.getHeight();
        int w = newMark.getWidth();
        double wMeters = newMark.getFullRange();

        if (w == 0 && h == 0) {
            w = 100;
            h = 100;
            wMeters = -1;
        }

        // adjustments:
        if (w < 100 || h < 100 || wMeters < 0.05) {
            if (w < 100) {
                w = 100;
                wMeters = -1;// wMeters defined with range
            }
            if (h < 100)
                h = 100;
        } else if (w < 150 || h < 150) {
            if (w < 150) {
                w *= 1.2;
                wMeters *= 1.2;
            }
            if (h < 150)
                h *= 1.2;
        } else if (w < 200 || h < 200) {
            if (w < 200) {
                w *= 1.1;
                wMeters *= 1.1;
            }
            if (h < 200)
                h *= 1.1;
        }

        newMark.setHeight(h);
        newMark.setWidth(w);
        newMark.setFullRange(wMeters);

        return newMark;
    }

    public static ArrayList<SidescanLine> adjustLines(ArrayList<SidescanLine> list, SidescanLogMarker mark) {
        int h = mark.getHeight();
        long t = (long) mark.getTimestamp();

        int yref = list.size();
        while (yref > h) {
            long tFirst = list.get(0).getTimestampMillis();
            long tLast = list.get(list.size() - 1).getTimestampMillis();
            if (tFirst == t) {
                list.remove(list.size() - 1);
                yref--;
                continue;
            }
            if (tLast == t) {
                list.remove(0);
                yref--;
                continue;
            }
            if (Math.abs(t - tFirst) < Math.abs(t - tLast)) {
                list.remove(list.size() - 1);
                yref--;
                continue;
            }
            if (Math.abs(t - tFirst) >= Math.abs(t - tLast)) {
                list.remove(0);
                yref--;
            }
        }
        return list;

    }

    /**
     * @param subSysN = 0/1 index in list, subSys =
     *                ssParser.getSubsystemList().get(subSysN)
     */
    private static BufferedImage getSidescanMarkImage(SidescanParser ssParser, SidescanParameters sidescanParams,
            SidescanLogMarker mark,
            int subSysN, boolean adjustMark) throws Exception {
        BufferedImage result;

        SidescanLogMarker adjustedMark = mark;
        if(adjustMark)
            adjustedMark = adjustMark(mark);

        int subSys = ssParser.getSubsystemList().get(subSysN);
        double wMeters = adjustedMark.getFullRange();
        System.out.println("wMeters: " + wMeters);
        // get the lines
        ArrayList<SidescanLine> list;
        list = getLines(ssParser, subSys, sidescanParams, adjustedMark);

        list = adjustLines(list, adjustedMark);

        float range = list.get(list.size() / 2).getRange();
        System.out.println("range: " + range);
        if (wMeters == -1)
            wMeters = list.get(list.size() / 2).getRange() / 5;
        double x, x1, x2;
        x = adjustedMark.getX();
        x += range;
        x1 = (x - (wMeters / 2));
        x2 = (x + (wMeters / 2));

        // check limits & double frequency problems
        if (x > 2 * range || x < 0)// image outside of available range
            return null;
        if (x1 < 0) {
            x1 = 0;
        }
        if (x2 > 2 * range) {
            x2 = 2 * range;
        }

        if (x1 > x2)
            throw new Exception("x1>x2");

        int size = list.get(list.size() / 2).getData().length;
        int i1 = convertMtoIndex(x1, range, size);
        int i2 = convertMtoIndex(x2, range, size);

        if (i2 > size) {
            i2 = size;
        }
        if (i1 < 0) {
            i1 = 0;
        }

        //config.colorMap = ColorMapFactory.getColorMapByName(adjustedMark.getColorMap());

        result = createImgLineList(list, i1, i2, adjustedMark);

        return result;
    }

    public static BufferedImage drawImage(ArrayList<BufferedImage> imgLineList, SidescanLogMarker mark) {
        int w = mark.getWidth();
        int h = mark.getHeight();

        BufferedImage result;
        BufferedImage imgScalled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imgScalled.createGraphics();
        int y = imgLineList.size();
        for (BufferedImage imgLine : imgLineList) {
            if (y < 0)
                return null;
            g2d.drawImage(ImageUtils.getScaledImage(imgLine, imgScalled.getWidth(), imgLine.getHeight(), true), 0, y,
                    null);
            y--;
        }

        result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        if (imgScalled.getWidth() != result.getWidth() || imgScalled.getHeight() != result.getHeight()) {
            g2d = result.createGraphics();
            g2d.drawImage(ImageUtils.getScaledImage(imgScalled, result.getWidth(), result.getHeight(), true), 0, 0,
                    null);
        } else {
            result = imgScalled;
        }

        return result;
    }

    public static BufferedImage createImgLineList(ArrayList<SidescanLine> list, int i1, int i2,
            SidescanLogMarker mark) {

        int w = mark.getWidth();
        int h = mark.getHeight();

        BufferedImage imgScalled = new BufferedImage(w * 3, h * 3, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = imgScalled.createGraphics();

        int y = list.size();

        for (SidescanLine l : list) {

            BufferedImage imgLine = new BufferedImage(i2 - i1, 1, BufferedImage.TYPE_INT_RGB);
            for (int c = 0; c < i2 - i1; c++) {
                int rgb = ColorMapFactory.createBronzeColormap().getColor(l.getData()[c + i1]).getRGB();
                imgLine.setRGB(c, 0, rgb);
            }
            int vZoomScale = 3;
            Image full = ImageUtils.getScaledImage(imgLine, imgScalled.getWidth(), vZoomScale, true);
            g2d.drawImage(full, 0, imgScalled.getHeight() + h - y, null);

            y = y + vZoomScale;

        }
        return imgScalled;
    }

    /**
     * @param m     double in meters
     * @param range float in meters
     * @param size  max index on SidescanLine.data
     * @return convert double m in meters to corresponding index within size
     */
    public static int convertMtoIndex(double m, float range, int size) {
        return (int) ((m / (2 * range)) * size);
    }

    public static Color getColor(SidescanLogMarker mark, SidescanParser ssParser, SidescanParameters sidescanParams) {

        Color color;
        int d = 1;
        long t = (long) mark.getTimestamp();

        ArrayList<SidescanLine> list2 = ssParser.getLinesBetween(t - d, t + d, mark.getSubSys(), sidescanParams);
        while (list2.isEmpty()) {
            d += 10;
            list2 = ssParser.getLinesBetween(t - d, t + d, mark.getSubSys(), sidescanParams);
        }
        SidescanLine l = list2.get(list2.size() / 2);
        int index = convertMtoIndex(mark.getX() + l.getRange(), l.getRange(), l.getData().length);

        color = ColorMapFactory.createBronzeColormap().getColor(l.getData()[index]);
        return color;
    }
}