package com.campusops.semester.controller;

import com.campusops.security.AccessScopeService;
import com.campusops.semester.dto.SemesterRequestDto;
import com.campusops.semester.dto.SemesterResponseDto;
import com.campusops.semester.dto.SemesterRolloverResultDto;
import com.campusops.semester.service.SemesterRolloverService;
import com.campusops.semester.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;
    private final SemesterRolloverService semesterRolloverService;
    private final AccessScopeService accessScope;

    @PostMapping
    public ResponseEntity<SemesterResponseDto> createSemester(
            @Valid @RequestBody SemesterRequestDto request) {
        SemesterResponseDto created = semesterService.createSemester(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SemesterResponseDto> getSemesterById(@PathVariable Long id) {
        return ResponseEntity.ok(semesterService.getSemesterById(id));
    }

    @GetMapping
    public ResponseEntity<List<SemesterResponseDto>> getSemesters(
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) Long levelId) {
        return ResponseEntity.ok(semesterService.filterSemesters(actif, levelId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SemesterResponseDto>> searchSemesters(@RequestParam String keyword) {
        return ResponseEntity.ok(semesterService.searchSemesters(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SemesterResponseDto> updateSemester(
            @PathVariable Long id, @Valid @RequestBody SemesterRequestDto request) {
        return ResponseEntity.ok(semesterService.updateSemester(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateSemester(@PathVariable Long id) {
        semesterService.deactivateSemester(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateSemester(@PathVariable Long id) {
        semesterService.activateSemester(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : modules, seances, emplois
     * du temps et examens qui bloquent la suppression du semestre. Alimente la
     * modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(semesterService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSemester(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        semesterService.deleteSemester(id, cascade);
        return ResponseEntity.noContent().build();
    }

    // --- Semestres courants & bascule de semestre (§20) ------------------

    /** Semestres actuellement courants (un niveau peut en avoir plusieurs). */
    @GetMapping("/current")
    public ResponseEntity<List<SemesterResponseDto>> getCurrentSemesters() {
        return ResponseEntity.ok(semesterService.getCurrentSemesters());
    }

    /**
     * Ajoute le semestre à l'ensemble des semestres courants de son niveau
     * (ADMIN). Un niveau peut en compter plusieurs simultanément (années
     * coexistantes) : les autres courants ne sont pas retirés.
     */
    @PatchMapping("/{id}/courant")
    public ResponseEntity<SemesterResponseDto> setCourant(@PathVariable Long id) {
        return ResponseEntity.ok(semesterService.setCourant(id));
    }

    /** Retire le semestre des semestres courants de son niveau (ADMIN). */
    @PatchMapping("/{id}/uncourant")
    public ResponseEntity<SemesterResponseDto> unsetCourant(@PathVariable Long id) {
        return ResponseEntity.ok(semesterService.unsetCourant(id));
    }

    /**
     * Bascule au semestre suivant tous les niveaux (S1→S2, S3→S4…), sans rien
     * supprimer : les emplois du temps du semestre quitté sont archivés (salles
     * libérées). Réservé à l'ADMIN, à l'image du passage à l'année suivante.
     */
    @PostMapping("/rollover")
    public ResponseEntity<SemesterRolloverResultDto> rolloverToNextSemester() {
        accessScope.requireAdmin();
        return ResponseEntity.ok(semesterRolloverService.passerAuSemestreSuivant());
    }
}
