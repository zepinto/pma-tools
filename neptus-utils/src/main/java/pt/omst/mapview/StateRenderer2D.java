package pt.omst.mapview;

import java.awt.geom.Point2D;

import javax.swing.JPanel;

import pt.lsts.neptus.core.LocationType;

public abstract class StateRenderer2D extends JPanel {

    public abstract Point2D getScreenPosition(LocationType location);

    public abstract LocationType getRealWorldLocation(Point2D screenPosition);

    public abstract int getLevelOfDetail();

    public abstract double getRotation();

    public abstract LocationType getCenter();

    public abstract double getMapScale();

    
   
}