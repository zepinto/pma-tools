package pt.omst.neptus.sidescan;

import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.core.SystemPositionAndAttitude;

public interface ISidescanLine {
    /**
     * @return the timestampMillis
     */
    public abstract long getTimestampMillis();

    /**
     * @return the frequency in Hz
     */
    public abstract float getFrequency();

    /**
     * @return the range
     */
    public abstract float getRange();

    /**
     * @return the state
     */
    public abstract SystemPositionAndAttitude getState();

    /**
     * @return the xSize
     */
    public abstract int getXSize();

    /**
     * @return the ySize
     */
    public abstract int getYSize();

    /**
     * @param ySize the ySize to set
     */
    public abstract void setYSize(int ySize);

    /**
     * @return the yPos
     */
    public abstract int getYPos();

    /**
     * @param yPos the yPos to set
     */
    public abstract void setYPos(int yPos);

    /**
     * Get the sidescan x from the distance in meters from nadir.
     *
     * @param distance        Distance from nadir (negative means port-side).
     * @param slantCorrection Indicates if distance is horizontal (true) or slant (false).
     * @return The sidescan x index (nadir is the half of total points).
     */
    public default int getIndexFromDistance(double distance, boolean slantCorrection) {
        double r = distance;
        if (slantCorrection) {
            if (Double.isNaN(distance))
                return getXSize() / 2;
            double alt = getState().getAltitude();
            r = Math.signum(distance) * Math.sqrt(distance * distance + alt * alt);
        }
        return (int) ((r + getRange()) / (getRange() * 2 / getXSize()));
    }

    /**
     * Based on a 'x' position within a scan line calculate the proper location
     *
     * @param x               The sidescan x index (nadir is the half of total points
     * @param slantCorrection Indicates if distance is horizontal (true) or slant (false).
     * @return a LocationType object containing the absolute GPS location of the point (wrapped into
     * {@link SidescanPoint}.
     */
    public default SidescanPoint calcPointFromIndex(int x, int y, boolean slantCorrection) {
        SystemPositionAndAttitude state = getState();
        LocationType location = new LocationType(state.getPosition());
        
        double distance = getDistanceFromIndex(x, slantCorrection);

        double angle = -state.getYaw() + (x < (getXSize() / 2) ? Math.PI : 0);
        double offsetNorth = Math.abs(distance) * Math.sin(angle);
        double offsetEast = Math.abs(distance) * Math.cos(angle);
        // Add the original vehicle offset to the calculated offset
        location.setOffsetNorth(state.getPosition().getOffsetNorth() + offsetNorth);
        location.setOffsetEast(state.getPosition().getOffsetEast() + offsetEast);

        // Return new absolute location
        return new SidescanPoint(x, y, getXSize(), location.getNewAbsoluteLatLonDepth(), this);
    }

    /**
     * Calculates the distance (horizontal (true) or slant (false)) from nadir.
     *
     * @param x               The sidescan x index (nadir is the half of total points
     * @param slantCorrection Indicates if distance is horizontal (true) or slant (false).
     * @return Distance from nadir (negative means port-side).
     */
    public default double getDistanceFromIndex(int x, boolean slantCorrection) {
        double distance = x * (getRange() * 2 / getXSize()) - (getRange());
        if (slantCorrection) {
            double alt = getState().getAltitude();
            alt = Math.max(alt, 0);
            double distanceG = Math.signum(distance) * Math.sqrt(distance * distance - alt * alt);
            distance = Double.isNaN(distanceG) ? 0 : distanceG;
        }
        return distance;
    }

    public abstract boolean isImageWithSlantCorrection();

    public abstract void setImageWithSlantCorrection(boolean imageWithSlantCorrection);
}
