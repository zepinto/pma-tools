//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.FlowLayout;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class ObservationsListPanel extends JPanel {

    ArrayList<ImageButton> thumbnails = new ArrayList<>();

    public ObservationsListPanel() {
        setLayout(new FlowLayout());
        setBorder(BorderFactory.createTitledBorder("Similar Observations"));
    }

    public void clearThumbnails() {
        for (ImageButton thumbnail : thumbnails) {
            remove(thumbnail);
        }
        thumbnails.clear();
    }

}
