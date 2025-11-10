//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.awt.BorderLayout;
import java.io.File;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import pt.omst.neptus.util.GuiUtils;

public class MultiContactEditor extends JPanel{
    private ContactEditor contactEditor;
    private ContactCollection contacts;
    private int contactIndex = -1;
    private JButton previousButton;
    private JButton nextButton;
    public MultiContactEditor(ContactCollection contacts) {
        setLayout(new BorderLayout());
        this.contacts = contacts;
        contactEditor = new ContactEditor();
        try {
            contacts.getAllContacts().getFirst();
            contactIndex = 0;            
            contactEditor.loadZct(contacts.getAllContacts().get(contactIndex).getZctFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
        installNextPreviousButtons();
        add(contactEditor, BorderLayout.CENTER);
    }

    private void installNextPreviousButtons() {
        JPanel bottomPanel = new JPanel();
        JPanel buttonPanel = contactEditor.getButtonPanel();
        previousButton = new JButton("Previous");
        nextButton = new JButton("Next");
        previousButton.addActionListener(e -> {
            previousClicked();
        });
        nextButton.addActionListener(e -> {
            nextClicked();
        });
        
        contactEditor.remove(buttonPanel);        
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(previousButton, BorderLayout.WEST);
        bottomPanel.add(nextButton, BorderLayout.EAST);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        contactEditor.add(bottomPanel, BorderLayout.SOUTH);

        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < contacts.getAllContacts().size() - 1);
    }

    private void nextClicked() {
        List<CompressedContact> allContacts = contacts.getAllContacts();
        if (contactIndex < allContacts.size() - 1) {            
            try {
                contactEditor.loadZct(allContacts.get(contactIndex+1).getZctFile());
                contactIndex++;
            }
            catch (Exception e) {
                e.printStackTrace();
            }                        
        }        
        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < allContacts.size() - 1);  
    }

    private void previousClicked() {
        List<CompressedContact> allContacts = contacts.getAllContacts();
        if (contactIndex > 0) {            
            try {
                contactEditor.loadZct(allContacts.get(contactIndex-1).getZctFile());
                contactIndex--;
            }
            catch (Exception e) {
                e.printStackTrace();
            }                        
        }
        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < allContacts.size() - 1);  
    }

    public void addContact(CompressedContact contact) {
        
        String label = contact.getLabel();
        CompressedContact existing = contacts.getContact(label);
        if (existing != null) {
            return;
        }
        contactIndex = contacts.getAllContacts().size() - 1;
        try {
            contactEditor.loadZct(contact.getZctFile());
            contacts.addContact(contact.getZctFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < contacts.getAllContacts().size() - 1);
    }

    public void removeContact(String contactId) {
        int index = -1;
        for (int i = 0; i < contacts.getAllContacts().size(); i++) {
            CompressedContact contact = contacts.getAllContacts().get(i);
            if (contact.getLabel().equals(contactId)) {
                index = i;
                break;
            }
        }
        contacts.removeContact(index);
        List<CompressedContact> allContacts = contacts.getAllContacts();
        
        if (index == contactIndex) {
            if (contactIndex > 0) {
                contactIndex--;
            }
            if (contactIndex < allContacts.size()) {
                try {
                    contactEditor.loadZct(allContacts.get(contactIndex).getZctFile());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (index < contactIndex) {
            contactIndex--;
        }
        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < contacts.getAllContacts().size() - 1);
           
    }

    public void editContact(String contactId) {
        List<CompressedContact> allContacts = contacts.getAllContacts();
        for (int i = 0; i < allContacts.size(); i++) {
            CompressedContact contact = allContacts.get(i);
            if (contact.getLabel().equals(contactId)) {
                contactIndex = i;
                break;
            }
        }

        if (contactIndex != -1) {
            try {
                contactEditor.loadZct(allContacts.get(contactIndex).getZctFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        previousButton.setEnabled(contactIndex > 0);
        nextButton.setEnabled(contactIndex < contacts.getAllContacts().size() - 1);
    }

    public static void main(String[] args) throws Exception {
        GuiUtils.setLookAndFeel();
        ContactCollection contacts = new ContactCollection(new File("/LOGS"));        
        MultiContactEditor editor = new MultiContactEditor(contacts);
        GuiUtils.testFrame(editor, "Contact Editor", 800, 600);
    }
}
