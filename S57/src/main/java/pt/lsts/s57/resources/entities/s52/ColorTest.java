/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 *
 * Created by Hugo Dias
 * 
 */
package pt.lsts.s57.resources.entities.s52;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

public class ColorTest extends JPanel implements ActionListener {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    protected JButton b1, b2, b3;

    public ColorTest() {
        Configuration config = null;
        try {
            config = new PropertiesConfiguration("resources/config.properties");
        }
        catch (ConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        ColorTable colortable = ColorTable.forge(config);
        Map<String, S52Color> map = colortable.getRecords();
        for (Entry<String, S52Color> entry : map.entrySet()) {
            Color color = entry.getValue().get(ColorScheme.DAY_BRIGHT);
            JButton button = new JButton(entry.getValue().getName() + "\n " + entry.getValue().getCode());
            button.setBackground(color);
            add(button);
        }

    }

    /**
     * Create the GUI and show it. For thread safety, this method should be invoked from the event-dispatching thread.
     */
    private static void createAndShowGUI() {

        // Create and set up the window.
        JFrame frame = new JFrame("ButtonDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create and set up the content pane.
        ColorTest newContentPane = new ColorTest();
        newContentPane.setOpaque(true); // content panes must be opaque
        newContentPane.setSize(400, 400);
        frame.setContentPane(newContentPane);
        frame.setSize(400, 400);
        // Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO Auto-generated method stub

    }
}
