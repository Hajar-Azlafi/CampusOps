package com.campusops.dashboard.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serialise un {@link ReportData} au format PDF via OpenPDF.
 */
@Component
public class PdfReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color HEADER_BG = new Color(30, 58, 95);
    private static final Color ROW_ALT = new Color(240, 244, 250);

    public byte[] write(ReportData data) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 42, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(20, 30, 48));
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.DARK_GRAY);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

            Paragraph title = new Paragraph(data.getTitre(), titleFont);
            title.setSpacingAfter(4f);
            document.add(title);

            if (data.getSousTitre() != null && !data.getSousTitre().isBlank()) {
                Paragraph sub = new Paragraph(data.getSousTitre(), subFont);
                sub.setSpacingAfter(2f);
                document.add(sub);
            }

            Paragraph meta = new Paragraph("Généré le " + data.getGenereLe().format(TS), metaFont);
            meta.setSpacingAfter(12f);
            document.add(meta);

            if (!data.getEntetes().isEmpty()) {
                document.add(buildTable(data));
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur lors de la generation du fichier PDF", e);
        }
    }

    private PdfPTable buildTable(ReportData data) {
        PdfPTable table = new PdfPTable(data.getEntetes().size());
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        for (String entete : data.getEntetes()) {
            PdfPCell cell = new PdfPCell(new Phrase(entete, headerFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6f);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        table.setHeaderRows(1);

        boolean alt = false;
        for (List<String> ligne : data.getLignes()) {
            for (String valeur : ligne) {
                PdfPCell cell = new PdfPCell(new Phrase(valeur != null ? valeur : "", bodyFont));
                cell.setPadding(5f);
                cell.setBorderColor(new Color(210, 216, 224));
                if (alt) {
                    cell.setBackgroundColor(ROW_ALT);
                }
                table.addCell(cell);
            }
            alt = !alt;
        }
        return table;
    }
}
