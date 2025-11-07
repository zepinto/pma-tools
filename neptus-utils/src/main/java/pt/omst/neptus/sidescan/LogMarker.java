/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: José Correia
 * Nov 16, 2012
 */

package pt.omst.neptus.sidescan;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.core.Pair;
import pt.omst.neptus.util.I18n;
import pt.omst.tablelayout.TableLayout;

/**
 * This will be serializable, so no name changes of the fields!
 *
 * @author jqcorreia
 */
public class LogMarker implements Serializable, Comparable<LogMarker> {
    private static final Logger LOG = LoggerFactory.getLogger(LogMarker.class);
    private static final long serialVersionUID = 2L;
    private static final String MARKER_FILE = "marks.dat";
    private String label;
    private double timestamp;
    private double lat;
    private double lon;
    private HashSet<LogMarker> children = new HashSet<LogMarker>();
    private LogMarker parent;

    /**
     * @param label     Text to associate with the marker
     * @param timestamp in milliseconds
     * @param latRads   Latitude, in radians of the marker. Use 0 if not available.
     * @param lonRads   Longitude, in radians of the marker. Use 0 if not available.
     */
    public LogMarker(String label, double timestamp, double latRads, double lonRads) {
        super();
        this.setLabel(label);
        this.setTimestamp(timestamp);
        this.setLatRads(latRads);
        this.setLonRads(lonRads);
    }

    public boolean hasParent() {
        return parent != null;
    }

    public void setParent(LogMarker newParent) {
        parent = newParent;
    }

    public LogMarker getParent() {
        return parent;
    }

    
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public Collection<LogMarker> getChildren() {
        return children;
    }

    public void removeAllChildren() {
        children.clear();
    }

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

    public void removeChild(LogMarker child) {
        children.remove(child);
        child.setParent(null);
    }

    private static boolean addingToMission = false;
    public static Pair<String, Boolean> getMarkerNameDialog(Component parentComponent) {
        TableLayout tl = new TableLayout(new double[] {0.33, 0.67}, new double[] {0.5,0.5});
        tl.setHGap(3);
                                                       
        JPanel p = new JPanel(tl);
        final JTextField txtLabel = new JTextField("");
        JCheckBox chkAddToMission = new JCheckBox("Add to mission");
        chkAddToMission.setSelected(addingToMission);
        p.add(new JLabel("Marker name:"), "0,0");
        p.add(txtLabel, "1,0");
        p.add(chkAddToMission,"1,1");                
        
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
            return new Pair<String,Boolean>(txtLabel.getText(), chkAddToMission.isSelected());        
        }
        else    
            return null;  
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
            in.defaultReadObject();
            if (children == null) {
                children = new HashSet<LogMarker>();
            }
    }

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
            return new ArrayList<>();
        }

        return logMarkers;
    }

    public static void save(ArrayList<LogMarker> logMarkers, File folder) {
        try {
            if (folder == null) {
                LOG.error(I18n.text("unable to save marks file: log file not found"));
                return;
            } else if (!folder.exists()) {
                LOG.error(I18n.text("unable to save marks file: log folder not found"));
                return;
            }

            OutputStream stream = new FileOutputStream(folder + File.separator + MARKER_FILE);
            ObjectOutputStream dos = new ObjectOutputStream(stream);
            dos.writeObject(logMarkers);
            dos.close();
        } catch (Exception e) {
            LOG.error(I18n.text("unable to save marks file: log folder not found"));
            return;
        }
    }

    public static LogMarker createWithSequence(String prefix, int sequence, long timestamp) {
        return new LogMarker(prefix + sequence, timestamp, 0, 0);
    }

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
