package com.campusops.dashboard.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serialise un {@link ReportData} au format Excel (.xlsx) via Apache POI.
 */
@Component
public class ExcelReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] write(ReportData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rapport");
            int colCount = Math.max(1, data.getEntetes().size());

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle subtitleStyle = subtitleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            int rowIdx = 0;

            // Titre
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.getTitre());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));

            // Sous-titre
            if (data.getSousTitre() != null && !data.getSousTitre().isBlank()) {
                Row subRow = sheet.createRow(rowIdx++);
                Cell subCell = subRow.createCell(0);
                subCell.setCellValue(data.getSousTitre());
                subCell.setCellStyle(subtitleStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));
            }

            // Date de generation
            Row genRow = sheet.createRow(rowIdx++);
            Cell genCell = genRow.createCell(0);
            genCell.setCellValue("Généré le " + data.getGenereLe().format(TS));
            genCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            rowIdx++; // ligne vide

            // En-tetes
            Row headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < data.getEntetes().size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(data.getEntetes().get(c));
                cell.setCellStyle(headerStyle);
            }

            // Corps
            for (List<String> ligne : data.getLignes()) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < ligne.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(ligne.get(c) != null ? ligne.get(c) : "");
                    cell.setCellStyle(bodyStyle);
                }
            }

            for (int c = 0; c < colCount; c++) {
                sheet.autoSizeColumn(c);
                int current = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(current + 512, 15000));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la generation du fichier Excel", e);
        }
    }

    private CellStyle titleStyle(XSSFWorkbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle subtitleStyle(XSSFWorkbook wb) {
        Font font = wb.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle bodyStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
