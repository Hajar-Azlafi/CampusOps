package com.campusops.occupation.controller;

import com.campusops.enums.OccupationCategorie;
import com.campusops.occupation.dto.OccupationRequestDto;
import com.campusops.occupation.dto.OccupationResponseDto;
import com.campusops.occupation.service.OccupationSupplementaireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API des <b>occupations supplémentaires génériques</b> : soutenances et
 * occupations « autre ». Les examens gardent leur propre API
 * ({@code /api/examens}) — ici on ne manipule jamais un examen.
 *
 * <p>La sécurité par périmètre (§12) est appliquée dans le service via
 * {@code AccessScopeService} : pas de {@code @PreAuthorize}, aucune règle laissée
 * au seul frontend.</p>
 *
 * <p>La liste est filtrée par <b>catégorie</b> ({@code SOUTENANCE} ou
 * {@code AUTRE}), ce qui alimente directement les deux onglets « Planning
 * soutenances » et « Autre » du module « Occupation supplémentaire ».</p>
 */
@RestController
@RequestMapping("/api/occupations")
@RequiredArgsConstructor
public class OccupationSupplementaireController {

    private final OccupationSupplementaireService occupationService;

    @PostMapping
    public ResponseEntity<OccupationResponseDto> create(
            @Valid @RequestBody OccupationRequestDto request) {
        OccupationResponseDto created = occupationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OccupationResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(occupationService.getById(id));
    }

    /**
     * Liste les occupations d'une catégorie (obligatoire : {@code SOUTENANCE} ou
     * {@code AUTRE}), filtrable par filière et par date.
     */
    @GetMapping
    public ResponseEntity<List<OccupationResponseDto>> list(
            @RequestParam OccupationCategorie categorie,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(occupationService.listByCategorie(categorie, programId, date));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OccupationResponseDto> update(
            @PathVariable Long id, @Valid @RequestBody OccupationRequestDto request) {
        return ResponseEntity.ok(occupationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        occupationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
