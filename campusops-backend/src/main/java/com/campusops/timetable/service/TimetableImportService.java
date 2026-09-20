package com.campusops.timetable.service;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.PresenceType;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.SessionType;
import com.campusops.enums.TimetableSource;
import com.campusops.enums.TimetableStatus;
import com.campusops.enums.WeekDay;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.notification.service.NotificationService;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.settings.service.ImportPolicyService;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.timetable.dto.TimetableImportPreviewDto;
import com.campusops.timetable.dto.TimetableImportResultDto;
import com.campusops.timetable.dto.TimetableImportRowDto;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.typeseance.repository.TypeSeanceRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Import d'un emploi du temps par un responsable pedagogique, en DEUX PHASES
 * (cahier des charges §5, §7, §8, §24) :
 *
 * <ol>
 *   <li>{@link #preview} : le fichier est analyse ligne par ligne et confronte
 *       aux donnees deja presentes (conflits GLOBAUX §33), SANS rien enregistrer.
 *       Un rapport detaille est renvoye (compteurs + erreurs par ligne).</li>
 *   <li>{@link #confirm} : rejoue l'analyse et, uniquement si AUCUNE ligne n'est
 *       en erreur, enregistre les seances en les rattachant a l'en-tete
 *       {@code EmploiDuTemps} du contexte (cree ou reutilise).</li>
 * </ol>
 *
 * <p>Le fichier ne contient QUE des seances : le contexte (annee, filiere,
 * niveau, promotion, groupe, semestre, session) est choisi dans l'interface et
 * lie automatiquement (§5). Aucune structure academique (salle, groupe...) n'est
 * creee par cet import : les salles doivent exister au prealable (resolution par
 * code, jamais de creation). Ce service est volontairement DISTINCT de l'import
 * ADMIN historique ({@code /api/schedules/import}) qui, lui, resout tout par nom
 * et peut creer des entites globales (§29 : ne pas casser l'existant).</p>
 *
 * <p>Securite (§12) : {@code preview}, {@code confirm} et la generation du modele
 * exigent l'acces a la filiere ciblee (ADMIN : tout ; responsable pedagogique :
 * SES filieres uniquement) via {@link AccessScopeService#assertProgramAccessible}.
 * Aucune donnee d'une autre filiere n'est atteignable, meme par appel direct.</p>
 */
@Service
@RequiredArgsConstructor
public class TimetableImportService {

    /** Module trace dans le journal d'audit (§34). */
    private static final String MODULE = "Emplois du temps";

    /** En-tetes attendus de la feuille des seances (dans cet ordre). */
    private static final String[] SEANCE_HEADERS = {
            "Jour", "Heure début", "Heure fin", "Module", "Type de séance",
            "Type de présence", "Salle (code)", "Enseignant"
    };

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Statuts de reservation qui bloquent effectivement une salle. */
    private static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED);

    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final ScheduleRepository scheduleRepository;
    private final SpaceRepository spaceRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final SessionUniversitaireRepository sessionRepository;
    private final ModuleRepository moduleRepository;
    private final TypeSeanceRepository typeSeanceRepository;
    private final ReservationRepository reservationRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccessScopeService accessScope;
    private final ImportPolicyService importPolicy;
    private final SettingsService settingsService;

    // ----- Phase 1 : previsualisation (aucun enregistrement) -----

    @Transactional(readOnly = true)
    public TimetableImportPreviewDto preview(Long academicYearId, Long programId, Long levelId,
                                             Long promotionId, Long groupId, Long semesterId,
                                             Long sessionId, LocalDate dateDebut, LocalDate dateFin,
                                             MultipartFile file) {
        ImportContext ctx = resolveContext(academicYearId, programId, levelId,
                promotionId, groupId, semesterId, sessionId);
        // Fenetre de validite choisie a l'import : elle doit rester dans la
        // periode du semestre courant selectionne (les EDT ne sont plus valables
        // hors semestre, notamment en periode d'examens). Validee des la phase 1
        // pour que le RP corrige avant de confirmer.
        assertPeriodeDansSemestre(ctx, dateDebut, dateFin);
        List<ParsedRow> rows = analyze(ctx, file);
        return buildPreview(ctx, originalFilename(file), rows);
    }

    // ----- Phase 2 : confirmation (transactionnelle) -----

    @Transactional
    public TimetableImportResultDto confirm(Long academicYearId, Long programId, Long levelId,
                                            Long promotionId, Long groupId, Long semesterId,
                                            Long sessionId, LocalDate dateDebut, LocalDate dateFin,
                                            MultipartFile file) {
        ImportContext ctx = resolveContext(academicYearId, programId, levelId,
                promotionId, groupId, semesterId, sessionId);
        assertPeriodeDansSemestre(ctx, dateDebut, dateFin);
        List<ParsedRow> rows = analyze(ctx, file);

        if (rows.isEmpty()) {
            throw new BadRequestException("Aucune séance détectée dans le fichier.");
        }
        long enErreur = rows.stream().filter(r -> !r.isValid()).count();
        // Validation automatique (Module 11, §9) : active, une seule ligne invalide
        // refuse le fichier entier — comportement historique de cet import.
        // Desactivee, l'import devient tolerant : les lignes valides sont
        // enregistrees et les lignes rejetees sont annoncees dans le message.
        if (enErreur > 0 && importPolicy.isValidationStricte()) {
            throw new BadRequestException("Le fichier contient " + enErreur
                    + " ligne(s) en erreur : corrigez-les puis relancez l'import. "
                    + "Aucune séance n'a été enregistrée.");
        }
        List<ParsedRow> retenues = rows.stream().filter(ParsedRow::isValid).toList();
        if (retenues.isEmpty()) {
            throw new BadRequestException("Aucune ligne valide dans le fichier : "
                    + enErreur + " ligne(s) en erreur. Aucune séance n'a été enregistrée.");
        }

        // En-tete d'emploi du temps : cree si absent, complete s'il existe (§20 :
        // aucun ecrasement, on ajoute les seances au contexte).
        EmploiDuTemps header = emploiDuTempsRepository
                .findByAcademicYearIdAndGroupIdAndSemesterIdAndSessionId(
                        ctx.academicYear.getId(), ctx.group.getId(),
                        ctx.semester.getId(), ctx.session.getId())
                .orElse(null);
        boolean created = false;
        if (header == null) {
            // Fenetre de validite choisie par le RP a l'import, bornee au semestre
            // courant (assertPeriodeDansSemestre l'a deja verifiee). A defaut de
            // choix explicite, on retombe sur la periode DERIVEE du semestre (§20).
            LocalDate[] periode = periodeDuSemestre(ctx);
            LocalDate debutEffectif = dateDebut != null ? dateDebut : periode[0];
            LocalDate finEffective = dateFin != null ? dateFin : periode[1];
            header = emploiDuTempsRepository.save(EmploiDuTemps.builder()
                    .academicYear(ctx.academicYear)
                    .program(ctx.program)
                    .level(ctx.level)
                    .promotion(ctx.promotion)
                    .group(ctx.group)
                    .semester(ctx.semester)
                    .session(ctx.session)
                    .dateDebut(debutEffectif)
                    .dateFin(finEffective)
                    .statut(TimetableStatus.BROUILLON)
                    .source(TimetableSource.IMPORT)
                    .importePar(accessScope.getCurrentUser())
                    .build());
            created = true;
        }

        int saved = 0;
        for (ParsedRow pr : retenues) {
            scheduleRepository.save(Schedule.builder()
                    .jour(pr.jour)
                    .timeSlot(pr.timeSlot)
                    .space(pr.space)
                    .program(ctx.program)
                    .level(ctx.level)
                    .promotion(ctx.promotion)
                    .group(ctx.group)
                    .semester(ctx.semester)
                    .academicYear(ctx.academicYear)
                    .emploiDuTemps(header)
                    .module(pr.moduleEntity)
                    // enseignant est NOT NULL en base : chaine vide si non renseigne (§6).
                    .enseignant(pr.enseignant)
                    .matiere(pr.module)
                    .type(pr.type)
                    .typeSeance(pr.typeSeance)
                    .typePresence(pr.presence)
                    .actif(true)
                    .build());
            saved++;
        }

        String fileName = originalFilename(file);
        String lignesIgnorees = enErreur == 0 ? ""
                : String.format(" — %d ligne(s) en erreur ignorée(s)", enErreur);
        String description = String.format(
                "Import d'emploi du temps « %s / %s / %s / %s » depuis '%s' : %d séance(s) enregistrée(s)%s",
                ctx.program.getNom(), ctx.group.getNom(), ctx.semester.getNom(),
                ctx.session.getNom(), fileName, saved, lignesIgnorees);
        auditService.record(AuditAction.EXCEL_IMPORT, MODULE, description);
        notificationService.notifyUser(accessScope.getCurrentUser(),
                NotificationType.SCHEDULE_IMPORTED,
                "Import d'emploi du temps terminé", description, "/timetables");

        return TimetableImportResultDto.builder()
                .emploiDuTempsId(header.getId())
                .fileName(fileName)
                .seancesEnregistrees(saved)
                .emploiDuTempsCree(created)
                .message((created
                        ? "Emploi du temps créé et " + saved + " séance(s) enregistrée(s)."
                        : saved + " séance(s) ajoutée(s) à l'emploi du temps existant.")
                        + (enErreur == 0 ? ""
                        : " " + enErreur + " ligne(s) en erreur ont été ignorées"
                        + " (validation automatique désactivée)."))
                .build();
    }

    // ----- Analyse (partagee entre preview et confirm) -----

    /**
     * Analyse le fichier ligne par ligne : structure, existence de la salle,
     * puis conflits (GLOBAUX face aux seances deja enregistrees, §33, ET internes
     * au fichier). Toutes les erreurs d'une ligne sont collectees (on ne s'arrete
     * pas a la premiere) afin que le RP puisse tout corriger d'un coup.
     */
    private List<ParsedRow> analyze(ImportContext ctx, MultipartFile file) {
        // Taille, extension ET signature binaire, selon les parametres d'import
        // (§9) : applique des la previsualisation, pour ne jamais analyser un
        // fichier que la politique refuse.
        importPolicy.verifierFichier(file, "de l'emploi du temps");

        // Referentiels charges une seule fois (jamais crees par l'import §21) :
        //  - salles resolues par code ;
        //  - creneaux horaires ACTIFS : les heures du fichier doivent correspondre
        //    a un creneau configure, sinon la ligne est en erreur (plus de
        //    creation automatique de creneau) ;
        //  - modules du CONTEXTE (filiere + semestre) : le module du fichier doit
        //    exister dans ce referentiel (erreur sinon, aucune creation) ;
        //  - types de seance configurables : resolus par nom ou code.
        RefData ref = new RefData(
                spaceRepository.findAll(),
                timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true),
                moduleRepository.findByProgramIdAndSemesterIdOrderByNomAsc(
                        ctx.program.getId(), ctx.semester.getId()),
                typeSeanceRepository.findAllByOrderByOrdreAsc());
        List<ParsedRow> rows = new ArrayList<>();
        List<ParsedRow> accepted = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = getSeancesSheet(workbook);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                ParsedRow pr = new ParsedRow();
                pr.rowNumber = rowIndex + 1;
                pr.jourRaw = getCellValue(row, 0);
                // Colonnes horaires : lecture normalisee « HH:mm » AVANT validation.
                // Excel stocke une heure « 08:30 » comme une valeur NUMERIQUE
                // (fraction de journee), pas comme du texte : readTimeCell la
                // convertit pour que parseTime recoive bien « 08:30 » (§import).
                pr.debutRaw = readTimeCell(row, 1);
                pr.finRaw = readTimeCell(row, 2);
                pr.moduleRaw = getCellValue(row, 3);
                pr.typeRaw = getCellValue(row, 4);
                pr.presenceRaw = getCellValue(row, 5);
                pr.salleRaw = getCellValue(row, 6);
                pr.enseignantRaw = getCellValue(row, 7);

                parseStructure(pr, ctx, ref);
                if (pr.isValid()) {
                    checkDbConflicts(pr, ctx);
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

    /** Analyse structurelle d'une ligne : jour, horaires, module, type, presence, salle. */
    private void parseStructure(ParsedRow pr, ImportContext ctx, RefData ref) {
        // Jour
        try {
            pr.jour = parseJour(pr.jourRaw);
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
        }

        // Horaires (§9G : debut < fin)
        LocalTime debut = null;
        LocalTime fin = null;
        try {
            debut = parseTime(pr.debutRaw, "heure de début");
            pr.debut = debut;
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.horaireInvalide = true;
        }
        try {
            fin = parseTime(pr.finRaw, "heure de fin");
            pr.fin = fin;
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.horaireInvalide = true;
        }
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            pr.errors.add("L'heure de fin doit être postérieure à l'heure de début.");
            pr.horaireInvalide = true;
        }

        // Creneau horaire (§21) : les heures doivent correspondre EXACTEMENT a un
        // creneau configure et actif. Aucun creneau n'est cree a la volee : une
        // ligne dont les heures ne correspondent a aucun creneau est en erreur.
        if (debut != null && fin != null && fin.isAfter(debut)) {
            resolveTimeSlot(pr, ref);
            // Duree maximale d'une seance (Module 11, §5) : meme regle et meme
            // texte que la creation unitaire, l'ecart etant seulement le canal de
            // restitution (erreur de ligne ici, 400 la-bas).
            String motifDuree = settingsService.motifDureeSeanceExcessive(debut, fin);
            if (motifDuree != null) {
                pr.errors.add(motifDuree);
            }
        }

        // Module (§21) : resolu dans le referentiel du CONTEXTE (filiere +
        // semestre). Introuvable => erreur (aucune creation) ; desactive => erreur.
        resolveModule(pr, ctx, ref);

        // Type de seance (§7) : resolu dans le referentiel configurable (par nom
        // ou par code). L'enum historique est derivee du code pour compatibilite.
        resolveType(pr, ref);

        // Type de presence (§6) : PRESENTIEL par defaut
        pr.presence = parsePresence(pr.presenceRaw, pr);

        // Salle : obligatoire en presentiel, doit exister si renseignee
        resolveSalle(pr, ref.spaces);

        // Enseignant facultatif (§6) : chaine vide si absent
        pr.enseignant = isBlank(pr.enseignantRaw) ? "" : pr.enseignantRaw.trim();
    }

    /**
     * Rattache la ligne au creneau horaire configure dont les heures de debut et
     * de fin correspondent (§21). Seuls les creneaux ACTIFS sont acceptes ; a
     * defaut de correspondance, la ligne est en erreur (le RP doit utiliser un
     * creneau existant ou l'ADMIN doit d'abord le configurer).
     */
    private void resolveTimeSlot(ParsedRow pr, RefData ref) {
        TimeSlot match = ref.slots.stream()
                .filter(s -> s.getHeureDebut().equals(pr.debut) && s.getHeureFin().equals(pr.fin))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucun créneau horaire configuré ne correspond à "
                    + pr.debut.format(HOUR) + "–" + pr.fin.format(HOUR)
                    + ". Utilisez un créneau existant (voir « Valeurs autorisées »).");
            pr.creneauInexistant = true;
        } else {
            pr.timeSlot = match;
        }
    }

    /**
     * Resout le module de la ligne dans le referentiel du contexte pedagogique
     * (filiere + semestre) par correspondance de nom (accents/casse ignores).
     * Aucune creation (§21) : un module absent ou desactive est une erreur. En cas
     * de succes, la matiere est alignee sur le nom officiel du module.
     */
    private void resolveModule(ParsedRow pr, ImportContext ctx, RefData ref) {
        if (isBlank(pr.moduleRaw)) {
            pr.errors.add("Le module est obligatoire.");
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
            pr.module = match.getNom();
        }
    }

    /**
     * Resout le type de seance dans le referentiel configurable (§7) par nom ou
     * par code (accents/casse ignores). L'enum historique {@link SessionType} est
     * derivee du code (repli {@link SessionType#AUTRE}) pour la compatibilite et
     * les statistiques. Un type absent ou desactive est une erreur.
     */
    private void resolveType(ParsedRow pr, RefData ref) {
        if (isBlank(pr.typeRaw)) {
            pr.errors.add("Le type de séance est obligatoire.");
            return;
        }
        String needle = stripAccents(pr.typeRaw.trim());
        TypeSeance match = ref.types.stream()
                .filter(t -> stripAccents(t.getNom()).equalsIgnoreCase(needle)
                        || (t.getCode() != null && t.getCode().equalsIgnoreCase(needle)))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Le type de séance « " + pr.typeRaw.trim()
                    + " » n'est pas valide (voir « Valeurs autorisées »).");
        } else if (!match.isActif()) {
            pr.errors.add("Le type de séance « " + match.getNom() + " » est désactivé.");
        } else {
            pr.typeSeance = match;
            pr.type = deriveEnumFromCode(match.getCode());
        }
    }

    /**
     * Derive l'enum historique {@link SessionType} depuis le code d'un type de
     * seance configurable. Les types personnalises retombent sur
     * {@link SessionType#AUTRE} (l'enum n'est qu'un rappel de compatibilite).
     */
    private SessionType deriveEnumFromCode(String code) {
        try {
            return SessionType.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            return SessionType.AUTRE;
        }
    }

    /** Resout la salle par code. Presentiel => salle obligatoire ; toute salle indiquee doit exister. */
    private void resolveSalle(ParsedRow pr, List<Space> allSpaces) {
        String code = isBlank(pr.salleRaw) ? "" : pr.salleRaw.trim();
        if (code.isBlank()) {
            if (pr.presence == PresenceType.PRESENTIEL) {
                pr.errors.add("Une séance en présentiel doit indiquer une salle (code).");
                pr.salleManquante = true;
            }
            pr.space = null;
            return;
        }
        Space match = allSpaces.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucune salle trouvée avec le code « " + code + " ».");
            pr.salleInexistante = true;
            pr.space = null;
            return;
        }
        // §5/§21 : une salle inactive (ou dont l'etage/batiment est desactive) ne
        // peut pas etre utilisee pour une nouvelle seance importee. Message
        // explicite nommant la cause exacte (jamais d'ignorance silencieuse). On
        // utilise reason(...) — non levant — pour joindre l'erreur aux autres
        // erreurs de la ligne plutot que d'interrompre l'analyse du fichier.
        String motifSalle = ReferentialStatus.reason(match);
        if (motifSalle != null) {
            pr.errors.add(motifSalle);
            pr.salleInexistante = true;
            pr.space = null;
            return;
        }
        pr.space = match;
    }

    /**
     * Conflits face aux seances DEJA enregistrees (comparaison GLOBALE §33, par
     * chevauchement horaire) : salle, groupe, enseignant, puis reservations.
     */
    private void checkDbConflicts(ParsedRow pr, ImportContext ctx) {
        Long ayId = ctx.academicYear.getId();
        Long semId = ctx.semester.getId();

        if (pr.space != null && scheduleRepository.existsSpaceConflict(
                ayId, semId, pr.jour, pr.debut, pr.fin, pr.space.getId())) {
            pr.errors.add("Conflit de salle : « " + pr.space.getCode()
                    + " » est déjà occupée sur ce créneau.");
            pr.conflit = true;
        }

        if (scheduleRepository.existsGroupConflict(
                ayId, semId, pr.jour, pr.debut, pr.fin, ctx.group.getId())) {
            pr.errors.add("Conflit de groupe : « " + ctx.group.getNom()
                    + " » a déjà une séance sur ce créneau.");
            pr.conflit = true;
        }

        if (!pr.enseignant.isBlank() && scheduleRepository.existsTeacherConflict(
                ayId, semId, pr.jour, pr.debut, pr.fin, pr.enseignant)) {
            pr.errors.add("Conflit d'enseignant : « " + pr.enseignant
                    + " » a déjà une séance sur ce créneau.");
            pr.conflit = true;
        }

        if (pr.space != null) {
            String reservationConflict = reservationConflict(pr, ctx);
            if (reservationConflict != null) {
                pr.errors.add(reservationConflict);
                pr.conflit = true;
            }
        }
    }

    /**
     * Confronte une salle aux reservations bloquantes existantes (§22/§23). La
     * seance etant hebdomadaire, on retient les reservations tombant le meme jour
     * de semaine et dans la periode de l'annee universitaire.
     */
    private String reservationConflict(ParsedRow pr, ImportContext ctx) {
        List<Reservation> blocking = reservationRepository.findBlockingForSpace(
                pr.space.getId(), pr.debut, pr.fin, BLOCKING_STATUSES);
        LocalDate from = ctx.academicYear.getDateDebut();
        LocalDate to = ctx.academicYear.getDateFin();
        for (Reservation r : blocking) {
            if (toWeekDay(r.getDate().getDayOfWeek()) != pr.jour) {
                continue;
            }
            if (from != null && r.getDate().isBefore(from)) {
                continue;
            }
            if (to != null && r.getDate().isAfter(to)) {
                continue;
            }
            return String.format(
                    "L'espace est déjà réservé le %s de %s à %s (réservation #%d).",
                    r.getDate().format(DAY), r.getHeureDebut().format(HOUR),
                    r.getHeureFin().format(HOUR), r.getId());
        }
        return null;
    }

    /**
     * Conflits INTERNES au fichier : le fichier decrit un unique contexte (meme
     * groupe), donc deux seances du meme jour qui se chevauchent sont en conflit.
     * Un chevauchement a l'identique (meme creneau, module, salle, type) est
     * signale comme doublon (§9H).
     */
    private void checkFileConflicts(ParsedRow pr, List<ParsedRow> accepted) {
        for (ParsedRow other : accepted) {
            if (other.jour != pr.jour) {
                continue;
            }
            if (!overlaps(pr.debut, pr.fin, other.debut, other.fin)) {
                continue;
            }
            boolean doublon = pr.debut.equals(other.debut)
                    && pr.fin.equals(other.fin)
                    && sameText(pr.module, other.module)
                    && sameSalle(pr, other)
                    && pr.type == other.type;
            if (doublon) {
                pr.errors.add("Doublon : séance identique à celle de la ligne "
                        + other.rowNumber + " du fichier.");
            } else {
                pr.errors.add("Chevauchement avec la séance de la ligne "
                        + other.rowNumber + " du fichier (même groupe).");
            }
            pr.conflit = true;
            return;
        }
    }

    // ----- Construction du rapport de previsualisation -----

    private TimetableImportPreviewDto buildPreview(ImportContext ctx, String fileName, List<ParsedRow> rows) {
        List<TimetableImportRowDto> lignes = new ArrayList<>();
        int valides = 0;
        int enErreur = 0;
        int conflits = 0;
        int sallesInexistantes = 0;
        int sallesManquantes = 0;
        int horairesInvalides = 0;
        int creneauxInexistants = 0;
        int modulesInexistants = 0;
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
            if (pr.horaireInvalide) {
                horairesInvalides++;
            }
            if (pr.creneauInexistant) {
                creneauxInexistants++;
            }
            if (pr.moduleInexistant) {
                modulesInexistants++;
            }
        }

        return TimetableImportPreviewDto.builder()
                .fileName(fileName)
                .anneeUniversitaire(ctx.academicYear.getLibelle())
                .filiere(ctx.program.getNom())
                .niveau(ctx.level.getNom())
                .promotion(ctx.promotion.getNom())
                .groupe(ctx.group.getNom())
                .semestre(ctx.semester.getNom())
                .session(ctx.session.getNom())
                .seancesDetectees(rows.size())
                .seancesValides(valides)
                .lignesEnErreur(enErreur)
                .conflits(conflits)
                .sallesInexistantes(sallesInexistantes)
                .sallesManquantes(sallesManquantes)
                .horairesInvalides(horairesInvalides)
                .creneauxInexistants(creneauxInexistants)
                .modulesInexistants(modulesInexistants)
                // Confirmable seulement si au moins une seance et AUCUNE erreur (§8).
                .confirmable(!rows.isEmpty() && enErreur == 0)
                .lignes(lignes)
                .build();
    }

    private TimetableImportRowDto toRowDto(ParsedRow pr) {
        return TimetableImportRowDto.builder()
                .ligne(pr.rowNumber)
                .jour(pr.jourRaw)
                .heureDebut(pr.debutRaw)
                .heureFin(pr.finRaw)
                .module(pr.moduleRaw)
                .type(pr.typeRaw)
                .typePresence(pr.presenceRaw)
                .salle(pr.salleRaw)
                .enseignant(pr.enseignantRaw)
                .valide(pr.isValid())
                .erreurs(new ArrayList<>(pr.errors))
                .build();
    }

    // ----- Modele Excel contextualise (§24/§25) -----

    /**
     * Genere le modele d'import pour un contexte donne : une feuille « Séances »
     * a remplir, une feuille « Instructions » (regles + rappel du contexte, qui
     * n'est PAS a saisir dans le fichier §5) et une feuille « Valeurs autorisées »
     * (types, presence, jours, salles disponibles). Aucun nom d'etablissement
     * n'est code en dur (§26) : les exemples sont generiques.
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long academicYearId, Long programId, Long levelId,
                                   Long promotionId, Long groupId, Long semesterId,
                                   Long sessionId) {
        ImportContext ctx = resolveContext(academicYearId, programId, levelId,
                promotionId, groupId, semesterId, sessionId);

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            buildSeancesSheet(workbook, headerStyle, ctx);
            buildInstructionsSheet(workbook, headerStyle, ctx);
            buildAllowedValuesSheet(workbook, headerStyle, ctx);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la génération du modèle Excel.");
        }
    }

    private void buildSeancesSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Séances");
        Row header = sheet.createRow(0);
        for (int i = 0; i < SEANCE_HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(SEANCE_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // Exemples bases sur les REFERENTIELS reels du contexte (§21/§26) : creneaux
        // configures, modules du contexte, types de seance et salle active. Aucun
        // nom d'etablissement code en dur : a defaut de donnees, on retombe sur des
        // valeurs generiques que le RP devra remplacer.
        List<TimeSlot> slots = timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true);
        List<Module> modules = moduleRepository.findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
                ctx.program.getId(), ctx.semester.getId(), true);
        List<TypeSeance> types = typeSeanceRepository.findByActifOrderByOrdreAsc(true);

        String slot1Debut = slots.isEmpty() ? "08:30" : slots.get(0).getHeureDebut().format(HOUR);
        String slot1Fin = slots.isEmpty() ? "10:25" : slots.get(0).getHeureFin().format(HOUR);
        String slot2Debut = slots.size() < 2 ? "10:35" : slots.get(1).getHeureDebut().format(HOUR);
        String slot2Fin = slots.size() < 2 ? "12:30" : slots.get(1).getHeureFin().format(HOUR);
        String module1 = modules.isEmpty() ? "Nom exact du module" : modules.get(0).getNom();
        String module2 = modules.size() < 2 ? module1 : modules.get(1).getNom();
        String type1 = types.isEmpty() ? "Cours" : types.get(0).getNom();
        String type2 = types.size() < 2 ? type1 : types.get(1).getNom();
        String exampleSalle = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(Space::getCode)
                .findFirst()
                .orElse("CODE-SALLE");

        String[][] examples = {
                {"Lundi", slot1Debut, slot1Fin, module1, type1, "Présentiel", exampleSalle, "Enseignant 1"},
                {"Mardi", slot2Debut, slot2Fin, module2, type2, "Distanciel", "", "Enseignant 2"}
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
        titleCell.setCellValue("Comment remplir ce modèle");
        titleCell.setCellStyle(headerStyle);

        String[] lines = {
                "",
                "1. Renseignez UNE séance par ligne dans la feuille « Séances ».",
                "2. Colonnes obligatoires : Jour, Heure début, Heure fin, Module, Type de séance, Type de présence.",
                "3. Les heures sont au format HH:mm (exemple : 08:30) et doivent correspondre EXACTEMENT",
                "   à un créneau horaire configuré (voir « Valeurs autorisées »). Aucun créneau n'est créé à la volée.",
                "4. Le Module doit exister dans le référentiel de ce contexte (filière + semestre) :",
                "   reprenez un nom de la liste « Modules du contexte ». L'import ne crée aucun module.",
                "5. Le Type de séance doit figurer dans la liste des types configurés (nom ou code).",
                "6. Type de présence « Présentiel » : la salle (code) est OBLIGATOIRE et doit déjà exister.",
                "7. Type de présence « Distanciel » : la salle peut rester vide.",
                "8. L'enseignant est facultatif.",
                "9. Consultez la feuille « Valeurs autorisées » pour les valeurs acceptées, les créneaux,",
                "   les modules du contexte et les codes de salle.",
                "10. Les accents et la casse sont ignorés lors de l'import.",
                "",
                "Rappel du contexte de cet emploi du temps",
                "(déjà sélectionné dans l'application — NE PAS le saisir dans le fichier) :",
                "   • Année universitaire : " + ctx.academicYear.getLibelle(),
                "   • Filière : " + ctx.program.getNom(),
                "   • Niveau : " + ctx.level.getNom(),
                "   • Promotion : " + ctx.promotion.getNom(),
                "   • Groupe : " + ctx.group.getNom(),
                "   • Semestre : " + ctx.semester.getNom(),
                "   • Session : " + ctx.session.getNom()
        };
        for (String line : lines) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
    }

    private void buildAllowedValuesSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Valeurs autorisées");

        String[] headers = {"Types de séance", "Types de présence", "Jours",
                "Créneaux horaires (HH:mm–HH:mm)", "Modules du contexte",
                "Salles disponibles (code — nom)"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 7000);
        }

        // Types de seance : referentiel CONFIGURABLE (§7/§11), jamais code en dur.
        List<String> types = typeSeanceRepository.findByActifOrderByOrdreAsc(true).stream()
                .map(TypeSeance::getNom)
                .toList();
        List<String> presences = new ArrayList<>();
        for (PresenceType p : PresenceType.values()) {
            presences.add(p.name());
        }
        // Jours de la grille : Lundi -> Samedi (§10).
        List<String> jours = List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi");
        // Creneaux configures actifs (§21) : les heures du fichier doivent y correspondre.
        List<String> creneaux = timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true).stream()
                .map(s -> s.getHeureDebut().format(HOUR) + "–" + s.getHeureFin().format(HOUR))
                .toList();
        // Modules ACTIFS du contexte (filiere + semestre) : seules valeurs acceptees (§21).
        List<String> modules = moduleRepository.findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
                        ctx.program.getId(), ctx.semester.getId(), true).stream()
                .map(Module::getNom)
                .toList();
        // Salles actives reelles (donnees de l'etablissement, non codees en dur).
        List<String> salles = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(s -> s.getCode() + " — " + s.getNom())
                .sorted()
                .toList();

        int maxRows = List.of(types.size(), presences.size(), jours.size(),
                        creneaux.size(), modules.size(), salles.size()).stream()
                .max(Integer::compareTo).orElse(0);
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < types.size()) {
                row.createCell(0).setCellValue(types.get(i));
            }
            if (i < presences.size()) {
                row.createCell(1).setCellValue(presences.get(i));
            }
            if (i < jours.size()) {
                row.createCell(2).setCellValue(jours.get(i));
            }
            if (i < creneaux.size()) {
                row.createCell(3).setCellValue(creneaux.get(i));
            }
            if (i < modules.size()) {
                row.createCell(4).setCellValue(modules.get(i));
            }
            if (i < salles.size()) {
                row.createCell(5).setCellValue(salles.get(i));
            }
        }
    }

    // ----- Contexte d'import -----

    /**
     * Resout et valide les 7 identifiants du contexte, verifie leur coherence
     * mutuelle (promotion/filiere/niveau, groupe/promotion) puis controle l'acces
     * a la filiere (§12/§14). Aucune de ces entites n'est jamais creee ici.
     */
    private ImportContext resolveContext(Long academicYearId, Long programId, Long levelId,
                                         Long promotionId, Long groupId, Long semesterId,
                                         Long sessionId) {
        ImportContext ctx = new ImportContext();
        ctx.academicYear = academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + academicYearId));
        ctx.program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + programId));
        ctx.level = levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + levelId));
        ctx.promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + promotionId));
        ctx.group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Groupe introuvable avec l'id " + groupId));
        ctx.semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semestre introuvable avec l'id " + semesterId));
        ctx.session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session universitaire introuvable avec l'id " + sessionId));

        if (!ctx.promotion.getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée n'appartient pas à la filière indiquée.");
        }
        if (!ctx.promotion.getLevel().getId().equals(ctx.level.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée ne correspond pas au niveau indiqué.");
        }
        if (!ctx.group.getPromotion().getId().equals(ctx.promotion.getId())) {
            throw new BadRequestException(
                    "Le groupe sélectionné n'appartient pas à la promotion indiquée.");
        }

        assertSemesterMatchesGroupYear(ctx.semester, ctx.level, ctx.group);

        // Securite backend (§12) : acces a la filiere exige (ADMIN ou RP proprietaire).
        accessScope.assertProgramAccessible(ctx.program.getId());

        // §20 : on n'importe pas (ni ne previsualise, ni ne genere un modele) un
        // emploi du temps sur un contexte inutilisable. Garder le groupe suffit a
        // couvrir toute la chaine academique (groupe -> promotion -> filiere ->
        // departement + annee) ; on garde en plus le semestre et la session.
        // Choke point commun a preview / confirm / generateTemplate.
        ReferentialStatus.requireUsable(ctx.group);
        ReferentialStatus.requireUsable(ctx.semester);
        ReferentialStatus.requireUsable(ctx.session);
        ReferentialStatus.requireUsable(ctx.academicYear);
        return ctx;
    }

    /**
     * Cohérence semestre ↔ année d'étude du groupe (§20, garde défensive alignée
     * sur le filtrage du sélecteur côté import). Un groupe de 1re année ne peut
     * recevoir que le semestre impair de son année (ordre = 2·anneeNiveau − 1) :
     * 1A → S1, 2A → S3, 3A → S5… Cela évite d'importer l'EDT d'un groupe de 1A
     * sur S3 (semestre de 2A) alors que les deux cohortes coexistent dans le même
     * cycle.
     *
     * <p>Contrôle <b>non cassant</b> : ignoré si le semestre n'est pas rattaché au
     * niveau (semestres libres : Doctorat, Formation continue), si son ordre est
     * nul, ou si le groupe n'a pas d'année d'étude renseignée (données legacy).</p>
     */
    private void assertSemesterMatchesGroupYear(
            com.campusops.semester.entity.Semester semester,
            com.campusops.level.entity.Level level,
            com.campusops.group.entity.Group group) {
        Integer annee = group.getAnneeNiveau();
        Integer ordre = semester.getOrdre();
        boolean semesterOfLevel = semester.getLevel() != null
                && semester.getLevel().getId().equals(level.getId());
        if (annee == null || annee < 1 || ordre == null || !semesterOfLevel) {
            return;
        }
        int expectedOrdre = 2 * annee - 1;
        if (ordre != expectedOrdre) {
            throw new BadRequestException(
                    "Le semestre « " + semester.getNom() + " » ne correspond pas à l'année d'étude "
                            + "du groupe (" + annee + "re/e année). Sélectionnez le semestre courant "
                            + "de cette année.");
        }
    }

    // ----- Utilitaires de parsing (feuille « Séances » uniquement) -----

    /** Feuille des seances : « Séances » si presente (accents/casse ignores), sinon la premiere. */
    private Sheet getSeancesSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (stripAccents(sheet.getSheetName().trim()).equalsIgnoreCase("SEANCES")) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    private WeekDay parseJour(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("Le jour est obligatoire.");
        }
        String normalized = stripAccents(raw.trim()).toUpperCase();
        return switch (normalized) {
            case "LUNDI", "MONDAY" -> WeekDay.LUNDI;
            case "MARDI", "TUESDAY" -> WeekDay.MARDI;
            case "MERCREDI", "WEDNESDAY" -> WeekDay.MERCREDI;
            case "JEUDI", "THURSDAY" -> WeekDay.JEUDI;
            case "VENDREDI", "FRIDAY" -> WeekDay.VENDREDI;
            case "SAMEDI", "SATURDAY" -> WeekDay.SAMEDI;
            case "DIMANCHE", "SUNDAY" -> WeekDay.DIMANCHE;
            default -> throw new IllegalArgumentException("Le jour « " + raw + " » n'est pas valide.");
        };
    }

    private PresenceType parsePresence(String raw, ParsedRow pr) {
        if (isBlank(raw)) {
            return PresenceType.PRESENTIEL;
        }
        String normalized = stripAccents(raw.trim()).toUpperCase();
        try {
            return PresenceType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            pr.errors.add("Le type de présence « " + raw
                    + " » n'est pas valide (attendu : Présentiel ou Distanciel).");
            return null;
        }
    }

    private LocalTime parseTime(String raw, String label) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("L'" + label + " est obligatoire.");
        }
        String value = raw.trim().replace('h', ':').replace('H', ':');
        // Repli robustesse : si une fraction de journee Excel arrivait encore sous
        // forme de texte (ex. « 0.354166666666667 »), on la normalise en heure
        // AVANT la validation plutot que de la rejeter. Le cas nominal (cellule
        // numerique) est deja traite par readTimeCell ; ceci couvre les fichiers
        // ou la valeur a ete saisie/collee en texte.
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

    /**
     * Lit une cellule d'heure et renvoie une chaine normalisee au format interne
     * {@code HH:mm}, AVANT toute validation (la validation n'est jamais
     * contournee : la valeur repasse par {@link #parseTime}). Excel enregistre le
     * plus souvent une heure saisie « 08:30 » comme une valeur NUMERIQUE egale a
     * une fraction de journee (08:30 = 0.354166...), et non comme du texte. On
     * couvre donc :
     * <ul>
     *   <li>cellule NUMERIQUE formatee comme date/heure : conversion exacte par
     *       Apache POI ({@link DateUtil#isCellDateFormatted});</li>
     *   <li>cellule NUMERIQUE brute (format « Standard ») : la fraction de
     *       journee est convertie en heure via {@link #fractionToTime} ;</li>
     *   <li>cellule TEXTE : renvoyee telle quelle (ex. « 08:30 », « 8h30 »), la
     *       compatibilite avec les valeurs deja saisies en texte est preservee ;</li>
     *   <li>formule : on lit le type/valeur mis en cache.</li>
     * </ul>
     */
    // Visibilite package-private : testable unitairement (voir TimetableImportServiceTimeTest).
    String readTimeCell(Row row, int index) {
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
                // Cellule reconnue par Excel comme une date/heure : POI convertit
                // la fraction en date/heure reelle, on n'en garde que l'heure.
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt != null) {
                        yield dt.toLocalTime().format(HOUR);
                    }
                }
                // Format « Standard » : nombre brut interprete comme fraction de journee.
                yield fractionToTime(cell.getNumericCellValue());
            }
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * Convertit une valeur numerique Excel (fraction de journee) en heure
     * « HH:mm ». La partie entiere (nombre de jours d'un « serial date ») est
     * ignoree ; seule la partie fractionnaire est convertie. L'arrondi a la
     * minute la plus proche absorbe l'imprecision binaire du flottant
     * (0.3541666... x 1440 = 509.99999... -> 510 min -> 08:30).
     */
    // Visibilite package-private : testable unitairement (voir TimetableImportServiceTimeTest).
    String fractionToTime(double numeric) {
        double fraction = numeric - Math.floor(numeric);
        long totalMinutes = Math.round(fraction * 24 * 60);
        totalMinutes = ((totalMinutes % 1440) + 1440) % 1440; // 24:00 ramene a 00:00
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private boolean sameSalle(ParsedRow a, ParsedRow b) {
        String ca = (a.space == null) ? "" : a.space.getCode();
        String cb = (b.space == null) ? "" : b.space.getCode();
        return ca.equalsIgnoreCase(cb);
    }

    private boolean sameText(String a, String b) {
        String na = (a == null) ? "" : a.trim();
        String nb = (b == null) ? "" : b.trim();
        return na.equalsIgnoreCase(nb);
    }

    private WeekDay toWeekDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> WeekDay.LUNDI;
            case TUESDAY -> WeekDay.MARDI;
            case WEDNESDAY -> WeekDay.MERCREDI;
            case THURSDAY -> WeekDay.JEUDI;
            case FRIDAY -> WeekDay.VENDREDI;
            case SATURDAY -> WeekDay.SAMEDI;
            case SUNDAY -> WeekDay.DIMANCHE;
        };
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < SEANCE_HEADERS.length; i++) {
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
                ? file.getOriginalFilename() : "import.xlsx";
    }

    // ----- Structures internes -----

    /**
     * Periode de validite [debut, fin] de l'en-tete d'emploi du temps (§20),
     * <b>derivee du semestre</b> : une borne absente du semestre retombe sur la
     * periode de l'annee universitaire. Un emploi du temps — et donc chacune de
     * ses seances importees — ne peut couvrir que la fenetre de dates de son
     * semestre. La periode n'est jamais saisie a la main lors de l'import.
     */
    private LocalDate[] periodeDuSemestre(ImportContext ctx) {
        LocalDate debut = (ctx.semester.getDateDebut() != null)
                ? ctx.semester.getDateDebut() : ctx.academicYear.getDateDebut();
        LocalDate fin = (ctx.semester.getDateFin() != null)
                ? ctx.semester.getDateFin() : ctx.academicYear.getDateFin();
        return new LocalDate[]{debut, fin};
    }

    /**
     * Valide la fenetre de validite [dateDebut, dateFin] choisie par le RP a
     * l'import : les deux bornes doivent rester DANS la periode du semestre
     * courant selectionne. Motivation metier : un emploi du temps n'est plus
     * valable hors de son semestre (notamment en periode d'examens), donc la
     * date d'expiration ne peut pas depasser la fin du semestre.
     *
     * <p>Non bloquant quand aucune date n'est fournie (retombee sur la periode
     * derivee du semestre) ou quand les bornes du semestre sont absentes
     * (donnees historiques non datees, §29 : on ne casse pas l'existant). On ne
     * borne alors que ce qui est connu.</p>
     */
    private void assertPeriodeDansSemestre(ImportContext ctx, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new BadRequestException(
                    "La date d'expiration ne peut pas preceder la date de debut de l'emploi du temps.");
        }
        LocalDate[] sem = periodeDuSemestre(ctx);
        LocalDate semDebut = sem[0];
        LocalDate semFin = sem[1];
        if (dateDebut != null && semDebut != null && dateDebut.isBefore(semDebut)) {
            throw new BadRequestException("La date de debut de l'emploi du temps (" + dateDebut
                    + ") ne peut pas preceder le debut du semestre courant (" + semDebut + ").");
        }
        if (dateFin != null && semFin != null && dateFin.isAfter(semFin)) {
            throw new BadRequestException("La date d'expiration de l'emploi du temps (" + dateFin
                    + ") ne peut pas depasser la fin du semestre courant (" + semFin + ").");
        }
    }

    /** Contexte d'import resolu et valide (les 7 entites du perimetre). */
    private static class ImportContext {
        private AcademicYear academicYear;
        private Program program;
        private Level level;
        private Promotion promotion;
        private Group group;
        private Semester semester;
        private SessionUniversitaire session;
    }

    /**
     * Referentiels charges une seule fois pour toute l'analyse (§21) : ils ne
     * sont jamais modifies par l'import. Les salles et les creneaux sont globaux ;
     * les modules sont deja bornes au contexte (filiere + semestre) ; les types de
     * seance sont l'ensemble configurable (actifs et inactifs, pour distinguer les
     * messages d'erreur).
     */
    private static class RefData {
        private final List<Space> spaces;
        private final List<TimeSlot> slots;
        private final List<Module> modules;
        private final List<TypeSeance> types;

        private RefData(List<Space> spaces, List<TimeSlot> slots,
                        List<Module> modules, List<TypeSeance> types) {
            this.spaces = spaces;
            this.slots = slots;
            this.modules = modules;
            this.types = types;
        }
    }

    /** Etat d'analyse d'une ligne du fichier (valeurs brutes + resolues + erreurs). */
    private static class ParsedRow {
        private int rowNumber;
        private String jourRaw;
        private String debutRaw;
        private String finRaw;
        private String moduleRaw;
        private String typeRaw;
        private String presenceRaw;
        private String salleRaw;
        private String enseignantRaw;

        private WeekDay jour;
        private LocalTime debut;
        private LocalTime fin;
        private TimeSlot timeSlot;
        private String module;
        private Module moduleEntity;
        private SessionType type;
        private TypeSeance typeSeance;
        private PresenceType presence;
        private Space space;
        private String enseignant = "";

        private final List<String> errors = new ArrayList<>();
        private boolean conflit;
        private boolean salleInexistante;
        private boolean salleManquante;
        private boolean horaireInvalide;
        private boolean creneauInexistant;
        private boolean moduleInexistant;

        private boolean isValid() {
            return errors.isEmpty();
        }
    }
}
