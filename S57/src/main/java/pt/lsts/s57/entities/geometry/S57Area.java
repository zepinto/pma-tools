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
 * 16 de Set de 2011
 */
package pt.lsts.s57.entities.geometry;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gdal.ogr.Geometry;

import pt.lsts.neptus.core.LocationType;



/**
 * Represents an area/polygon geometry in S57 format.
 * Implements the sealed S57Geometry interface.
 * 
 * @author Hugo Dias
 */
public final class S57Area implements S57Geometry, Serializable {

    @Serial
    private static final long serialVersionUID = 7634760723075256511L;
    
    private final LocationType startLocation;
    private final List<List<Point2D.Double>> pointsList;
    private final Shape shape;
    private final LocationType center;
    private final Path2D path;

    /**
     * Static factory method
     * 
     * @param geom GDAL geometry
     * @return new S57Area instance
     */
    public static S57Area forge(Geometry geom) {
        return new S57Area(geom);
    }

    private S57Area(Geometry geom) {
        this.startLocation = new LocationType();
        this.startLocation.setLatitudeDegs(geom.GetGeometryRef(0).GetY(0));
        this.startLocation.setLongitudeDegs(geom.GetGeometryRef(0).GetX(0));

        var areaPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        var polygonPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        var pointsListBuilder = new ArrayList<List<Point2D.Double>>();

        var start = new LocationType();
        
        // loop over geometry rings
        int geometryCount = geom.GetGeometryCount();
        for (int i = 0; i < geometryCount; i++) {
            var ring = geom.GetGeometryRef(i);
            int pointCount = ring.GetPointCount();
            
            // First point of this ring
            var firstPoint = new Point2D.Double(ring.GetY(0), ring.GetX(0));
            var currentRingStart = new LocationType();
            currentRingStart.setLatitudeDegs(firstPoint.getX());
            currentRingStart.setLongitudeDegs(firstPoint.getY());
            
            double[] offset = startLocation.getDistanceInPixelTo(currentRingStart, DEFAULT_LOD);
            double x = offset[0];
            double y = offset[1];
            areaPath.moveTo(x, y);
            start.setLocation(currentRingStart);

            // Build points list for this ring
            var ringPoints = new ArrayList<Point2D.Double>(pointCount);
            
            for (int j = 0; j < pointCount; j++) {
                var point = new Point2D.Double(ring.GetY(j), ring.GetX(j));
                ringPoints.add(point);
                
                if (i == 0 && j == 0) {
                    polygonPath.moveTo(point.getX(), point.getY());
                } else if (i == 0) {
                    polygonPath.lineTo(point.getX(), point.getY());
                }
                
                var next = new LocationType();
                next.setLatitudeDegs(point.getX());
                next.setLongitudeDegs(point.getY());
                
                offset = start.getDistanceInPixelTo(next, DEFAULT_LOD);
                x += offset[0];
                y += offset[1];
                areaPath.lineTo(x, y);
                start.setLocation(next);
            }
            
            pointsListBuilder.add(Collections.unmodifiableList(ringPoints));
        }
        
        polygonPath.closePath();
        
        this.path = polygonPath;
        this.shape = areaPath;
        this.pointsList = Collections.unmodifiableList(pointsListBuilder);
        this.center = calculateCentroid(pointsList);
    }

    /**
     * Calculates the centroid of a 2D polygon given with its vertices.
     * The algorithm assumes that each ring of the polygon is closed and is not self-intersecting.
     * 
     * @param rings vertices of the polygon ring(s)
     * @return Centroid of the polygon
     * @throws IllegalArgumentException if rings is null or empty
     */
    private static LocationType calculateCentroid(List<List<Point2D.Double>> rings) {
        if (rings == null || rings.isEmpty()) {
            throw new IllegalArgumentException("Polygon rings cannot be null or empty");
        }

        // Calculate areas for each ring
        double[] polygonAreas = new double[rings.size()];
        for (int k = 0; k < rings.size(); k++) {
            var ringPoints = rings.get(k);
            double area = 0.0;
            for (int i = 0; i < ringPoints.size() - 1; i++) {
                area += ringPoints.get(i).y * ringPoints.get(i + 1).x 
                      - ringPoints.get(i + 1).y * ringPoints.get(i).x;
            }
            polygonAreas[k] = area / 2.0;
        }

        // Calculate final area (external ring minus internal rings)
        double finalArea = polygonAreas[0];
        for (int i = 1; i < polygonAreas.length; i++) {
            finalArea -= polygonAreas[i];
        }

        // Calculate centroid from external ring
        var externalRing = rings.getFirst();
        double pointX = 0.0;
        double pointY = 0.0;
        
        for (int i = 0; i < externalRing.size() - 1; i++) {
            double tmp = externalRing.get(i).y * externalRing.get(i + 1).x 
                       - externalRing.get(i + 1).y * externalRing.get(i).x;
            pointX += (externalRing.get(i).y + externalRing.get(i + 1).y) * tmp;
            pointY += (externalRing.get(i).x + externalRing.get(i + 1).x) * tmp;
        }
        
        pointX *= 1.0 / (6.0 * finalArea);
        pointY *= 1.0 / (6.0 * finalArea);
        
        var centroid = new LocationType();
        centroid.setLongitudeDegs(pointX);
        centroid.setLatitudeDegs(pointY);
        return centroid;
    }

    @Override
    public S57GeometryType getType() {
        return S57GeometryType.AREA;
    }

    @Override
    public LocationType getLocation() {
        return startLocation;
    }

    @Override
    public Shape getShape() {
        return shape;
    }

    @Override
    public LocationType getCenter() {
        return center;
    }

    @Override
    public boolean containsPoint(S57Geometry pointGeo) {
        if (pointGeo.getType() != S57GeometryType.POINT) {
            throw new IllegalArgumentException("Argument must be a POINT geometry");
        }
        
        double y = pointGeo.getLocation().getLatitudeDegs();
        double x = pointGeo.getLocation().getLongitudeDegs();
        return path.contains(x, y);
    }

    @Override
    public Path2D getPolygon() {
        return path;
    }

    @Override
    public List<List<Point2D.Double>> getList() {
        return pointsList;
    }
}
