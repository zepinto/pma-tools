//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import pt.omst.neptus.util.GuiUtils;

/**
 * Visual test for LED indicators.
 */
public class LedIconTest {
    
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        JFrame frame = new JFrame("LED Icon Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        
        // Test different LED states
        JLabel inactiveLabel = new JLabel("Inactive (Gray):", LedIcon.createInactive(12), JLabel.LEFT);
        JLabel activeLabel = new JLabel("Active (Green):", LedIcon.createActive(12), JLabel.LEFT);
        JLabel errorLabel = new JLabel("Error (Red):", LedIcon.createError(12), JLabel.LEFT);
        
        panel.add(inactiveLabel);
        panel.add(activeLabel);
        panel.add(errorLabel);
        
        // Test different sizes
        panel.add(new JLabel("Small:", LedIcon.createActive(8), JLabel.LEFT));
        panel.add(new JLabel("Medium:", LedIcon.createActive(12), JLabel.LEFT));
        panel.add(new JLabel("Large:", LedIcon.createActive(16), JLabel.LEFT));
        
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
