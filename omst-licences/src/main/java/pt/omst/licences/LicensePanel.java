//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.licences;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import javax0.license3j.License;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.neptus.util.ImageUtils;

public class LicensePanel extends JPanel {

    private ArrayList<String> ignoredFeatures = new ArrayList<>();
    {
        ignoredFeatures.add("expiryDate");
        ignoredFeatures.add("license-id");
        ignoredFeatures.add("licenseSignature");
        ignoredFeatures.add("signatureDigest");
        ignoredFeatures.add("computer-id");
    }

    private final JLabel neptusLabel, licenseState, licenseId, licenseUser, licenseExpiration, licenseFeatures;

    public void setLicense(License license, License activation) {
        if (activation == null) {
            licenseState.setText("Not activated");
            licenseId.setText(license.get("id").getString());
            licenseUser.setText(license.get("user").getString());
            licenseExpiration.setText(license.get("expiryDate").getDate().toString());
            licenseFeatures.setText("n/a");
            return;
        }
        Date expirationDate = activation.get("expiryDate").getDate();
        if (license.get("expiryDate").getDate().before(expirationDate))
            expirationDate = license.get("expiryDate").getDate();
        expirationDate = new Date(expirationDate.getTime() - 1000L * 60 * 60 * 24);
        licenseState.setText("Active until " + expirationDate);
        licenseId.setText(license.get("id").getString());
        licenseUser.setText(license.get("user").getString());
        licenseExpiration.setText(license.get("expiryDate").getDate().toString());
        String features = "<html>";
        for (String key : activation.getFeatures().keySet()) {
            if (ignoredFeatures.contains(key))
                continue;
            if (features.length() > 6)
                features += ", ";
            features += key;
        }

        licenseFeatures.setText(features + "</html>");
    }

    private void revokeLicense() {
        try {
            LicenseChecker.releaseLicense();
            setLicense(LicenseChecker.getMainLicense(), null);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public LicensePanel() {
        
        setPreferredSize(new Dimension(600, 350));
        Image oceanScanImage = ImageUtils.getScaledImage("images/oceanscan.png", 500, 150);
        neptusLabel = oceanScanImage != null ? new JLabel(new ImageIcon(oceanScanImage)) : new JLabel("OMST");
        licenseState = new JLabel("");
        licenseId = new JLabel("");
        licenseUser = new JLabel("");
        licenseExpiration = new JLabel("");
        licenseFeatures = new JLabel("");
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        add(neptusLabel, gbc);

        // License label and value
        gbc.gridwidth = 1;

        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("State: "), gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 1;
        add(licenseState, gbc);


        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("License Id: "), gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 1;
        add(licenseId, gbc);

        // User label and value
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Licensed to: "), gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 1;
        add(licenseUser, gbc);

        // Expiration label and value
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridx = 0;
        gbc.gridy = 4;
        add(new JLabel("Expiration: "), gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 1;
        add(licenseExpiration, gbc);

        // Features label and value
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridx = 0;
        gbc.gridy = 5;
        add(new JLabel("Active Features: "), gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 1;
        add(licenseFeatures, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton renewButton = new JButton("Renew License");
        JButton releaseButton = new JButton("Release License");

        // Add actions to buttons
        buttonPanel.add(releaseButton);
        buttonPanel.add(renewButton);

        renewButton.addActionListener(e -> {
            try {
                System.out.println("Renewing license...");
                LicenseChecker.reactivateLicense(NeptusLicense.activationDays);
                setLicense(LicenseChecker.getMainLicense(), LicenseChecker.getLicenseActivation(true, true));
            }
            catch (LicenseException.LicenseExpired ex) {
                JOptionPane.showMessageDialog(this, "License has expired", "License Expired", JOptionPane.ERROR_MESSAGE);
                revokeLicense();
            }
            catch (LicenseException.NoAvailableSeatsException ex) {
                JOptionPane.showMessageDialog(this, "No available seats", "No Available Seats", JOptionPane.ERROR_MESSAGE);
            }
            catch (LicenseException.RunningInVMException ex) {
                JOptionPane.showMessageDialog(this, "Cannot run Neptus from inside a VM", "Running in VM", JOptionPane.ERROR_MESSAGE);
            }
            catch (LicenseException.LicenseNotFoundException ex) {
                JOptionPane.showMessageDialog(this, "License not found", "License Not Found", JOptionPane.ERROR_MESSAGE);
                revokeLicense();
            }
            catch (LicenseException.LicenseServerUnreachable ex) {
                JOptionPane.showMessageDialog(this, "License server unreachable", "License Server Unreachable", JOptionPane.ERROR_MESSAGE);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        releaseButton.addActionListener(e -> {
            try {
                LicenseChecker.releaseLicense();
                setLicense(LicenseChecker.getMainLicense(), null);

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.anchor = GridBagConstraints.CENTER;
        add(buttonPanel, gbc);
    }

    public static void main(String[] args) {

        GuiUtils.setLookAndFeel();
        // Create the main frame
        JFrame frame = new JFrame("Software License");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setResizable(false);
        LicensePanel panel = new LicensePanel();

        try {
            License mainLicense = LicenseChecker.getMainLicense();
            if (mainLicense != null) {
                License activation = LicenseChecker.getLicenseActivation();
                panel.setLicense(mainLicense, activation);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }


        // Add panel to frame
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null); // Center the window
        frame.setVisible(true);
    }
}
