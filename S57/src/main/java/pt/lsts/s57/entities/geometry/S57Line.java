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
 * 20 de Set de 2011
 */
package pt.lsts.s57.entities.geometry;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gdal.ogr.Geometry;

import pt.lsts.neptus.core.LocationType;

/**
 * Represents a line geometry in S57 format.
 * Implements the sealed S57Geometry interface.
 * 
 * @author Hugo Dias
 */
public final class S57Line implements S57Geometry, Serializable {

    @Serial
    private static final long serialVersionUID = 6787863321834605250L;
    
    private final LocationType location;
    private final Shape shape;
    private final List<LocationType> points;
    private final Path2D path;

    /**
     * Static factory method
     * 
     * @param geom GDAL geometry
     * @return new S57Line instance
     */
    public static S57Line forge(Geometry geom) {
        return new S57Line(geom);
    }

    private S57Line(Geometry geom) {
        int pointCount = geom.GetPointCount();
        
        double lat = geom.GetY(0);
        double lon = geom.GetX(0);

        this.location = new LocationType();
        this.location.setLatitudeDegs(lat);
        this.location.setLongitudeDegs(lon);
        
        var pointList = new ArrayList<LocationType>(pointCount);
        pointList.add(location);

        var linePath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        linePath.moveTo(lat, lon);

        var generalPath = new GeneralPath(GeneralPath.WIND_NON_ZERO, pointCount + 2);
        generalPath.moveTo(0.0, 0.0);

        double x = 0.0;
        double y = 0.0;

        var locStart = new LocationType();
        locStart.setLocation(location);
        var locNext = new LocationType();

        for (int i = 1; i < pointCount; i++) {
            lat = geom.GetY(i);
            lon = geom.GetX(i);

            locNext.setLongitudeDegs(lon);
            locNext.setLatitudeDegs(lat);
            pointList.add(locNext);
            
            double[] xyOffset = locStart.getDistanceInPixelTo(locNext, DEFAULT_LOD);

            x += xyOffset[0];
            y += xyOffset[1];

            generalPath.lineTo(x, y);
            linePath.lineTo(lat, lon);

            locStart.setLocation(locNext);
        }
        
        this.shape = generalPath;
        this.path = linePath;
        this.points = Collections.unmodifiableList(pointList);
    }

    @Override
    public S57GeometryType getType() {
        return S57GeometryType.LINE;
    }

    @Override
    public LocationType getLocation() {
        return location;
    }

    @Override
    public Shape getShape() {
        return shape;
    }

    @Override
    public LocationType getCenter() {
        return location;
    }

    @Override
    public Path2D getPolygon() {
        return path;
    }
    
    /**
     * Gets the list of points that make up this line.
     * 
     * @return unmodifiable list of location points
     */
    public List<LocationType> getPoints() {
        return points;
    }
}
