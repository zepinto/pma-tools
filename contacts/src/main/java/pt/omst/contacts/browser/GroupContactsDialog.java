//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterlib.contacts.CompressedContact;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for grouping multiple contacts into a single contact.
 * Allows user to select which contacts to merge and which one should be the main contact.
 */
@Slf4j
public class GroupContactsDialog extends JDialog {
    
    private final List<CompressedContact> contacts;
    private final JList<ContactItem> contactList;
    private final DefaultListModel<ContactItem> listModel;
    private boolean confirmed = false;
    
    private static class ContactItem {
        final CompressedContact contact;
        boolean includeInMerge;
        boolean isMainContact;
        
        ContactItem(CompressedContact contact, boolean includeInMerge, boolean isMainContact) {
            this.contact = contact;
            this.includeInMerge = includeInMerge;
            this.isMainContact = isMainContact;
        }
    }
    
    private class ContactListCellRenderer extends JPanel implements ListCellRenderer<ContactItem> {
        private final JCheckBox includeCheckBox;
        private final JRadioButton mainRadioButton;
        private final JLabel labelField;
        private final JLabel coordsField;
        private final JLabel depthField;
        private final JLabel obsCountField;
        
        ContactListCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(new EmptyBorder(5, 5, 5, 5));
            
            // Left side: checkboxes and radio buttons
            JPanel controlsPanel = new JPanel();
            controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.X_AXIS));
            includeCheckBox = new JCheckBox();
            mainRadioButton = new JRadioButton();
            controlsPanel.add(includeCheckBox);
            controlsPanel.add(Box.createHorizontalStrut(5));
            controlsPanel.add(mainRadioButton);
            controlsPanel.setOpaque(false);
            
            // Right side: contact info
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setOpaque(false);
            
            labelField = new JLabel();
            labelField.setFont(labelField.getFont().deriveFont(Font.BOLD));
            coordsField = new JLabel();
            coordsField.setFont(coordsField.getFont().deriveFont(Font.PLAIN, 10f));
            depthField = new JLabel();
            depthField.setFont(depthField.getFont().deriveFont(Font.PLAIN, 10f));
            obsCountField = new JLabel();
            obsCountField.setFont(obsCountField.getFont().deriveFont(Font.PLAIN, 10f));
            
            infoPanel.add(labelField);
            infoPanel.add(coordsField);
            infoPanel.add(depthField);
            infoPanel.add(obsCountField);
            
            add(controlsPanel, BorderLayout.WEST);
            add(infoPanel, BorderLayout.CENTER);
        }
        
        @Override
        public Component getListCellRendererComponent(JList<? extends ContactItem> list, ContactItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            includeCheckBox.setSelected(value.includeInMerge);
            mainRadioButton.setSelected(value.isMainContact);
            
            CompressedContact contact = value.contact;
            labelField.setText(contact.getContact().getLabel());
            coordsField.setText(String.format("%.6f°, %.6f°", 
                contact.getContact().getLatitude(), 
                contact.getContact().getLongitude()));
            depthField.setText(String.format("Depth: %.1f m", contact.getContact().getDepth()));
            
            int obsCount = contact.getContact().getObservations() != null ? 
                contact.getContact().getObservations().size() : 0;
            obsCountField.setText(String.format("%d observation%s", obsCount, obsCount != 1 ? "s" : ""));
            
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setOpaque(isSelected);
            
            return this;
        }
    }
    
    public GroupContactsDialog(Window owner, List<CompressedContact> contacts) {
        super(owner, "Group Contacts", ModalityType.APPLICATION_MODAL);
        this.contacts = new ArrayList<>(contacts);
        
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Create header label
        JLabel headerLabel = new JLabel("Select contacts to merge and choose main contact:");
        headerLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(headerLabel, BorderLayout.NORTH);
        
        // Create list model and populate
        listModel = new DefaultListModel<>();
        for (int i = 0; i < this.contacts.size(); i++) {
            listModel.addElement(new ContactItem(this.contacts.get(i), true, i == 0));
        }
        
        // Create list with custom renderer
        contactList = new JList<>(listModel);
        contactList.setCellRenderer(new ContactListCellRenderer());
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Add mouse listener for checkbox and radio button clicks
        contactList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = contactList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    Rectangle cellBounds = contactList.getCellBounds(index, index);
                    if (cellBounds.contains(e.getPoint())) {
                        ContactItem item = listModel.getElementAt(index);
                        
                        // Determine which control was clicked based on x position
                        int relativeX = e.getX() - cellBounds.x;
                        if (relativeX < 30) { // Checkbox area
                            item.includeInMerge = !item.includeInMerge;
                            // If unchecking and it was main, make first checked item main
                            if (!item.includeInMerge && item.isMainContact) {
                                item.isMainContact = false;
                                for (int i = 0; i < listModel.size(); i++) {
                                    ContactItem ci = listModel.getElementAt(i);
                                    if (ci.includeInMerge) {
                                        ci.isMainContact = true;
                                        break;
                                    }
                                }
                            }
                            contactList.repaint();
                        } else if (relativeX >= 30 && relativeX < 60) { // Radio button area
                            if (item.includeInMerge) {
                                // Uncheck all others
                                for (int i = 0; i < listModel.size(); i++) {
                                    listModel.getElementAt(i).isMainContact = false;
                                }
                                item.isMainContact = true;
                                contactList.repaint();
                            }
                        }
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(contactList);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        add(scrollPane, BorderLayout.CENTER);
        
        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton groupButton = new JButton("Group Contacts");
        JButton cancelButton = new JButton("Cancel");
        
        groupButton.addActionListener(e -> {
            if (getContactsToMerge().isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "Please select at least one contact to merge.", 
                    "No Contacts Selected", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });
        
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        buttonPanel.add(groupButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(owner);
    }
    
    /**
     * Returns true if user confirmed the grouping operation.
     */
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Returns the contact selected as the main contact.
     */
    public CompressedContact getMainContact() {
        for (int i = 0; i < listModel.size(); i++) {
            ContactItem item = listModel.getElementAt(i);
            if (item.isMainContact && item.includeInMerge) {
                return item.contact;
            }
        }
        return null;
    }
    
    /**
     * Returns the list of contacts to merge (excluding the main contact).
     */
    public List<CompressedContact> getContactsToMerge() {
        List<CompressedContact> result = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            ContactItem item = listModel.getElementAt(i);
            if (item.includeInMerge && !item.isMainContact) {
                result.add(item.contact);
            }
        }
        return result;
    }
    
    /**
     * Test main method for dialog development.
     */
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        // Create some dummy contacts for testing
        List<CompressedContact> testContacts = new ArrayList<>();
        // Note: This is just for UI testing, actual contacts would come from real data
        
        SwingUtilities.invokeLater(() -> {
            GroupContactsDialog dialog = new GroupContactsDialog(null, testContacts);
            dialog.setVisible(true);
            
            if (dialog.isConfirmed()) {
                log.info("Main contact: {}", dialog.getMainContact());
                log.info("Contacts to merge: {}", dialog.getContactsToMerge().size());
            } else {
                log.info("Operation cancelled");
            }
            System.exit(0);
        });
    }
}
