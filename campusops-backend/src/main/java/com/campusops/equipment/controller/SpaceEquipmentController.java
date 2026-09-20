package com.campusops.equipment.controller;

import com.campusops.equipment.dto.AssignEquipmentsRequestDto;
import com.campusops.equipment.dto.EquipmentResponseDto;
import com.campusops.equipment.service.SpaceEquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces/{spaceId}/equipments")
@RequiredArgsConstructor
public class SpaceEquipmentController {

    private final SpaceEquipmentService spaceEquipmentService;

    @GetMapping
    public ResponseEntity<List<EquipmentResponseDto>> getEquipmentsOfSpace(
            @PathVariable Long spaceId) {
        return ResponseEntity.ok(spaceEquipmentService.getEquipmentsOfSpace(spaceId));
    }

    @PostMapping
    public ResponseEntity<List<EquipmentResponseDto>> assignEquipments(
            @PathVariable Long spaceId,
            @Valid @RequestBody AssignEquipmentsRequestDto request) {
        return ResponseEntity.ok(
                spaceEquipmentService.assignEquipments(spaceId, request.getEquipmentIds()));
    }

    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> removeEquipment(
            @PathVariable Long spaceId, @PathVariable Long equipmentId) {
        spaceEquipmentService.removeEquipment(spaceId, equipmentId);
        return ResponseEntity.noContent().build();
    }
}
