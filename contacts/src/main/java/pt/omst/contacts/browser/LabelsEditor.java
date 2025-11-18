//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.Folders;
import pt.omst.neptus.util.GuiUtils;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * Standalone application for editing labels CSV file.
 */
@Slf4j
public class LabelsEditor extends JFrame {
    
    private final CSVEntryEditorPanel editorPanel;
    
    public LabelsEditor() {
        super("Labels Editor");
        
        File csvFile = new File(Folders.getConfigFolder(), "labels.csv");
        editorPanel = new CSVEntryEditorPanel(csvFile);
        
        // Set callback after save (labels don't have a reload method in ItemList)
        editorPanel.setOnSaveCallback(() -> {
            log.info("Labels saved successfully");
        });
        
        setContentPane(editorPanel);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        
        setSize(900, 600);
        setLocationRelativeTo(null);
    }
    
    private void onClose() {
        // Could add unsaved changes check here if needed
        dispose();
    }
    
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            LabelsEditor editor = new LabelsEditor();
            editor.setVisible(true);
        });
    }
}
