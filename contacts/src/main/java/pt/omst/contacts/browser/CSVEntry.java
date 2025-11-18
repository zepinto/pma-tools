//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single entry in a CSV file with label, description, and optional sub-labels.
 */
public class CSVEntry {
    private String label;
    private String description;
    private List<String> subLabels;

    public CSVEntry() {
        this.label = "";
        this.description = "";
        this.subLabels = new ArrayList<>();
    }

    public CSVEntry(String label, String description) {
        this.label = label != null ? label : "";
        this.description = description != null ? description : "";
        this.subLabels = new ArrayList<>();
    }

    public CSVEntry(String label, String description, List<String> subLabels) {
        this.label = label != null ? label : "";
        this.description = description != null ? description : "";
        this.subLabels = subLabels != null ? new ArrayList<>(subLabels) : new ArrayList<>();
    }

    /**
     * Copy constructor for creating a clone of an entry.
     */
    public CSVEntry(CSVEntry other) {
        this.label = other.label;
        this.description = other.description;
        this.subLabels = new ArrayList<>(other.subLabels);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public List<String> getSubLabels() {
        return subLabels;
    }

    public void setSubLabels(List<String> subLabels) {
        this.subLabels = subLabels != null ? new ArrayList<>(subLabels) : new ArrayList<>();
    }

    /**
     * Creates a deep copy of this entry.
     */
    public CSVEntry clone() {
        return new CSVEntry(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CSVEntry csvEntry = (CSVEntry) o;
        return Objects.equals(label, csvEntry.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public String toString() {
        return label;
    }
}
