//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.filtering;

import pt.omst.rasterlib.contacts.CompressedContact;

/**
 * Listener interface for contact filter panel events.
 */
public interface ContactFilterListener {
    
    /**
     * Called when filter criteria have changed (after debounce delay).
     */
    void onFilterChanged();
    
    /**
     * Called when a contact is selected from the contact list.
     * 
     * @param contact The selected contact
     */
    void onContactSelected(CompressedContact contact);
}
