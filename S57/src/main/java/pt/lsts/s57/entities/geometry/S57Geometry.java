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
import java.util.Collections;
import java.util.List;

import pt.lsts.neptus.core.LocationType;

/**
 * Sealed interface representing S57 geometry types.
 * Using Java 21 sealed interface to restrict implementations.
 * 
 * @author Hugo Dias
 */
public sealed interface S57Geometry permits S57Area, S57Line, S57Point, S57Point25d, S57None {

    int DEFAULT_LOD = 22;

    /**
     * Get Geometry Type {@link S57GeometryType}
     * 
     * @return S57GeometryType
     */
    S57GeometryType getType();

    /**
     * Gets the initial location to start painting
     * 
     * @return LocationType
     */
    LocationType getLocation();

    /**
     * Gets shape to paint
     * 
     * @return Shape
     */
    Shape getShape();

    /**
     * Gets the center if its an area or the start loc
     * 
     * @return LocationType
     */
    LocationType getCenter();

    /**
     * Depth for point25d
     * 
     * @return depth value
     */
    default double getDepth() {
        return 0;
    }

    /**
     * Check if the given point is inside the S57 geometry
     * 
     * @param pointGeo the point geometry to check
     * @return true if point is contained
     */
    default boolean containsPoint(S57Geometry pointGeo) {
        return false;
    }

    /**
     * Gets list of points for this geometry
     * 
     * @return list of point lists
     */
    default List<List<Point2D.Double>> getList() {
        return Collections.emptyList();
    }

    /**
     * Gets the polygon path
     * 
     * @return Path2D polygon
     */
    default Path2D getPolygon() {
        return null;
    }
}
