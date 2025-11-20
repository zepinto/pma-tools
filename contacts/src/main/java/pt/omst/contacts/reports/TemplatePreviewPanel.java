//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.reports;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xhtmlrenderer.extend.NamespaceHandler;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.simple.xhtml.XhtmlNamespaceHandler;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Panel for previewing report templates with sample data.
 */
@Slf4j
public class TemplatePreviewPanel extends JPanel {
    
    private final XHTMLPanel xhtmlPanel;
    private final Map<File, String> htmlCache;
    private final String sampleImage1Base64;
    private final String sampleImage2Base64;
    
    public TemplatePreviewPanel() {
        setLayout(new BorderLayout());
        
        // Create XHTML panel for rendering
        xhtmlPanel = new XHTMLPanel();
        JScrollPane scrollPane = new JScrollPane(xhtmlPanel);
        add(scrollPane, BorderLayout.CENTER);
        
        // Initialize cache
        htmlCache = new HashMap<>();
        
        // Load sample images from resources
        sampleImage1Base64 = loadSampleImage("/samples/contact_thumb1.png");
        sampleImage2Base64 = loadSampleImage("/samples/contact_thumb2.png");
        
        log.info("TemplatePreviewPanel initialized");
    }
    
    /**
     * Update preview with the selected template file.
     * Uses cached HTML if available.
     */
    public void updatePreview(File templateFile) {
        if (templateFile == null || !templateFile.exists()) {
            log.warn("Template file does not exist: {}", templateFile);
            return;
        }
        
        try {
            // Check cache first
            String cachedHtml = htmlCache.get(templateFile);
            if (cachedHtml != null) {
                log.debug("Using cached HTML for template: {}", templateFile.getName());
                NamespaceHandler nsh = new XhtmlNamespaceHandler();
                xhtmlPanel.setDocumentFromString(cachedHtml, templateFile.toURI().toString(), nsh);
                return;
            }
            
            // Generate HTML from template
            String html = renderTemplate(templateFile);
            
            // Cache the result
            htmlCache.put(templateFile, html);
            
            // Display in XHTML panel
            NamespaceHandler nsh = new XhtmlNamespaceHandler();
            xhtmlPanel.setDocumentFromString(html, templateFile.toURI().toString(), nsh);
            
            log.info("Preview updated for template: {}", templateFile.getName());
            
        } catch (Exception e) {
            log.error("Error updating preview for template: " + templateFile.getName(), e);
            NamespaceHandler nsh = new XhtmlNamespaceHandler();
            xhtmlPanel.setDocumentFromString(
                "<html><body><h2>Error loading template</h2><p>" + e.getMessage() + "</p></body></html>",
                "about:blank", nsh);
        }
    }
    
    /**
     * Clear the HTML cache.
     */
    public void clearCache() {
        htmlCache.clear();
        log.debug("HTML cache cleared");
    }
    
    /**
     * Render a template with mock data.
     */
    private String renderTemplate(File templateFile) throws Exception {
        // Create mock report data
        ContactReportGenerator.ReportData mockData = createMockData();
        
        // Compile template
        MustacheFactory mf = new DefaultMustacheFactory();
        Reader templateReader = new FileReader(templateFile);
        Mustache mustache = mf.compile(templateReader, templateFile.getName());
        
        // Execute template
        StringWriter writer = new StringWriter();
        mustache.execute(writer, mockData).flush();
        String rawHtml = writer.toString();
        
        // Clean HTML with Jsoup (convert to XHTML)
        Document document = Jsoup.parse(rawHtml);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        String xhtml = document.html();
        
        return xhtml;
    }
    
    /**
     * Create mock report data with sample contacts.
     */
    private ContactReportGenerator.ReportData createMockData() {
        List<SidescanContact> contacts = new ArrayList<>();
        
        // Contact 1
        SidescanContact c1 = new SidescanContact("C-001", 41.1850, -8.7032, "Rock", "", null);
        c1.setDims(1.5, 2.3, 12.5);
        c1.setBase64Image(sampleImage1Base64);
        contacts.add(c1);
        
        // Contact 2
        SidescanContact c2 = new SidescanContact("C-002", 41.1855, -8.7028, "Wreck", "", null);
        c2.setDims(3.2, 5.8, 15.2);
        c2.setBase64Image(sampleImage2Base64);
        contacts.add(c2);
        
        return new ContactReportGenerator.ReportData(
            "Sample Report",
            "Demo Mission",
            contacts
        );
    }
    
    /**
     * Load a sample image from resources and convert to Base64.
     */
    private String loadSampleImage(String resourcePath) {
        try {
            InputStream imageStream = getClass().getResourceAsStream(resourcePath);
            if (imageStream == null) {
                log.warn("Sample image not found: {}", resourcePath);
                return "";
            }
            
            BufferedImage image = ImageIO.read(imageStream);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            
            return Base64.getEncoder().encodeToString(imageBytes);
            
        } catch (Exception e) {
            log.error("Error loading sample image: " + resourcePath, e);
            return "";
        }
    }
}
