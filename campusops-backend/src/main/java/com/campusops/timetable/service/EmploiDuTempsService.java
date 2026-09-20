package com.campusops.timetable.service;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.TimetableSource;
import com.campusops.enums.TimetableStatus;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.schedule.dto.ScheduleResponseDto;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.mapper.ScheduleMapper;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.timetable.dto.EmploiDuTempsRequestDto;
import com.campusops.timetable.dto.EmploiDuTempsResponseDto;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.timetable.mapper.EmploiDuTempsMapper;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import com.campusops.user.entity.User;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Cycle de vie des en-tetes d'emploi du temps (cahier des charges §1, §17).
 *
 * <p>Securite (§12) appliquee cote backend : un responsable pedagogique ne voit
 * et ne manipule que les emplois du temps de SES filieres ; l'administrateur a
 * une vue globale. Aucun controle n'est laisse au seul frontend.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EmploiDuTempsService {

    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final EmploiDuTempsMapper emploiDuTempsMapper;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;
    private final AcademicYearRepository academicYearRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final SessionUniversitaireRepository sessionRepository;
    private final AccessScopeService accessScope;

    // ----- Commandes -----

    public EmploiDuTempsResponseDto createTimetable(EmploiDuTempsRequestDto request) {
        TimetableContext ctx = resolveContext(request);
        accessScope.assertProgramAccessible(ctx.program.getId());

        // §15/§20 : un nouvel emploi du temps ne peut pas etre cree sur un contexte
        // inutilisable. Garder le groupe suffit a couvrir toute la chaine
        // academique (groupe -> promotion -> filiere -> departement + annee) ; on
        // garde en plus le semestre, la session et l'annee de l'en-tete. Chaque
        // garde nomme la cause exacte de l'inutilisabilite.
        ReferentialStatus.requireUsable(ctx.group);
        ReferentialStatus.requireUsable(ctx.semester);
        ReferentialStatus.requireUsable(ctx.session);
        ReferentialStatus.requireUsable(ctx.academicYear);

        emploiDuTempsRepository.findByAcademicYearIdAndGroupIdAndSemesterIdAndSessionId(
                        ctx.academicYear.getId(), ctx.group.getId(),
                        ctx.semester.getId(), ctx.session.getId())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Un emploi du temps existe déjà pour ce contexte "
                                    + "(année, groupe, semestre, session).");
                });

        EmploiDuTemps entity = EmploiDuTemps.builder()
                .academicYear(ctx.academicYear)
                .program(ctx.program)
                .level(ctx.level)
                .promotion(ctx.promotion)
                .group(ctx.group)
                .semester(ctx.semester)
                .session(ctx.session)
                .dateDebut(ctx.dateDebut)
                .dateFin(ctx.dateFin)
                .statut(TimetableStatus.BROUILLON)
                .source(TimetableSource.MANUEL)
                .importePar(accessScope.getCurrentUser())
                .build();

        return toDto(emploiDuTempsRepository.save(entity));
    }

    public EmploiDuTempsResponseDto publish(Long id) {
        return changeStatut(id, TimetableStatus.PUBLIE);
    }

    public EmploiDuTempsResponseDto archive(Long id) {
        return changeStatut(id, TimetableStatus.ARCHIVE);
    }

    public EmploiDuTempsResponseDto reopen(Long id) {
        return changeStatut(id, TimetableStatus.BROUILLON);
    }

    /**
     * Suppression explicite d'un emploi du temps par un utilisateur autorise
     * (§17) : l'en-tete ET ses seances sont retires dans une meme transaction.
     * Action volontaire et tracable, distincte de la bascule d'annee qui, elle,
     * ne supprime jamais rien (§20).
     */
    public void deleteTimetable(Long id) {
        EmploiDuTemps timetable = findAccessibleOrThrow(id);
        List<Schedule> seances = scheduleRepository.findByEmploiDuTempsId(id);
        if (!seances.isEmpty()) {
            scheduleRepository.deleteAll(seances);
        }
        emploiDuTempsRepository.delete(timetable);
    }

    // ----- Consultations -----

    @Transactional(readOnly = true)
    public EmploiDuTempsResponseDto getTimetableById(Long id) {
        return toDto(findAccessibleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<EmploiDuTempsResponseDto> filterTimetables(Long academicYearId, Long programId,
                                                            Long levelId, Long groupId,
                                                            Long semesterId, Long sessionId,
                                                            TimetableStatus statut) {
        // Par defaut, on se cale sur l'annee universitaire active (§16, §24) :
        // sans identifiant explicite, la liste ne montre que l'annee active plutot
        // que tout l'historique. Les donnees des autres annees restent en base.
        Long effectiveYearId = resolveEffectiveYearId(academicYearId);

        List<EmploiDuTemps> timetables = accessScope.isAdmin()
                ? emploiDuTempsRepository.findAll()
                : emploiDuTempsRepository.findByProgramIdIn(accessScope.myProgramIds());

        return timetables.stream()
                .filter(t -> effectiveYearId == null || t.getAcademicYear().getId().equals(effectiveYearId))
                .filter(t -> programId == null || t.getProgram().getId().equals(programId))
                .filter(t -> levelId == null || t.getLevel().getId().equals(levelId))
                .filter(t -> groupId == null || t.getGroup().getId().equals(groupId))
                .filter(t -> semesterId == null || t.getSemester().getId().equals(semesterId))
                .filter(t -> sessionId == null || t.getSession().getId().equals(sessionId))
                .filter(t -> statut == null || t.getStatut() == statut)
                .map(this::toDto)
                .toList();
    }

    /**
     * Resout l'annee universitaire a utiliser pour un filtrage : celle demandee
     * explicitement, sinon l'annee active. Renvoie {@code null} si aucune annee
     * n'est demandee et qu'aucune annee active n'existe (repli non cassant :
     * l'ensemble des annees reste alors visible).
     */
    private Long resolveEffectiveYearId(Long academicYearId) {
        if (academicYearId != null) {
            return academicYearId;
        }
        return academicYearRepository.findFirstByActifTrue()
                .map(AcademicYear::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getTimetableSeances(Long id) {
        findAccessibleOrThrow(id);
        return scheduleRepository.findByEmploiDuTempsId(id).stream()
                .map(scheduleMapper::toResponseDto)
                .toList();
    }

    // ----- Regles metier / helpers -----

    private EmploiDuTempsResponseDto changeStatut(Long id, TimetableStatus cible) {
        EmploiDuTemps timetable = findAccessibleOrThrow(id);
        assertTransitionAutorisee(timetable.getStatut(), cible);
        timetable.setStatut(cible);
        return toDto(emploiDuTempsRepository.save(timetable));
    }

    /**
     * Machine à états du cycle de vie d'un emploi du temps. Donner un sens métier
     * aux statuts, c'est d'abord interdire les transitions incohérentes :
     *
     * <ul>
     *   <li>{@code BROUILLON} → {@code PUBLIE} (publication) ou {@code ARCHIVE}
     *       (abandon d'un brouillon jamais diffusé, conservé pour l'historique).</li>
     *   <li>{@code PUBLIE} → {@code ARCHIVE} (retrait de service : les salles sont
     *       libérées) ou {@code BROUILLON} (dépublication pour corriger).</li>
     *   <li>{@code ARCHIVE} → {@code BROUILLON} (réouverture : l'emploi du temps
     *       reprend vie et ses salles redeviennent occupées, il faut donc repasser
     *       par une phase de brouillon avant toute nouvelle publication).</li>
     * </ul>
     *
     * <p>Rappel du sens de chaque statut dans le système :
     * {@code BROUILLON} = en construction, <b>non diffusé</b> mais ses séances
     * <b>occupent déjà les salles</b> (pour éviter les conflits pendant la saisie) ;
     * {@code PUBLIE} = officiel et diffusé, salles occupées ; {@code ARCHIVE} =
     * historique, <b>salles libérées</b> automatiquement (cf.
     * {@code AvailabilityService.timetableActiveOn}).</p>
     */
    private void assertTransitionAutorisee(TimetableStatus actuel, TimetableStatus cible) {
        if (actuel == cible) {
            throw new BadRequestException(
                    "L'emploi du temps est déjà au statut « " + libelle(cible) + " ».");
        }
        boolean autorisee = switch (actuel) {
            case BROUILLON -> cible == TimetableStatus.PUBLIE || cible == TimetableStatus.ARCHIVE;
            case PUBLIE -> cible == TimetableStatus.ARCHIVE || cible == TimetableStatus.BROUILLON;
            case ARCHIVE -> cible == TimetableStatus.BROUILLON;
        };
        if (!autorisee) {
            throw new BadRequestException(
                    "Transition de statut interdite : « " + libelle(actuel) + " » → « "
                            + libelle(cible) + " ».");
        }
    }

    private String libelle(TimetableStatus statut) {
        return switch (statut) {
            case BROUILLON -> "Brouillon";
            case PUBLIE -> "Publié";
            case ARCHIVE -> "Archivé";
        };
    }

    /**
     * Charge un emploi du temps et verifie que l'utilisateur courant y a acces
     * (ADMIN : tout ; responsable pedagogique : uniquement SES filieres). Meme si
     * un identifiant arbitraire est fourni via l'API, l'acces est refuse (§12).
     */
    private EmploiDuTemps findAccessibleOrThrow(Long id) {
        EmploiDuTemps timetable = emploiDuTempsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Emploi du temps introuvable avec l'id " + id));
        accessScope.assertProgramAccessible(timetable.getProgram().getId());
        return timetable;
    }

    private TimetableContext resolveContext(EmploiDuTempsRequestDto request) {
        TimetableContext ctx = new TimetableContext();
        ctx.academicYear = findAcademicYearOrThrow(request.getAcademicYearId());
        ctx.program = findProgramOrThrow(request.getProgramId());
        ctx.level = findLevelOrThrow(request.getLevelId());
        ctx.promotion = findPromotionOrThrow(request.getPromotionId());
        ctx.group = findGroupOrThrow(request.getGroupId());
        ctx.semester = findSemesterOrThrow(request.getSemesterId());
        ctx.session = findSessionOrThrow(request.getSessionId());

        if (!ctx.promotion.getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée n'appartient pas à la filière indiquée");
        }
        if (!ctx.promotion.getLevel().getId().equals(ctx.level.getId())) {
            throw new BadRequestException(
                    "La promotion sélectionnée ne correspond pas au niveau indiqué");
        }
        if (!ctx.group.getPromotion().getId().equals(ctx.promotion.getId())) {
            throw new BadRequestException(
                    "Le groupe sélectionné n'appartient pas à la promotion indiquée");
        }

        // Periode de validite (§20) DERIVEE du semestre : un emploi du temps ne
        // couvre que la fenetre de son semestre. La periode n'est plus saisie a
        // la main — le semestre en est l'unique source (repli sur l'annee).
        LocalDate[] periode = resolvePeriode(ctx.semester, ctx.academicYear);
        ctx.dateDebut = periode[0];
        ctx.dateFin = periode[1];
        return ctx;
    }

    /**
     * Determine la periode de validite [debut, fin] d'un emploi du temps (§20),
     * <b>derivee du semestre</b> : un emploi du temps — et donc chacune de ses
     * seances — ne peut couvrir que la fenetre [debut, fin] de son semestre.
     * C'est ce qui garantit qu'aucune seance ne deborde des dates du semestre,
     * sans qu'aucune date ne soit saisie manuellement a la creation/import (§20).
     *
     * <p>Chaque borne du semestre est facultative : une borne absente retombe
     * sur la periode de l'annee universitaire (elle-meme facultative). On valide
     * uniquement la coherence {@code fin >= debut}.</p>
     */
    private LocalDate[] resolvePeriode(Semester semester, AcademicYear year) {
        LocalDate debut = (semester.getDateDebut() != null)
                ? semester.getDateDebut() : year.getDateDebut();
        LocalDate fin = (semester.getDateFin() != null)
                ? semester.getDateFin() : year.getDateFin();

        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new BadRequestException(
                    "La période du semestre est incohérente : la date de fin doit être "
                            + "postérieure ou égale à la date de début. Corrigez-la dans la "
                            + "gestion des semestres.");
        }
        return new LocalDate[]{debut, fin};
    }

    private EmploiDuTempsResponseDto toDto(EmploiDuTemps entity) {
        EmploiDuTempsResponseDto dto = emploiDuTempsMapper.toResponseDto(entity);
        dto.setNombreSeances(scheduleRepository.countByEmploiDuTempsId(entity.getId()));
        // Expiration (§20) : la date de fin est passee → salles automatiquement
        // liberees. Calcule a la date du jour (une borne nulle = jamais expire).
        dto.setExpire(entity.getDateFin() != null
                && entity.getDateFin().isBefore(LocalDate.now()));
        User importePar = entity.getImportePar();
        if (importePar != null) {
            dto.setImporteParNom((importePar.getFirstName() + " " + importePar.getLastName()).trim());
        }
        return dto;
    }

    private AcademicYear findAcademicYearOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + id));
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

    private SessionUniversitaire findSessionOrThrow(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session universitaire introuvable avec l'id " + id));
    }

    private static class TimetableContext {
        private AcademicYear academicYear;
        private Program program;
        private Level level;
        private Promotion promotion;
        private Group group;
        private Semester semester;
        private SessionUniversitaire session;
        private LocalDate dateDebut;
        private LocalDate dateFin;
    }
}
