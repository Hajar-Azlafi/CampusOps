package com.campusops.equipment.controller;

import com.campusops.equipment.dto.EquipmentRequestDto;
import com.campusops.equipment.dto.EquipmentResponseDto;
import com.campusops.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @PostMapping
    public ResponseEntity<EquipmentResponseDto> createEquipment(
            @Valid @RequestBody EquipmentRequestDto request) {
        EquipmentResponseDto created = equipmentService.createEquipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentResponseDto> getEquipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }

    @GetMapping
    public ResponseEntity<List<EquipmentResponseDto>> getEquipments(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(equipmentService.filterEquipments(actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<EquipmentResponseDto>> searchEquipments(@RequestParam String keyword) {
        return ResponseEntity.ok(equipmentService.searchEquipments(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentResponseDto> updateEquipment(
            @PathVariable Long id, @Valid @RequestBody EquipmentRequestDto request) {
        return ResponseEntity.ok(equipmentService.updateEquipment(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateEquipment(@PathVariable Long id) {
        equipmentService.deactivateEquipment(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateEquipment(@PathVariable Long id) {
        equipmentService.activateEquipment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : salles dont l'equipement
     * sera retire. Association technique, jamais bloquante. Alimente la modale.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        equipmentService.deleteEquipment(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
