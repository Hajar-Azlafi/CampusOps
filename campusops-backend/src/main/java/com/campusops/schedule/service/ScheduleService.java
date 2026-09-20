package com.campusops.schedule.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.PresenceType;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.SessionType;
import com.campusops.enums.WeekDay;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.dto.ScheduleRequestDto;
import com.campusops.schedule.dto.ScheduleResponseDto;
import com.campusops.schedule.dto.SessionTypeCountDto;
import com.campusops.schedule.dto.SessionTypeStatsDto;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.mapper.ScheduleMapper;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.typeseance.repository.TypeSeanceRepository;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Statuts de reservation qui bloquent effectivement une salle. */
    private static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED);

    private final ScheduleRepository scheduleRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SpaceRepository spaceRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ReservationRepository reservationRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final ModuleRepository moduleRepository;
    private final TypeSeanceRepository typeSeanceRepository;
    private final ScheduleMapper scheduleMapper;
    private final AccessScopeService accessScope;
    private final SettingsService settingsService;

    public ScheduleResponseDto createSchedule(ScheduleRequestDto request) {
        ScheduleContext ctx = resolveContext(request);
        accessScope.assertProgramAccessible(ctx.program.getId());
        checkConflicts(request, ctx, null);

        Schedule schedule = scheduleMapper.toEntity(request);
        applyContext(schedule, ctx);
        schedule.setActif(true);

        Schedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ScheduleResponseDto getScheduleById(Long id) {
        Schedule schedule = findScheduleOrThrow(id);
        accessScope.assertProgramAccessible(schedule.getProgram().getId());
        return scheduleMapper.toResponseDto(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> filterSchedules(Boolean actif) {
        List<Schedule> schedules;
        if (accessScope.isAdmin()) {
            schedules = (actif != null)
                    ? scheduleRepository.findByActif(actif)
                    : scheduleRepository.findAll();
        } else {
            schedules = scheduleRepository.findByProgramIdIn(accessScope.myProgramIds());
            if (actif != null) {
                schedules = schedules.stream().filter(s -> s.isActif() == actif).toList();
            }
        }
        return schedules.stream().map(scheduleMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedulesByPromotion(Long promotionId) {
        Promotion promotion = findPromotionOrThrow(promotionId);
        accessScope.assertProgramAccessible(promotion.getProgram().getId());
        return scheduleRepository.findByPromotionId(promotionId).stream()
                .map(scheduleMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedulesByGroup(Long groupId) {
        Group group = findGroupOrThrow(groupId);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        return scheduleRepository.findByGroupId(groupId).stream()
                .map(scheduleMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedulesBySpace(Long spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new ResourceNotFoundException("Espace introuvable avec l'id " + spaceId);
        }
        return restrictToScope(scheduleRepository.findBySpaceId(spaceId)).stream()
                .map(scheduleMapper::toResponseDto).toList();
    }

    /**
     * Consultation de l'emploi du temps d'une année universitaire donnée
     * (courante ou historique). Les séances des années passées restent
     * consultables sans se mélanger à celles de l'année active.
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedulesByAcademicYear(Long academicYearId) {
        if (!academicYearRepository.existsById(academicYearId)) {
            throw new ResourceNotFoundException(
                    "Année universitaire introuvable avec l'id " + academicYearId);
        }
        return restrictToScope(scheduleRepository.findByAcademicYearId(academicYearId)).stream()
                .map(scheduleMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> searchSchedules(String keyword) {
        return restrictToScope(scheduleRepository.searchByKeyword(keyword)).stream()
                .map(scheduleMapper::toResponseDto).toList();
    }

    /**
     * Statistiques de repartition des seances <b>par type</b> (§6/§19). Calcul
     * reel et dynamique : on part des seances ACTIVES du perimetre de
     * l'utilisateur (ADMIN = toutes ; responsable pedagogique = ses seules
     * filieres via {@link #restrictToScope}), on applique les filtres de contexte
     * fournis (tous facultatifs), puis on agrege par type de seance.
     *
     * <p>Chaque seance est rattachee a son type configurable ({@code typeSeance}).
     * Les seances heritees dont la FK est absente (import ADMIN historique) sont
     * rattachees via l'enumeration {@code type} en retrouvant le type de meme
     * code — aucune statistique n'est ainsi perdue.</p>
     *
     * <p>La repartition liste <b>tous les types actifs</b> (valeur 0 comprise,
     * jamais masques : « Examen » = 0 tant qu'aucun examen n'existe), puis les
     * types desactives encore utilises, puis un agregat « Non categorise »
     * uniquement si necessaire ; la somme des valeurs egale toujours le total.</p>
     */
    @Transactional(readOnly = true)
    public SessionTypeStatsDto getSessionTypeStats(Long academicYearId, Long programId, Long levelId,
                                                   Long promotionId, Long groupId, Long semesterId,
                                                   Long emploiDuTempsId) {
        // Perimetre : seances actives visibles par l'utilisateur, filtrees par le
        // contexte pedagogique demande (chaque critere est facultatif).
        List<Schedule> schedules = restrictToScope(scheduleRepository.findByActif(true)).stream()
                .filter(s -> idMatches(academicYearId, s.getAcademicYear().getId()))
                .filter(s -> idMatches(programId, s.getProgram().getId()))
                .filter(s -> idMatches(levelId, s.getLevel().getId()))
                .filter(s -> idMatches(promotionId, s.getPromotion().getId()))
                .filter(s -> idMatches(groupId, s.getGroup().getId()))
                .filter(s -> idMatches(semesterId, s.getSemester().getId()))
                .filter(s -> idMatches(emploiDuTempsId,
                        s.getEmploiDuTemps() != null ? s.getEmploiDuTemps().getId() : null))
                .toList();

        // Referentiels : ordre d'affichage = types actifs ; table de repli par
        // code (tous types confondus) pour rattacher l'enumeration historique.
        List<TypeSeance> activeTypes = typeSeanceRepository.findByActifOrderByOrdreAsc(true);
        Map<String, TypeSeance> byCode = new HashMap<>();
        for (TypeSeance t : typeSeanceRepository.findAllByOrderByOrdreAsc()) {
            if (t.getCode() != null) {
                byCode.putIfAbsent(t.getCode(), t);
            }
        }

        // Comptage par type resolu : FK si presente, sinon repli sur l'enum
        // historique en retrouvant le type de meme code.
        Map<Long, Long> counts = new LinkedHashMap<>();
        Map<Long, TypeSeance> resolvedById = new LinkedHashMap<>();
        long nonCategorise = 0L;
        for (Schedule s : schedules) {
            TypeSeance resolved = s.getTypeSeance();
            if (resolved == null && s.getType() != null) {
                resolved = byCode.get(s.getType().name());
            }
            if (resolved == null) {
                nonCategorise++;
            } else {
                counts.merge(resolved.getId(), 1L, Long::sum);
                resolvedById.putIfAbsent(resolved.getId(), resolved);
            }
        }

        // Restitution : tous les types actifs (0 compris), dans l'ordre d'affichage.
        List<SessionTypeCountDto> repartition = new ArrayList<>();
        for (TypeSeance t : activeTypes) {
            repartition.add(toCount(t, counts.getOrDefault(t.getId(), 0L)));
        }
        // Types desactives mais encore utilises : ajoutes pour que la somme colle
        // (un type actif est deja present ci-dessus, on ne l'ajoute pas deux fois).
        for (TypeSeance t : resolvedById.values()) {
            if (!t.isActif()) {
                repartition.add(toCount(t, counts.getOrDefault(t.getId(), 0L)));
            }
        }
        // Agregat de repli, uniquement s'il reste des seances non rattachables.
        if (nonCategorise > 0) {
            repartition.add(SessionTypeCountDto.builder()
                    .typeSeanceId(null)
                    .code(null)
                    .label("Non categorise")
                    .couleur(null)
                    .actif(false)
                    .value(nonCategorise)
                    .build());
        }

        return SessionTypeStatsDto.builder()
                .total(schedules.size())
                .repartition(repartition)
                .build();
    }

    public ScheduleResponseDto updateSchedule(Long id, ScheduleRequestDto request) {
        Schedule schedule = findScheduleOrThrow(id);
        accessScope.assertProgramAccessible(schedule.getProgram().getId());
        ScheduleContext ctx = resolveContext(request);
        accessScope.assertProgramAccessible(ctx.program.getId());
        checkConflicts(request, ctx, id);

        applyContext(schedule, ctx);
        schedule.setJour(request.getJour());
        schedule.setEnseignant(request.getEnseignant());
        schedule.setCommentaire(request.getCommentaire());

        Schedule updated = scheduleRepository.save(schedule);
        return scheduleMapper.toResponseDto(updated);
    }

    public void deactivateSchedule(Long id) {
        Schedule schedule = findScheduleOrThrow(id);
        accessScope.assertProgramAccessible(schedule.getProgram().getId());
        schedule.setActif(false);
        scheduleRepository.save(schedule);
    }

    public void activateSchedule(Long id) {
        Schedule schedule = findScheduleOrThrow(id);
        accessScope.assertProgramAccessible(schedule.getProgram().getId());
        checkConflicts(toRequest(schedule), toContext(schedule), id);
        schedule.setActif(true);
        scheduleRepository.save(schedule);
    }

    // ----- Helpers -----

    /**
     * Detecte les conflits d'une seance (cahier des charges §9/§33) : la
     * comparaison est GLOBALE (toutes les seances deja presentes) et repose sur
     * le CHEVAUCHEMENT horaire, pas sur l'egalite stricte de creneau. Sont
     * verifies successivement : la salle (si presentiel), le groupe,
     * l'enseignant, puis les reservations existantes sur la meme salle.
     */
    private void checkConflicts(ScheduleRequestDto request, ScheduleContext ctx, Long excludeId) {
        // §20 : une seance ne peut etre programmee (creation, modification ou
        // reactivation) que sur un contexte utilisable. Ce point est commun aux
        // trois chemins d'ecriture, donc la reactivation d'une seance dont le
        // contexte a ete desactive depuis est elle aussi bloquee. Chaque garde
        // nomme la cause exacte (salle/etage/batiment, groupe et sa chaine,
        // semestre, annee, module, type de seance).
        if (ctx.space != null) {
            ReferentialStatus.requireUsable(ctx.space);
        }
        ReferentialStatus.requireUsable(ctx.group);
        ReferentialStatus.requireUsable(ctx.semester);
        ReferentialStatus.requireUsable(ctx.academicYear);
        // Le creneau (grille horaire officielle) est obligatoire pour une seance :
        // une seance ne peut etre ancree sur un creneau desactive, retire de la
        // grille (§20/§26). Les horaires d'ouverture sont derives des creneaux
        // actifs, la seance doit donc referencer un creneau actif.
        ReferentialStatus.requireUsable(ctx.timeSlot);
        if (ctx.module != null) {
            ReferentialStatus.requireUsable(ctx.module);
        }
        if (ctx.typeSeance != null) {
            ReferentialStatus.requireUsable(ctx.typeSeance);
        }

        Long academicYearId = ctx.academicYear.getId();
        Long semesterId = ctx.semester.getId();
        LocalTime debut = ctx.timeSlot.getHeureDebut();
        LocalTime fin = ctx.timeSlot.getHeureFin();
        WeekDay jour = request.getJour();

        // Duree maximale d'une seance (Module 11, §5) : verifiee ici parce que
        // creation, modification et reactivation passent toutes par ce point. Le
        // motif est produit par la configuration centrale, donc identique a celui
        // de l'import (§20 : une seule regle, un seul texte).
        String motifDuree = settingsService.motifDureeSeanceExcessive(debut, fin);
        if (motifDuree != null) {
            throw new BadRequestException(motifDuree);
        }

        // Conflit de salle : uniquement si une salle est affectee (presentiel).
        if (ctx.space != null) {
            boolean spaceConflict = (excludeId == null)
                    ? scheduleRepository.existsSpaceConflict(
                            academicYearId, semesterId, jour, debut, fin, ctx.space.getId())
                    : scheduleRepository.existsSpaceConflictExcludingId(
                            academicYearId, semesterId, jour, debut, fin, ctx.space.getId(), excludeId);
            if (spaceConflict) {
                throw new BadRequestException(
                        "Cet espace est déjà occupé par une autre séance sur ce créneau");
            }
        }

        boolean groupConflict = (excludeId == null)
                ? scheduleRepository.existsGroupConflict(
                        academicYearId, semesterId, jour, debut, fin, ctx.group.getId())
                : scheduleRepository.existsGroupConflictExcludingId(
                        academicYearId, semesterId, jour, debut, fin, ctx.group.getId(), excludeId);
        if (groupConflict) {
            throw new BadRequestException(
                    "Ce groupe a déjà une séance programmée sur ce créneau");
        }

        boolean teacherConflict = (excludeId == null)
                ? scheduleRepository.existsTeacherConflict(
                        academicYearId, semesterId, jour, debut, fin, request.getEnseignant())
                : scheduleRepository.existsTeacherConflictExcludingId(
                        academicYearId, semesterId, jour, debut, fin, request.getEnseignant(), excludeId);
        if (teacherConflict) {
            throw new BadRequestException(
                    "Cet enseignant a déjà une séance programmée sur ce créneau");
        }

        // Confrontation avec les reservations existantes (§22/§23) : une salle
        // deja reservee (en attente ou validee) ne peut pas accueillir de seance
        // recurrente sur le meme jour et le meme creneau.
        if (ctx.space != null) {
            checkReservationConflicts(ctx, jour, debut, fin);
        }
    }

    /**
     * Verifie qu'aucune reservation bloquante ne recouvre le creneau de la
     * seance. La seance etant hebdomadaire, on retient les reservations dont la
     * date tombe le meme jour de la semaine et dans la periode de l'annee
     * universitaire.
     */
    private void checkReservationConflicts(ScheduleContext ctx, WeekDay jour, LocalTime debut, LocalTime fin) {
        List<Reservation> blocking = reservationRepository.findBlockingForSpace(
                ctx.space.getId(), debut, fin, BLOCKING_STATUSES);
        LocalDate from = ctx.academicYear.getDateDebut();
        LocalDate to = ctx.academicYear.getDateFin();
        for (Reservation r : blocking) {
            if (toWeekDay(r.getDate().getDayOfWeek()) != jour) {
                continue;
            }
            if (from != null && r.getDate().isBefore(from)) {
                continue;
            }
            if (to != null && r.getDate().isAfter(to)) {
                continue;
            }
            throw new BadRequestException(String.format(
                    "L'espace est déjà réservé le %s de %s à %s (réservation #%d) : "
                            + "impossible d'y programmer cette séance",
                    r.getDate().format(DAY), r.getHeureDebut().format(HOUR),
                    r.getHeureFin().format(HOUR), r.getId()));
        }
    }

    private ScheduleContext resolveContext(ScheduleRequestDto request) {
        ScheduleContext ctx = new ScheduleContext();
        ctx.timeSlot = findTimeSlotOrThrow(request.getTimeSlotId());
        // La salle est facultative : absente pour une seance distancielle (§6).
        ctx.space = (request.getSpaceId() != null)
                ? findSpaceOrThrow(request.getSpaceId())
                : null;
        ctx.program = findProgramOrThrow(request.getProgramId());
        ctx.level = findLevelOrThrow(request.getLevelId());
        ctx.promotion = findPromotionOrThrow(request.getPromotionId());
        ctx.group = findGroupOrThrow(request.getGroupId());
        ctx.semester = findSemesterOrThrow(request.getSemesterId());
        ctx.academicYear = findAcademicYearOrThrow(request.getAcademicYearId());

        if (!ctx.promotion.getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "La promotion selectionnee n'appartient pas a la filiere indiquee");
        }
        if (!ctx.promotion.getLevel().getId().equals(ctx.level.getId())) {
            throw new BadRequestException(
                    "La promotion selectionnee ne correspond pas au niveau indique");
        }
        if (!ctx.group.getPromotion().getId().equals(ctx.promotion.getId())) {
            throw new BadRequestException(
                    "Le groupe selectionne n'appartient pas a la promotion indiquee");
        }

        // Module d'enseignement (§8-§11) : prioritaire lorsqu'il est fourni. Il
        // doit appartenir au contexte pedagogique de la seance (meme filiere ET
        // meme semestre) et etre actif. A defaut de module, une matiere en texte
        // libre reste acceptee (import Excel, anciens appels).
        if (request.getModuleId() != null) {
            Module module = findModuleOrThrow(request.getModuleId());
            if (!module.getProgram().getId().equals(ctx.program.getId())
                    || !module.getSemester().getId().equals(ctx.semester.getId())) {
                throw new BadRequestException(
                        "Le module sélectionné n'appartient pas au contexte "
                                + "(filière + semestre) de la séance");
            }
            if (!module.isActif()) {
                throw new BadRequestException("Le module sélectionné est désactivé");
            }
            ctx.module = module;
            ctx.matiere = module.getNom();
        } else if (request.getMatiere() != null && !request.getMatiere().isBlank()) {
            ctx.matiere = request.getMatiere().trim();
        } else {
            throw new BadRequestException("La matière (ou le module) est obligatoire");
        }

        // Type de seance (§7) : le type configurable est prioritaire. L'enum
        // historique est toujours renseignee (derivee du code du type
        // configurable si besoin) pour la retro-compatibilite et les statistiques.
        if (request.getTypeSeanceId() != null) {
            TypeSeance ts = findTypeSeanceOrThrow(request.getTypeSeanceId());
            if (!ts.isActif()) {
                throw new BadRequestException("Le type de séance sélectionné est désactivé");
            }
            ctx.typeSeance = ts;
            ctx.type = deriveEnumFromCode(ts.getCode());
        } else if (request.getType() != null) {
            ctx.type = request.getType();
            ctx.typeSeance = typeSeanceRepository.findByCode(request.getType().name()).orElse(null);
        } else {
            throw new BadRequestException("Le type de séance est obligatoire");
        }

        // Type de presence (§6) : PRESENTIEL par defaut. En presentiel la salle
        // est obligatoire ; en distanciel elle reste facultative.
        ctx.typePresence = (request.getTypePresence() != null)
                ? request.getTypePresence()
                : PresenceType.PRESENTIEL;
        if (ctx.typePresence == PresenceType.PRESENTIEL && ctx.space == null) {
            throw new BadRequestException(
                    "Une séance en présentiel doit être associée à une salle");
        }

        // Rattachement facultatif a un en-tete d'emploi du temps (§1/§17) : le
        // contexte de la seance doit alors coincider avec celui de l'en-tete.
        if (request.getEmploiDuTempsId() != null) {
            EmploiDuTemps header = emploiDuTempsRepository.findById(request.getEmploiDuTempsId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Emploi du temps introuvable avec l'id " + request.getEmploiDuTempsId()));
            assertHeaderMatchesContext(header, ctx);
            ctx.emploiDuTemps = header;
        }
        return ctx;
    }

    /**
     * Verifie que le contexte de la seance (annee, filiere, niveau, promotion,
     * groupe, semestre) coincide avec celui de l'en-tete d'emploi du temps
     * auquel on souhaite la rattacher : on ne peut pas attacher une seance a un
     * emploi du temps d'un autre perimetre.
     */
    private void assertHeaderMatchesContext(EmploiDuTemps header, ScheduleContext ctx) {
        boolean matches = header.getAcademicYear().getId().equals(ctx.academicYear.getId())
                && header.getProgram().getId().equals(ctx.program.getId())
                && header.getLevel().getId().equals(ctx.level.getId())
                && header.getPromotion().getId().equals(ctx.promotion.getId())
                && header.getGroup().getId().equals(ctx.group.getId())
                && header.getSemester().getId().equals(ctx.semester.getId());
        if (!matches) {
            throw new BadRequestException(
                    "La séance ne correspond pas au contexte de l'emploi du temps "
                            + "sélectionné (année, filière, niveau, promotion, groupe, semestre)");
        }
    }

    private void applyContext(Schedule schedule, ScheduleContext ctx) {
        schedule.setTimeSlot(ctx.timeSlot);
        schedule.setSpace(ctx.space);
        schedule.setProgram(ctx.program);
        schedule.setLevel(ctx.level);
        schedule.setPromotion(ctx.promotion);
        schedule.setGroup(ctx.group);
        schedule.setSemester(ctx.semester);
        schedule.setAcademicYear(ctx.academicYear);
        schedule.setModule(ctx.module);
        schedule.setMatiere(ctx.matiere);
        schedule.setTypeSeance(ctx.typeSeance);
        schedule.setType(ctx.type);
        schedule.setTypePresence(ctx.typePresence);
        schedule.setEmploiDuTemps(ctx.emploiDuTemps);
    }

    private ScheduleContext toContext(Schedule schedule) {
        ScheduleContext ctx = new ScheduleContext();
        ctx.timeSlot = schedule.getTimeSlot();
        ctx.space = schedule.getSpace();
        ctx.program = schedule.getProgram();
        ctx.level = schedule.getLevel();
        ctx.promotion = schedule.getPromotion();
        ctx.group = schedule.getGroup();
        ctx.semester = schedule.getSemester();
        ctx.academicYear = schedule.getAcademicYear();
        ctx.module = schedule.getModule();
        ctx.matiere = schedule.getMatiere();
        ctx.typeSeance = schedule.getTypeSeance();
        ctx.type = schedule.getType();
        ctx.typePresence = schedule.getTypePresence();
        ctx.emploiDuTemps = schedule.getEmploiDuTemps();
        return ctx;
    }

    private ScheduleRequestDto toRequest(Schedule schedule) {
        return ScheduleRequestDto.builder()
                .jour(schedule.getJour())
                .enseignant(schedule.getEnseignant())
                .build();
    }

    /**
     * Restreint une liste de seances au perimetre de l'utilisateur courant.
     * ADMIN : liste inchangee. RP : uniquement les seances de SES filieres.
     */
    private List<Schedule> restrictToScope(List<Schedule> schedules) {
        if (accessScope.isAdmin()) {
            return schedules;
        }
        Set<Long> mine = accessScope.myProgramIdSet();
        return schedules.stream()
                .filter(s -> s.getProgram() != null && mine.contains(s.getProgram().getId()))
                .toList();
    }

    /**
     * Vrai lorsqu'aucun filtre n'est demande ({@code filter == null}) ou que
     * l'identifiant reel correspond. Sert au filtrage de contexte des statistiques.
     */
    private boolean idMatches(Long filter, Long actual) {
        return filter == null || filter.equals(actual);
    }

    /** Construit une ligne de repartition a partir d'un type de seance et de son comptage. */
    private SessionTypeCountDto toCount(TypeSeance type, long value) {
        return SessionTypeCountDto.builder()
                .typeSeanceId(type.getId())
                .code(type.getCode())
                .label(type.getNom())
                .couleur(type.getCouleur())
                .actif(type.isActif())
                .value(value)
                .build();
    }

    private Schedule findScheduleOrThrow(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Emploi du temps introuvable avec l'id " + id));
    }

    private TimeSlot findTimeSlotOrThrow(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Creneau horaire introuvable avec l'id " + id));
    }

    private Space findSpaceOrThrow(Long id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    private Program findProgramOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + id));
    }

    private Level findLevelOrThrow(Long id) {
        return levelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + id));
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

    private Semester findSemesterOrThrow(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semestre introuvable avec l'id " + id));
    }

    private AcademicYear findAcademicYearOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + id));
    }

    private Module findModuleOrThrow(Long id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Module introuvable avec l'id " + id));
    }

    private TypeSeance findTypeSeanceOrThrow(Long id) {
        return typeSeanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Type de séance introuvable avec l'id " + id));
    }

    /**
     * Derive l'enum historique {@link SessionType} depuis le code d'un type de
     * seance configurable. Les types personnalises (hors nomenclature d'origine)
     * retombent sur {@link SessionType#AUTRE} : l'enum n'est qu'un rappel de
     * compatibilite, la source de verite etant desormais le type configurable.
     */
    private SessionType deriveEnumFromCode(String code) {
        try {
            return SessionType.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            return SessionType.AUTRE;
        }
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

    private static class ScheduleContext {
        private TimeSlot timeSlot;
        private Space space;
        private Program program;
        private Level level;
        private Promotion promotion;
        private Group group;
        private Semester semester;
        private AcademicYear academicYear;
        private Module module;
        private String matiere;
        private TypeSeance typeSeance;
        private SessionType type;
        private PresenceType typePresence;
        private EmploiDuTemps emploiDuTemps;
    }
}
