//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.help;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Utility class for exporting the user manual to PDF format.
 * Uses Flying Saucer (xhtmlrenderer) for PDF generation.
 */
@Slf4j
public class ManualPdfExporter {

    /**
     * Exports HTML content to a PDF file.
     *
     * @param htmlContent The HTML content to export
     * @param outputFile  The output PDF file
     * @throws Exception if PDF generation fails
     */
    public static void exportToPdf(String htmlContent, File outputFile) throws Exception {
        log.info("Exporting manual to PDF: {}", outputFile.getAbsolutePath());

        // Clean and parse HTML with Jsoup (crucial for Flying Saucer)
        Document document = Jsoup.parse(htmlContent);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        String xhtml = document.html();

        // Enhance styling for PDF output
        String styledXhtml = enhancePdfStyling(xhtml);

        // Render PDF using Flying Saucer
        try (OutputStream os = new FileOutputStream(outputFile)) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(styledXhtml);
            renderer.layout();
            renderer.createPDF(os);
        }

        log.info("PDF created successfully: {}", outputFile.getAbsolutePath());
    }

    /**
     * Enhances HTML styling specifically for PDF rendering.
     * Adds page breaks, headers, footers, and print-specific CSS.
     */
    private static String enhancePdfStyling(String xhtml) {
        // Add PDF-specific CSS
        String pdfCss = "<style type=\"text/css\">\n" +
                "@page {\n" +
                "    size: A4;\n" +
                "    margin: 2cm 2cm 3cm 2cm;\n" +
                "    @bottom-center {\n" +
                "        content: \"TargetManager User Manual - Page \" counter(page);\n" +
                "        font-size: 9pt;\n" +
                "        color: #666;\n" +
                "    }\n" +
                "}\n" +
                "body {\n" +
                "    font-family: 'DejaVu Sans', 'Helvetica', 'Arial', sans-serif;\n" +
                "    font-size: 11pt;\n" +
                "}\n" +
                "h1 {\n" +
                "    page-break-before: always;\n" +
                "    margin-top: 0;\n" +
                "}\n" +
                "h1:first-of-type {\n" +
                "    page-break-before: avoid;\n" +
                "}\n" +
                "h2 {\n" +
                "    page-break-after: avoid;\n" +
                "}\n" +
                "table {\n" +
                "    page-break-inside: avoid;\n" +
                "}\n" +
                "img {\n" +
                "    page-break-inside: avoid;\n" +
                "    max-width: 100%;\n" +
                "}\n" +
                "pre, code {\n" +
                "    page-break-inside: avoid;\n" +
                "    font-family: 'DejaVu Sans Mono', 'Courier New', monospace;\n" +
                "}\n" +
                ".toc {\n" +
                "    page-break-after: always;\n" +
                "}\n" +
                "</style>\n";

        // Insert CSS into head section
        xhtml = xhtml.replace("</head>", pdfCss + "</head>");

        return xhtml;
    }

    /**
     * Exports Markdown content directly to PDF (convenience method).
     *
     * @param markdownContent The Markdown content
     * @param outputFile      The output PDF file
     * @throws Exception if conversion or PDF generation fails
     */
    public static void exportMarkdownToPdf(String markdownContent, File outputFile) throws Exception {
        // Convert Markdown to HTML
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().build();
        org.commonmark.node.Node document = parser.parse(markdownContent);
        org.commonmark.renderer.html.HtmlRenderer renderer = org.commonmark.renderer.html.HtmlRenderer.builder()
                .build();
        String html = renderer.render(document);

        // Wrap in complete HTML document
        String completeHtml = wrapHtmlDocument(html);

        // Export to PDF
        exportToPdf(completeHtml, outputFile);
    }

    /**
     * Wraps raw HTML in a complete HTML document structure.
     */
    private static String wrapHtmlDocument(String bodyContent) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\"/>\n" +
                "<title>TargetManager User Manual</title>\n" +
                "</head>\n" +
                "<body>\n" +
                bodyContent + "\n" +
                "</body>\n" +
                "</html>";
    }
}
