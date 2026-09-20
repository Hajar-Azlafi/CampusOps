package com.campusops.schedule.controller;

import com.campusops.schedule.dto.ScheduleImportHistoryDto;
import com.campusops.schedule.dto.ScheduleImportResultDto;
import com.campusops.schedule.service.ScheduleExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/schedules/import")
@RequiredArgsConstructor
public class ScheduleImportController {

    private final ScheduleExcelImportService scheduleExcelImportService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ScheduleImportResultDto> importSchedules(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(scheduleExcelImportService.importSchedules(file));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] template = scheduleExcelImportService.generateTemplate();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=modele_import_emplois_du_temps.xlsx")
                .body(template);
    }

    @GetMapping("/history")
    public ResponseEntity<List<ScheduleImportHistoryDto>> getImportHistory() {
        return ResponseEntity.ok(scheduleExcelImportService.getImportHistory());
    }
}
