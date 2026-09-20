package com.campusops.exam.service;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.OccupationType;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.TimetableStatus;
import com.campusops.enums.WeekDay;
import com.campusops.exam.dto.ExamenRequestDto;
import com.campusops.exam.dto.ExamenResponseDto;
import com.campusops.exam.entity.Examen;
import com.campusops.exam.mapper.ExamenMapper;
import com.campusops.exam.repository.ExamenRepository;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.occupation.entity.OccupationSupplementaire;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gestion des examens datés (Lot 2 F2).
 *
 * <p>Un examen est un <strong>événement daté</strong> (date + créneau + salle +
 * matière + public), distinct des séances récurrentes de l'emploi du temps. Le
 * service applique la sécurité par périmètre (§12) directement — sans
 * {@code @PreAuthorize} — via {@link AccessScopeService} : un responsable
 * pédagogique ne gère que les examens de SES filières, l'administrateur gère
 * tout.</p>
 *
 * <p><strong>Détection de conflit bidirectionnelle</strong> à la création et à la
 * modification :</p>
 * <ol>
 *   <li>la <em>salle</em> doit être libre vis-à-vis des séances actives, des
 *       réservations bloquantes et des autres examens ;</li>
 *   <li>le <em>public</em> (groupe précis ou promotion entière) ne doit pas être
 *       déjà occupé par une séance ou un autre examen qui se chevauche.</li>
 * </ol>
 *
 * <p>Réciproquement, les réservations et le calcul de disponibilité tiennent
 * compte des examens (voir {@code ReservationService} et
 * {@code AvailabilityService}).</p>
 *
 * <p>Les écritures posent un <strong>verrou pessimiste sur la salle</strong>
 * (§21), comme les réservations et les autres occupations supplémentaires : les
 * trois chemins se sérialisent ainsi sur la même ressource et ne peuvent pas
 * engager deux fois le même espace en parallèle.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExamenService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Statuts de réservation qui bloquent effectivement un créneau. */
    private static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED);

    private final ExamenRepository examenRepository;
    private final OccupationSupplementaireRepository occupationRepository;
    private final ExamenMapper examenMapper;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SpaceRepository spaceRepository;
    private final ModuleRepository moduleRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SessionUniversitaireRepository sessionRepository;
    private final AccessScopeService accessScope;
    private final AcademicYearRepository academicYearRepository;

    // ----- Commandes -----

    public ExamenResponseDto createExamen(ExamenRequestDto request) {
        ExamenContext ctx = resolveContext(request);
        accessScope.assertProgramAccessible(ctx.program.getId());
        lockSpaceOrThrow(ctx);
        checkConflicts(ctx, null);

        Examen entity = Examen.builder()
                .type(OccupationType.EXAMEN)
                .intitule(examLabel(ctx))
                .date(ctx.date)
                .heureDebut(ctx.timeSlot.getHeureDebut())
                .heureFin(ctx.timeSlot.getHeureFin())
                .timeSlot(ctx.timeSlot)
                .space(ctx.space)
                .module(ctx.module)
                .program(ctx.program)
                .promotion(ctx.promotion)
                .group(ctx.group)
                .semester(ctx.semester)
                .academicYear(ctx.academicYear)
                .session(ctx.session)
                .commentaire(request.getCommentaire())
                .actif(true)
                .build();

        return examenMapper.toResponseDto(examenRepository.save(entity));
    }

    public ExamenResponseDto updateExamen(Long id, ExamenRequestDto request) {
        // Charge et vérifie l'accès à l'examen existant (périmètre RP).
        Examen examen = findAccessibleOrThrow(id);
        ExamenContext ctx = resolveContext(request);
        // Le nouveau contexte doit lui aussi être dans le périmètre (un RP ne peut
        // pas déplacer un examen vers une filière qui n'est pas la sienne).
        accessScope.assertProgramAccessible(ctx.program.getId());
        lockSpaceOrThrow(ctx);
        checkConflicts(ctx, examen.getId());

        examen.setType(OccupationType.EXAMEN);
        examen.setIntitule(examLabel(ctx));
        examen.setDate(ctx.date);
        examen.setHeureDebut(ctx.timeSlot.getHeureDebut());
        examen.setHeureFin(ctx.timeSlot.getHeureFin());
        examen.setTimeSlot(ctx.timeSlot);
        examen.setSpace(ctx.space);
        examen.setModule(ctx.module);
        examen.setProgram(ctx.program);
        examen.setPromotion(ctx.promotion);
        examen.setGroup(ctx.group);
        examen.setSemester(ctx.semester);
        examen.setAcademicYear(ctx.academicYear);
        examen.setSession(ctx.session);
        examen.setCommentaire(request.getCommentaire());

        return examenMapper.toResponseDto(examenRepository.save(examen));
    }

    public void deleteExamen(Long id) {
        Examen examen = findAccessibleOrThrow(id);
        examenRepository.delete(examen);
    }

    // ----- Consultations -----

    @Transactional(readOnly = true)
    public ExamenResponseDto getExamenById(Long id) {
        return examenMapper.toResponseDto(findAccessibleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ExamenResponseDto> filterExamens(Long academicYearId, Long programId,
                                                 Long promotionId, Long groupId,
                                                 Long semesterId, Long sessionId,
                                                 Long moduleId, LocalDate date) {
        // Sans annee explicite, on se cale sur l'annee active (§17, §24) : la liste
        // ne montre que les examens de l'annee active ; l'historique reste en base.
        Long effectiveYearId = resolveEffectiveYearId(academicYearId);

        List<Examen> examens = accessScope.isAdmin()
                ? examenRepository.findAll()
                : examenRepository.findByProgramIdIn(accessScope.myProgramIds());

        return examens.stream()
                .filter(e -> effectiveYearId == null || e.getAcademicYear().getId().equals(effectiveYearId))
                .filter(e -> programId == null || e.getProgram().getId().equals(programId))
                .filter(e -> promotionId == null || e.getPromotion().getId().equals(promotionId))
                .filter(e -> groupId == null || (e.getGroup() != null && e.getGroup().getId().equals(groupId)))
                .filter(e -> semesterId == null || e.getSemester().getId().equals(semesterId))
                .filter(e -> sessionId == null || e.getSession().getId().equals(sessionId))
                .filter(e -> moduleId == null || e.getModule().getId().equals(moduleId))
                .filter(e -> date == null || e.getDate().equals(date))
                .sorted(Comparator.comparing(Examen::getDate)
                        .thenComparing(e -> e.getTimeSlot().getHeureDebut()))
                .map(examenMapper::toResponseDto)
                .toList();
    }

    /**
     * Resout l'annee de filtrage : celle demandee, sinon l'annee active ; ou
     * {@code null} si aucune annee active (repli non cassant, historique visible).
     */
    private Long resolveEffectiveYearId(Long academicYearId) {
        if (academicYearId != null) {
            return academicYearId;
        }
        return academicYearRepository.findFirstByActifTrue()
                .map(AcademicYear::getId)
                .orElse(null);
    }

    // ----- Import (validation « à blanc » pour la prévisualisation 2 phases) -----

    /**
     * Validation <b>à blanc</b> d'un examen pour l'import en deux phases (§8) :
     * résout le contexte, applique la sécurité de périmètre (§12) et détecte les
     * conflits <b>sans rien enregistrer</b>, en RENVOYANT les messages d'erreur au
     * lieu de lever une exception. C'est l'unique source de vérité des règles de
     * conflit d'examen (salle + public), réutilisée par le service d'import pour ne
     * pas dupliquer — et donc ne pas faire diverger — cette logique.
     *
     * <p>La persistance de l'import passe, elle, par {@link #createExamen} : les
     * deux chemins partagent {@code resolveContext} + {@code checkConflicts}.</p>
     */
    @Transactional(readOnly = true)
    public List<String> dryRunImportErrors(ExamenRequestDto request) {
        List<String> errors = new ArrayList<>();
        ExamenContext ctx;
        try {
            ctx = resolveContext(request);
        } catch (BadRequestException | ResourceNotFoundException e) {
            errors.add(e.getMessage());
            return errors;
        }
        try {
            accessScope.assertProgramAccessible(ctx.program.getId());
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
            return errors;
        }
        try {
            checkConflicts(ctx, null);
        } catch (BadRequestException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    // ----- Détection de conflit (bidirectionnelle) -----

    /**
     * Vérifie qu'un examen n'entre en conflit ni sur la <b>salle</b> (séances
     * actives, réservations bloquantes, autres examens) ni sur le <b>public</b>
     * (le groupe concerné — ou toute la promotion — ne doit pas déjà avoir une
     * séance ou un examen qui se chevauche). Le chevauchement horaire suit la
     * convention demi-ouverte {@code [debut, fin)} commune au projet (§9/§33).
     *
     * @param excludeId identifiant de l'examen en cours de modification à ignorer
     *                  (null lors d'une création)
     */
    private void checkConflicts(ExamenContext ctx, Long excludeId) {
        // §20/§23 : un examen ne peut etre programme (ni cree, ni deplace, ni
        // importe) que sur un contexte utilisable. Choke point commun aux chemins
        // d'ecriture (create/update) ET a la previsualisation d'import
        // (dryRunImportErrors), qui garantit un message d'erreur explicite nommant
        // la cause exacte de l'inutilisabilite (§21). La salle est toujours requise
        // pour un examen ; le groupe est facultatif (null = toute la promotion).
        ReferentialStatus.requireUsable(ctx.space);
        if (ctx.group != null) {
            ReferentialStatus.requireUsable(ctx.group);
        } else {
            ReferentialStatus.requireUsable(ctx.promotion);
        }
        ReferentialStatus.requireUsable(ctx.module);
        ReferentialStatus.requireUsable(ctx.semester);
        ReferentialStatus.requireUsable(ctx.academicYear);
        ReferentialStatus.requireUsable(ctx.session);
        // Le creneau (grille horaire) porte les horaires de l'examen : un examen
        // ne peut etre place sur un creneau desactive, retire de la grille
        // officielle (§20/§23/§26).
        ReferentialStatus.requireUsable(ctx.timeSlot);

        LocalDate date = ctx.date;
        LocalTime debut = ctx.timeSlot.getHeureDebut();
        LocalTime fin = ctx.timeSlot.getHeureFin();
        WeekDay jour = toWeekDay(date.getDayOfWeek());

        // (1) Salle vs séances récurrentes de l'emploi du temps.
        for (Schedule schedule : scheduleRepository.findBySpaceId(ctx.space.getId())) {
            if (!schedule.isActif() || schedule.getJour() != jour
                    || !academicYearCoversDate(schedule, date)
                    || !timetableActiveOn(schedule, date)) {
                continue;
            }
            LocalTime sDebut = schedule.getTimeSlot().getHeureDebut();
            LocalTime sFin = schedule.getTimeSlot().getHeureFin();
            if (sDebut.isBefore(fin) && sFin.isAfter(debut)) {
                throw new BadRequestException(String.format(
                        "La salle est occupée par une séance (%s) de %s à %s ce jour-là.",
                        safe(schedule.getMatiere()), sDebut.format(HOUR), sFin.format(HOUR)));
            }
        }

        // (2) Salle vs réservations bloquantes.
        List<Reservation> reservationConflicts = reservationRepository
                .findConflicting(ctx.space.getId(), date, debut, fin, BLOCKING_STATUSES);
        if (!reservationConflicts.isEmpty()) {
            Reservation existing = reservationConflicts.get(0);
            throw new BadRequestException(String.format(
                    "La salle est déjà réservée de %s à %s le %s (réservation #%d).",
                    existing.getHeureDebut().format(HOUR), existing.getHeureFin().format(HOUR),
                    date.format(DAY), existing.getId()));
        }

        // (3) Salle vs TOUTES les occupations supplémentaires (examens,
        // soutenances, autres). Requête polymorphe : une salle déjà tenue par une
        // soutenance ou un événement bloque tout autant qu'un autre examen. On
        // s'exclut soi-même en modification (excludeId).
        List<OccupationSupplementaire> spaceOccupationConflicts =
                occupationRepository.findConflictingForSpace(
                        ctx.space.getId(), date, debut, fin, excludeId);
        if (!spaceOccupationConflicts.isEmpty()) {
            OccupationSupplementaire existing = spaceOccupationConflicts.get(0);
            String nature = existing.getType() != null
                    ? existing.getType().getLibelle().toLowerCase() : "occupation";
            throw new BadRequestException(String.format(
                    "La salle accueille déjà une %s de %s à %s le %s.",
                    nature,
                    existing.getHeureDebut().format(HOUR),
                    existing.getHeureFin().format(HOUR),
                    date.format(DAY)));
        }

        // (4) Public vs séances : un groupe précis ne peut pas être en séance ;
        // une promotion entière est en conflit avec la séance de n'importe lequel
        // de ses groupes.
        List<Schedule> studentSeances = (ctx.group != null)
                ? scheduleRepository.findByGroupId(ctx.group.getId())
                : scheduleRepository.findByPromotionId(ctx.promotion.getId());
        for (Schedule schedule : studentSeances) {
            if (!schedule.isActif() || schedule.getJour() != jour
                    || !academicYearCoversDate(schedule, date)
                    || !timetableActiveOn(schedule, date)) {
                continue;
            }
            LocalTime sDebut = schedule.getTimeSlot().getHeureDebut();
            LocalTime sFin = schedule.getTimeSlot().getHeureFin();
            if (sDebut.isBefore(fin) && sFin.isAfter(debut)) {
                throw new BadRequestException(String.format(
                        "Le public concerné a déjà une séance (%s) de %s à %s ce jour-là.",
                        safe(schedule.getMatiere()), sDebut.format(HOUR), sFin.format(HOUR)));
            }
        }

        // (5) Public vs autres examens : même promotion + chevauchement = conflit,
        // SAUF s'il s'agit de deux groupes précis et distincts (publics disjoints).
        Long myGroupId = (ctx.group != null) ? ctx.group.getId() : null;
        for (Examen other : examenRepository.findByPromotionIdAndDate(ctx.promotion.getId(), date)) {
            if (!other.isActif() || (excludeId != null && other.getId().equals(excludeId))) {
                continue;
            }
            LocalTime oDebut = other.getTimeSlot().getHeureDebut();
            LocalTime oFin = other.getTimeSlot().getHeureFin();
            if (!(oDebut.isBefore(fin) && oFin.isAfter(debut))) {
                continue; // pas de chevauchement horaire
            }
            Long otherGroupId = (other.getGroup() != null) ? other.getGroup().getId() : null;
            boolean disjointGroups = myGroupId != null && otherGroupId != null
                    && !myGroupId.equals(otherGroupId);
            if (!disjointGroups) {
                throw new BadRequestException(String.format(
                        "Le public concerné a déjà un examen (%s) de %s à %s le %s.",
                        safe(other.getModule().getNom()),
                        oDebut.format(HOUR), oFin.format(HOUR), date.format(DAY)));
            }
        }
    }

    // ----- Résolution / cohérence du contexte -----

    /**
     * Résout les entités référencées et dérive le contexte pédagogique : la
     * <em>filière</em> et l'<em>année</em> proviennent de la promotion, le
     * <em>semestre</em> provient de la matière. La cohérence est contrôlée :
     * la matière doit appartenir à la filière de la promotion, et le groupe (s'il
     * est fourni) à la promotion.
     */
    private ExamenContext resolveContext(ExamenRequestDto request) {
        ExamenContext ctx = new ExamenContext();
        ctx.date = request.getDate();
        ctx.timeSlot = findTimeSlotOrThrow(request.getTimeSlotId());
        ctx.space = findSpaceOrThrow(request.getSpaceId());
        ctx.module = findModuleOrThrow(request.getModuleId());
        ctx.promotion = findPromotionOrThrow(request.getPromotionId());
        ctx.session = findSessionOrThrow(request.getSessionId());
        if (request.getGroupId() != null) {
            ctx.group = findGroupOrThrow(request.getGroupId());
        }

        // Contexte dérivé (aucune redondance saisie côté client).
        ctx.program = ctx.promotion.getProgram();
        ctx.academicYear = ctx.promotion.getAcademicYear();
        ctx.semester = ctx.module.getSemester();

        // Cohérence : la matière appartient bien à la filière de la promotion.
        if (!ctx.module.getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "La matière sélectionnée n'appartient pas à la filière de la promotion.");
        }
        // Cohérence + sécurité : un groupe fourni doit appartenir à la promotion.
        if (ctx.group != null
                && !ctx.group.getPromotion().getId().equals(ctx.promotion.getId())) {
            throw new BadRequestException(
                    "Le groupe sélectionné n'appartient pas à la promotion indiquée.");
        }

        // Période (§20) : l'examen doit se tenir dans la fenêtre de dates de son
        // semestre (dérivé de la matière). Bornes facultatives : une borne nulle
        // n'est pas contrainte. Même logique que « une séance ne déborde pas des
        // dates de son semestre », appliquée ici directement puisque l'examen est daté.
        LocalDate semDebut = (ctx.semester != null) ? ctx.semester.getDateDebut() : null;
        LocalDate semFin = (ctx.semester != null) ? ctx.semester.getDateFin() : null;
        if (semDebut != null && ctx.date != null && ctx.date.isBefore(semDebut)) {
            throw new BadRequestException(
                    "La date de l'examen (" + ctx.date + ") précède le début du semestre ("
                            + semDebut + "). Choisissez une date comprise dans la période du semestre.");
        }
        if (semFin != null && ctx.date != null && ctx.date.isAfter(semFin)) {
            throw new BadRequestException(
                    "La date de l'examen (" + ctx.date + ") dépasse la fin du semestre ("
                            + semFin + "). Choisissez une date comprise dans la période du semestre.");
        }
        return ctx;
    }

    /**
     * Charge un examen et vérifie que l'utilisateur courant y a accès (ADMIN :
     * tout ; responsable pédagogique : uniquement SES filières). L'accès est
     * refusé même si un identifiant arbitraire est fourni via l'API (§12).
     */
    private Examen findAccessibleOrThrow(Long id) {
        Examen examen = examenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Examen introuvable avec l'id " + id));
        accessScope.assertProgramAccessible(examen.getProgram().getId());
        return examen;
    }

    // ----- Helpers de chevauchement (mêmes règles que séances/réservations) -----

    private boolean academicYearCoversDate(Schedule schedule, LocalDate date) {
        if (schedule.getAcademicYear() == null) {
            return true;
        }
        LocalDate debut = schedule.getAcademicYear().getDateDebut();
        LocalDate fin = schedule.getAcademicYear().getDateFin();
        if (debut != null && date.isBefore(debut)) {
            return false;
        }
        return fin == null || !date.isAfter(fin);
    }

    /**
     * Une séance n'occupe sa salle / son public à une date donnée que si l'emploi
     * du temps qui la porte est <b>actif</b> à cette date (§20). Un emploi du
     * temps <b>archivé</b> (libération anticipée) ou <b>expiré</b> (date
     * dépassée) libère ses salles. Les séances sans en-tête ({@code emploiDuTemps}
     * nul : import ADMIN, historique) bloquent toujours (repli non destructif §29).
     */
    private boolean timetableActiveOn(Schedule schedule, LocalDate date) {
        EmploiDuTemps edt = schedule.getEmploiDuTemps();
        if (edt == null) {
            return true;
        }
        if (edt.getStatut() == TimetableStatus.ARCHIVE) {
            return false;
        }
        LocalDate debut = edt.getDateDebut();
        LocalDate fin = edt.getDateFin();
        if (debut != null && date.isBefore(debut)) {
            return false;
        }
        return fin == null || !date.isAfter(fin);
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

    private String safe(String value) {
        return (value == null || value.isBlank()) ? "examen" : value;
    }

    /**
     * Intitulé <b>non nominatif</b> figé sur l'occupation : la matière évaluée.
     * Sert de libellé générique dans les listes d'occupations et n'expose jamais
     * l'enseignant ni un étudiant (§16).
     */
    private String examLabel(ExamenContext ctx) {
        String matiere = (ctx.module != null) ? ctx.module.getNom() : null;
        return (matiere == null || matiere.isBlank()) ? "Examen" : "Examen — " + matiere;
    }

    // ----- Lookups -----

    private TimeSlot findTimeSlotOrThrow(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Créneau horaire introuvable avec l'id " + id));
    }

    private Space findSpaceOrThrow(Long id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    /**
     * Pose un <b>verrou pessimiste</b> sur la salle visée (§21) et remplace
     * l'instance du contexte par l'instance verrouillée.
     *
     * <p>Un examen est une occupation supplémentaire comme une autre : il doit se
     * sérialiser sur la même ressource que les réservations
     * ({@code ReservationService}) et que les soutenances / autres occupations
     * ({@code OccupationSupplementaireService}), qui verrouillent tous deux
     * l'espace avant de valider. Sans ce verrou, deux examens créés
     * simultanément sur la même salle franchissent chacun la détection de
     * conflit avant que l'autre ne soit enregistré, et la salle se retrouve
     * doublement occupée.</p>
     *
     * <p>N'est appelé que sur les chemins d'écriture : la validation « à blanc »
     * de l'import ({@link #dryRunImportErrors}) reste strictement en lecture
     * seule et ne verrouille rien.</p>
     */
    private void lockSpaceOrThrow(ExamenContext ctx) {
        ctx.space = spaceRepository.lockById(ctx.space.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + ctx.space.getId()));
    }

    private Module findModuleOrThrow(Long id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Module introuvable avec l'id " + id));
    }

    private Promotion findPromotionOrThrow(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + id));
    }

    private Group findGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Groupe introuvable avec l'id " + id));
    }

    private SessionUniversitaire findSessionOrThrow(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session universitaire introuvable avec l'id " + id));
    }

    /** Contexte résolu et validé d'un examen. */
    private static class ExamenContext {
        private LocalDate date;
        private TimeSlot timeSlot;
        private Space space;
        private Module module;
        private Program program;
        private Promotion promotion;
        private Group group; // nullable : null = toute la promotion
        private Semester semester;
        private AcademicYear academicYear;
        private SessionUniversitaire session;
    }
}
