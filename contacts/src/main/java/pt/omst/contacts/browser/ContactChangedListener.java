//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import pt.omst.rasterlib.Contact;

/**
 * Listener interface for contact field changes.
 */
public interface ContactChangedListener {
    /**
     * Called when contact fields (name, type, confidence, description) are modified.
     * @param contact the contact that was changed
     */
    void onContactChanged(Contact contact);
}
