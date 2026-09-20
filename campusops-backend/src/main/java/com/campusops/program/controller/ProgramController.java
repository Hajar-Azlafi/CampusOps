package com.campusops.program.controller;

import com.campusops.program.dto.AssignResponsableRequestDto;
import com.campusops.program.dto.ProgramRequestDto;
import com.campusops.program.dto.ProgramResponseDto;
import com.campusops.program.service.ProgramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @PostMapping
    public ResponseEntity<ProgramResponseDto> createProgram(
            @Valid @RequestBody ProgramRequestDto request) {
        ProgramResponseDto created = programService.createProgram(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponseDto> getProgramById(@PathVariable Long id) {
        return ResponseEntity.ok(programService.getProgramById(id));
    }

    @GetMapping
    public ResponseEntity<List<ProgramResponseDto>> getPrograms(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(programService.filterPrograms(actif));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<ProgramResponseDto>> getProgramsByDepartment(
            @PathVariable Long departmentId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(programService.getProgramsByDepartment(departmentId, actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProgramResponseDto>> searchPrograms(@RequestParam String keyword) {
        return ResponseEntity.ok(programService.searchPrograms(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProgramResponseDto> updateProgram(
            @PathVariable Long id, @Valid @RequestBody ProgramRequestDto request) {
        return ResponseEntity.ok(programService.updateProgram(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProgram(@PathVariable Long id) {
        programService.deactivateProgram(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateProgram(@PathVariable Long id) {
        programService.activateProgram(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Compteurs d'impact avant desactivation (§17) : promotions et groupes
     * actifs qui deviendront indisponibles. Sert a la confirmation frontend.
     */
    @GetMapping("/{id}/impact-desactivation")
    public ResponseEntity<com.campusops.validation.dto.DeactivationImpact> deactivationImpact(
            @PathVariable Long id) {
        return ResponseEntity.ok(programService.getDeactivationImpact(id));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : sous-arbre supprime en
     * cascade et usages metier bloquants. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(programService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProgram(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        programService.deleteProgram(id, cascade);
        return ResponseEntity.noContent().build();
    }

    /**
     * Affecte ou retire le responsable pedagogique d'une filiere (ADMIN).
     * Un corps {@code {"userId": null}} retire le responsable actuel.
     */
    @PatchMapping("/{id}/responsable")
    public ResponseEntity<ProgramResponseDto> assignResponsable(
            @PathVariable Long id, @RequestBody(required = false) AssignResponsableRequestDto request) {
        Long userId = request != null ? request.getUserId() : null;
        return ResponseEntity.ok(programService.assignResponsable(id, userId));
    }

    /** Liste les filieres dont l'utilisateur est responsable pedagogique (ADMIN). */
    @GetMapping("/responsable/{userId}")
    public ResponseEntity<List<ProgramResponseDto>> getProgramsByResponsable(
            @PathVariable Long userId) {
        return ResponseEntity.ok(programService.getProgramsByResponsable(userId));
    }
}
