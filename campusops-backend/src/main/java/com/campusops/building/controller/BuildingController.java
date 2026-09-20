package com.campusops.building.controller;

import com.campusops.building.dto.BuildingRequestDto;
import com.campusops.building.dto.BuildingResponseDto;
import com.campusops.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @PostMapping
    public ResponseEntity<BuildingResponseDto> createBuilding(
            @Valid @RequestBody BuildingRequestDto request) {
        BuildingResponseDto created = buildingService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BuildingResponseDto> getBuildingById(@PathVariable Long id) {
        return ResponseEntity.ok(buildingService.getBuildingById(id));
    }

    @GetMapping
    public ResponseEntity<List<BuildingResponseDto>> getBuildings(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(buildingService.filterBuildings(actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<BuildingResponseDto>> searchBuildings(@RequestParam String keyword) {
        return ResponseEntity.ok(buildingService.searchBuildings(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BuildingResponseDto> updateBuilding(
            @PathVariable Long id, @Valid @RequestBody BuildingRequestDto request) {
        return ResponseEntity.ok(buildingService.updateBuilding(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateBuilding(@PathVariable Long id) {
        buildingService.deactivateBuilding(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateBuilding(@PathVariable Long id) {
        buildingService.activateBuilding(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Compteurs d'impact avant desactivation (§17) : etages et salles actifs qui
     * deviendront indisponibles (recherche, reservations, EDT). Sert a la
     * confirmation cote admin.
     */
    @GetMapping("/{id}/impact-desactivation")
    public ResponseEntity<com.campusops.validation.dto.DeactivationImpact> deactivationImpact(
            @PathVariable Long id) {
        return ResponseEntity.ok(buildingService.getDeactivationImpact(id));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : etages/salles supprimes
     * en cascade et usages metier bloquants. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(buildingService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBuilding(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        buildingService.deleteBuilding(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
