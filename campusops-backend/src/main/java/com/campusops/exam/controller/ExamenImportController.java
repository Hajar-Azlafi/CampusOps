package com.campusops.exam.controller;

import com.campusops.exam.dto.ExamenImportPreviewDto;
import com.campusops.exam.dto.ExamenImportResultDto;
import com.campusops.exam.service.ExamenImportService;
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
 * Import d'un PLANNING D'EXAMENS par un responsable pedagogique, en deux phases
 * (meme mecanique que l'import d'emploi du temps, §5/§7/§8/§24).
 *
 * <p>Le fichier ne contient que des examens (evenements dates) ; le contexte
 * (annee, filiere, promotion, semestre, session) est fourni en parametres depuis
 * l'interface (§5). Le groupe est facultatif et se saisit ligne par ligne. La
 * securite (acces a la filiere) est appliquee cote service via
 * {@code AccessScopeService} (§12).</p>
 *
 * <ul>
 *   <li>{@code POST /preview} : analyse et rapport, sans aucun enregistrement.</li>
 *   <li>{@code POST /confirm} : enregistre les examens si aucune ligne en erreur.</li>
 *   <li>{@code GET /template} : modele Excel contextualise a telecharger.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/examens/import")
@RequiredArgsConstructor
public class ExamenImportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExamenImportService examenImportService;

    /** Phase 1 : previsualisation (aucun enregistrement). */
    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public ResponseEntity<ExamenImportPreviewDto> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId) {
        return ResponseEntity.ok(examenImportService.preview(
                academicYearId, programId, promotionId, semesterId, sessionId, file));
    }

    /** Phase 2 : confirmation (enregistrement transactionnel). */
    @PostMapping(value = "/confirm", consumes = "multipart/form-data")
    public ResponseEntity<ExamenImportResultDto> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId) {
        return ResponseEntity.ok(examenImportService.confirm(
                academicYearId, programId, promotionId, semesterId, sessionId, file));
    }

    /** Modele Excel contextualise (feuilles Examens / Instructions / Valeurs autorisées). */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId) {
        byte[] template = examenImportService.generateTemplate(
                academicYearId, programId, promotionId, semesterId, sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=modele_planning_examens.xlsx")
                .body(template);
    }
}
