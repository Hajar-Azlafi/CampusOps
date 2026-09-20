package com.campusops.availability.controller;

import com.campusops.availability.dto.AvailabilitySearchRequestDto;
import com.campusops.availability.dto.AvailabilitySearchResponseDto;
import com.campusops.availability.dto.SearchWindowDto;
import com.campusops.availability.service.AvailabilityService;
import com.campusops.enums.SpaceType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Points d'entree REST pour la detection des espaces disponibles. Toute la
 * logique est deleguee au service, lui-meme adosse au moteur central (§2, §19).
 * Chaque reponse porte le contexte de journee (jour ouvrable, horaires derives
 * des creneaux, annee universitaire) en plus de la liste des espaces.
 */
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /**
     * Regles de saisie de la recherche : bornes de date ouvertes, heure limite de
     * la journee en cours, horaires d'ouverture, durees proposees et creneaux
     * officiels. Appele au chargement du formulaire pour le borner.
     */
    @GetMapping("/window")
    public ResponseEntity<SearchWindowDto> searchWindow() {
        return ResponseEntity.ok(availabilityService.searchWindow());
    }

    /** Recherche personnalisee (§6). Les heures et la duree sont <b>optionnelles</b>. */
    @GetMapping("/search")
    public ResponseEntity<AvailabilitySearchResponseDto> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureDebut,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureFin,
            @RequestParam(required = false) Integer dureeMinutes,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) SpaceType type,
            @RequestParam(required = false) Integer capaciteMin,
            @RequestParam(required = false) List<Long> equipmentIds,
            @RequestParam(required = false) String exactSpaceQuery) {
        AvailabilitySearchRequestDto request = AvailabilitySearchRequestDto.builder()
                .date(date)
                .heureDebut(heureDebut)
                .heureFin(heureFin)
                .dureeMinutes(dureeMinutes)
                .buildingId(buildingId)
                .floorId(floorId)
                .type(type)
                .capaciteMin(capaciteMin)
                .equipmentIds(equipmentIds)
                .exactSpaceQuery(exactSpaceQuery)
                .build();
        return ResponseEntity.ok(availabilityService.search(request));
    }

    @PostMapping("/search")
    public ResponseEntity<AvailabilitySearchResponseDto> search(
            @Valid @RequestBody AvailabilitySearchRequestDto request) {
        return ResponseEntity.ok(availabilityService.search(request));
    }

    @GetMapping("/building/{buildingId}")
    public ResponseEntity<AvailabilitySearchResponseDto> searchByBuilding(
            @PathVariable Long buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureDebut,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureFin) {
        return ResponseEntity.ok(
                availabilityService.searchByBuilding(buildingId, date, heureDebut, heureFin));
    }

    @GetMapping("/floor/{floorId}")
    public ResponseEntity<AvailabilitySearchResponseDto> searchByFloor(
            @PathVariable Long floorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureDebut,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureFin) {
        return ResponseEntity.ok(
                availabilityService.searchByFloor(floorId, date, heureDebut, heureFin));
    }

    /** Libre aujourd'hui (§11) : periodes libres du jour, sans saisie d'heures. */
    @GetMapping("/today")
    public ResponseEntity<AvailabilitySearchResponseDto> availableToday() {
        return ResponseEntity.ok(availabilityService.availableToday());
    }

    /** Libre maintenant (§10) : de l'instant present a la prochaine occupation. */
    @GetMapping("/now")
    public ResponseEntity<AvailabilitySearchResponseDto> availableNow() {
        return ResponseEntity.ok(availabilityService.availableNow());
    }
}
