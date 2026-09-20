package com.campusops.typeseance.controller;

import com.campusops.typeseance.dto.TypeSeanceRequestDto;
import com.campusops.typeseance.dto.TypeSeanceResponseDto;
import com.campusops.typeseance.service.TypeSeanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seance-types")
@RequiredArgsConstructor
public class TypeSeanceController {

    private final TypeSeanceService typeSeanceService;

    @PostMapping
    public ResponseEntity<TypeSeanceResponseDto> createTypeSeance(
            @Valid @RequestBody TypeSeanceRequestDto request) {
        TypeSeanceResponseDto created = typeSeanceService.createTypeSeance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TypeSeanceResponseDto> getTypeSeanceById(@PathVariable Long id) {
        return ResponseEntity.ok(typeSeanceService.getTypeSeanceById(id));
    }

    @GetMapping
    public ResponseEntity<List<TypeSeanceResponseDto>> getTypesSeance(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(typeSeanceService.filterTypesSeance(actif));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TypeSeanceResponseDto> updateTypeSeance(
            @PathVariable Long id, @Valid @RequestBody TypeSeanceRequestDto request) {
        return ResponseEntity.ok(typeSeanceService.updateTypeSeance(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateTypeSeance(@PathVariable Long id) {
        typeSeanceService.deactivateTypeSeance(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateTypeSeance(@PathVariable Long id) {
        typeSeanceService.activateTypeSeance(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : séances qui bloquent la
     * suppression du type de séance. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(typeSeanceService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTypeSeance(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        typeSeanceService.deleteTypeSeance(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
