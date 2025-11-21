//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.help;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Dialog for displaying the user manual with Markdown rendering and PDF export
 * capability.
 */
@Slf4j
public class ManualViewerDialog extends JDialog {

    private final JEditorPane editorPane;
    private String markdownContent;
    private String htmlContent;

    public ManualViewerDialog(JFrame parent) {
        super(parent, "TargetManager User Manual", false);

        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(1000, 700));

        // Create editor pane for HTML display
        editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.setBackground(Color.WHITE);

        // Add hyperlink listener for internal navigation
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if (e.getDescription().startsWith("#")) {
                    // Internal anchor link
                    editorPane.scrollToReference(e.getDescription().substring(1));
                } else if (e.getURL() != null) {
                    // External link
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception ex) {
                        log.error("Failed to open link: {}", e.getURL(), ex);
                    }
                }
            }
        });

        // Scroll pane for the manual content
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton exportButton = new JButton("Export to PDF");
        exportButton.addActionListener(e -> exportToPdf());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(exportButton);
        buttonPanel.add(closeButton);

        // Add components to dialog
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Load and display the manual
        loadManual();

        // Set up keyboard shortcuts
        setupKeyBindings();

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Loads the manual from resources and renders it as HTML.
     */
    private void loadManual() {
        try {
            // Load Markdown content from resources
            markdownContent = loadResourceAsString("/docs/user-manual.md");

            // Convert Markdown to HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdownContent);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String rawHtml = renderer.render(document);

            // Wrap in styled HTML document
            htmlContent = wrapInStyledHtml(rawHtml);

            // Display in editor pane
            editorPane.setText(htmlContent);
            editorPane.setCaretPosition(0); // Scroll to top

        } catch (IOException e) {
            log.error("Failed to load user manual", e);
            editorPane.setText("<html><body><h1>Error</h1><p>Failed to load user manual: "
                    + e.getMessage() + "</p></body></html>");
        }
    }

    /**
     * Loads a resource file as a string.
     */
    private String loadResourceAsString(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Wraps the raw HTML in a styled HTML document with CSS.
     */
    private String wrapInStyledHtml(String bodyHtml) {
        // Process image paths to use absolute resource URLs
        String processedHtml = processImagePaths(bodyHtml);

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<style>\n" +
                "body {\n" +
                "    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                "    line-height: 1.6;\n" +
                "    max-width: 900px;\n" +
                "    margin: 0 auto;\n" +
                "    padding: 20px;\n" +
                "    color: #333;\n" +
                "    background-color: #fff;\n" +
                "}\n" +
                "h1, h2, h3, h4, h5, h6 {\n" +
                "    color: #2c3e50;\n" +
                "    margin-top: 24px;\n" +
                "    margin-bottom: 16px;\n" +
                "    font-weight: 600;\n" +
                "}\n" +
                "h1 {\n" +
                "    font-size: 2em;\n" +
                "    border-bottom: 2px solid #3498db;\n" +
                "    padding-bottom: 10px;\n" +
                "}\n" +
                "h2 {\n" +
                "    font-size: 1.5em;\n" +
                "    border-bottom: 1px solid #bdc3c7;\n" +
                "    padding-bottom: 8px;\n" +
                "}\n" +
                "h3 {\n" +
                "    font-size: 1.25em;\n" +
                "}\n" +
                "code {\n" +
                "    background-color: #f4f4f4;\n" +
                "    padding: 2px 6px;\n" +
                "    border-radius: 3px;\n" +
                "    font-family: 'Courier New', monospace;\n" +
                "    font-size: 0.9em;\n" +
                "}\n" +
                "pre {\n" +
                "    background-color: #f4f4f4;\n" +
                "    padding: 16px;\n" +
                "    border-radius: 5px;\n" +
                "    border-left: 4px solid #3498db;\n" +
                "    overflow-x: auto;\n" +
                "}\n" +
                "pre code {\n" +
                "    background-color: transparent;\n" +
                "    padding: 0;\n" +
                "}\n" +
                "table {\n" +
                "    border-collapse: collapse;\n" +
                "    width: 100%;\n" +
                "    margin: 20px 0;\n" +
                "}\n" +
                "th, td {\n" +
                "    border: 1px solid #ddd;\n" +
                "    padding: 12px;\n" +
                "    text-align: left;\n" +
                "}\n" +
                "th {\n" +
                "    background-color: #3498db;\n" +
                "    color: white;\n" +
                "    font-weight: 600;\n" +
                "}\n" +
                "tr:nth-child(even) {\n" +
                "    background-color: #f9f9f9;\n" +
                "}\n" +
                "a {\n" +
                "    color: #3498db;\n" +
                "    text-decoration: none;\n" +
                "}\n" +
                "a:hover {\n" +
                "    text-decoration: underline;\n" +
                "}\n" +
                "img {\n" +
                "    max-width: 100%;\n" +
                "    height: auto;\n" +
                "    display: block;\n" +
                "    margin: 20px 0;\n" +
                "    border: 1px solid #ddd;\n" +
                "    border-radius: 5px;\n" +
                "}\n" +
                "blockquote {\n" +
                "    border-left: 4px solid #3498db;\n" +
                "    padding-left: 16px;\n" +
                "    margin-left: 0;\n" +
                "    color: #555;\n" +
                "    font-style: italic;\n" +
                "}\n" +
                "ul, ol {\n" +
                "    padding-left: 30px;\n" +
                "}\n" +
                "li {\n" +
                "    margin: 8px 0;\n" +
                "}\n" +
                "hr {\n" +
                "    border: none;\n" +
                "    border-top: 2px solid #ecf0f1;\n" +
                "    margin: 30px 0;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                processedHtml + "\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * Processes image paths to convert relative paths to resource URLs.
     */
    private String processImagePaths(String html) {
        // Replace relative image paths with resource URLs
        return html.replaceAll(
                "src=\"images/([^\"]+)\"",
                "src=\"" + getClass().getResource("/docs/images/$1") + "\"");
    }

    /**
     * Sets up keyboard shortcuts for the dialog.
     */
    private void setupKeyBindings() {
        JRootPane rootPane = getRootPane();

        // ESC to close
        rootPane.registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Exports the manual to PDF.
     */
    private void exportToPdf() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Manual to PDF");
        fileChooser.setSelectedFile(new java.io.File("TargetManager_Manual.pdf"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File outputFile = fileChooser.getSelectedFile();

            // Ensure .pdf extension
            if (!outputFile.getName().toLowerCase().endsWith(".pdf")) {
                outputFile = new java.io.File(outputFile.getAbsolutePath() + ".pdf");
            }

            try {
                ManualPdfExporter.exportToPdf(htmlContent, outputFile);

                int openResult = JOptionPane.showConfirmDialog(
                        this,
                        "PDF exported successfully to:\n" + outputFile.getAbsolutePath() + "\n\nOpen the PDF?",
                        "Export Successful",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE);

                if (openResult == JOptionPane.YES_OPTION) {
                    Desktop.getDesktop().open(outputFile);
                }
            } catch (Exception e) {
                log.error("Failed to export manual to PDF", e);
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to export PDF: " + e.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Shows the manual viewer dialog.
     */
    public static void showManual(JFrame parent) {
        SwingUtilities.invokeLater(() -> {
            ManualViewerDialog dialog = new ManualViewerDialog(parent);
            dialog.setVisible(true);
        });
    }
}
