package pt.omst.contacts.reports;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
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
        MustacheFactory mf = new DefaultMustacheFactory();
        InputStream templateStream = ContactReportGenerator.class.getResourceAsStream("/templates/report-template.html");
        if (templateStream == null) {
            throw new IllegalArgumentException("Template not found in resources: " + "/templates/report-template.html");
        }
        Reader templateReader = new InputStreamReader(templateStream);
    }

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