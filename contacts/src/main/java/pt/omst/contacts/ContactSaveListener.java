//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.io.File;
import java.util.UUID;

/**
 * Listener interface for contact save events.
 */
public interface ContactSaveListener {
    
    /**
     * Called when a contact is saved.
     * 
     * @param contactId the UUID of the saved contact
     * @param zctFile the .zct file that was saved
     */
    void onContactSaved(UUID contactId, File zctFile);
}
