package com.campusops.timeslot.controller;

import com.campusops.timeslot.dto.TimeSlotRequestDto;
import com.campusops.timeslot.dto.TimeSlotResponseDto;
import com.campusops.timeslot.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    @PostMapping
    public ResponseEntity<TimeSlotResponseDto> createTimeSlot(
            @Valid @RequestBody TimeSlotRequestDto request) {
        TimeSlotResponseDto created = timeSlotService.createTimeSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimeSlotResponseDto> getTimeSlotById(@PathVariable Long id) {
        return ResponseEntity.ok(timeSlotService.getTimeSlotById(id));
    }

    @GetMapping
    public ResponseEntity<List<TimeSlotResponseDto>> getTimeSlots(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(timeSlotService.filterTimeSlots(actif));
    }

    /**
     * Réorganise les créneaux selon la liste ordonnée d'identifiants fournie
     * (§2/§3). Réservé à l'ADMIN (contrôle en service). Déclaré avant
     * {@code PUT /{id}} : « reorder » est un chemin littéral, prioritaire.
     */
    @PutMapping("/reorder")
    public ResponseEntity<List<TimeSlotResponseDto>> reorderTimeSlots(
            @RequestBody List<Long> orderedIds) {
        return ResponseEntity.ok(timeSlotService.reorder(orderedIds));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TimeSlotResponseDto> updateTimeSlot(
            @PathVariable Long id, @Valid @RequestBody TimeSlotRequestDto request) {
        return ResponseEntity.ok(timeSlotService.updateTimeSlot(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateTimeSlot(@PathVariable Long id) {
        timeSlotService.deactivateTimeSlot(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateTimeSlot(@PathVariable Long id) {
        timeSlotService.activateTimeSlot(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : séances et examens qui
     * bloquent la suppression du créneau. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(timeSlotService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimeSlot(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        timeSlotService.deleteTimeSlot(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
