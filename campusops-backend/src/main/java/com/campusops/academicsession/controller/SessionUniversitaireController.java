package com.campusops.academicsession.controller;

import com.campusops.academicsession.dto.SessionUniversitaireRequestDto;
import com.campusops.academicsession.dto.SessionUniversitaireResponseDto;
import com.campusops.academicsession.service.SessionUniversitaireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic-sessions")
@RequiredArgsConstructor
public class SessionUniversitaireController {

    private final SessionUniversitaireService sessionService;

    @PostMapping
    public ResponseEntity<SessionUniversitaireResponseDto> createSession(
            @Valid @RequestBody SessionUniversitaireRequestDto request) {
        SessionUniversitaireResponseDto created = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionUniversitaireResponseDto> getSessionById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSessionById(id));
    }

    @GetMapping
    public ResponseEntity<List<SessionUniversitaireResponseDto>> getSessions(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(sessionService.filterSessions(actif));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SessionUniversitaireResponseDto> updateSession(
            @PathVariable Long id, @Valid @RequestBody SessionUniversitaireRequestDto request) {
        return ResponseEntity.ok(sessionService.updateSession(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateSession(@PathVariable Long id) {
        sessionService.deactivateSession(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateSession(@PathVariable Long id) {
        sessionService.activateSession(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : emplois du temps et examens
     * qui bloquent la suppression de la session. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        sessionService.deleteSession(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
