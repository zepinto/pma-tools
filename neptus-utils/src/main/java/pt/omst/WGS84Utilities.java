package pt.omst;

/**
 * Utilities for WGS84 geodetic coordinate system calculations.
 * <p>
 * This class provides methods for converting between geodetic coordinates (latitude, longitude, depth)
 * and Earth-Centered Earth-Fixed (ECEF) Cartesian coordinates, as well as computing displacements
 * and distances on the WGS84 ellipsoid.
 * </p>
 * <p>
 * The implementation is based on the WGS84 (World Geodetic System 1984) reference ellipsoid parameters
 * and algorithms originally from LSTS Dune.
 * </p>
 * 
 * @author LSTS Dune (original implementation)
 */
public class WGS84Utilities {

    /** WGS84 semi-major axis (equatorial radius) in meters */
    public static final double c_wgs84_a = 6378137.0;
    
    /** WGS84 first eccentricity squared */
    public static final double c_wgs84_e2 = 0.00669437999013;
    
    /** WGS84 flattening factor */
    public static final double c_wgs84_f = 0.0033528106647475;

    // ---------------------------------------------------------------------------------------------
    /**
     * Converts WGS84 geodetic coordinates to Earth-Centered Earth-Fixed (ECEF) Cartesian coordinates.
     * <p>
     * ECEF is a Cartesian coordinate system where:
     * <ul>
     *   <li>Origin is at Earth's center of mass</li>
     *   <li>X-axis points to 0° latitude, 0° longitude (intersection of equator and prime meridian)</li>
     *   <li>Y-axis points to 0° latitude, 90° East longitude</li>
     *   <li>Z-axis points to the North Pole</li>
     * </ul>
     * </p>
     * 
     * @param latDegrees latitude in decimal degrees (positive North, negative South)
     * @param lonDegrees longitude in decimal degrees (positive East, negative West)
     * @param depth depth/altitude in meters (positive values are below surface, negative values are above)
     * @return array of 3 doubles [x, y, z] representing ECEF coordinates in meters
     */
    private static double[] toECEF(double latDegrees, double lonDegrees, double depth) {

        double lld[] = { latDegrees, lonDegrees, depth };

        lld[0] = Math.toRadians(lld[0]);
        lld[1] = Math.toRadians(lld[1]);

        double cos_lat = Math.cos(lld[0]);
        double sin_lat = Math.sin(lld[0]);
        double cos_lon = Math.cos(lld[1]);
        double sin_lon = Math.sin(lld[1]);
        double rn = c_wgs84_a / Math.sqrt(1.0 - c_wgs84_e2 * sin_lat * sin_lat);
        double[] ned = new double[3];
        ned[0] = (rn - lld[2]) * cos_lat * cos_lon;
        ned[1] = (rn - lld[2]) * cos_lat * sin_lon;
        ned[2] = (((1.0 - c_wgs84_e2) * rn) - lld[2]) * sin_lat;

        return ned;
    }

    /**
     * Computes the prime vertical radius of curvature at a given latitude.
     * <p>
     * This is the radius of curvature in the prime vertical (perpendicular to the meridian),
     * which varies with latitude due to Earth's ellipsoidal shape.
     * </p>
     * 
     * @param lat latitude in radians
     * @return radius of curvature in meters
     */
    private static double n_rad(double lat) {
        double lat_sin = Math.sin(lat);
        return c_wgs84_a / Math.sqrt(1 - c_wgs84_e2 * (lat_sin * lat_sin));
    }

    /**
     * Converts ECEF (Earth-Centered Earth-Fixed) Cartesian coordinates to WGS84 geodetic coordinates.
     * <p>
     * Uses an iterative algorithm to compute geodetic coordinates from ECEF coordinates.
     * The iteration continues until the height (altitude) converges within 0.1mm precision.
     * </p>
     * 
     * @param x ECEF X coordinate in meters
     * @param y ECEF Y coordinate in meters
     * @param z ECEF Z coordinate in meters
     * @return array of 3 doubles [latitude (degrees), longitude (degrees), altitude (meters)]
     */
    private static double[] toGeodetic(double x, double y, double z) {
        double[] lld = new double[3];

        double p = Math.sqrt(x * x + y * y);
        lld[1] = Math.atan2(y, x);
        lld[0] = Math.atan2(z / p, 0.01);
        double n = n_rad(lld[0]);
        lld[2] = p / Math.cos(lld[0]) - n;
        double old_hae = -1e-9;
        double num = z / p;

        while (Math.abs(lld[2] - old_hae) > 1e-4) {
            old_hae = lld[2];
            double den = 1 - c_wgs84_e2 * n / (n + lld[2]);
            lld[0] = Math.atan2(num, den);
            n = n_rad(lld[0]);
            lld[2] = p / Math.cos(lld[0]) - n;
        }

        lld[0] = Math.toDegrees(lld[0]);
        lld[1] = Math.toDegrees(lld[1]);

        return lld;
    }

    /**
     * Computes the North-East-Down (NED) displacement vector from one WGS84 position to another.
     * <p>
     * The NED coordinate system is a local tangent plane coordinate system at the first position:
     * <ul>
     *   <li>North: points towards geographic North</li>
     *   <li>East: points towards geographic East</li>
     *   <li>Down: points downward perpendicular to the tangent plane (opposite to altitude increase)</li>
     * </ul>
     * This is useful for computing relative positions in marine and aerial navigation.
     * </p>
     * 
     * @param latDegrees1 latitude of origin position in decimal degrees
     * @param lonDegrees1 longitude of origin position in decimal degrees
     * @param depth1 depth of origin position in meters (positive down)
     * @param latDegrees2 latitude of target position in decimal degrees
     * @param lonDegrees2 longitude of target position in decimal degrees
     * @param depth2 depth of target position in meters (positive down)
     * @return array of 3 doubles [north (m), east (m), down (m)] representing the displacement from position 1 to position 2
     */
    public static double[] WGS84displacement(double latDegrees1, double lonDegrees1, double depth1,
            double latDegrees2, double lonDegrees2, double depth2) {

        double cs1[];
        double cs2[];

        cs1 = toECEF(latDegrees1, lonDegrees1, depth1);
        cs2 = toECEF(latDegrees2, lonDegrees2, depth2);

        double ox = cs2[0] - cs1[0];
        double oy = cs2[1] - cs1[1];
        double oz = cs2[2] - cs1[2];
        double[] lld1 = { latDegrees1, lonDegrees1, depth1 };

        double slat = Math.sin(Math.toRadians(lld1[0]));
        double clat = Math.cos(Math.toRadians(lld1[0]));
        double slon = Math.sin(Math.toRadians(lld1[1]));
        double clon = Math.cos(Math.toRadians(lld1[1]));

        double[] ret = new double[3];

        ret[0] = -slat * clon * ox - slat * slon * oy + clat * oz; // North
        ret[1] = -slon * ox + clon * oy; // East
        ret[2] = depth1 - depth2;

        return ret;
    }

    /**
     * Computes a new WGS84 position by applying a North-East-Down (NED) displacement to an origin position.
     * <p>
     * This is the inverse operation of {@link #WGS84displacement}. Given an origin position and
     * NED offsets, it computes the resulting geodetic coordinates.
     * </p>
     * <p>
     * The algorithm converts to ECEF coordinates, applies the displacement in the local tangent plane,
     * then converts back to geodetic coordinates.
     * </p>
     * 
     * @param latDegrees origin latitude in decimal degrees
     * @param lonDegrees origin longitude in decimal degrees
     * @param depth origin depth in meters (positive down)
     * @param n north offset in meters (positive North)
     * @param e east offset in meters (positive East)
     * @param d down offset in meters (positive down/increasing depth)
     * @return array of 3 doubles [latitude (degrees), longitude (degrees), depth (meters)] of the displaced position
     */
    public static double[] WGS84displace(double latDegrees, double lonDegrees, double depth, double n, double e,
            double d) {
        // Convert reference to ECEF coordinates
        double xyz[] = toECEF(latDegrees, lonDegrees, depth);
        double lld[] = { latDegrees, lonDegrees, depth };
        // Compute Geocentric latitude
        double phi = Math.atan2(xyz[2], Math.sqrt(xyz[0] * xyz[0] + xyz[1] * xyz[1]));

        // Compute all needed sine and cosine terms for conversion.
        double slon = Math.sin(Math.toRadians(lld[1]));
        double clon = Math.cos(Math.toRadians(lld[1]));
        double sphi = Math.sin(phi);
        double cphi = Math.cos(phi);

        // Obtain ECEF coordinates of displaced point
        // Note: some signs from standard ENU formula
        // are inverted - we are working with NED (= END) coordinates
        xyz[0] += -slon * e - clon * sphi * n - clon * cphi * d;
        xyz[1] += clon * e - slon * sphi * n - slon * cphi * d;
        xyz[2] += cphi * n - sphi * d;

        // Convert back to WGS-84 coordinates
        lld = toGeodetic(xyz[0], xyz[1], xyz[2]);
        // LocationType loc2 = new LocationType();
        // loc2.setLatitude(lld[0]);
        // loc2.setLongitude(lld[1]);
        // loc2.setDepth(lld[2]);
        // loc.setLocation(loc2);

        if (d != 0d)
            lld[2] = depth + d;
        else
            lld[2] = depth;
        return lld;
    }

    /**
     * Computes the horizontal distance between two WGS84 positions.
     * <p>
     * This calculates the Euclidean distance in the horizontal plane (North-East) between
     * two geodetic positions, ignoring any depth/altitude differences. This is useful for
     * computing surface distances or horizontal separation between positions.
     * </p>
     * 
     * @param latDegrees1 latitude of first position in decimal degrees
     * @param lonDegrees1 longitude of first position in decimal degrees
     * @param latDegrees2 latitude of second position in decimal degrees
     * @param lonDegrees2 longitude of second position in decimal degrees
     * @return horizontal distance in meters
     */
    public static double distance(double latDegrees1, double lonDegrees1, double latDegrees2, double lonDegrees2) {
        double[] offsets = WGS84displacement(latDegrees1, lonDegrees1, 0, latDegrees2, lonDegrees2, 0);
        return Math.hypot(offsets[0], offsets[1]);
    }
}
