package pt.omst.rasterlib.contacts;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

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
