package com.campusops.floor.controller;

import com.campusops.floor.dto.FloorRequestDto;
import com.campusops.floor.dto.FloorResponseDto;
import com.campusops.floor.service.FloorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/floors")
@RequiredArgsConstructor
public class FloorController {

    private final FloorService floorService;

    @PostMapping
    public ResponseEntity<FloorResponseDto> createFloor(
            @Valid @RequestBody FloorRequestDto request) {
        FloorResponseDto created = floorService.createFloor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FloorResponseDto> getFloorById(@PathVariable Long id) {
        return ResponseEntity.ok(floorService.getFloorById(id));
    }

    @GetMapping
    public ResponseEntity<List<FloorResponseDto>> getFloors(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(floorService.filterFloors(actif));
    }

    @GetMapping("/building/{buildingId}")
    public ResponseEntity<List<FloorResponseDto>> getFloorsByBuilding(
            @PathVariable Long buildingId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(floorService.getFloorsByBuilding(buildingId, actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FloorResponseDto>> searchFloors(@RequestParam String keyword) {
        return ResponseEntity.ok(floorService.searchFloors(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FloorResponseDto> updateFloor(
            @PathVariable Long id, @Valid @RequestBody FloorRequestDto request) {
        return ResponseEntity.ok(floorService.updateFloor(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateFloor(@PathVariable Long id) {
        floorService.deactivateFloor(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateFloor(@PathVariable Long id) {
        floorService.activateFloor(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Compteurs d'impact avant desactivation (§17) : salles actives qui
     * deviendront indisponibles. Sert a la confirmation cote admin.
     */
    @GetMapping("/{id}/impact-desactivation")
    public ResponseEntity<com.campusops.validation.dto.DeactivationImpact> deactivationImpact(
            @PathVariable Long id) {
        return ResponseEntity.ok(floorService.getDeactivationImpact(id));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : salles supprimees en
     * cascade et usages metier bloquants. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(floorService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFloor(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        floorService.deleteFloor(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
