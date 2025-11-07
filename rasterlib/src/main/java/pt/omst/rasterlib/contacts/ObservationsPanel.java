package pt.omst.rasterlib.contacts;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterlib.Observation;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

@Slf4j
public class ObservationsPanel extends JPanel implements ContactChangeListener {
    private final JTabbedPane observationsTabs;
    private final JLabel noObservationsLabel;

    ArrayList<ContactChangeListener> changeListeners = new ArrayList<>();

    public ObservationsPanel() {
        setLayout(new BorderLayout());
        observationsTabs = new JTabbedPane();
        noObservationsLabel = new JLabel("(No observations)");
        noObservationsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(noObservationsLabel, BorderLayout.CENTER);
        setBorder(new TitledBorder("Observations"));
    }

    public void addObservation(File folder, Observation observation) {
        if (observationsTabs.getTabCount() == 0) {
            remove(noObservationsLabel);
            add(observationsTabs, BorderLayout.CENTER);
        }
        SidescanObservationPanel panel = new SidescanObservationPanel(folder, observation);
        panel.addChangeListener(this);
        observationsTabs.addTab(""+(observationsTabs.getTabCount()+1), panel);
        revalidate();
        SwingUtilities.invokeLater(() -> observationsTabs.setSelectedIndex(observationsTabs.getTabCount() - 1));
    }

    @Override
    public void observationChanged(Observation observation) {
        notifyObservationChanged(observation);
    }

    @Override
    public void observationDeleted(Observation observation) {
        notifyObservationDeleted(observation);
    }

    public void addChangeListener(ContactChangeListener listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(ContactChangeListener listener) {
        changeListeners.remove(listener);
    }

    public void notifyObservationChanged(Observation observation) {
        for (ContactChangeListener listener : changeListeners) {
            listener.observationChanged(observation);
        }
    }

    public void notifyObservationDeleted(Observation observation) {
        for (ContactChangeListener listener : changeListeners) {
            listener.observationDeleted(observation);
        }
    }

    public void clear() {
        for (int i = 0; i < observationsTabs.getTabCount(); i++) {
            SidescanObservationPanel panel = (SidescanObservationPanel) observationsTabs.getComponentAt(i);
            panel.removeObservationChangeListener(this);
            try {
                panel.close();
            }
            catch (Exception e) {
                log.warn("Error closing observation panel: " + e.getMessage());
            }
        }
        observationsTabs.removeAll();

        add(noObservationsLabel, BorderLayout.CENTER);
        revalidate();
    }

}
