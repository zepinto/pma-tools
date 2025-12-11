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
 * 28 de Nov de 2013
 */
package pt.lsts.s57;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import pt.lsts.s57.entities.S57Map;
import pt.lsts.s57.entities.S57Object;
import pt.lsts.s57.entities.geometry.S57GeometryType;

/**
 * Query interface for S57 maps.
 * Modernized with Java 21 switch expressions and pattern matching.
 * 
 * @author Hugo Dias
 */
public class S57Query {

    private final S57 s57;

    /**
     * Static factory method
     * 
     * @param s57 the S57 instance
     * @return new S57Query instance
     */
    public static S57Query forge(S57 s57) {
        return new S57Query(s57);
    }

    /**
     * Creates a new S57Query
     * 
     * @param s57 the S57 instance
     */
    public S57Query(S57 s57) {
        this.s57 = s57;
    }

    /**
     * Find maps containing a specific point
     * 
     * @param lat latitude
     * @param lon longitude
     * @return list of matching maps
     */
    public List<S57Map> findMapsContains(double lat, double lon) {
        var matches = new ArrayList<S57Map>();
        for (var map : s57.getMaps().values()) {
            for (var obj : map.getCoverage()) {
                if (obj.getGeometry().getPolygon().contains(lat, lon)) {
                    matches.add(map);
                }
            }
        }
        return matches;
    }

    /**
     * Find maps containing a bounding box
     * 
     * @param lat1 first latitude
     * @param lon1 first longitude
     * @param lat2 second latitude
     * @param lon2 second longitude
     * @return list of matching maps
     */
    public List<S57Map> findMapsContains(double lat1, double lon1, double lat2, double lon2) {
        var bbox = boundingBox(lat1, lon1, lat2, lon2);
        var matches = new ArrayList<S57Map>();
        
        for (var map : s57.getMaps().values()) {
            for (var obj : map.getCoverage()) {
                if (obj.getGeometry().getPolygon().contains(bbox)) {
                    matches.add(map);
                    if (!map.isLoaded()) {
                        map.loadMapObjects(null);
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Find maps intersecting a bounding box
     * 
     * @param lat1 first latitude
     * @param lon1 first longitude
     * @param lat2 second latitude
     * @param lon2 second longitude
     * @return list of matching maps
     */
    public List<S57Map> findMapsIntersects(double lat1, double lon1, double lat2, double lon2) {
        var bbox = boundingBox(lat1, lon1, lat2, lon2);
        var matches = new ArrayList<S57Map>();
        
        for (var map : s57.getMaps().values()) {
            for (var obj : map.getCoverage()) {
                if (obj.getGeometry().getPolygon().intersects(bbox.getBounds2D())) {
                    matches.add(map);
                }
            }
        }
        return matches;
    }

    /**
     * Find objects inside a bounding box
     * 
     * @param lat1 first latitude
     * @param lon1 first longitude
     * @param lat2 second latitude
     * @param lon2 second longitude
     * @return list of matching objects
     */
    public List<S57Object> findObjectsInside(double lat1, double lon1, double lat2, double lon2) {
        var bbox = boundingBox(lat1, lon1, lat2, lon2);
        var matches = new ArrayList<S57Object>();
        
        for (var map : s57.getMaps().values()) {
            for (var obj : map.getCoverage()) {
                if (obj.getGeometry().getPolygon().intersects(bbox)) {
                    for (var obj2 : map.getObjects()) {
                        if (isObjectInsideBbox(obj2, bbox)) {
                            matches.add(obj2);
                        }
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Find objects inside a bounding box filtered by acronyms
     * 
     * @param lat1 first latitude
     * @param lon1 first longitude
     * @param lat2 second latitude
     * @param lon2 second longitude
     * @param acronyms filter acronyms
     * @return list of matching objects
     */
    public List<S57Object> findObjectsInside(double lat1, double lon1, double lat2, double lon2, String[] acronyms) {
        var bbox = boundingBox(lat1, lon1, lat2, lon2);
        var matches = new ArrayList<S57Object>();
        
        for (var map : s57.getMaps().values()) {
            for (var obj : map.getCoverage()) {
                if (obj.getGeometry().getPolygon().intersects(bbox)) {
                    for (var obj2 : map.getObjects(acronyms)) {
                        if (isObjectInsideBbox(obj2, bbox)) {
                            matches.add(obj2);
                        }
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Check if an object is inside a bounding box using pattern matching switch
     */
    private boolean isObjectInsideBbox(S57Object obj, Rectangle2D bbox) {
        return switch (obj.getGeoType()) {
            case POINT -> {
                var loc = obj.getGeometry().getLocation();
                System.out.println(obj.getGeometry().getDepth());
                yield bbox.contains(loc.getLatitudeDegs(), loc.getLongitudeDegs());
            }
            case NONE -> false;
            case AREA, LINE -> bbox.contains(obj.getGeometry().getPolygon().getBounds2D());
        };
    }

    /**
     * Create a bounding box from coordinates
     */
    private Rectangle2D boundingBox(double lat1, double lon1, double lat2, double lon2) {
        var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.moveTo(lat1, lon1);
        path.lineTo(lat1, lon2);
        path.lineTo(lat2, lon2);
        path.lineTo(lat2, lon1);
        path.closePath();
        return path.getBounds2D();
    }
}
