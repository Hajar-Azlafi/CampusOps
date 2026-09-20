package com.campusops.exam.service;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.exam.dto.ExamenImportPreviewDto;
import com.campusops.exam.dto.ExamenImportResultDto;
import com.campusops.exam.dto.ExamenImportRowDto;
import com.campusops.exam.dto.ExamenRequestDto;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.notification.service.NotificationService;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.settings.service.ImportPolicyService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Import d'un PLANNING D'EXAMENS par un responsable pedagogique, en DEUX PHASES
 * (meme mecanique que l'import d'emploi du temps, §5/§7/§8/§24) :
 *
 * <ol>
 *   <li>{@link #preview} : le fichier est analyse ligne par ligne et confronte
 *       aux donnees deja presentes (conflits GLOBAUX §33), SANS rien enregistrer.
 *       Un rapport detaille est renvoye (compteurs + erreurs par ligne).</li>
 *   <li>{@link #confirm} : rejoue l'analyse et, uniquement si AUCUNE ligne n'est
 *       en erreur, enregistre les examens (chacun via {@link ExamenService}).</li>
 * </ol>
 *
 * <p>Le fichier ne contient QUE des examens : le contexte (annee, filiere,
 * promotion, semestre, session) est choisi dans l'interface et transmis en
 * parametres (§5). Le <b>groupe</b> est facultatif et se saisit ligne par ligne
 * (vide = toute la promotion), car un planning d'examens melange souvent des
 * epreuves par groupe et par promotion.</p>
 *
 * <p>Contrairement a une seance recurrente (portant un <em>jour</em> de la
 * semaine), un examen est un <b>evenement date</b> : la colonne cle est une
 * <b>date</b> precise. Les regles de conflit (salle + public) et la validation de
 * periode (l'examen doit tomber dans la fenetre du semestre) ne sont PAS
 * reimplementees ici : elles sont deleguees a {@link ExamenService}
 * ({@link ExamenService#dryRunImportErrors} en phase 1,
 * {@link ExamenService#createExamen} en phase 2) pour garder une source unique de
 * verite. Aucune entite academique (salle, module, groupe) n'est creee par
 * l'import : tout est resolu par correspondance, jamais cree (§21/§29).</p>
 *
 * <p>Securite (§12) : chaque phase exige l'acces a la filiere ciblee (ADMIN :
 * tout ; RP : SES filieres) via {@link AccessScopeService}.</p>
 */
@Service
@RequiredArgsConstructor
public class ExamenImportService {

    /** Module trace dans le journal d'audit (§34). */
    private static final String MODULE = "Examens";

    /** En-tetes attendus de la feuille des examens (dans cet ordre). */
    private static final String[] EXAMEN_HEADERS = {
            "Date", "Heure début", "Heure fin", "Module",
            "Salle (code)", "Groupe (optionnel)", "Commentaire"
    };

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final SpaceRepository spaceRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final SessionUniversitaireRepository sessionRepository;
    private final ModuleRepository moduleRepository;
    private final ExamenService examenService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccessScopeService accessScope;
    private final ImportPolicyService importPolicy;

    // ----- Phase 1 : previsualisation (aucun enregistrement) -----

    @Transactional(readOnly = true)
    public ExamenImportPreviewDto preview(Long academicYearId, Long programId, Long promotionId,
                                          Long semesterId, Long sessionId, MultipartFile file) {
        ImportContext ctx = resolveContext(academicYearId, programId, promotionId, semesterId, sessionId);
        List<ParsedRow> rows = analyze(ctx, file);
        return buildPreview(ctx, originalFilename(file), rows);
    }

    // ----- Phase 2 : confirmation (transactionnelle) -----

    @Transactional
    public ExamenImportResultDto confirm(Long academicYearId, Long programId, Long promotionId,
                                         Long semesterId, Long sessionId, MultipartFile file) {
        ImportContext ctx = resolveContext(academicYearId, programId, promotionId, semesterId, sessionId);
        List<ParsedRow> rows = analyze(ctx, file);

        if (rows.isEmpty()) {
            throw new BadRequestException("Aucun examen détecté dans le fichier.");
        }
        long enErreur = rows.stream().filter(r -> !r.isValid()).count();
        // Validation automatique (Module 11, §9) : active (defaut), le fichier est
        // refuse en bloc des la premiere ligne invalide ; desactivee, seules les
        // lignes valides sont enregistrees.
        if (enErreur > 0 && importPolicy.isValidationStricte()) {
            throw new BadRequestException("Le fichier contient " + enErreur
                    + " ligne(s) en erreur : corrigez-les puis relancez l'import. "
                    + "Aucun examen n'a été enregistré.");
        }
        List<ParsedRow> retenues = rows.stream().filter(ParsedRow::isValid).toList();
        if (retenues.isEmpty()) {
            throw new BadRequestException("Aucune ligne valide dans le fichier : "
                    + enErreur + " ligne(s) en erreur. Aucun examen n'a été enregistré.");
        }

        int saved = 0;
        for (ParsedRow pr : retenues) {
            // Persistance via ExamenService : re-resout le contexte + re-verifie
            // les conflits (source unique de verite). Toute erreur residuelle
            // (course entre deux imports) fait echouer la transaction entiere.
            examenService.createExamen(toRequest(ctx, pr));
            saved++;
        }

        String fileName = originalFilename(file);
        String lignesIgnorees = enErreur == 0 ? ""
                : String.format(" — %d ligne(s) en erreur ignorée(s)", enErreur);
        String description = String.format(
                "Import de planning d'examens « %s / %s / %s / %s » depuis '%s' : %d examen(s) enregistré(s)%s",
                ctx.program.getNom(), ctx.promotion.getNom(), ctx.semester.getNom(),
                ctx.session.getNom(), fileName, saved, lignesIgnorees);
        auditService.record(AuditAction.EXCEL_IMPORT, MODULE, description);
        notificationService.notifyUser(accessScope.getCurrentUser(),
                NotificationType.SCHEDULE_IMPORTED,
                "Import de planning d'examens terminé", description,
                "/occupations?onglet=examens");

        return ExamenImportResultDto.builder()
                .fileName(fileName)
                .examensEnregistres(saved)
                .message(saved + " examen(s) enregistré(s)."
                        + (enErreur == 0 ? ""
                        : " " + enErreur + " ligne(s) en erreur ont été ignorées"
                        + " (validation automatique désactivée)."))
                .build();
    }

    /** Construit la requete d'examen (IDs resolus) attendue par {@link ExamenService}. */
    private ExamenRequestDto toRequest(ImportContext ctx, ParsedRow pr) {
        return ExamenRequestDto.builder()
                .date(pr.date)
                .timeSlotId(pr.timeSlot.getId())
                .spaceId(pr.space.getId())
                .moduleId(pr.moduleEntity.getId())
                .promotionId(ctx.promotion.getId())
                .groupId(pr.group != null ? pr.group.getId() : null)
                .sessionId(ctx.session.getId())
                .commentaire(pr.commentaire)
                .build();
    }

    // ----- Analyse (partagee entre preview et confirm) -----

    /**
     * Analyse le fichier ligne par ligne : structure, existence de la salle/module/
     * groupe, puis conflits (DELEGUES a {@link ExamenService#dryRunImportErrors}
     * pour les conflits GLOBAUX §33, ET internes au fichier). Toutes les erreurs
     * d'une ligne sont collectees (on ne s'arrete pas a la premiere).
     */
    private List<ParsedRow> analyze(ImportContext ctx, MultipartFile file) {
        // Taille, extension ET signature binaire, selon les parametres d'import (§9).
        importPolicy.verifierFichier(file, "du planning d'examens");

        RefData ref = new RefData(
                spaceRepository.findAll(),
                timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true),
                moduleRepository.findByProgramIdAndSemesterIdOrderByNomAsc(
                        ctx.program.getId(), ctx.semester.getId()),
                groupRepository.findByPromotionId(ctx.promotion.getId()));
        List<ParsedRow> rows = new ArrayList<>();
        List<ParsedRow> accepted = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = getExamensSheet(workbook);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                ParsedRow pr = new ParsedRow();
                pr.rowNumber = rowIndex + 1;
                pr.dateRaw = readDateCell(row, 0);
                pr.debutRaw = readTimeCell(row, 1);
                pr.finRaw = readTimeCell(row, 2);
                pr.moduleRaw = getCellValue(row, 3);
                pr.salleRaw = getCellValue(row, 4);
                pr.groupeRaw = getCellValue(row, 5);
                pr.commentaireRaw = getCellValue(row, 6);

                parseStructure(pr, ctx, ref);
                if (pr.isValid()) {
                    // Conflits GLOBAUX + coherence + periode du semestre : source
                    // unique de verite (ExamenService), reutilisee sans duplication.
                    for (String err : examenService.dryRunImportErrors(toRequest(ctx, pr))) {
                        pr.errors.add(err);
                        pr.conflit = true;
                    }
                }
                if (pr.isValid()) {
                    checkFileConflicts(pr, accepted);
                }
                if (pr.isValid()) {
                    accepted.add(pr);
                }
                rows.add(pr);
            }
        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + e.getMessage());
        }

        return rows;
    }

    /** Analyse structurelle d'une ligne : date, horaires/creneau, module, salle, groupe. */
    private void parseStructure(ParsedRow pr, ImportContext ctx, RefData ref) {
        // Date (§20 : evenement date). La borne semestre est verifiee par ExamenService.
        try {
            pr.date = parseDate(pr.dateRaw);
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.dateInvalide = true;
        }

        // Horaires -> creneau configure (correspondance exacte, aucune creation §21).
        LocalTime debut = null;
        LocalTime fin = null;
        try {
            debut = parseTime(pr.debutRaw, "heure de début");
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
        }
        try {
            fin = parseTime(pr.finRaw, "heure de fin");
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
        }
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            pr.errors.add("L'heure de fin doit être postérieure à l'heure de début.");
        }
        if (debut != null && fin != null && fin.isAfter(debut)) {
            resolveTimeSlot(pr, debut, fin, ref);
        }

        resolveModule(pr, ctx, ref);
        resolveSalle(pr, ref.spaces);
        resolveGroupe(pr, ctx, ref);

        pr.commentaire = isBlank(pr.commentaireRaw) ? null : pr.commentaireRaw.trim();
    }

    /** Rattache la ligne au creneau configure dont les heures correspondent (§21). */
    private void resolveTimeSlot(ParsedRow pr, LocalTime debut, LocalTime fin, RefData ref) {
        TimeSlot match = ref.slots.stream()
                .filter(s -> s.getHeureDebut().equals(debut) && s.getHeureFin().equals(fin))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucun créneau horaire configuré ne correspond à "
                    + debut.format(HOUR) + "–" + fin.format(HOUR)
                    + ". Utilisez un créneau existant (voir « Valeurs autorisées »).");
            pr.creneauInexistant = true;
        } else {
            pr.timeSlot = match;
        }
    }

    /** Resout le module dans le referentiel du contexte (filiere + semestre). Aucune creation. */
    private void resolveModule(ParsedRow pr, ImportContext ctx, RefData ref) {
        if (isBlank(pr.moduleRaw)) {
            pr.errors.add("Le module (matière évaluée) est obligatoire.");
            return;
        }
        String needle = stripAccents(pr.moduleRaw.trim());
        Module match = ref.modules.stream()
                .filter(m -> stripAccents(m.getNom()).equalsIgnoreCase(needle))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucun module « " + pr.moduleRaw.trim() + " » n'existe pour "
                    + ctx.program.getNom() + " / " + ctx.semester.getNom()
                    + ". Créez-le au préalable (aucun module n'est créé par l'import).");
            pr.moduleInexistant = true;
        } else if (!match.isActif()) {
            pr.errors.add("Le module « " + match.getNom() + " » est désactivé : "
                    + "réactivez-le ou choisissez un module actif.");
            pr.moduleInexistant = true;
        } else {
            pr.moduleEntity = match;
        }
    }

    /** Resout la salle par code. Un examen occupe TOUJOURS une salle (obligatoire). */
    private void resolveSalle(ParsedRow pr, List<Space> allSpaces) {
        String code = isBlank(pr.salleRaw) ? "" : pr.salleRaw.trim();
        if (code.isBlank()) {
            pr.errors.add("Un examen doit indiquer une salle (code).");
            pr.salleManquante = true;
            return;
        }
        Space match = allSpaces.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucune salle trouvée avec le code « " + code + " ».");
            pr.salleInexistante = true;
            return;
        }
        // §5/§21/§23 : une salle inactive (ou dont l'etage/batiment est desactive)
        // ne peut pas accueillir un nouvel examen importe. Message explicite nommant
        // la cause exacte, joint aux autres erreurs de la ligne (non levant) plutot
        // que d'interrompre l'analyse du fichier. Redondant avec la garde de
        // ExamenService.checkConflicts (via dryRunImportErrors), mais categorise
        // clairement l'erreur en « salle » des la phase structurelle.
        String motifSalle = ReferentialStatus.reason(match);
        if (motifSalle != null) {
            pr.errors.add(motifSalle);
            pr.salleInexistante = true;
            return;
        }
        pr.space = match;
    }

    /** Resout le groupe (facultatif) dans la promotion. Vide = toute la promotion. */
    private void resolveGroupe(ParsedRow pr, ImportContext ctx, RefData ref) {
        if (isBlank(pr.groupeRaw)) {
            pr.group = null; // examen adresse a toute la promotion
            return;
        }
        String needle = stripAccents(pr.groupeRaw.trim());
        Group match = ref.groups.stream()
                .filter(g -> stripAccents(g.getNom()).equalsIgnoreCase(needle))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucun groupe « " + pr.groupeRaw.trim() + " » dans la promotion "
                    + ctx.promotion.getNom() + " (laissez vide pour toute la promotion).");
            pr.groupeInexistant = true;
        } else {
            pr.group = match;
        }
    }

    /**
     * Conflits INTERNES au fichier : deux examens le meme jour dont les creneaux se
     * chevauchent sont en conflit s'ils partagent la salle, OU s'ils visent un
     * public non disjoint (meme promotion : sauf deux groupes precis distincts).
     */
    private void checkFileConflicts(ParsedRow pr, List<ParsedRow> accepted) {
        for (ParsedRow other : accepted) {
            if (!pr.date.equals(other.date)) {
                continue;
            }
            if (!overlaps(pr.timeSlot.getHeureDebut(), pr.timeSlot.getHeureFin(),
                    other.timeSlot.getHeureDebut(), other.timeSlot.getHeureFin())) {
                continue;
            }
            if (pr.space.getId().equals(other.space.getId())) {
                pr.errors.add("Conflit de salle avec l'examen de la ligne "
                        + other.rowNumber + " du fichier (même salle, créneaux qui se chevauchent).");
                pr.conflit = true;
                return;
            }
            Long myGroupId = (pr.group != null) ? pr.group.getId() : null;
            Long otherGroupId = (other.group != null) ? other.group.getId() : null;
            boolean disjointGroups = myGroupId != null && otherGroupId != null
                    && !myGroupId.equals(otherGroupId);
            if (!disjointGroups) {
                pr.errors.add("Conflit de public avec l'examen de la ligne "
                        + other.rowNumber + " du fichier (même promotion/groupe, créneaux qui se chevauchent).");
                pr.conflit = true;
                return;
            }
        }
    }

    // ----- Construction du rapport de previsualisation -----

    private ExamenImportPreviewDto buildPreview(ImportContext ctx, String fileName, List<ParsedRow> rows) {
        List<ExamenImportRowDto> lignes = new ArrayList<>();
        int valides = 0;
        int enErreur = 0;
        int conflits = 0;
        int sallesInexistantes = 0;
        int sallesManquantes = 0;
        int datesInvalides = 0;
        int creneauxInexistants = 0;
        int modulesInexistants = 0;
        int groupesInexistants = 0;
        for (ParsedRow pr : rows) {
            lignes.add(toRowDto(pr));
            if (pr.isValid()) {
                valides++;
            } else {
                enErreur++;
            }
            if (pr.conflit) {
                conflits++;
            }
            if (pr.salleInexistante) {
                sallesInexistantes++;
            }
            if (pr.salleManquante) {
                sallesManquantes++;
            }
            if (pr.dateInvalide) {
                datesInvalides++;
            }
            if (pr.creneauInexistant) {
                creneauxInexistants++;
            }
            if (pr.moduleInexistant) {
                modulesInexistants++;
            }
            if (pr.groupeInexistant) {
                groupesInexistants++;
            }
        }

        Level level = ctx.promotion.getLevel();
        return ExamenImportPreviewDto.builder()
                .fileName(fileName)
                .anneeUniversitaire(ctx.academicYear.getLibelle())
                .filiere(ctx.program.getNom())
                .niveau(level != null ? level.getNom() : null)
                .promotion(ctx.promotion.getNom())
                .semestre(ctx.semester.getNom())
                .session(ctx.session.getNom())
                .examensDetectes(rows.size())
                .examensValides(valides)
                .lignesEnErreur(enErreur)
                .conflits(conflits)
                .sallesInexistantes(sallesInexistantes)
                .sallesManquantes(sallesManquantes)
                .datesInvalides(datesInvalides)
                .creneauxInexistants(creneauxInexistants)
                .modulesInexistants(modulesInexistants)
                .groupesInexistants(groupesInexistants)
                // Confirmable seulement si au moins un examen et AUCUNE erreur (§8).
                .confirmable(!rows.isEmpty() && enErreur == 0)
                .lignes(lignes)
                .build();
    }

    private ExamenImportRowDto toRowDto(ParsedRow pr) {
        return ExamenImportRowDto.builder()
                .ligne(pr.rowNumber)
                .date(pr.dateRaw)
                .heureDebut(pr.debutRaw)
                .heureFin(pr.finRaw)
                .module(pr.moduleRaw)
                .salle(pr.salleRaw)
                .groupe(pr.groupeRaw)
                .commentaire(pr.commentaireRaw)
                .valide(pr.isValid())
                .erreurs(new ArrayList<>(pr.errors))
                .build();
    }

    // ----- Modele Excel contextualise (§24/§25) -----

    /**
     * Genere le modele d'import d'examens pour un contexte donne : une feuille
     * « Examens » a remplir, une feuille « Instructions » (regles + rappel du
     * contexte, qui n'est PAS a saisir §5) et une feuille « Valeurs autorisées »
     * (creneaux, modules du contexte, groupes de la promotion, salles). Aucun nom
     * d'etablissement code en dur (§26) : les exemples sont generiques.
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long academicYearId, Long programId, Long promotionId,
                                   Long semesterId, Long sessionId) {
        ImportContext ctx = resolveContext(academicYearId, programId, promotionId, semesterId, sessionId);

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            buildExamensSheet(workbook, headerStyle, ctx);
            buildInstructionsSheet(workbook, headerStyle, ctx);
            buildAllowedValuesSheet(workbook, headerStyle, ctx);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la génération du modèle Excel.");
        }
    }

    private void buildExamensSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Examens");
        Row header = sheet.createRow(0);
        for (int i = 0; i < EXAMEN_HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(EXAMEN_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        List<TimeSlot> slots = timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true);
        List<Module> modules = moduleRepository.findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
                ctx.program.getId(), ctx.semester.getId(), true);
        List<Group> groups = groupRepository.findByPromotionId(ctx.promotion.getId());

        String slot1Debut = slots.isEmpty() ? "08:30" : slots.get(0).getHeureDebut().format(HOUR);
        String slot1Fin = slots.isEmpty() ? "10:25" : slots.get(0).getHeureFin().format(HOUR);
        String slot2Debut = slots.size() < 2 ? "10:35" : slots.get(1).getHeureDebut().format(HOUR);
        String slot2Fin = slots.size() < 2 ? "12:30" : slots.get(1).getHeureFin().format(HOUR);
        String module1 = modules.isEmpty() ? "Nom exact du module" : modules.get(0).getNom();
        String module2 = modules.size() < 2 ? module1 : modules.get(1).getNom();
        String groupe1 = groups.isEmpty() ? "" : groups.get(0).getNom();
        String exampleSalle = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(Space::getCode)
                .findFirst()
                .orElse("CODE-SALLE");

        // Bornes de dates du semestre pour l'exemple (l'examen doit y tomber, §20).
        LocalDate exampleDate = (ctx.semester.getDateDebut() != null)
                ? ctx.semester.getDateDebut() : LocalDate.now();

        String[][] examples = {
                {exampleDate.format(DAY), slot1Debut, slot1Fin, module1, exampleSalle, groupe1,
                        "Épreuve écrite — prévoir 2 surveillants"},
                {exampleDate.format(DAY), slot2Debut, slot2Fin, module2, exampleSalle, "",
                        "Examen commun à toute la promotion (groupe laissé vide)"}
        };
        for (int r = 0; r < examples.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < examples[r].length; c++) {
                row.createCell(c).setCellValue(examples[r][c]);
            }
        }
    }

    private void buildInstructionsSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Instructions");
        sheet.setColumnWidth(0, 12000);

        int r = 0;
        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Comment remplir ce modèle de planning d'examens");
        titleCell.setCellStyle(headerStyle);

        LocalDate semDebut = ctx.semester.getDateDebut();
        LocalDate semFin = ctx.semester.getDateFin();
        String periode = (semDebut != null && semFin != null)
                ? "du " + semDebut.format(DAY) + " au " + semFin.format(DAY)
                : "(période du semestre non renseignée)";

        String[] lines = {
                "",
                "1. Renseignez UN examen par ligne dans la feuille « Examens ».",
                "2. Colonnes obligatoires : Date, Heure début, Heure fin, Module, Salle (code).",
                "3. La Date est au format JJ/MM/AAAA (exemple : 05/01/2027) et doit tomber DANS la",
                "   période du semestre courant : " + periode + ".",
                "4. Les heures sont au format HH:mm (exemple : 08:30) et doivent correspondre EXACTEMENT",
                "   à un créneau horaire configuré (voir « Valeurs autorisées »). Aucun créneau n'est créé.",
                "5. Le Module doit exister dans le référentiel de ce contexte (filière + semestre) :",
                "   reprenez un nom de la liste « Modules du contexte ». L'import ne crée aucun module.",
                "6. La Salle (code) est OBLIGATOIRE : un examen occupe toujours une salle existante.",
                "7. Le Groupe est FACULTATIF : laissez-le vide pour un examen commun à toute la promotion,",
                "   ou indiquez un groupe de la promotion pour une épreuve ciblée.",
                "8. Le Commentaire est facultatif (consignes, surveillants, etc.).",
                "9. Consultez la feuille « Valeurs autorisées » pour les créneaux, les modules du contexte,",
                "   les groupes de la promotion et les codes de salle.",
                "10. Les accents et la casse sont ignorés lors de l'import.",
                "",
                "Rappel du contexte de ce planning d'examens",
                "(déjà sélectionné dans l'application — NE PAS le saisir dans le fichier) :",
                "   • Année universitaire : " + ctx.academicYear.getLibelle(),
                "   • Filière : " + ctx.program.getNom(),
                "   • Promotion : " + ctx.promotion.getNom(),
                "   • Semestre : " + ctx.semester.getNom(),
                "   • Session : " + ctx.session.getNom()
        };
        for (String line : lines) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
    }

    private void buildAllowedValuesSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Valeurs autorisées");

        String[] headers = {"Créneaux horaires (HH:mm–HH:mm)", "Modules du contexte",
                "Groupes de la promotion", "Salles disponibles (code — nom)"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 8000);
        }

        List<String> creneaux = timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true).stream()
                .map(s -> s.getHeureDebut().format(HOUR) + "–" + s.getHeureFin().format(HOUR))
                .toList();
        List<String> modules = moduleRepository.findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
                        ctx.program.getId(), ctx.semester.getId(), true).stream()
                .map(Module::getNom)
                .toList();
        List<String> groupes = groupRepository.findByPromotionId(ctx.promotion.getId()).stream()
                .map(Group::getNom)
                .sorted()
                .toList();
        List<String> salles = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(s -> s.getCode() + " — " + s.getNom())
                .sorted()
                .toList();

        int maxRows = List.of(creneaux.size(), modules.size(), groupes.size(), salles.size()).stream()
                .max(Integer::compareTo).orElse(0);
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < creneaux.size()) {
                row.createCell(0).setCellValue(creneaux.get(i));
            }
            if (i < modules.size()) {
                row.createCell(1).setCellValue(modules.get(i));
            }
            if (i < groupes.size()) {
                row.createCell(2).setCellValue(groupes.get(i));
            }
            if (i < salles.size()) {
                row.createCell(3).setCellValue(salles.get(i));
            }
        }
    }

    // ----- Contexte d'import -----

    /**
     * Resout et valide les 5 identifiants du contexte (annee, filiere, promotion,
     * semestre, session), verifie leur coherence mutuelle puis controle l'acces a
     * la filiere (§12/§14). Aucune de ces entites n'est jamais creee ici. Le
     * <b>groupe</b> ne fait pas partie du contexte : il est facultatif et resolu
     * ligne par ligne.
     */
    private ImportContext resolveContext(Long academicYearId, Long programId, Long promotionId,
                                         Long semesterId, Long sessionId) {
        ImportContext ctx = new ImportContext();
        ctx.academicYear = academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + academicYearId));
        ctx.program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + programId));
        ctx.promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + promotionId));
        ctx.semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semestre introuvable avec l'id " + semesterId));
        ctx.session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session universitaire introuvable avec l'id " + sessionId));

        if (ctx.promotion.getProgram() == null
                || !ctx.promotion.getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée n'appartient pas à la filière indiquée.");
        }
        if (ctx.promotion.getAcademicYear() == null
                || !ctx.promotion.getAcademicYear().getId().equals(ctx.academicYear.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée n'appartient pas à l'année universitaire indiquée.");
        }

        // Securite backend (§12) : acces a la filiere exige (ADMIN ou RP proprietaire).
        accessScope.assertProgramAccessible(ctx.program.getId());
        return ctx;
    }

    // ----- Utilitaires de parsing (feuille « Examens » uniquement) -----

    /** Feuille des examens : « Examens » si presente (accents/casse ignores), sinon la premiere. */
    private Sheet getExamensSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (stripAccents(sheet.getSheetName().trim()).equalsIgnoreCase("EXAMENS")) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    /**
     * Analyse une date au format {@code JJ/MM/AAAA} (ou {@code J/M/AAAA}, ou ISO
     * {@code AAAA-MM-JJ} par robustesse). La borne « dans le semestre » n'est pas
     * verifiee ici mais par {@link ExamenService} (source unique de verite).
     */
    private LocalDate parseDate(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("La date de l'examen est obligatoire.");
        }
        String value = raw.trim();
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{DAY, DAY_SHORT}) {
            try {
                return LocalDate.parse(value, fmt);
            } catch (Exception ignored) {
                // essai suivant
            }
        }
        try {
            return LocalDate.parse(value); // ISO yyyy-MM-dd
        } catch (Exception e) {
            throw new IllegalArgumentException("La date « " + raw
                    + " » n'est pas valide (format attendu JJ/MM/AAAA).");
        }
    }

    private LocalTime parseTime(String raw, String label) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("L'" + label + " est obligatoire.");
        }
        String value = raw.trim().replace('h', ':').replace('H', ':');
        if (value.matches("\\d*\\.\\d+")) {
            try {
                return LocalTime.parse(fractionToTime(Double.parseDouble(value)), HOUR);
            } catch (Exception ignored) {
                // on laisse la validation standard ci-dessous produire le message
            }
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e1) {
            try {
                return LocalTime.parse(value);
            } catch (Exception e2) {
                throw new IllegalArgumentException("L'" + label + " « " + raw
                        + " » n'est pas valide (format attendu HH:mm).");
            }
        }
    }

    /** Lit une cellule de date et renvoie une chaine « JJ/MM/AAAA » (cellule date Excel ou texte). */
    private String readDateCell(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt != null) {
                        yield dt.toLocalDate().format(DAY);
                    }
                }
                // Nombre brut : on le renvoie tel quel, parseDate signalera l'erreur.
                double numeric = cell.getNumericCellValue();
                yield (numeric == Math.floor(numeric))
                        ? String.valueOf((long) numeric) : String.valueOf(numeric);
            }
            case STRING -> cell.getStringCellValue().trim();
            default -> "";
        };
    }

    /** Lit une cellule d'heure et renvoie « HH:mm » (cellule numerique fraction ou texte). */
    private String readTimeCell(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt != null) {
                        yield dt.toLocalTime().format(HOUR);
                    }
                }
                yield fractionToTime(cell.getNumericCellValue());
            }
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Convertit une fraction de journee Excel en heure « HH:mm » (arrondi minute). */
    private String fractionToTime(double numeric) {
        double fraction = numeric - Math.floor(numeric);
        long totalMinutes = Math.round(fraction * 24 * 60);
        totalMinutes = ((totalMinutes % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < EXAMEN_HEADERS.length; i++) {
            if (!getCellValue(row, i).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numeric = cell.getNumericCellValue();
                if (numeric == Math.floor(numeric)) {
                    yield String.valueOf((long) numeric);
                }
                yield String.valueOf(numeric);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String originalFilename(MultipartFile file) {
        return (file != null && file.getOriginalFilename() != null)
                ? file.getOriginalFilename() : "import_examens.xlsx";
    }

    // ----- Structures internes -----

    /** Contexte d'import resolu et valide (les 5 entites du perimetre ; groupe hors contexte). */
    private static class ImportContext {
        private AcademicYear academicYear;
        private Program program;
        private Promotion promotion;
        private Semester semester;
        private SessionUniversitaire session;
    }

    /** Referentiels charges une seule fois pour toute l'analyse (§21). */
    private static class RefData {
        private final List<Space> spaces;
        private final List<TimeSlot> slots;
        private final List<Module> modules;
        private final List<Group> groups;

        private RefData(List<Space> spaces, List<TimeSlot> slots,
                        List<Module> modules, List<Group> groups) {
            this.spaces = spaces;
            this.slots = slots;
            this.modules = modules;
            this.groups = groups;
        }
    }

    /** Etat d'analyse d'une ligne du fichier (valeurs brutes + resolues + erreurs). */
    private static class ParsedRow {
        private int rowNumber;
        private String dateRaw;
        private String debutRaw;
        private String finRaw;
        private String moduleRaw;
        private String salleRaw;
        private String groupeRaw;
        private String commentaireRaw;

        private LocalDate date;
        private TimeSlot timeSlot;
        private Module moduleEntity;
        private Space space;
        private Group group; // nullable : null = toute la promotion
        private String commentaire;

        private final List<String> errors = new ArrayList<>();
        private boolean conflit;
        private boolean salleInexistante;
        private boolean salleManquante;
        private boolean dateInvalide;
        private boolean creneauInexistant;
        private boolean moduleInexistant;
        private boolean groupeInexistant;

        private boolean isValid() {
            return errors.isEmpty();
        }
    }
}





