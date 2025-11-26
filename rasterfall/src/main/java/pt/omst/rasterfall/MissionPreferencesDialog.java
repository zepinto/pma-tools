//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.I18n;
import pt.omst.util.UserPreferences;

/**
 * Dialog for editing mission-related preferences including user name,
 * campaign name, system name, and contact name prefix.
 */
@Slf4j
public class MissionPreferencesDialog extends JDialog {
    
    private static final long serialVersionUID = 1L;
    private JTextField usernameField;
    private JTextField campaignNameField;
    private JTextField systemNameField;
    private JTextField contactNameField;
    private JSpinner contactSizeSpinner;
    private boolean confirmed = false;
    
    public MissionPreferencesDialog(JFrame parent) {
        super(parent, I18n.text("prefs.mission.title"), true);
        initComponents();
        setLocationRelativeTo(parent);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(new EmptyBorder(15, 15, 10, 15));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // Username field
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel(I18n.text("prefs.user.username") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        usernameField = new JTextField(UserPreferences.getUsername(), 25);
        usernameField.setToolTipText(I18n.text("prefs.user.username.tooltip"));
        mainPanel.add(usernameField, gbc);
        
        row++;
        
        // Campaign name field
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel(I18n.text("prefs.mission.campaign") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        campaignNameField = new JTextField(RasterfallPreferences.getCampaignName(), 25);
        campaignNameField.setToolTipText(I18n.text("prefs.mission.campaign.tooltip"));
        mainPanel.add(campaignNameField, gbc);
        
        row++;
        
        // System name field
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel(I18n.text("prefs.mission.system") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        systemNameField = new JTextField(RasterfallPreferences.getSystemName(), 25);
        systemNameField.setToolTipText(I18n.text("prefs.mission.system.tooltip"));
        mainPanel.add(systemNameField, gbc);
        
        row++;
        
        // Contact name prefix field
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel(I18n.text("prefs.mission.contact.prefix") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        contactNameField = new JTextField(RasterfallPreferences.getContactName(), 25);
        contactNameField.setToolTipText(I18n.text("prefs.mission.contact.prefix.tooltip"));
        mainPanel.add(contactNameField, gbc);
        
        row++;
        
        // Contact size field
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel(I18n.text("prefs.mission.contact.size") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        SpinnerNumberModel sizeModel = new SpinnerNumberModel(
                (double) RasterfallPreferences.getContactSize(), 1.0, 20.0, 0.5);
        contactSizeSpinner = new JSpinner(sizeModel);
        contactSizeSpinner.setToolTipText(I18n.text("prefs.mission.contact.size.tooltip"));
        mainPanel.add(contactSizeSpinner, gbc);
        
        row++;
        
        // Hint label
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.insets = new Insets(10, 5, 5, 5);
        JLabel hintLabel = new JLabel(I18n.text("prefs.mission.hint"));
        hintLabel.setFont(hintLabel.getFont().deriveFont(10f));
        mainPanel.add(hintLabel, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton okButton = new JButton(I18n.text("button.ok"));
        okButton.addActionListener(e -> {
            savePreferences();
            log.info("Mission preferences saved");
            confirmed = true;
            dispose();
        });
        
        JButton cancelButton = new JButton(I18n.text("button.cancel"));
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Set default button
        getRootPane().setDefaultButton(okButton);
        
        pack();
        setResizable(false);
    }
    
    private void savePreferences() {
        // Save username
        String username = usernameField.getText().trim();
        UserPreferences.setUsername(username.isEmpty() ? null : username);
        
        // Save campaign name
        String campaignName = campaignNameField.getText().trim();
        if (!campaignName.isEmpty()) {
            RasterfallPreferences.setCampaignName(campaignName);
        }
        
        // Save system name
        String systemName = systemNameField.getText().trim();
        if (!systemName.isEmpty()) {
            RasterfallPreferences.setSystemName(systemName);
        }
        
        // Save contact name prefix
        String contactName = contactNameField.getText().trim();
        if (!contactName.isEmpty()) {
            RasterfallPreferences.setContactName(contactName);
        }
        
        // Save contact size
        float contactSize = ((Number) contactSizeSpinner.getValue()).floatValue();
        RasterfallPreferences.setContactSize(contactSize);
        log.info("Contact size preference set to: {} meters", contactSize);
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Shows the mission preferences dialog.
     * @param parent Parent frame
     * @return true if user clicked OK, false if cancelled
     */
    public static boolean showDialog(JFrame parent) {
        MissionPreferencesDialog dialog = new MissionPreferencesDialog(parent);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}
