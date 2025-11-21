//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;

import lombok.Getter;
import pt.lsts.neptus.util.I18n;

/**
 * Dialog for selecting a date and time using spinner components.
 * Allows user-friendly input of year, month, day, hour, minute, and second.
 */
public class DateTimeDialog extends JDialog {
    
    private final JSpinner dateSpinner;
    private final JSpinner hourSpinner;
    private final JSpinner minuteSpinner;
    private final JSpinner secondSpinner;
    
    @Getter
    private boolean confirmed = false;
    
    @Getter
    private Instant result = null;
    
    private final ZoneId zone = ZoneId.systemDefault();
    
    /**
     * Creates a new date/time selection dialog.
     * 
     * @param parent the parent window
     * @param initialTime the initial time to display
     */
    public DateTimeDialog(Window parent, Instant initialTime) {
        super(parent, I18n.textOrDefault("datetime.dialog.title", "Set Date & Time"), ModalityType.APPLICATION_MODAL);
        
        // Convert Instant to local date/time
        ZonedDateTime zdt = initialTime.atZone(zone);
        
        // Create form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Date spinner
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel(I18n.textOrDefault("datetime.dialog.date", "Date") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(Date.from(initialTime));
        dateSpinner = new JSpinner(new SpinnerDateModel(calendar.getTime(), null, null, Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        formPanel.add(dateSpinner, gbc);
        
        // Hour spinner
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(I18n.textOrDefault("datetime.dialog.hour", "Hour") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        hourSpinner = new JSpinner(new SpinnerNumberModel(zdt.getHour(), 0, 23, 1));
        formPanel.add(hourSpinner, gbc);
        
        // Minute spinner
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(I18n.textOrDefault("datetime.dialog.minute", "Minute") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        minuteSpinner = new JSpinner(new SpinnerNumberModel(zdt.getMinute(), 0, 59, 1));
        formPanel.add(minuteSpinner, gbc);
        
        // Second spinner
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel(I18n.textOrDefault("datetime.dialog.second", "Second") + ":"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        secondSpinner = new JSpinner(new SpinnerNumberModel(zdt.getSecond(), 0, 59, 1));
        formPanel.add(secondSpinner, gbc);
        
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
        
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void onOk() {
        try {
            // Get date from date spinner
            Date date = (Date) dateSpinner.getValue();
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            
            // Get time components
            int hour = (Integer) hourSpinner.getValue();
            int minute = (Integer) minuteSpinner.getValue();
            int second = (Integer) secondSpinner.getValue();
            
            // Combine into LocalDateTime
            LocalDateTime ldt = LocalDateTime.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, // Calendar months are 0-based
                cal.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                second
            );
            
            // Convert to Instant
            result = ldt.atZone(zone).toInstant();
            confirmed = true;
            dispose();
        } catch (Exception e) {
            // If any error occurs, just close without confirming
            confirmed = false;
            dispose();
        }
    }
    
    private void onCancel() {
        confirmed = false;
        result = null;
        dispose();
    }
    
    /**
     * Shows the dialog and returns the selected date/time.
     * 
     * @param parent the parent window
     * @param initialTime the initial time to display
     * @return the selected time, or null if cancelled
     */
    public static Instant showDialog(Window parent, Instant initialTime) {
        DateTimeDialog dialog = new DateTimeDialog(parent, initialTime);
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.result : null;
    }
}
