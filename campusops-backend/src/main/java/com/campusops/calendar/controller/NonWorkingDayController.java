package com.campusops.calendar.controller;

import com.campusops.calendar.dto.NonWorkingDayRequestDto;
import com.campusops.calendar.dto.NonWorkingDayResponseDto;
import com.campusops.calendar.service.NonWorkingDayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Administration du calendrier non ouvrable (§4). L'ecriture est reservee a
 * l'ADMIN (controle en service, pas par le chemin), la lecture est ouverte aux
 * utilisateurs authentifies pour permettre a l'interface d'expliquer une
 * fermeture.
 */
@RestController
@RequestMapping("/api/non-working-days")
@RequiredArgsConstructor
public class NonWorkingDayController {

    private final NonWorkingDayService service;

    @PostMapping
    public ResponseEntity<NonWorkingDayResponseDto> create(
            @Valid @RequestBody NonWorkingDayRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<NonWorkingDayResponseDto>> list(
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(service.list(actif, debut, fin));
    }

    @GetMapping("/year/{annee}")
    public ResponseEntity<List<NonWorkingDayResponseDto>> listByYear(@PathVariable int annee) {
        return ResponseEntity.ok(service.listByYear(annee));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NonWorkingDayResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NonWorkingDayResponseDto> update(
            @PathVariable Long id, @Valid @RequestBody NonWorkingDayRequestDto request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
