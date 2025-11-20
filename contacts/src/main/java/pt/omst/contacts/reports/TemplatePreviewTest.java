//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.reports;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import pt.lsts.neptus.util.GuiUtils;

/**
 * Test class to verify template preview functionality.
 */
public class TemplatePreviewTest {

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            try {
                // Extract default templates
                ContactReportGenerator.extractDefaultTemplates();
                
                // Create preview panel
                TemplatePreviewPanel previewPanel = new TemplatePreviewPanel();
                
                // Load a template
                File templateFile = new File("conf/templates/report-template-standard.html");
                if (templateFile.exists()) {
                    previewPanel.updatePreview(templateFile);
                }
                
                // Show in dialog
                JDialog dialog = new JDialog((JFrame)null, "Template Preview Test", false);
                dialog.setSize(900, 700);
                dialog.setLocationRelativeTo(null);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setContentPane(previewPanel);
                dialog.setVisible(true);
                
                // Test switching templates after 3 seconds
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        SwingUtilities.invokeLater(() -> {
                            File compactTemplate = new File("conf/templates/report-template-compact.html");
                            if (compactTemplate.exists()) {
                                System.out.println("Switching to compact template...");
                                previewPanel.updatePreview(compactTemplate);
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
