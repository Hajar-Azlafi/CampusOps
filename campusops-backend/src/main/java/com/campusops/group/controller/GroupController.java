package com.campusops.group.controller;

import com.campusops.group.dto.GroupRequestDto;
import com.campusops.group.dto.GroupResponseDto;
import com.campusops.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponseDto> createGroup(
            @Valid @RequestBody GroupRequestDto request) {
        GroupResponseDto created = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponseDto> getGroupById(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponseDto>> getGroups(
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(groupService.filterGroups(actif, academicYearId));
    }

    @GetMapping("/promotion/{promotionId}")
    public ResponseEntity<List<GroupResponseDto>> getGroupsByPromotion(
            @PathVariable Long promotionId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(groupService.getGroupsByPromotion(promotionId, actif));
    }

    @GetMapping("/academic-year/{academicYearId}")
    public ResponseEntity<List<GroupResponseDto>> getGroupsByAcademicYear(
            @PathVariable Long academicYearId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(
                groupService.getGroupsByAcademicYear(academicYearId, actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupResponseDto>> searchGroups(@RequestParam String keyword) {
        return ResponseEntity.ok(groupService.searchGroups(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponseDto> updateGroup(
            @PathVariable Long id, @Valid @RequestBody GroupRequestDto request) {
        return ResponseEntity.ok(groupService.updateGroup(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateGroup(@PathVariable Long id) {
        groupService.deactivateGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateGroup(@PathVariable Long id) {
        groupService.activateGroup(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : usages metier qui
     * bloquent la suppression du groupe. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        groupService.deleteGroup(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
