package pt.omst.contacts.reports;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import pt.omst.rasterlib.contacts.CompressedContact;

public class ContactReportGenerator {

    // The Data Container for the Template
    static class ReportData {
        String reportTitle;
        String missionName;
        String generationDate;
        List<SidescanContact> contacts;

        public ReportData(String title, String mission, List<SidescanContact> contacts) {
            this.reportTitle = title;
            this.missionName = mission;
            this.contacts = contacts;
            this.generationDate = LocalDate.now().toString();
        }

        // Getters needed for Mustache
        public String getReportTitle() {
            return reportTitle;
        }

        public String getMissionName() {
            return missionName;
        }

        public String getGenerationDate() {
            return generationDate;
        }

        public List<SidescanContact> getContacts() {
            return contacts;
        }
    }

    public static void generateReport(List<CompressedContact> contacts) {
        // Legacy method - kept for backwards compatibility
        // Use generateReport(File, String, ReportData) or generateReport(String, String, ReportData) instead
        throw new UnsupportedOperationException("This method is deprecated. Use the instance methods instead.");
    }

    /**
     * Generate report using a template file from filesystem.
     */
    public void generateReport(File templateFile, String outputPdfPath, ReportData data) throws Exception {
        if (templateFile == null || !templateFile.exists()) {
            throw new IllegalArgumentException("Template file does not exist: " + templateFile);
        }
        
        // 1. Compile the Template
        MustacheFactory mf = new DefaultMustacheFactory();
        Reader templateReader = new FileReader(templateFile);
        Mustache mustache = mf.compile(templateReader, templateFile.getName());

        // 2. Execute Template (Render HTML)
        StringWriter writer = new StringWriter();
        mustache.execute(writer, data).flush();
        String rawHtml = writer.toString();

        // 3. Clean HTML with Jsoup (Crucial for PDF Renderer)
        Document document = Jsoup.parse(rawHtml);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml); // Enforce XHTML
        String xhtml = document.html();

        // 4. Render PDF
        try (OutputStream os = new FileOutputStream(outputPdfPath)) {
            ITextRenderer renderer = new ITextRenderer();
            // Adjust for High DPI images if necessary
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(os);
        }

        System.out.println("PDF Created: " + outputPdfPath);
    }

    /**
     * Generate report using a template path from classpath resources.
     * Kept for backwards compatibility.
     */
    public void generateReport(String templatePath, String outputPdfPath, ReportData data) throws Exception {

        // 1. Compile the Template
        MustacheFactory mf = new DefaultMustacheFactory();
        // Load template from resources
        InputStream templateStream = getClass().getResourceAsStream(templatePath);
        if (templateStream == null) {
            throw new IllegalArgumentException("Template not found in resources: " + templatePath);
        }
        Reader templateReader = new InputStreamReader(templateStream);
        Mustache mustache = mf.compile(templateReader, templatePath);

        // 2. Execute Template (Render HTML)
        StringWriter writer = new StringWriter();
        mustache.execute(writer, data).flush();
        String rawHtml = writer.toString();

        // 3. Clean HTML with Jsoup (Crucial for PDF Renderer)
        Document document = Jsoup.parse(rawHtml);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml); // Enforce XHTML
        String xhtml = document.html();

        // 4. Render PDF
        try (OutputStream os = new FileOutputStream(outputPdfPath)) {
            ITextRenderer renderer = new ITextRenderer();
            // Adjust for High DPI images if necessary
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(os);
        }

        System.out.println("PDF Created: " + outputPdfPath);
    }

    /**
     * Extract default templates from resources to conf/templates/ directory.
     * Creates directory if it doesn't exist.
     */
    public static void extractDefaultTemplates() throws Exception {
        File templatesDir = new File("conf/templates");
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }
        
        String[] templates = {"report-template-standard.html", "report-template-compact.html"};
        
        for (String templateName : templates) {
            File targetFile = new File(templatesDir, templateName);
            if (!targetFile.exists()) {
                InputStream is = ContactReportGenerator.class.getResourceAsStream("/templates/" + templateName);
                if (is != null) {
                    Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Extracted template: " + templateName);
                }
            }
        }
    }

    // Helper to convert file path to Base64 for the model
    public static String imagePathToBase64(String path) {
        try {
            File f = new File(path);
            if (!f.exists())
                return ""; // or return a placeholder base64
            BufferedImage img = ImageIO.read(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void main(String[] args) throws Exception {
        // --- Mock Data Generation ---
        List<SidescanContact> contacts = new ArrayList<>();

        SidescanContact c1 = new SidescanContact("C-001", 41.15, -8.61, "Mine-like", "High", null);
        c1.setDims(1.2, 2.5, 5.0);

        c1.setBase64Image(
                "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==");

        SidescanContact c2 = new SidescanContact("C-002", 41.16, -8.62, "Rock", "Low", null);
        c2.setDims(0.5, 0.5, 5.2);
        c2.setBase64Image(
                "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==");

        contacts.add(c1);
        contacts.add(c2);

        ReportData data = new ReportData("Daily Sidescan Log", "Mission Alpha", contacts);
        // ----------------------------

        ContactReportGenerator generator = new ContactReportGenerator();

        // Template should be in src/main/resources/
        generator.generateReport("/templates/report-template.html", "ContactsReport.pdf", data);
    }
}