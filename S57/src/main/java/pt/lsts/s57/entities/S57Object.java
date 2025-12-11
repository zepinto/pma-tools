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
 * 
 */
package pt.lsts.s57.entities;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gdal.ogr.Feature;
import org.gdal.ogr.ogrConstants;

import pt.lsts.s57.entities.geometry.S57Geometry;
import pt.lsts.s57.entities.geometry.S57GeometryFactory;
import pt.lsts.s57.entities.geometry.S57GeometryType;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.Agency;
import pt.lsts.s57.resources.entities.ObjectClass;
import pt.lsts.s57.resources.entities.s52.DisplayCategory;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.resources.entities.s52.LookupTableRecord;
import pt.lsts.s57.resources.entities.s52.LookupTableType;

public class S57Object implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 7260691375690798755L;
    // properties
    /*
     * 6 char code to identify object
     */
    private final String acronym;
    private final ObjectClass objectClass;
    private final Agency agency;
    private final S57Geometry geometry;
    private final Map<LookupTableType, LookupTableRecord> lup;
    @SuppressWarnings("unused")
    transient private final Resources resources;
    /*
     * Attributes Map
     */
    private final Map<String, S57Attribute> attributes = new HashMap<>();

    /*
     * Identifier field map
     * 
     * record identifier [RCNM, RCID]; object geometric primitive [PRIM]; P {1} Point L {2} Line A {3} Area N {255}
     * Object does not directly reference any geometry group [GRUP]; object label/code [OBJL]; record version [RVER];
     * record update instruction [RUIN]. producing agency [AGEN]; feature Identification number [FIDN]; feature
     * Identification subdivision [FIDS].
     */
    private final Map<String, String> idFields = new HashMap<>();

    // TODO handle all this info especially agencies with resources class

    /**
     * Static factory method
     * 
     * @param resources
     * @param ogrFeature
     * @return S57Object
     */
    public static S57Object forge(Resources resources, Feature ogrFeature) {
        return new S57Object(resources, ogrFeature);
    }

    /**
     * Construct
     * 
     * @param resources
     * @param feature
     */
    private S57Object(Resources resources, Feature feature) {
        this.resources = resources;
        if (feature.GetFID() == ogrConstants.NullFID) {
            throw new IllegalArgumentException("Invalid feature FID");
        }
        this.acronym = feature.GetDefnRef().GetName();
        this.objectClass = resources.getObjectClass(acronym);
        for (int i = 0; i < feature.GetFieldCount(); i++) {
            var attribAcronym = feature.GetFieldDefnRef(i).GetNameRef();
            // fields that arent set, have empty string ""
            var value = feature.IsFieldSet(i) ? feature.GetFieldAsString(i) : "";
            // identifier fields
            if (attribAcronym.length() != 6) {
                idFields.put(attribAcronym, value);
            } else {
                // attributes
                if (objectClass.getAttrib_A().contains(attribAcronym)
                        || objectClass.getAttrib_B().contains(attribAcronym)
                        || objectClass.getAttrib_C().contains(attribAcronym)) {
                    attributes.put(attribAcronym, S57Attribute.forge(resources, attribAcronym, value));
                } else {
                    throw new IllegalArgumentException("object doesnt have the necessary attributes");
                }
            }
        }
        agency = resources.getAgency(idFields.get("AGEN"));
        geometry = S57GeometryFactory.factory(feature.GetGeometryRef());
        lup = resources.getLupRecord(geometry.getType(), acronym, attributes);
    }

    /*
     * Accessors
     */

    /**
     * Get objects acronym
     * 
     * @return the acronym
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * Gets object class info {@link ObjectClass}
     * <ul>
     * <li>code
     * <li>long name
     * <li>acronym
     * <li>attributes A (List)
     * <li>attributes B (List)
     * <li>attributes C (List)
     * <li>type
     * <li>primitives (List)
     * </ul>
     * 
     * @return the objectClass
     */
    public ObjectClass getObjectClass() {
        return objectClass;
    }

    /**
     * @return the attributes
     */
    public Map<String, S57Attribute> getAttributes() {
        return attributes;
    }

    public S57Attribute getAttribute(String acronym) {
        return attributes.get(acronym);
    }

    /**
     * Check if the attribute is given (may be empty)
     * 
     * @param acronym
     * @return
     */
    public boolean hasAttribute(String acronym) {
        return attributes.get(acronym) != null;
    }

    /**
     * Checks if attribute is given and not empty
     * 
     * @param acronym
     * @return
     */
    public boolean isAttribute(String acronym) {
        var attr = attributes.get(acronym);
        return attr != null && !attr.isEmpty();
    }

    /**
     * @return the idFields
     */
    public Map<String, String> getIdFields() {
        return idFields;
    }

    /**
     * @return the idFields
     */
    public String getIdField(String field) {
        return idFields.get(field);
    }

    public boolean isGroupOne() {
        return "1".equals(idFields.get("GRUP"));
    }

    public boolean intersects(S57Object obj) {
        if (this.getGeoType() == S57GeometryType.NONE || obj.getGeoType() == S57GeometryType.NONE) {
            return false;
        }

        if (this.getGeoType() == S57GeometryType.POINT) {
            if (obj.getGeoType() == S57GeometryType.POINT) {
                return false;
            } else {
                var bounds = obj.getGeometry().getPolygon();
                var gps = this.getGeometry().getLocation();
                return bounds.contains(gps.getLatitudeDegs(), gps.getLongitudeDegs());
            }
        } else {
            // this != point so area or line
            if (obj.getGeoType() == S57GeometryType.POINT) {
                // this != point and obj == point
                var bounds = this.getGeometry().getPolygon();
                var gps = obj.getGeometry().getLocation();
                return bounds.contains(gps.getLatitudeDegs(), gps.getLongitudeDegs());
            } else {
                // this != point and obj != point
                return this.geometry.getPolygon().intersects(obj.getGeometry().getPolygon().getBounds2D());
            }
        }
    }

    public boolean intersects(Path2D bounds) {
        if (this.getGeoType() == S57GeometryType.POINT) {
            var gps = this.geometry.getLocation();
            return bounds.contains(gps.getLatitudeDegs(), gps.getLongitudeDegs());
        }
        if (this.getGeoType() == S57GeometryType.NONE) {
            return false;
        }
        return this.geometry.getPolygon().getBounds2D().intersects(bounds.getBounds2D());
    }

    public boolean contains(Path2D bounds) {
        if (this.getGeoType() == S57GeometryType.AREA) {
            return this.geometry.getPolygon().contains(bounds.getBounds2D());
        }
        return false;
    }

    /**
     * Gets the object's geometry type
     * 
     * @return S57GeometryType
     */
    public S57GeometryType getGeoType() {
        return geometry.getType();
    }

    /**
     * Gets the agency object for this s57 object
     * 
     * @return Agency
     */
    public Agency getAgency() {
        return agency;
    }

    /**
     * @return the geometry
     */
    public S57Geometry getGeometry() {
        return geometry;
    }

    /**
     * Gets this object shape to draw
     * 
     * @return
     */
    public Shape getShape() {
        return geometry.getShape();
    }

    public double getScamin() {
        return this.getAttribute("SCAMIN").isEmpty() ? S57ObjectPainter.SCAMIN_INFINITE
                : Double.valueOf(this.getAttribute("SCAMIN").getValue().get(0));
    }

    public LookupTableRecord getLup(MarinerControls mc) {
        return lup.get(getLupType(mc));
    }

    protected LookupTableType getLupType(MarinerControls mc) {
        return switch (geometry.getType()) {
            case AREA -> mc.getAreaStyle();
            case LINE -> LookupTableType.LINES;
            case POINT -> mc.getPointStyle();
            case NONE -> throw new IllegalArgumentException("Invalid geometry type: NONE");
        };
    }

    public boolean hasConditional(MarinerControls mc) {
        return getLup(mc).hasConditional();
    }

    public int getPriority(MarinerControls mc) {
        return getLup(mc).getPriority();
    }

    public boolean isOverRadar(MarinerControls mc) {
        return getLup(mc).getRadarFlag();
    }

    public DisplayCategory getDisplayCategory(MarinerControls mc) {
        return getLup(mc).getCategory();
    }

    public String getViewingGroup(MarinerControls mc) {
        return getLup(mc).getGroup();
    }

    public List<Instruction> getInstructions(MarinerControls mc) {
        return getLup(mc).getIntructions();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("S57Object [ ").append(acronym)
          .append(" code").append(objectClass.getCode())
          .append(" type: ").append(objectClass.getType()).append("]\n");
        sb.append("   Info: ");
        idFields.forEach((key, value) -> sb.append(key).append("=").append(value).append("; "));
        sb.append("\n   Attributes: ");
        attributes.forEach((key, value) -> sb.append(key).append("=").append(value).append("; "));
        sb.append("\n");
        return sb.toString();
    }

    public String toStringLup(MarinerControls mc) {
        var sb = new StringBuilder();
        sb.append("S57Object [ ").append(acronym)
          .append(" code").append(objectClass.getCode())
          .append(" type: ").append(objectClass.getType()).append("]\n");
        sb.append("   Info: ");
        idFields.forEach((key, value) -> sb.append(key).append("=").append(value).append("; "));
        sb.append("\n   Attributes: ");
        attributes.forEach((key, value) -> sb.append(key).append("=").append(value.getValue()).append("; "));
        sb.append("\n").append(this.getLupType(mc).toString()).append(" ");
        sb.append(this.getLup(mc).toString());
        sb.append("\n");
        return sb.toString();
    }

}
