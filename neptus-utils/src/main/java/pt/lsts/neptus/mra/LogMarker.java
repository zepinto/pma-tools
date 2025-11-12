//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.lsts.neptus.mra;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.core.Pair;
import pt.omst.tablelayout.TableLayout;

/**
 * Represents a temporal and spatial marker associated with log data.
 * 
 * <p>LogMarkers are used to annotate specific points in time during log replay,
 * optionally including geographic coordinates. Markers can be organized in a 
 * hierarchical parent-child structure, allowing related markers to be grouped together.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Timestamp-based ordering for chronological display</li>
 *   <li>Optional geographic location (latitude/longitude)</li>
 *   <li>Hierarchical parent-child relationships for organizing markers</li>
 *   <li>Serializable for persistence to disk</li>
 *   <li>Dialog-based creation with optional mission integration</li>
 * </ul>
 * 
 * <p>Markers are typically stored in a {@code marks.dat} file within the log directory
 * and can be loaded/saved using the static {@link #load(File)} and 
 * {@link #save(ArrayList, File)} methods.</p>
 * 
 * @author José Pinto
 * @see LocationType
 */
@Slf4j
public class LogMarker implements Serializable, Comparable<LogMarker> {
    private static final long serialVersionUID = 2L;
    private static final String MARKER_FILE = "marks.dat";
    private String label;
    private double timestamp;
    private double lat;
    private double lon;
    private HashSet<LogMarker> children = new HashSet<LogMarker>();
    private LogMarker parent;

    /**
     * Creates a new log marker with the specified properties.
     * 
     * @param label     Text label to associate with the marker
     * @param timestamp Timestamp in milliseconds since epoch
     * @param latRads   Latitude in radians (use 0 if not available)
     * @param lonRads   Longitude in radians (use 0 if not available)
     */
    public LogMarker(String label, double timestamp, double latRads, double lonRads) {
        super();
        this.setLabel(label);
        this.setTimestamp(timestamp);
        this.setLatRads(latRads);
        this.setLonRads(lonRads);
    }

    public Instant getTimestampAsInstant() {
        return Instant.ofEpochMilli((long) getTimestamp());
    }

    public Date getTimestampAsDate() {
        return new Date((long) getTimestamp());
    }

    /**
     * Checks if this marker has a parent marker.
     * 
     * @return true if this marker has a parent, false otherwise
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Sets the parent marker for this marker.
     * 
     * @param newParent The parent marker to set
     */
    public void setParent(LogMarker newParent) {
        parent = newParent;
    }

    /**
     * Gets the parent marker of this marker.
     * 
     * @return The parent marker, or null if this marker has no parent
     */
    public LogMarker getParent() {
        return parent;
    }

    /**
     * Checks if this marker has any child markers.
     * 
     * @return true if this marker has children, false otherwise
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Gets all child markers of this marker.
     * 
     * @return Collection of child markers (may be empty)
     */
    public Collection<LogMarker> getChildren() {
        return children;
    }

    /**
     * Removes all child markers from this marker.
     * The child markers' parent references are not cleared.
     */
    public void removeAllChildren() {
        children.clear();
    }

    /**
     * Adds a child marker to this marker.
     * 
     * <p>If the child already has a parent, it is removed from that parent first.
     * If the child has its own children, they are flattened into this marker's
     * children collection (the child's children become siblings).</p>
     * 
     * @param child The marker to add as a child
     */
    public void addChild(LogMarker child) {
        if (child.hasParent()) {
            child.getParent().removeChild(child);
        }
        // flatten children into new parent
        if (child.hasChildren()) {
            for (LogMarker c : child.getChildren()) {
                children.add(c);
            }
            child.removeAllChildren();
        }
        children.add(child);
        child.setParent(this);
    }

    /**
     * Removes a child marker from this marker.
     * The child's parent reference is set to null.
     * 
     * @param child The child marker to remove
     */
    public void removeChild(LogMarker child) {
        children.remove(child);
        child.setParent(null);
    }

    private static boolean addingToMission = false;

    /**
     * Displays a dialog for entering a new marker name and mission integration option.
     * 
     * <p>The dialog includes a text field for the marker name and a checkbox for
     * optionally adding the marker to the mission. The checkbox state is remembered
     * between invocations.</p>
     * 
     * @param parentComponent The parent component for the dialog
     * @return A pair containing the marker name and mission integration flag,
     *         or null if the dialog was cancelled
     */
    public static Pair<String, Boolean> getMarkerNameDialog(Component parentComponent) {
        TableLayout tl = new TableLayout(new double[] { 0.33, 0.67 }, new double[] { 0.5, 0.5 });
        tl.setHGap(3);

        JPanel p = new JPanel(tl);
        final JTextField txtLabel = new JTextField("");
        JCheckBox chkAddToMission = new JCheckBox("Add to mission");
        chkAddToMission.setSelected(addingToMission);
        p.add(new JLabel("Marker name:"), "0,0");
        p.add(txtLabel, "1,0");
        p.add(chkAddToMission, "1,1");

        JOptionPane pane = new JOptionPane(p, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION) {
            public void setInitialValue(Object newInitialValue) {
                txtLabel.requestFocusInWindow();
            };
        };

        txtLabel.addFocusListener(new FocusAdapter() {
            boolean focusWasLost = false;

            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                if (!focusWasLost)
                    txtLabel.requestFocusInWindow();
                focusWasLost = true;
            }
        });

        JDialog dialog = pane.createDialog(parentComponent, "Add Log Marker");
        dialog.setVisible(true);
        if (pane.getValue().equals(JOptionPane.OK_OPTION)) {
            addingToMission = chkAddToMission.isSelected();
            return new Pair<String, Boolean>(txtLabel.getText(), chkAddToMission.isSelected());
        } else
            return null;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (children == null) {
            children = new HashSet<LogMarker>();
        }
    }

    /**
     * Loads log markers from a folder.
     * 
     * <p>Searches for {@code marks.dat} in the specified folder, and if not found,
     * searches in the parent directory.</p>
     * 
     * @param folder The folder to search for marker data
     * @return Collection of loaded markers (empty if file not found)
     */
    public static Collection<LogMarker> loadFromFolder(File folder) {
        ArrayList<LogMarker> logMarkers = new ArrayList<>();
        if (new File(folder + File.separator + MARKER_FILE).exists()) {
            logMarkers = (ArrayList<LogMarker>) load(new File(folder + File.separator + MARKER_FILE));
        } else if (new File(folder + File.separator + ".." + File.separator + MARKER_FILE).exists()) {
            logMarkers = (ArrayList<LogMarker>) load(
                    new File(folder + File.separator + ".." + File.separator + MARKER_FILE));
        }

        return logMarkers;
    }

    /**
     * Loads log markers from a file.
     * 
     * <p>If a directory is provided, looks for {@code marks.dat} within it.
     * Returns an empty collection if the file doesn't exist or loading fails.</p>
     * 
     * @param file The file or directory to load markers from
     * @return Collection of loaded markers (empty on error)
     */
    @SuppressWarnings("unchecked")
    public static Collection<LogMarker> load(File file) {
        if (file.isDirectory()) {
            return load(new File(file, MARKER_FILE));
        }

        ArrayList<LogMarker> logMarkers = new ArrayList<>();
        try {
            InputStream stream = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(stream);
            logMarkers.addAll(((ArrayList<LogMarker>) ois.readObject()));
            ois.close();
        } catch (Exception e) {
            log.error("exception: ", e);
            return new ArrayList<>();
        }

        return logMarkers;
    }

    /**
     * Saves a collection of log markers to a file in the specified folder.
     * 
     * <p>Markers are saved to {@code marks.dat} within the folder using
     * Java serialization.</p>
     * 
     * @param logMarkers The markers to save
     * @param folder The folder where the markers file will be created
     */
    public static void save(ArrayList<LogMarker> logMarkers, File folder) {
        try {

            OutputStream stream = new FileOutputStream(folder + File.separator + MARKER_FILE);
            ObjectOutputStream dos = new ObjectOutputStream(stream);
            dos.writeObject(logMarkers);
            dos.close();
        } catch (Exception e) {
            log.error("exception: ", e);
        }
    }

    /**
     * Creates a marker with a sequenced label (e.g., "marker1", "marker2").
     * The marker will have no geographic coordinates (0, 0).
     * 
     * @param prefix The label prefix
     * @param sequence The sequence number to append to the prefix
     * @param timestamp The timestamp in milliseconds
     * @return A new LogMarker with the generated label
     */
    public static LogMarker createWithSequence(String prefix, int sequence, long timestamp) {
        return new LogMarker(prefix + sequence, timestamp, 0, 0);
    }

    /**
     * Compares this marker to another based on timestamp.
     * Earlier timestamps come first in sorted order.
     * 
     * @param o The marker to compare to
     * @return Negative if this marker is earlier, positive if later, 0 if equal
     */
    @Override
    public int compareTo(LogMarker o) {
        if (o.getTimestamp() > getTimestamp())
            return -1;
        else if (o.getTimestamp() < getTimestamp())
            return 1;

        return 0;
    }

    // timestamp in milliseconds
    public double getTimestamp() {
        return timestamp;
    }

    // timestamp in milliseconds
    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the geographic location of this marker as a LocationType.
     * 
     * @return LocationType with latitude and longitude in degrees
     */
    public LocationType getLocation() {
        return new LocationType(Math.toDegrees(getLatRads()), Math.toDegrees(getLonRads()));
    }

    public double getLatRads() {
        return lat;
    }

    public void setLatRads(double latRads) {
        this.lat = latRads;
    }

    public double getLonRads() {
        return lon;
    }

    public void setLonRads(double lonRads) {
        this.lon = lonRads;
    }

    /**
     * Gets the timestamp as a Date object.
     * 
     * @return Date representation of the marker's timestamp
     */
    public Date getDate() {
        return new Date((long) getTimestamp());
    }

    @Override
    public String toString() {
        return getLabel();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}
