package com.campusops.level.controller;

import com.campusops.level.dto.LevelRequestDto;
import com.campusops.level.dto.LevelResponseDto;
import com.campusops.level.service.LevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @PostMapping
    public ResponseEntity<LevelResponseDto> createLevel(
            @Valid @RequestBody LevelRequestDto request) {
        LevelResponseDto created = levelService.createLevel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LevelResponseDto> getLevelById(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getLevelById(id));
    }

    @GetMapping
    public ResponseEntity<List<LevelResponseDto>> getLevels(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(levelService.filterLevels(actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<LevelResponseDto>> searchLevels(@RequestParam String keyword) {
        return ResponseEntity.ok(levelService.searchLevels(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LevelResponseDto> updateLevel(
            @PathVariable Long id, @Valid @RequestBody LevelRequestDto request) {
        return ResponseEntity.ok(levelService.updateLevel(id, request));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : usages metier qui
     * bloquent la suppression du niveau. Alimente la modale de confirmation.
     * Aucun enfant supprime en cascade (referentiel taxonomique).
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLevel(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        levelService.deleteLevel(id, cascade);
        return ResponseEntity.noContent().build();
    }

    // Pas d'endpoint activate/deactivate : le niveau / cycle est un referentiel
    // taxonomique stable partage par toutes les filieres. Son statut actif/inactif
    // n'a pas de sens metier (il n'entre dans aucun predicat d'usabilite) ; on ne
    // l'expose donc pas comme une operation, conformement au cahier (§9/§26).
    // Un niveau obsolete se supprime (suppression refusee s'il est encore utilise).
}
