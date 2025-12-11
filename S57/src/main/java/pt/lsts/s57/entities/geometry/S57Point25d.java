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
 * Nov 24, 2011
 */
package pt.lsts.s57.entities.geometry;

import java.awt.Shape;
import java.io.Serial;
import java.io.Serializable;

import org.gdal.ogr.Geometry;

import pt.lsts.neptus.core.LocationType;

/**
 * Represents a 2.5D point geometry (point with depth) in S57 format.
 * Implements the sealed S57Geometry interface.
 * 
 * @author Hugo Dias
 */
public final class S57Point25d implements S57Geometry, Serializable {
    
    @Serial
    private static final long serialVersionUID = 3412058470562630621L;
    
    private final LocationType location;
    private final double depth;

    /**
     * Static factory method
     * 
     * @param geom GDAL geometry
     * @return new S57Point25d instance
     */
    public static S57Point25d forge(Geometry geom) {
        return new S57Point25d(geom);
    }

    private S57Point25d(Geometry geom) {
        this.depth = geom.GetZ();
        this.location = new LocationType(geom.GetY(), geom.GetX());
    }

    @Override
    public S57GeometryType getType() {
        return S57GeometryType.POINT;
    }

    @Override
    public LocationType getLocation() {
        return location;
    }

    @Override
    public Shape getShape() {
        return null;
    }

    @Override
    public LocationType getCenter() {
        return location;
    }

    @Override
    public double getDepth() {
        return depth;
    }
}
