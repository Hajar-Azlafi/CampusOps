package com.campusops.exam.controller;

import com.campusops.exam.dto.ExamenRequestDto;
import com.campusops.exam.dto.ExamenResponseDto;
import com.campusops.exam.service.ExamenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API des examens datés (Lot 2 F2). La sécurité par périmètre (§12) est appliquée
 * dans le service via {@code AccessScopeService} — aucune règle n'est laissée au
 * seul frontend et il n'y a pas de {@code @PreAuthorize} ici.
 */
@RestController
@RequestMapping("/api/examens")
@RequiredArgsConstructor
public class ExamenController {

    private final ExamenService examenService;

    @PostMapping
    public ResponseEntity<ExamenResponseDto> createExamen(
            @Valid @RequestBody ExamenRequestDto request) {
        ExamenResponseDto created = examenService.createExamen(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamenResponseDto> getExamenById(@PathVariable Long id) {
        return ResponseEntity.ok(examenService.getExamenById(id));
    }

    @GetMapping
    public ResponseEntity<List<ExamenResponseDto>> getExamens(
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long promotionId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(examenService.filterExamens(
                academicYearId, programId, promotionId, groupId,
                semesterId, sessionId, moduleId, date));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamenResponseDto> updateExamen(
            @PathVariable Long id, @Valid @RequestBody ExamenRequestDto request) {
        return ResponseEntity.ok(examenService.updateExamen(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExamen(@PathVariable Long id) {
        examenService.deleteExamen(id);
        return ResponseEntity.noContent().build();
    }
}
