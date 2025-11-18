//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import lombok.Getter;
import pt.lsts.neptus.util.I18n;

/**
 * Dialog for configuring a Pulvis server connection.
 */
public class DatabaseConnectionDialog extends JDialog {
    
    private final JTextField hostField;
    private final JSpinner portSpinner;
    
    @Getter
    private boolean confirmed = false;
    
    @Getter
    private PulvisDataSource result = null;
    
    /**
     * Creates a new Pulvis connection dialog.
     * 
     * @param parent the parent window
     */
    public DatabaseConnectionDialog(Window parent) {
        super(parent, I18n.textOrDefault("datasource.dialog.title", "Pulvis Connection"), 
              ModalityType.APPLICATION_MODAL);
        
        // Create form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Host
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.host", "Host") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        hostField = new JTextField("localhost", 20);
        formPanel.add(hostField, gbc);
        
        // Port
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.port", "Port") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        portSpinner = new JSpinner(new SpinnerNumberModel(8080, 1, 65535, 1));
        formPanel.add(portSpinner, gbc);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(I18n.textOrDefault("button.ok", "OK"));
        JButton cancelButton = new JButton(I18n.textOrDefault("button.cancel", "Cancel"));
        
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> onCancel());
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        // Layout
        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Dialog settings
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
        
        // Set default button
        getRootPane().setDefaultButton(okButton);
    }
    
    private void onOk() {
        String host = hostField.getText().trim();
        int port = (Integer) portSpinner.getValue();
        
        if (host.isEmpty()) {
            hostField.requestFocus();
            return;
        }
        
        result = new PulvisDataSource(host, port);
        confirmed = true;
        dispose();
    }
    
    private void onCancel() {
        confirmed = false;
        result = null;
        dispose();
    }
    
    /**
     * Shows the dialog and returns the configured Pulvis connection.
     * 
     * @param parent the parent window
     * @return the configured Pulvis connection, or null if cancelled
     */
    public static PulvisDataSource showDialog(Window parent) {
        DatabaseConnectionDialog dialog = new DatabaseConnectionDialog(parent);
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.result : null;
    }
}
