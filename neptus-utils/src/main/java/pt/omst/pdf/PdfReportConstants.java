package pt.omst.pdf;

public class PdfReportConstants {

    public final static String pageCSS = """
            <style>
                header {
                    position: running(header);
                    font-family: Times;
                    font-size: 12px;
                    color: gray;
                }
                footer {
                    position: running(footer);
                    font-family: Times;
                    font-size: 12px;
                    color: gray;
                }
                @page {
                    size: A4;
                    margin: 2cm;
                    @bottom-right {
                        content: counter(page) " / " counter(pages);
                        font-family: Arial, sans-serif;
                        font-size: 12px;
                        color: gray;
                    }
            
                    @top-right {
                        content: element(header);
                    }
            
                    @bottom-left {
                        content: element(footer);
                    }
                }
                p.caption {
                    font-size: 12px;
                    color: gray;
                    font-style: italic;
                }
            
                img.caption {
                    font-size: 12px;
                    color: gray;
                    font-style: italic;
                }
            
                img {
                    padding: 10px;
                    max-width: 70%;
                    height: auto;
                }
            
                h1 {
                    text-align: center; margin-top: 0.24in;
                    margin-bottom: 0.24in;
                    background: transparent;
                    page-break-before: auto;
                    page-break-after: avoid;
                    font-size: 32px;
                    font-family: Arial, sans-serif;
                    color: #33a;
                }
            
                h2 {
                    font-size: 20px;
                    font-family: Arial, sans-serif;
                    color: #336;
                }
            
                p {
                    font-size: 16px;
                    text-align: justify;
                    font-family: Arial, sans-serif;
                    color: #000;
                }
            
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin-top: 20px;
                }
            
                th, td {
                    border: 1px solid black;
                    padding: 8px;
                    text-align: left;
                }
            
                th {
                    background-color: #336;
                    color: #fff;
                    font-weight: bold;
                    font-family: Arial, sans-serif;
                    text-align: center;
                    font-weight: bold;
                }

                table tr:nth-child(even) {
                    background: #fff;
                }
            
               table tr:nth-child(odd) {
                    background: #eee;
               }
            
            

            </style>
            """;

    public static final String OceanScanHeader = """
            Polo do Mar do UPTEC, Avenida da Liberdade
            4450-718 Leça da Palmeira, Matosinhos, Portugal
            Phone: (+351) 22 030 1576
            www.oceanscan-mst.com | info@oceanscan-mst.com""";

    public static final String OceanScanFooter = """
            OceanScan – Marine Systems & Technology Lda.<br/>
            Mod. 0.048.00""";
}
