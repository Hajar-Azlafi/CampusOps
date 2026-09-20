package com.campusops.occupation.controller;

import com.campusops.enums.OccupationCategorie;
import com.campusops.occupation.dto.OccupationImportPreviewDto;
import com.campusops.occupation.dto.OccupationImportResultDto;
import com.campusops.occupation.service.OccupationImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import d'un planning d'<b>occupations supplémentaires</b> — soutenances ou
 * occupations « autre » — en deux phases, même mécanique que l'import d'examens
 * et d'emploi du temps (§5/§7/§8/§24).
 *
 * <p>Le fichier ne contient que les événements datés. Le contexte — la
 * {@code categorie} (obligatoire), la filière et la promotion (facultatives selon
 * la catégorie) — est fourni en paramètres depuis l'interface (§5). La sécurité
 * par périmètre est appliquée côté service via {@code AccessScopeService} (§12) :
 * une soutenance exige une filière accessible, une occupation « autre » sans
 * filière est réservée à l'administrateur.</p>
 *
 * <ul>
 *   <li>{@code POST /preview} : analyse et rapport, sans aucun enregistrement.</li>
 *   <li>{@code POST /confirm} : enregistre les occupations si aucune ligne n'est en erreur.</li>
 *   <li>{@code GET /template} : modèle Excel contextualisé à télécharger.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/occupations/import")
@RequiredArgsConstructor
public class OccupationImportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final OccupationImportService occupationImportService;

    /** Phase 1 : prévisualisation (aucun enregistrement). */
    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public ResponseEntity<OccupationImportPreviewDto> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categorie") OccupationCategorie categorie,
            @RequestParam(value = "programId", required = false) Long programId,
            @RequestParam(value = "promotionId", required = false) Long promotionId) {
        return ResponseEntity.ok(occupationImportService.preview(
                categorie, programId, promotionId, file));
    }

    /** Phase 2 : confirmation (enregistrement transactionnel). */
    @PostMapping(value = "/confirm", consumes = "multipart/form-data")
    public ResponseEntity<OccupationImportResultDto> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categorie") OccupationCategorie categorie,
            @RequestParam(value = "programId", required = false) Long programId,
            @RequestParam(value = "promotionId", required = false) Long promotionId) {
        return ResponseEntity.ok(occupationImportService.confirm(
                categorie, programId, promotionId, file));
    }

    /**
     * Modèle Excel contextualisé : feuille de saisie (« Soutenances » ou
     * « Autres occupations »), « Instructions » et « Valeurs autorisées ».
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam("categorie") OccupationCategorie categorie,
            @RequestParam(value = "programId", required = false) Long programId,
            @RequestParam(value = "promotionId", required = false) Long promotionId) {
        byte[] template = occupationImportService.generateTemplate(categorie, programId, promotionId);
        String fileName = (categorie == OccupationCategorie.SOUTENANCE)
                ? "modele_planning_soutenances.xlsx"
                : "modele_autres_occupations.xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .body(template);
    }
}
