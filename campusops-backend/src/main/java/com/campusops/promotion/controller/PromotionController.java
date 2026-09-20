package com.campusops.promotion.controller;

import com.campusops.promotion.dto.PromotionRequestDto;
import com.campusops.promotion.dto.PromotionResponseDto;
import com.campusops.promotion.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<PromotionResponseDto> createPromotion(
            @Valid @RequestBody PromotionRequestDto request) {
        PromotionResponseDto created = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponseDto> getPromotionById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getPromotionById(id));
    }

    @GetMapping
    public ResponseEntity<List<PromotionResponseDto>> getPromotions(
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(promotionService.filterPromotions(actif));
    }

    @GetMapping("/program/{programId}")
    public ResponseEntity<List<PromotionResponseDto>> getPromotionsByProgram(
            @PathVariable Long programId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(promotionService.getPromotionsByProgram(programId, actif));
    }

    @GetMapping("/academic-year/{academicYearId}")
    public ResponseEntity<List<PromotionResponseDto>> getPromotionsByAcademicYear(
            @PathVariable Long academicYearId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(
                promotionService.getPromotionsByAcademicYear(academicYearId, actif));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PromotionResponseDto>> searchPromotions(@RequestParam String keyword) {
        return ResponseEntity.ok(promotionService.searchPromotions(keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionResponseDto> updatePromotion(
            @PathVariable Long id, @Valid @RequestBody PromotionRequestDto request) {
        return ResponseEntity.ok(promotionService.updatePromotion(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivatePromotion(@PathVariable Long id) {
        promotionService.deactivatePromotion(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activatePromotion(@PathVariable Long id) {
        promotionService.activatePromotion(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Compteurs d'impact avant desactivation (§17) : groupes actifs qui
     * deviendront indisponibles. Sert a la confirmation cote admin.
     */
    @GetMapping("/{id}/impact-desactivation")
    public ResponseEntity<com.campusops.validation.dto.DeactivationImpact> deactivationImpact(
            @PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getDeactivationImpact(id));
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : groupes supprimes en
     * cascade et usages metier bloquants. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromotion(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        promotionService.deletePromotion(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
