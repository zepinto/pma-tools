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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import lombok.Getter;
import pt.omst.neptus.util.I18n;

/**
 * Dialog for configuring a database connection.
 */
public class DatabaseConnectionDialog extends JDialog {
    
    private final JTextField hostField;
    private final JSpinner portSpinner;
    private final JTextField databaseField;
    private final JTextField userField;
    private final JPasswordField passwordField;
    private final JComboBox<String> typeCombo;
    
    @Getter
    private boolean confirmed = false;
    
    @Getter
    private DatabaseDataSource result = null;
    
    /**
     * Creates a new database connection dialog.
     * 
     * @param parent the parent window
     */
    public DatabaseConnectionDialog(Window parent) {
        super(parent, I18n.textOrDefault("datasource.dialog.title", "Database Connection"), 
              ModalityType.APPLICATION_MODAL);
        
        // Create form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Database Type
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.type", "Type") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        typeCombo = new JComboBox<>(new String[]{"MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB"});
        formPanel.add(typeCombo, gbc);
        
        // Host
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.host", "Host") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        hostField = new JTextField("localhost", 20);
        formPanel.add(hostField, gbc);
        
        // Port
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.port", "Port") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        portSpinner = new JSpinner(new SpinnerNumberModel(3306, 1, 65535, 1));
        formPanel.add(portSpinner, gbc);
        
        // Database Name
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.database", "Database") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        databaseField = new JTextField(20);
        formPanel.add(databaseField, gbc);
        
        // User
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.user", "User") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        userField = new JTextField(20);
        formPanel.add(userField, gbc);
        
        // Password
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datasource.dialog.password", "Password") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        formPanel.add(passwordField, gbc);
        
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
        
        // Update port when type changes
        typeCombo.addActionListener(e -> updateDefaultPort());
        
        // Dialog settings
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
        
        // Set default button
        getRootPane().setDefaultButton(okButton);
    }
    
    private void updateDefaultPort() {
        String type = (String) typeCombo.getSelectedItem();
        if (type == null) return;
        
        switch (type) {
            case "MySQL":
                portSpinner.setValue(3306);
                break;
            case "PostgreSQL":
                portSpinner.setValue(5432);
                break;
            case "Oracle":
                portSpinner.setValue(1521);
                break;
            case "SQL Server":
                portSpinner.setValue(1433);
                break;
            case "MongoDB":
                portSpinner.setValue(27017);
                break;
        }
    }
    
    private void onOk() {
        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();
        String user = userField.getText().trim();
        String password = new String(passwordField.getPassword());
        String type = (String) typeCombo.getSelectedItem();
        int port = (Integer) portSpinner.getValue();
        
        if (host.isEmpty()) {
            hostField.requestFocus();
            return;
        }
        
        if (database.isEmpty()) {
            databaseField.requestFocus();
            return;
        }
        
        result = new DatabaseDataSource(host, port, database, user, password, type);
        confirmed = true;
        dispose();
    }
    
    private void onCancel() {
        confirmed = false;
        result = null;
        dispose();
    }
    
    /**
     * Shows the dialog and returns the configured database data source.
     * 
     * @param parent the parent window
     * @return the configured database data source, or null if cancelled
     */
    public static DatabaseDataSource showDialog(Window parent) {
        DatabaseConnectionDialog dialog = new DatabaseConnectionDialog(parent);
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.result : null;
    }
}
