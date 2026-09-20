package com.campusops.academicyear.controller;

import com.campusops.academicyear.dto.AcademicYearRequestDto;
import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.academicyear.service.AcademicYearRolloverService;
import com.campusops.academicyear.service.AcademicYearService;
import com.campusops.security.AccessScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;
    private final AcademicYearRolloverService academicYearRolloverService;
    private final AccessScopeService accessScope;

    @PostMapping
    public ResponseEntity<AcademicYearResponseDto> createAcademicYear(
            @Valid @RequestBody AcademicYearRequestDto request) {
        accessScope.requireAdmin();
        AcademicYearResponseDto created = academicYearService.createAcademicYear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcademicYearResponseDto> getAcademicYearById(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.getAcademicYearById(id));
    }

    @GetMapping("/current")
    public ResponseEntity<AcademicYearResponseDto> getCurrentAcademicYear() {
        return ResponseEntity.ok(academicYearService.getCurrentAcademicYear());
    }

    @GetMapping
    public ResponseEntity<List<AcademicYearResponseDto>> getAcademicYears(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(academicYearService.filterAcademicYears(actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AcademicYearResponseDto>> searchAcademicYears(@RequestParam String keyword) {
        return ResponseEntity.ok(academicYearService.searchAcademicYears(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcademicYearResponseDto> updateAcademicYear(
            @PathVariable Long id, @Valid @RequestBody AcademicYearRequestDto request) {
        accessScope.requireAdmin();
        return ResponseEntity.ok(academicYearService.updateAcademicYear(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateAcademicYear(@PathVariable Long id) {
        accessScope.requireAdmin();
        academicYearService.deactivateAcademicYear(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateAcademicYear(@PathVariable Long id) {
        accessScope.requireAdmin();
        academicYearService.activateAcademicYear(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : promotions, séances,
     * emplois du temps, occupations/examens et réservations qui bloquent la
     * suppression de l'année. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        accessScope.requireAdmin();
        return ResponseEntity.ok(academicYearService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAcademicYear(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        accessScope.requireAdmin();
        academicYearService.deleteAcademicYear(id, cascade);
        return ResponseEntity.noContent().build();
    }

    /**
     * Passage manuel à l'année universitaire suivante (contrôlé par l'admin).
     * Crée l'année suivante si nécessaire, l'active (en désactivant l'ancienne),
     * puis amorce ses promotions/groupes. Ne supprime aucune donnée : l'année
     * précédente et toute sa structure restent consultables.
     */
    @PostMapping("/rollover")
    public ResponseEntity<AcademicYearResponseDto> rolloverToNextYear() {
        accessScope.requireAdmin();
        return ResponseEntity.ok(academicYearRolloverService.passerAAnneeSuivante());
    }
}
