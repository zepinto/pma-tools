//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import pt.omst.rasterlib.contacts.CompressedContact;

import java.util.List;

/**
 * Interface for handling contact grouping operations.
 * Allows different implementations (with or without Pulvis synchronization).
 */
public interface ContactGroupingHandler {
    /**
     * Groups multiple contacts into a single main contact asynchronously.
     * @param mainContact The main contact that will contain merged data
     * @param mergeContacts The contacts to merge into the main contact
     */
    void groupContactsAsync(CompressedContact mainContact, List<CompressedContact> mergeContacts);
}
