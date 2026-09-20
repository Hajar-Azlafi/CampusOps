package com.campusops.space.controller;

import com.campusops.enums.SpaceType;
import com.campusops.space.dto.SpaceRequestDto;
import com.campusops.space.dto.SpaceResponseDto;
import com.campusops.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @PostMapping
    public ResponseEntity<SpaceResponseDto> createSpace(
            @Valid @RequestBody SpaceRequestDto request) {
        SpaceResponseDto created = spaceService.createSpace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceResponseDto> getSpaceById(@PathVariable Long id) {
        return ResponseEntity.ok(spaceService.getSpaceById(id));
    }

    @GetMapping
    public ResponseEntity<List<SpaceResponseDto>> getSpaces(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(spaceService.filterSpaces(actif));
    }

    @GetMapping("/floor/{floorId}")
    public ResponseEntity<List<SpaceResponseDto>> getSpacesByFloor(
            @PathVariable Long floorId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(spaceService.getSpacesByFloor(floorId, actif));
    }

    @GetMapping("/building/{buildingId}")
    public ResponseEntity<List<SpaceResponseDto>> getSpacesByBuilding(
            @PathVariable Long buildingId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(spaceService.getSpacesByBuilding(buildingId, actif));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<SpaceResponseDto>> getSpacesByType(
            @PathVariable SpaceType type,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(spaceService.getSpacesByType(type, actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SpaceResponseDto>> searchSpaces(@RequestParam String keyword) {
        return ResponseEntity.ok(spaceService.searchSpaces(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SpaceResponseDto> updateSpace(
            @PathVariable Long id, @Valid @RequestBody SpaceRequestDto request) {
        return ResponseEntity.ok(spaceService.updateSpace(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateSpace(@PathVariable Long id) {
        spaceService.deactivateSpace(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateSpace(@PathVariable Long id) {
        spaceService.activateSpace(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : usages metier (seances,
     * reservations, occupations/examens) qui bloquent la suppression de la salle.
     * Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(spaceService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpace(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        spaceService.deleteSpace(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
