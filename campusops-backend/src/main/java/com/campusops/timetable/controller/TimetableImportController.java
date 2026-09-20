package com.campusops.timetable.controller;

import com.campusops.timetable.dto.TimetableImportPreviewDto;
import com.campusops.timetable.dto.TimetableImportResultDto;
import com.campusops.timetable.service.TimetableImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Import d'emploi du temps par un responsable pedagogique, en deux phases
 * (cahier des charges §5, §7, §8, §24). DISTINCT de l'import ADMIN historique
 * {@code /api/schedules/import} (§29 : ne pas casser l'existant).
 *
 * <p>Le fichier ne contient que des seances ; le contexte (annee, filiere,
 * niveau, promotion, groupe, semestre, session) est fourni en parametres depuis
 * l'interface (§5). La securite (acces a la filiere) est appliquee cote service
 * via {@code AccessScopeService} (§12) : aucune verification n'est laissee au
 * seul frontend.</p>
 *
 * <ul>
 *   <li>{@code POST /preview} : analyse et rapport, sans aucun enregistrement.</li>
 *   <li>{@code POST /confirm} : enregistre les seances si aucune ligne en erreur.</li>
 *   <li>{@code GET /template} : modele Excel contextualise a telecharger.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/timetables/import")
@RequiredArgsConstructor
public class TimetableImportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TimetableImportService timetableImportService;

    /** Phase 1 : previsualisation (aucun enregistrement). */
    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public ResponseEntity<TimetableImportPreviewDto> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("levelId") Long levelId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("groupId") Long groupId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(value = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(timetableImportService.preview(
                academicYearId, programId, levelId, promotionId,
                groupId, semesterId, sessionId, dateDebut, dateFin, file));
    }

    /** Phase 2 : confirmation (enregistrement transactionnel). */
    @PostMapping(value = "/confirm", consumes = "multipart/form-data")
    public ResponseEntity<TimetableImportResultDto> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("levelId") Long levelId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("groupId") Long groupId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(value = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(timetableImportService.confirm(
                academicYearId, programId, levelId, promotionId,
                groupId, semesterId, sessionId, dateDebut, dateFin, file));
    }

    /** Modele Excel contextualise (feuilles Séances / Instructions / Valeurs autorisées). */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam("academicYearId") Long academicYearId,
            @RequestParam("programId") Long programId,
            @RequestParam("levelId") Long levelId,
            @RequestParam("promotionId") Long promotionId,
            @RequestParam("groupId") Long groupId,
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("sessionId") Long sessionId) {
        byte[] template = timetableImportService.generateTemplate(
                academicYearId, programId, levelId, promotionId,
                groupId, semesterId, sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=modele_emploi_du_temps.xlsx")
                .body(template);
    }
}
