package pt.omst.rasterlib.contacts;

import pt.omst.rasterlib.Observation;

/**
 * Interface for classes that want to be notified of changes to the contact editor.
 */
public interface ContactChangeListener {
    /**
     * Called when an observation is changed.
     * @param observation the observation that was changed
     */
    void observationChanged(Observation observation);

    /**
     * Called when an observation is deleted.
     * @param observation the observation that was deleted
     */
    void observationDeleted(Observation observation);
}
