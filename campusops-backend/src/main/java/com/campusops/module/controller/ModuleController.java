package com.campusops.module.controller;

import com.campusops.module.dto.ModuleRequestDto;
import com.campusops.module.dto.ModuleResponseDto;
import com.campusops.module.service.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    public ResponseEntity<ModuleResponseDto> createModule(
            @Valid @RequestBody ModuleRequestDto request) {
        ModuleResponseDto created = moduleService.createModule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModuleResponseDto> getModuleById(@PathVariable Long id) {
        return ResponseEntity.ok(moduleService.getModuleById(id));
    }

    /**
     * Liste les modules, filtrée par contexte pédagogique (§10-§11) et bornée au
     * périmètre du demandeur (§12-§13). Tous les paramètres sont facultatifs :
     * <ul>
     *   <li>{@code programId} + {@code semesterId} : modules d'un contexte précis
     *       (alimente la liste déroulante d'ajout de séance) ;</li>
     *   <li>{@code actif} : ne renvoyer que les modules actifs ({@code true}) ou
     *       inactifs ({@code false}) ;</li>
     *   <li>aucun paramètre : ADMIN → tout ; RP → ses modules.</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<ModuleResponseDto>> getModules(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(moduleService.listModules(programId, semesterId, actif));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModuleResponseDto> updateModule(
            @PathVariable Long id, @Valid @RequestBody ModuleRequestDto request) {
        return ResponseEntity.ok(moduleService.updateModule(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateModule(@PathVariable Long id) {
        moduleService.deactivateModule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateModule(@PathVariable Long id) {
        moduleService.activateModule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyse d'impact avant suppression (§ preview) : seances et examens qui
     * bloquent la suppression du module. Alimente la modale de confirmation.
     */
    @GetMapping("/{id}/impact-suppression")
    public ResponseEntity<com.campusops.deletion.DeletionImpact> deletionImpact(@PathVariable Long id) {
        return ResponseEntity.ok(moduleService.getDeletionImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModule(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean cascade) {
        moduleService.deleteModule(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
