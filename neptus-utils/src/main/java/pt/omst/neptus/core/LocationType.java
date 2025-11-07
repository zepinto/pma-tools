package pt.omst.neptus.core;

/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Paulo Dias, Ze Pinto
 * 2005/03/05
 */
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.omst.neptus.util.GuiUtils;

/**
 * The base for the definition of this coordinate system
 * is the N-E-D. That is x pointing to north, y to east
 * and z down. The origin of the default coordinate system
 * is N0 E0 with height 0 meters.
 * <p>
 * <p>
 * <b>Important note: </b> you should always implement all the methods in the
 * XmlOutputMethods interface and the DEFAULT_ROOT_ELEMENT variable. 
 * If not, the root element of the output XML will be the one of the parent class.<br/>
 * You should also implement the constructors and the load(String) method to
 * perfect results. It can be something like this: <code><br/>
 * &nbsp;&nbsp;public boolean load (String xml)<br/>
 * &nbsp;&nbsp;{<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;try<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;{<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;boolean res = super.load(xml);<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;if (!res)<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;throw new DocumentException();<br/>
 * <br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;...<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;} catch (DocumentException e)<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;{<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;LOG.error(e.getMessage(), e);<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;return false;<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;}<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;return true;<br/>
 * &nbsp;&nbsp;}<br/>
 * </code>
 *
 * @author Paulo Dias
 * @author ZP
 * @version 2.0 2011-10-03 Merge LocationType and AbstractLocationPoint
 */
public class LocationType implements Serializable, Comparable<LocationType>, Cloneable {
    private static final long serialVersionUID = -5672043713440034944L;
    private final static Logger LOG = LoggerFactory.getLogger(LocationType.class);
    private static NumberFormat nf8 = GuiUtils.getNeptusDecimalFormat(8);
    private static NumberFormat nf2 = GuiUtils.getNeptusDecimalFormat(2);

    protected String id = UUID.randomUUID().toString();
    protected String name = id;

    protected double latitudeRads = 0;
    protected double longitudeRads = 0;
    // spherical coordinates in degrees (º)
    protected double offsetDistance = 0;
    protected double azimuth = 0;
    protected double zenith = 90;
    private double depth = 0;
    // offsets are in meters (m)
    private boolean isOffsetNorthUsed = true;
    private double offsetNorth = 0;
    //    private double offsetSouth = 0;
    private boolean isOffsetEastUsed = true;
    private double offsetEast = 0;
    //    private double offsetWest = 0;
    private boolean isOffsetUpUsed = true;
    //    private double offsetUp = 0;
    private double offsetDown = 0;

    public LocationType() {
        super();
    }

    /**
     * @param offsetNorth               The offset North to set.
     * @param useOffsetNorthInXMLOutput updates the {@link #isOffsetNorthUsed()}.
     */
    public void setOffsetNorth(double offsetNorth, boolean useOffsetNorthInXMLOutput) {
        setOffsetNorth(offsetNorth);
        setOffsetNorthUsed(useOffsetNorthInXMLOutput);
    }

    /**
     * @param offsetSouth               The offset South to set.
     * @param useOffsetSouthInXMLOutput updates the {@link #isOffsetNorthUsed()}.
     */
    public void setOffsetSouth(double offsetSouth, boolean useOffsetSouthInXMLOutput) {
        setOffsetSouth(offsetSouth);
        setOffsetNorthUsed(!useOffsetSouthInXMLOutput);
    }

    /**
     * @param offsetEast               The offset East to set.
     * @param useOffsetEastInXMLOutput updates the {@link #isOffsetEastUsed()}.
     */
    public void setOffsetEast(double offsetEast, boolean useOffsetEastInXMLOutput) {
        setOffsetEast(offsetEast);
        setOffsetEastUsed(useOffsetEastInXMLOutput);
    }

    /**
     * @param offsetWest               The offsetWest to set.
     * @param useOffsetWestInXMLOutput updates the {@link #isOffsetEastUsed()}.
     */
    public void setOffsetWest(double offsetWest, boolean useOffsetWestInXMLOutput) {
        setOffsetWest(offsetWest);
        setOffsetEastUsed(!useOffsetWestInXMLOutput);
    }

    /**
     * @param offsetUp               The offsetUp to set.
     * @param useOffsetUpInXMLOutput updates the {@link #isOffsetUpUsed()}.
     */
    public void setOffsetUp(double offsetUp, boolean useOffsetUpInXMLOutput) {
        setOffsetUp(offsetUp);
        setOffsetUpUsed(useOffsetUpInXMLOutput);
    }

    /**
     * @param offsetDown               The offsetDown to set.
     * @param useOffsetDownInXMLOutput updates the {@link #isOffsetUpUsed()}.
     */
    public void setOffsetDown(double offsetDown, boolean useOffsetDownInXMLOutput) {
        setOffsetDown(offsetDown);
        setOffsetUpUsed(!useOffsetDownInXMLOutput);
    }

    /**
     * @param anotherLocation
     */
    public LocationType(LocationType anotherLocation) {
        super();
        setLocation(anotherLocation);
    }

    /**
     * Copies the given location to this one. (Does not link them together.)
     *
     * @param anotherPoint
     */
    public void setLocation(LocationType anotherPoint) {

        if (anotherPoint == null)
            return;

        this.setLatitudeRads(anotherPoint.getLatitudeRads());
        this.setLongitudeRads(anotherPoint.getLongitudeRads());
        this.setDepth(anotherPoint.getDepth());

        this.setAzimuth(anotherPoint.getAzimuth());
        this.setZenith(anotherPoint.getZenith());
        this.setOffsetDistance(anotherPoint.getOffsetDistance());

        this.setOffsetDown(anotherPoint.getOffsetDown());
        this.setOffsetEast(anotherPoint.getOffsetEast());
        this.setOffsetNorth(anotherPoint.getOffsetNorth());

        this.setOffsetEastUsed(anotherPoint.isOffsetEastUsed());
        this.setOffsetNorthUsed(anotherPoint.isOffsetNorthUsed());
        this.setOffsetUpUsed(anotherPoint.isOffsetUpUsed());

    }

    /**
     * @return
     */
    public double getLatitudeRads() {
        return latitudeRads;
    }

    /**
     * @param latitudeRads The latitude to set in radians.
     */
    public void setLatitudeRads(double latitudeRads) {
        this.latitudeRads = latitudeRads;
    }

    /**
     * @return
     */
    public double getLongitudeRads() {
        return longitudeRads;
    }

    /**
     * @param longitudeRads The longitude to set in radians.
     */
    public void setLongitudeRads(double longitudeRads) {
        this.longitudeRads = longitudeRads;
    }

    /**
     * @return Returns the z value.
     */
    public double getDepth() {
        if (depth == 0)
            return 0;
        return depth;
    }

    /**
     * @param depth The value for depth
     */
    public void setDepth(double depth) {
        this.depth = depth;
    }

    /**
     * @return Returns the azimuth.
     */
    public double getAzimuth() {
        return azimuth;
    }

    /**
     * @param azimuth The azimuth to set.
     */
    public void setAzimuth(double azimuth) {
        this.azimuth = azimuth;
    }

    /**
     * @return Returns the zenith.
     */
    public double getZenith() {
        return zenith;
    }

    /**
     * @return Returns the offsetDistance.
     */
    public double getOffsetDistance() {
        return offsetDistance;
    }

    /**
     * @param offsetDistance The offsetDistance to set.
     */
    public void setOffsetDistance(double offsetDistance) {
        this.offsetDistance = offsetDistance;
    }

    /**
     * @return Returns the offsetDown.
     */
    public double getOffsetDown() {
        return offsetDown;
    }

    /**
     * @return Returns the offsetEast.
     */
    public double getOffsetEast() {
        return offsetEast;
    }

    /**
     * @return Returns the offsetNorth.
     */
    public double getOffsetNorth() {
        return offsetNorth;
    }

    /**
     * @param offsetNorth The offsetNorth to set.
     */
    public void setOffsetNorth(double offsetNorth) {
        this.offsetNorth = offsetNorth;
        //        this.offsetSouth = -offsetNorth;
        //        if (this.offsetSouth == 0)
        //            this.offsetSouth = 0;
    }

    /**
     * @return Returns the isOffsetEastUsed.
     */
    public boolean isOffsetEastUsed() {
        return isOffsetEastUsed;
    }

    /**
     * @param isOffsetEastUsed The isOffsetEastUsed to set.
     */
    public void setOffsetEastUsed(boolean isOffsetEastUsed) {
        this.isOffsetEastUsed = isOffsetEastUsed;
    }

    /**
     * @return Returns the isOffsetNorthUsed.
     */
    public boolean isOffsetNorthUsed() {
        return isOffsetNorthUsed;
    }

    /**
     * @param isOffsetNorthUsed The isOffsetNorthUsed to set.
     */
    public void setOffsetNorthUsed(boolean isOffsetNorthUsed) {
        this.isOffsetNorthUsed = isOffsetNorthUsed;
    }

    /**
     * @return Returns the isOffsetUpUsed.
     */
    public boolean isOffsetUpUsed() {
        return isOffsetUpUsed;
    }

    /**
     * @param isOffsetUpUsed The isOffsetUpUsed to set.
     */
    public void setOffsetUpUsed(boolean isOffsetUpUsed) {
        this.isOffsetUpUsed = isOffsetUpUsed;
    }

    /**
     * @param offsetEast The offsetEast to set.
     */
    public void setOffsetEast(double offsetEast) {
        this.offsetEast = offsetEast;
    }

    /**
     * @param offsetDown The offsetDown to set.
     */
    public void setOffsetDown(double offsetDown) {
        this.offsetDown = offsetDown;
    }

    /**
     * @param zenith The zenith to set.
     */
    public void setZenith(double zenith) {
        this.zenith = zenith;
    }

    public LocationType(double latitudeDegrees, double longitudeDegrees) {
        this.latitudeRads = Math.toRadians(latitudeDegrees);
        this.longitudeRads = Math.toRadians(longitudeDegrees);
    }

    /**
     * @return Returns the height.
     */
    public double getHeight() {
        return -getDepth();
    }

    /**
     * @param height The height to set.
     */
    public void setHeight(double height) {
        this.depth = -height;
    }

    /**
     * @return Returns the offsetUp.
     */
    public double getOffsetUp() {
        if (this.offsetDown == 0)
            return this.offsetDown;
        else
            return -this.offsetDown;
    }

    /**
     * @return Returns the offsetSouth.
     */
    public double getOffsetSouth() {
        //        return offsetSouth;
        if (this.offsetNorth == 0)
            return offsetNorth;
        else
            return -offsetNorth;
    }

    /**
     * @param offsetSouth The offsetSouth to set.
     */
    public void setOffsetSouth(double offsetSouth) {
        //        this.offsetSouth = offsetSouth;
        this.offsetNorth = -offsetSouth;
        if (this.offsetNorth == 0)
            this.offsetNorth = 0;
    }

    /**
     * @return Returns the offsetWest.
     */
    public double getOffsetWest() {
        if (this.offsetEast == 0)
            return offsetEast;
        else
            return -offsetEast;
    }

    /**
     * @return in decimal degrees
     */
    public double getLongitudeDegs() {
        return Math.toDegrees(this.longitudeRads);
    }

    /**
     * @param longitude The longitude to set in decimal degrees.
     */
    public void setLongitudeDegs(double longitude) {
        setLongitudeRads(Math.toRadians(longitude));
    }

    /**
     * @param offsetWest The offsetWest to set.
     */
    public void setOffsetWest(double offsetWest) {
        this.offsetEast = -offsetWest;
        if (this.offsetEast == 0)
            this.offsetEast = 0;
    }

    /**
     * @param offsetUp The offsetUp to set.
     */
    public void setOffsetUp(double offsetUp) {
        this.offsetDown = -offsetUp;
        if (this.offsetDown == 0)
            this.offsetDown = 0;
    }
    
    /**
     * Returns the distance relative to other location, in meters
     *
     * @param anotherLocation Another Location
     * @return The distance, in meters, to the location given
     */
    public double getHorizontalDistanceInMeters(LocationType anotherLocation) {
        double[] offsets = getOffsetFrom(anotherLocation);
        double sum = offsets[0] * offsets[0] + offsets[1] * offsets[1];
        return Math.sqrt(sum);
    }

    /**
     * This method gives a vector from otherLocation to this location
     *
     * @param otherLocation
     * @return
     */
    public double[] getOffsetFrom(LocationType otherLocation) {
        return CoordinateUtil.WGS84displacement(otherLocation, this);
    }

    /**
     * Calculates the angle between two 2D points.
     */
    public static double calcAngle(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double angle;

        // Calculate angle
        if (dx == 0.0) {
            if (dy == 0.0)
                angle = 0.0;
            else if (dy > 0.0)
                angle = Math.PI / 2.0;
            else
                angle = Math.PI * 3.0 / 2.0;
        } else if (dy == 0.0) {
            if (dx > 0.0)
                angle = 0.0;
            else
                angle = Math.PI;
        } else {
            if (dx < 0.0)
                angle = Math.atan(dy / dx) + Math.PI;
            else if (dy < 0.0)
                angle = Math.atan(dy / dx) + (2.0 * Math.PI);
            else
                angle = Math.atan(dy / dx);
        }

        return -1 * (angle - (Math.PI / 2.0));
    }

    /**
     * @param anotherLocation
     * @return The angle to the other location, in radians
     */
    public double getXYAngle(LocationType anotherLocation) {
        double o2[] = anotherLocation.getOffsetFrom(this);
        double ang = calcAngle(0, 0, o2[1], o2[0]);

        if (ang < 0)
            ang += Math.PI * 2;

        return ang;
    }

    /**
     * This calls {@link #translatePosition(double, double, double)}.
     *
     * @param nedOffsets
     * @return This location.
     */
    public LocationType translatePosition(double[] nedOffsets) {
        if (nedOffsets.length < 3) {
            LOG.error("invalid offsets length: found " + nedOffsets.length
                    + " values, expecting 3");
            return this;
        }
        return translatePosition(nedOffsets[0], nedOffsets[1], nedOffsets[2]);
    }

    /**
     * Translate this location by the offsets.
     *
     * @param offsetNorth
     * @param offsetEast
     * @param offsetDown
     * @return This location.
     */
    public LocationType translatePosition(double offsetNorth, double offsetEast,
                                          double offsetDown) {

        setOffsetNorth(getOffsetNorth() + offsetNorth);
        setOffsetEast(getOffsetEast() + offsetEast);
        setOffsetDown(getOffsetDown() + offsetDown);
        return this;
    }

    public String getLatitudeAsPrettyString() {
        return CoordinateUtil.latitudeAsPrettyString(getLatitudeDegs(), false);
    }

    /**
     * @return in decimal degrees
     */
    public double getLatitudeDegs() {
        return Math.toDegrees(latitudeRads);
    }

    /**
     * @param latitude The latitude to set in decimal degrees.
     */
    public void setLatitudeDegs(double latitude) {
        setLatitudeRads(Math.toRadians(latitude));
    }

    public String getLongitudeAsPrettyString() {
        return CoordinateUtil.longitudeAsPrettyString(getLongitudeDegs(), false);
    }

    /**
     * @return The total North(m), East(m) and Depth(m) offsets from the Lat/Lon/Depth value.
     */
    public double[] getAbsoluteNEDInMeters() {
        double[] absoluteLatLonDepth = getAbsoluteLatLonDepth();
        if (absoluteLatLonDepth == null)
            return null;

        double[] tmpDouble = CoordinateUtil.latLonDiff(0, 0, absoluteLatLonDepth[0],
                absoluteLatLonDepth[1]);
        double[] result = new double[3];
        result[0] = tmpDouble[0];
        result[1] = tmpDouble[1];
        result[2] = absoluteLatLonDepth[2];
        return result;
    }

    /**
     * Combines absolute z and additional offset values and returns the result
     *
     * @return z with all offsets added
     */
    public double getAllZ() {
        double[] stc = CoordinateUtil.sphericalToCartesianCoordinates(getOffsetDistance(),
                getAzimuth(), getZenith());
        stc[2] += getOffsetDown();
        stc[2] += getDepth();

        return stc[2];
    }

    /**
     * Calls {@link #makeTotalDepthZero()} and then sets the value of depth to the given value
     *
     * @param z absolute depth
     */
    public void setAbsoluteDepth(double z) {
        makeTotalDepthZero();
        setDepth(z);
    }

    /**
     * Sets both z and down offsets to zero.
     */
    protected void makeTotalDepthZero() {
        setDepth(0);
        setOffsetDown(0);
        setOffsetDistance(0);
        setAzimuth(0);
        setZenith(90);
    }

    /**
     * Converts this Location to absolute (Lat/Lon/Depth without offsets).
     *
     * @return The Location itself.
     */
    public LocationType convertToAbsoluteLatLonDepth() {
        if (offsetNorth == 0 && offsetEast == 0 && offsetDown == 0 && offsetDistance == 0)
            return this;

        double lld[] = getAbsoluteLatLonDepth();

        setLocation(new LocationType());
        setLatitudeDegs(lld[0]);
        setLongitudeDegs(lld[1]);
        setDepth(lld[2]);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        if (!(obj instanceof LocationType))
            return false;
        LocationType otherLoc = (LocationType) obj;

        return isLocationEqual(otherLoc);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return getNewAbsoluteLatLonDepth();
    }

    @Override
    public String toString() {
        double[] absLoc = getAbsoluteLatLonDepth();

        double lat = absLoc[0];
        double lon = absLoc[1];

        String latStr = "N";
        String lonStr = "E";

        if (lat < 0) {
            lat = -lat;
            latStr = "S";
        }

        if (lon < 0) {
            lon = -lon;
            lonStr = "W";
        }

        // Any change to this reflects #valurOf method!!
        return latStr + nf8.format(lat) + CoordinateUtil.CHAR_DEGREE + ", " + lonStr
                + nf8.format(lon) + CoordinateUtil.CHAR_DEGREE + (getHeight() != 0 ? (", " + nf2.format(getHeight())) : "");
    }

    /**
     * Converts a copy of this Location to absolute (Lat/Lon/Depth without offsets).
     *
     * @return A copy of the location.
     */
    @SuppressWarnings("unchecked")
    public <L extends LocationType> L getNewAbsoluteLatLonDepth() {
        double latlondepth[] = getAbsoluteLatLonDepth();
        L loc;
        try {
            loc = (L) this.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            loc = (L) new LocationType();
        }

        loc.setLatitudeDegs(latlondepth[0]);
        loc.setLongitudeDegs(latlondepth[1]);
        loc.setDepth(latlondepth[2]);

        return loc;
    }

    /**
     * @return The total Lat(degrees), Lon(degrees) and Depth(m)
     */
    public double[] getAbsoluteLatLonDepth() {
        double[] totalLatLonDepth = new double[]{0d, 0d, 0d};
        totalLatLonDepth[0] = getLatitudeDegs();
        totalLatLonDepth[1] = getLongitudeDegs();
        totalLatLonDepth[2] = getDepth();

        double[] tmpDouble = CoordinateUtil.sphericalToCartesianCoordinates(getOffsetDistance(),
                getAzimuth(), getZenith());
        double north = getOffsetNorth() + tmpDouble[0];
        double east = getOffsetEast() + tmpDouble[1];
        double down = getOffsetDown() + tmpDouble[2];

        if (north != 0.0 || east != 0.0 || down != 0.0)
            return CoordinateUtil.WGS84displace(totalLatLonDepth[0], totalLatLonDepth[1], totalLatLonDepth[2], north, east, down);
        else
            return totalLatLonDepth;
    }

    /**
     * Compares 2 locations
     *
     * @param location
     * @return
     */
    public boolean isLocationEqual(LocationType location) {
        if (location == null)
            return false;

        LocationType loc1 = this.getNewAbsoluteLatLonDepth();
        LocationType loc2 = location.getNewAbsoluteLatLonDepth();

        if (loc2.getLatitudeDegs() == 0.0)
            return loc1.getLatitudeDegs() == 0.0;

        double loc1LatDouble = cropDecimalDigits(10, loc1.getLatitudeDegs());
        double loc2LatDouble = cropDecimalDigits(10, loc2.getLatitudeDegs());
        double loc1LonDouble = cropDecimalDigits(10, loc1.getLongitudeDegs());
        double loc2LonDouble = cropDecimalDigits(10, loc2.getLongitudeDegs());

        return Double.compare(loc1LatDouble, loc2LatDouble) == 0 && Double.compare(loc1LonDouble, loc2LonDouble) == 0
                && (loc1.getDepth()) == (loc2.getDepth());
    }

    private double cropDecimalDigits(int digit, double value) {
        String string = value + "";
        String[] tokens = string.split("\\.");
        String res = tokens[0] + "." + tokens[1].substring(0, (tokens[1].length() > digit) ? digit : tokens[1].length());
        return Double.valueOf(res);
    }

    @Override
    public int compareTo(LocationType o) {
        return (int) getDistanceInMeters(o);
    }

    /**
     * Returns the distance relative to other location, in meters
     *
     * @param anotherLocation Another Location
     * @return The distance, in meters, to the location given
     */
    public double getDistanceInMeters(LocationType anotherLocation) {
        double[] offsets = getOffsetFrom(anotherLocation);
        double sum = offsets[0] * offsets[0] + offsets[1] * offsets[1] + offsets[2] * offsets[2];
        return Math.sqrt(sum);
    }

    public double get2DDistanceInMeters(LocationType anotherLocation) {
        double[] offsets = getOffsetFrom(anotherLocation);
        double sum = offsets[0] * offsets[0] + offsets[1] * offsets[1];
        return Math.sqrt(sum);
    }
}
