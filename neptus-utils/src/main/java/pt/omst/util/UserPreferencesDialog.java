//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.util;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import pt.lsts.neptus.util.I18n;

/**
 * Dialog for editing user preferences like username.
 */
public class UserPreferencesDialog extends JDialog {
    
    private static final long serialVersionUID = 1L;
    private JTextField usernameField;
    private boolean confirmed = false;
    
    public UserPreferencesDialog(JFrame parent) {
        super(parent, I18n.text("User Preferences"), true);
        initComponents();
        setLocationRelativeTo(parent);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Username field
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel(I18n.text("Username") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        usernameField = new JTextField(UserPreferences.getUsername(), 20);
        mainPanel.add(usernameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel hintLabel = new JLabel(I18n.text("Used for contact annotations and observations"));
        hintLabel.setFont(hintLabel.getFont().deriveFont(10f));
        mainPanel.add(hintLabel, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton okButton = new JButton(I18n.text("OK"));
        okButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            UserPreferences.setUsername(username.isEmpty() ? null : username);
            confirmed = true;
            dispose();
        });
        
        JButton cancelButton = new JButton(I18n.text("Cancel"));
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        pack();
        setResizable(false);
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Shows the user preferences dialog.
     * @param parent Parent frame
     * @return true if user clicked OK, false if cancelled
     */
    public static boolean showDialog(JFrame parent) {
        UserPreferencesDialog dialog = new UserPreferencesDialog(parent);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}
