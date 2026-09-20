package com.campusops.department.controller;

import com.campusops.department.dto.DepartmentRequestDto;
import com.campusops.department.dto.DepartmentResponseDto;
import com.campusops.department.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<DepartmentResponseDto> createDepartment(
            @Valid @RequestBody DepartmentRequestDto request) {
        DepartmentResponseDto created = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponseDto>> getDepartments(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(departmentService.filterDepartments(actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DepartmentResponseDto>> searchDepartments(@RequestParam String keyword) {
        return ResponseEntity.ok(departmentService.searchDepartments(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponseDto> updateDepartment(
            @PathVariable Long id, @Valid @RequestBody DepartmentRequestDto request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateDepartment(@PathVariable Long id) {
        departmentService.deactivateDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateDepartment(@PathVariable Long id) {
        departmentService.activateDepartment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Compteurs d'impact avant desactivation (§17) : filieres, promotions et
     * groupes actifs qui deviendront indisponibles. Sert a la confirmation.
     */
    @GetMapping("/{id}/impact-desactivation")
    public ResponseEntity<com.campusops.validation.dto.DeactivationImpact> deactivationImpact(
            @PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDeactivationImpact(id));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : enfants supprimes en
     * cascade et usages metier bloquants. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        departmentService.deleteDepartment(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
