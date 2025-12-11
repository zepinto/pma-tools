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
import java.io.Serial;
import java.io.Serializable;

import pt.lsts.neptus.core.LocationType;

/**
 * Null Object pattern implementation for S57 geometry.
 * Represents an absent or undefined geometry.
 * Uses singleton pattern for efficiency.
 * 
 * @author Hugo Dias
 */
public final class S57None implements S57Geometry, Serializable {
    
    @Serial
    private static final long serialVersionUID = -1044021687638985278L;
    
    private static final S57None INSTANCE = new S57None();

    /**
     * Returns the singleton instance of S57None.
     * 
     * @return the singleton instance
     */
    public static S57None forge() {
        return INSTANCE;
    }

    private S57None() {
        // Private constructor for singleton
    }

    @Override
    public S57GeometryType getType() {
        return S57GeometryType.NONE;
    }

    @Override
    public LocationType getLocation() {
        return null;
    }

    @Override
    public Shape getShape() {
        return null;
    }

    @Override
    public LocationType getCenter() {
        return null;
    }
    
    /**
     * Ensures singleton pattern is preserved during deserialization.
     */
    @Serial
    private Object readResolve() {
        return INSTANCE;
    }
}
