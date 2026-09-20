package com.campusops.timetable.controller;

import com.campusops.enums.TimetableStatus;
import com.campusops.schedule.dto.ScheduleResponseDto;
import com.campusops.timetable.dto.EmploiDuTempsRequestDto;
import com.campusops.timetable.dto.EmploiDuTempsResponseDto;
import com.campusops.timetable.service.EmploiDuTempsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class EmploiDuTempsController {

    private final EmploiDuTempsService emploiDuTempsService;

    @PostMapping
    public ResponseEntity<EmploiDuTempsResponseDto> createTimetable(
            @Valid @RequestBody EmploiDuTempsRequestDto request) {
        EmploiDuTempsResponseDto created = emploiDuTempsService.createTimetable(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmploiDuTempsResponseDto> getTimetableById(@PathVariable Long id) {
        return ResponseEntity.ok(emploiDuTempsService.getTimetableById(id));
    }

    @GetMapping
    public ResponseEntity<List<EmploiDuTempsResponseDto>> getTimetables(
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) TimetableStatus statut) {
        return ResponseEntity.ok(emploiDuTempsService.filterTimetables(
                academicYearId, programId, levelId, groupId, semesterId, sessionId, statut));
    }

    @GetMapping("/{id}/seances")
    public ResponseEntity<List<ScheduleResponseDto>> getTimetableSeances(@PathVariable Long id) {
        return ResponseEntity.ok(emploiDuTempsService.getTimetableSeances(id));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<EmploiDuTempsResponseDto> publish(@PathVariable Long id) {
        return ResponseEntity.ok(emploiDuTempsService.publish(id));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<EmploiDuTempsResponseDto> archive(@PathVariable Long id) {
        return ResponseEntity.ok(emploiDuTempsService.archive(id));
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<EmploiDuTempsResponseDto> reopen(@PathVariable Long id) {
        return ResponseEntity.ok(emploiDuTempsService.reopen(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimetable(@PathVariable Long id) {
        emploiDuTempsService.deleteTimetable(id);
        return ResponseEntity.noContent().build();
    }
}
