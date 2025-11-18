package pt.omst.pdf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

public class PdfReport {
    private String title = "Report";
    private String frontPageHtml = "";
    private String headerHtml = "";
    private String footerHtml = "";
    private final StringBuilder content = new StringBuilder();

    int sectionCounter = 1;

    public PdfReport setTitle(String title) {
        this.title = title;
        return this;
    }

    public PdfReport setFrontPage(String frontPageHtml) {
        this.frontPageHtml = frontPageHtml;
        return this;
    }

    public PdfReport setHeader(String headerHtml) {
        this.headerHtml = headerHtml;
        return this;
    }

    public PdfReport setFooter(String footerHtml) {
        this.footerHtml = footerHtml;
        return this;
    }

    public static String table(Object[][] data, String[] headers, String caption) {
        StringBuilder table = new StringBuilder();
        table.append("<table><tr>");
        for (String header : headers) {
            table.append("<th>").append(header).append("</th>");
        }
        table.append("</tr>");
        for (Object[] row : data) {
            table.append("<tr>");
            for (Object cell : row) {
                table.append("<td>").append(cell).append("</td>");
            }
            table.append("</tr>");
        }
        table.append("</table>");
        table.append("<p class='caption'>").append(caption).append("</p>");
        return table.toString();
    }

    public static String image(String imagePath, String caption) {
        return "<div style='text-align: center;'>"
                + "<img src='" + imagePath + "' style='max-width: 100%; height: auto;'> &nbsp; </img>"
                + "<p class='caption'>" + caption + "</p>"
                + "</div>";
    }

    public static String image(BufferedImage image, String caption) throws Exception {
        // Convert BufferedImage to Base64
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        // Embed image in HTML
        return "<div style='text-align: center;'>"
                + "<img src='data:image/png;base64," + base64Image + "' style='max-width: 100%; height: auto;' >&nbsp; </img>"
                + "<p class='caption'>" + caption + "</p>"
                + "</div>";
    }

    public static String paragraph(String text) {
        return "<p>" + text + "</p>";
    }

    public void addSection(String title, String... htmlParts) {
        StringBuilder section = new StringBuilder();
        section.append("<h2>")
                .append(sectionCounter)
                .append(" ")
                .append(title)
                .append("</h2>");
        sectionCounter++;
        for (String part : htmlParts) {
            section.append(part);
        }
        content.append(section);
    }

    public void addPageBreak() {
        content.append("<div style='page-break-after: always;'></div>");
    }

    public void exportToPdf(String outputPath, boolean open) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<html><head>")
                .append("<title>").append(title).append("</title>")
                .append(PdfReportConstants.pageCSS)
                .append("<header>").append(headerHtml).append("</header>")
                .append("<footer>").append(footerHtml).append("</footer>")
                .append("</head>")
                .append("<body>");

        if (!frontPageHtml.isEmpty()) {
            html.append("<div class='front-page'>").append(frontPageHtml).append("</div>");
        }
        html.append("<div style='page-break-after: always;'></div>");
        html.append(content.toString());
        html.append("</body>");
        html.append("</html>");

        Document document = Jsoup.parse(html.toString());
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        String xhtml = document.html();

        try (OutputStream os = new FileOutputStream(outputPath)) {
            ITextRenderer renderer = new ITextRenderer(4f, 3);
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(os);
        }

        if (open)
            Runtime.getRuntime().exec(new String[]{"xdg-open", new File(outputPath).getAbsolutePath()});
    }

    public static void main(String[] args) throws Exception {
        PdfReport report = new PdfReport()
                .setTitle("My Report")
                .setFrontPage("<h1>Endurance Test - lauv-arl-01</h1>"+
                        PdfReport.table(new Object[][]{{"Date", "2024-12-05"}, {"Duration", "1h 30m"}}, new String[]{"Parameter", "Value"}, "Test Information")
                )
                .setHeader("Header")
                .setFooter("Footer");
        report.exportToPdf(new File("report.pdf").getAbsolutePath(), true);
    }
}
