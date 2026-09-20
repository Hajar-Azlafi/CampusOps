package com.campusops.dashboard.controller;

import com.campusops.dashboard.dto.ReportDescriptorDto;
import com.campusops.dashboard.service.DashboardReportService;
import com.campusops.dashboard.service.DashboardReportService.ReportFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Points d'entree REST pour le catalogue et l'export des rapports. La generation
 * des fichiers est entierement deleguee au service ; le controleur assemble
 * uniquement la reponse HTTP (entetes de telechargement, type de contenu).
 */
@RestController
@RequestMapping("/api/dashboard/reports")
@RequiredArgsConstructor
public class DashboardReportController {

    private static final MediaType EXCEL_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardReportService dashboardReportService;

    @GetMapping
    public ResponseEntity<List<ReportDescriptorDto>> getAvailableReports() {
        return ResponseEntity.ok(dashboardReportService.getAvailableReports());
    }

    @GetMapping("/{code}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestParam(required = false) Long academicYearId) {
        ReportFormat reportFormat = dashboardReportService.parseFormat(format);
        byte[] content = dashboardReportService.export(code, reportFormat, academicYearId);
        String fileName = dashboardReportService.fileName(code, reportFormat);
        MediaType mediaType = reportFormat == ReportFormat.PDF
                ? MediaType.APPLICATION_PDF
                : EXCEL_MEDIA_TYPE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(content);
    }
}
