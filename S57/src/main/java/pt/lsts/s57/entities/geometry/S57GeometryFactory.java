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

import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogrConstants;

/**
 * Factory class for creating S57Geometry instances from GDAL geometry objects.
 * Uses switch expression (Java 21) for cleaner code.
 * 
 * @author Hugo Dias
 */
public final class S57GeometryFactory {

    private S57GeometryFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates an S57Geometry from a GDAL Geometry object.
     * 
     * @param geometry the GDAL geometry
     * @return the corresponding S57Geometry implementation
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static S57Geometry factory(Geometry geometry) {
        if (geometry == null) {
            return S57None.forge();
        }
        
        int type;
        try {
            type = geometry.GetGeometryType();
        } catch (NullPointerException e) {
            return S57None.forge();
        }

        return switch (type) {
            case ogrConstants.wkbPoint -> S57Point.forge(geometry);
            case ogrConstants.wkbLineString -> S57Line.forge(geometry);
            case ogrConstants.wkbPolygon -> S57Area.forge(geometry);
            case ogrConstants.wkbPoint25D -> S57Point25d.forge(geometry);
            default -> {
                System.err.println("Invalid geometry type: " + type);
                throw new IllegalArgumentException("Invalid geometry type: " + type);
            }
        };
    }
}
