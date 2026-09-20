package com.campusops.schedule.controller;

import com.campusops.schedule.dto.ScheduleRequestDto;
import com.campusops.schedule.dto.ScheduleResponseDto;
import com.campusops.schedule.dto.SessionTypeStatsDto;
import com.campusops.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponseDto> createSchedule(
            @Valid @RequestBody ScheduleRequestDto request) {
        ScheduleResponseDto created = scheduleService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponseDto> getScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getScheduleById(id));
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponseDto>> getSchedules(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(scheduleService.filterSchedules(actif));
    }

    @GetMapping("/promotion/{promotionId}")
    public ResponseEntity<List<ScheduleResponseDto>> getSchedulesByPromotion(
            @PathVariable Long promotionId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByPromotion(promotionId));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<ScheduleResponseDto>> getSchedulesByGroup(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByGroup(groupId));
    }

    @GetMapping("/space/{spaceId}")
    public ResponseEntity<List<ScheduleResponseDto>> getSchedulesBySpace(
            @PathVariable Long spaceId) {
        return ResponseEntity.ok(scheduleService.getSchedulesBySpace(spaceId));
    }

    @GetMapping("/academic-year/{academicYearId}")
    public ResponseEntity<List<ScheduleResponseDto>> getSchedulesByAcademicYear(
            @PathVariable Long academicYearId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByAcademicYear(academicYearId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ScheduleResponseDto>> searchSchedules(@RequestParam String keyword) {
        return ResponseEntity.ok(scheduleService.searchSchedules(keyword));
    }

    /**
     * Statistiques de repartition des seances par type (§6/§19). Reservees au
     * perimetre de l'utilisateur (ADMIN = tout ; responsable pedagogique = ses
     * filieres) et affinees par le contexte pedagogique fourni (tous les
     * parametres sont facultatifs). Toujours calculees sur des donnees reelles.
     */
    @GetMapping("/stats/session-types")
    public ResponseEntity<SessionTypeStatsDto> getSessionTypeStats(
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Long promotionId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long emploiDuTempsId) {
        return ResponseEntity.ok(scheduleService.getSessionTypeStats(
                academicYearId, programId, levelId, promotionId,
                groupId, semesterId, emploiDuTempsId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponseDto> updateSchedule(
            @PathVariable Long id, @Valid @RequestBody ScheduleRequestDto request) {
        return ResponseEntity.ok(scheduleService.updateSchedule(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateSchedule(@PathVariable Long id) {
        scheduleService.deactivateSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateSchedule(@PathVariable Long id) {
        scheduleService.activateSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
