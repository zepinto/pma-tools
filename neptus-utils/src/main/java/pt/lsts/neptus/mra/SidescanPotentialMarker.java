package pt.lsts.neptus.mra;

public class SidescanPotentialMarker extends LogMarker {
    private static final long serialVersionUID = 11L;
    private double distanceToNadir;
    
    public SidescanPotentialMarker(SidescanLogMarker parent, String label, double timestamp, double latRads, double lonRads, double distanceToNadir) {
        super(label, timestamp, latRads, lonRads);
        this.distanceToNadir = distanceToNadir;
        setParent(parent);
    }

    @Override
    public boolean hasParent() {
        return true;
    }

    public double getDistanceToNadir() {
        return distanceToNadir;
    }
    
}
