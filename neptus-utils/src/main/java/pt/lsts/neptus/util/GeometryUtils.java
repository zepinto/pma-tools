//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.lsts.neptus.util;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for geometric calculations.
 */
public class GeometryUtils {

    private GeometryUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Computes the convex hull of a set of points using the Graham scan algorithm.
     * 
     * @param points the list of points to compute the convex hull for
     * @return a list of points representing the convex hull in counter-clockwise order
     */
    public static List<Point2D> computeConvexHull(List<Point2D> points) {
        if (points == null || points.size() < 3) {
            return new ArrayList<>(points != null ? points : List.of());
        }

        // Find the point with the lowest y-coordinate (and leftmost if tie)
        Point2D pivot = points.get(0);
        for (Point2D p : points) {
            if (p.getY() < pivot.getY() || (p.getY() == pivot.getY() && p.getX() < pivot.getX())) {
                pivot = p;
            }
        }

        final Point2D finalPivot = pivot;
        
        // Sort points by polar angle with respect to pivot
        List<Point2D> sorted = new ArrayList<>(points);
        sorted.sort((p1, p2) -> {
            if (p1.equals(finalPivot)) return -1;
            if (p2.equals(finalPivot)) return 1;
            
            double angle1 = Math.atan2(p1.getY() - finalPivot.getY(), p1.getX() - finalPivot.getX());
            double angle2 = Math.atan2(p2.getY() - finalPivot.getY(), p2.getX() - finalPivot.getX());
            
            if (angle1 != angle2) {
                return Double.compare(angle1, angle2);
            }
            // If same angle, closer point comes first
            double dist1 = finalPivot.distanceSq(p1);
            double dist2 = finalPivot.distanceSq(p2);
            return Double.compare(dist1, dist2);
        });

        // Build the hull using a stack
        List<Point2D> hull = new ArrayList<>();
        for (Point2D p : sorted) {
            while (hull.size() > 1 && crossProduct(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(p);
        }

        return hull;
    }

    /**
     * Calculates the cross product of vectors (p1->p2) and (p1->p3).
     * 
     * @param p1 the origin point
     * @param p2 the end point of the first vector
     * @param p3 the end point of the second vector
     * @return positive if counter-clockwise, negative if clockwise, 0 if collinear
     */
    public static double crossProduct(Point2D p1, Point2D p2, Point2D p3) {
        return (p2.getX() - p1.getX()) * (p3.getY() - p1.getY()) 
             - (p2.getY() - p1.getY()) * (p3.getX() - p1.getX());
    }
}
