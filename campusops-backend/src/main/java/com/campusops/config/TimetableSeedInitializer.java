package com.campusops.config;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.PresenceType;
import com.campusops.enums.SessionType;
import com.campusops.enums.TimetableSource;
import com.campusops.enums.TimetableStatus;
import com.campusops.enums.WeekDay;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
/**
 * Amorçage du module « Emplois du temps » au démarrage (cahier des charges §4,
 * §26, §36). Deux responsabilités indépendantes, chacune sous son propre
 * interrupteur :
 *
 * <ol>
 *   <li><b>Sessions universitaires de référence</b> ({@code campusops.seed.academic-sessions},
 *       <i>activé par défaut</i>) : sans au moins une session, aucun emploi du
 *       temps ne peut être créé (une session fait partie du contexte, §1/§4). On
 *       amorce donc des sessions <b>génériques</b> (« Session normale », « Session
 *       de rattrapage ») — aucun nom d'établissement n'est codé en dur (§26).
 *       Chaque établissement peut ensuite les compléter via l'administration.</li>
 *   <li><b>Emploi du temps de démonstration</b> ({@code campusops.seed.timetables},
 *       <i>désactivé par défaut</i>) : crée un exemple minimal et générique pour
 *       le premier groupe de l'année active, uniquement pour illustrer l'écran.
 *       Opt-in car il s'agit de données de démonstration (§26).</li>
 * </ol>
 *
 * <p><b>Idempotent</b> : les sessions sont reconnues par leur code ; l'emploi du
 * temps de démonstration n'est créé que s'il n'existe pas déjà pour son contexte.
 * <b>Non destructif</b> (§20) : rien n'est jamais supprimé ni écrasé. S'exécute
 * après le référentiel académique et les responsables ({@code @Order(11)}).</p>
 */
@Component
@Order(11)
@RequiredArgsConstructor
@Slf4j
public class TimetableSeedInitializer implements CommandLineRunner {

    private final SessionUniversitaireRepository sessionRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final ScheduleRepository scheduleRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;

    /** Amorçage des sessions universitaires de référence (activé par défaut). */
    @Value("${campusops.seed.academic-sessions:true}")
    private boolean sessionsEnabled;

    /** Emploi du temps de démonstration (désactivé par défaut). */
    @Value("${campusops.seed.timetables:false}")
    private boolean timetablesEnabled;

    /** Code de la session « normale », réutilisé pour l'emploi du temps de démonstration. */
    private static final String SESSION_NORMALE_CODE = "NORMALE";

    /** Sessions de référence génériques (libellé, code, ordre). */
    private static final List<SessionSeed> DEFAULT_SESSIONS = List.of(
            new SessionSeed("Session normale", SESSION_NORMALE_CODE, 1),
            new SessionSeed("Session de rattrapage", "RATTRAPAGE", 2)
    );

    private record SessionSeed(String nom, String code, int ordre) {
    }

    @Override
    @Transactional
    public void run(String... args) {
        int sessionsCreated = seedSessions();
        boolean demoCreated = seedDemoTimetable();

        log.info("========================================");
        log.info("Amorçage « Emplois du temps » :");
        log.info("  - {} session(s) de référence créée(s) (total : {})",
                sessionsCreated, sessionRepository.count());
        log.info("  - emploi du temps de démonstration : {}",
                demoCreated ? "créé" : "non créé");
        log.info("========================================");
    }

    // ----- 1) Sessions universitaires de référence -----

    private int seedSessions() {
        if (!sessionsEnabled) {
            log.info("Amorçage des sessions universitaires désactivé "
                    + "(campusops.seed.academic-sessions=false).");
            return 0;
        }
        int created = 0;
        for (SessionSeed seed : DEFAULT_SESSIONS) {
            if (sessionRepository.existsByCode(seed.code())) {
                continue; // déjà présente : idempotent, aucune modification.
            }
            sessionRepository.save(SessionUniversitaire.builder()
                    .nom(seed.nom())
                    .code(seed.code())
                    .ordre(seed.ordre())
                    .actif(true)
                    .build());
            created++;
            log.info("Session universitaire de référence créée : {} ({}).",
                    seed.nom(), seed.code());
        }
        return created;
    }

    // ----- 2) Emploi du temps de démonstration (opt-in) -----

    /**
     * Crée un emploi du temps de démonstration minimal et générique pour le
     * premier groupe de l'année active. Les séances sont en distanciel (aucune
     * salle) afin de ne créer aucun conflit d'occupation ni de réservation.
     * Renvoie {@code true} si un emploi du temps a été créé.
     */
    private boolean seedDemoTimetable() {
        if (!timetablesEnabled) {
            return false;
        }

        AcademicYear activeYear = academicYearRepository.findAll().stream()
                .filter(AcademicYear::isActif)
                .findFirst()
                .orElse(null);
        if (activeYear == null) {
            log.info("Emploi du temps de démonstration ignoré : aucune année universitaire active.");
            return false;
        }

        Group group = groupRepository.findAll().stream()
                .filter(g -> g.getPromotion() != null
                        && g.getPromotion().getAcademicYear() != null
                        && g.getPromotion().getAcademicYear().getId().equals(activeYear.getId()))
                .findFirst()
                .orElse(null);
        if (group == null) {
            log.info("Emploi du temps de démonstration ignoré : aucun groupe pour l'année active.");
            return false;
        }

        Promotion promotion = group.getPromotion();
        Program program = promotion.getProgram();
        Level level = promotion.getLevel();

        // Semestre du niveau du groupe (à défaut, le premier semestre disponible).
        Semester semester = semesterRepository.findAll().stream()
                .filter(s -> s.getLevel() != null && level != null
                        && s.getLevel().getId().equals(level.getId()))
                .min(Comparator.comparing(Semester::getOrdre))
                .orElseGet(() -> semesterRepository.findAll().stream().findFirst().orElse(null));
        if (semester == null) {
            log.info("Emploi du temps de démonstration ignoré : aucun semestre disponible.");
            return false;
        }

        SessionUniversitaire session = sessionRepository.findByCode(SESSION_NORMALE_CODE).orElse(null);
        if (session == null) {
            log.info("Emploi du temps de démonstration ignoré : session « {} » absente.",
                    SESSION_NORMALE_CODE);
            return false;
        }

        // Idempotence : ne rien recréer si l'emploi du temps du contexte existe déjà.
        boolean alreadyExists = emploiDuTempsRepository
                .findByAcademicYearIdAndGroupIdAndSemesterIdAndSessionId(
                        activeYear.getId(), group.getId(), semester.getId(), session.getId())
                .isPresent();
        if (alreadyExists) {
            return false;
        }

        // Créneaux de référence (§1.1) amorcés par TimeSlotInitializer (@Order 5) :
        // on réutilise les deux premiers, sans jamais créer de créneau hors grille.
        List<TimeSlot> refSlots = timeSlotRepository.findAllByOrderByOrdreAscHeureDebutAsc();
        if (refSlots.size() < 2) {
            log.info("Emploi du temps de démonstration ignoré : "
                    + "moins de 2 créneaux de référence disponibles.");
            return false;
        }
        TimeSlot morning = refSlots.get(0);
        TimeSlot midday = refSlots.get(1);

        EmploiDuTemps header = emploiDuTempsRepository.save(EmploiDuTemps.builder()
                .academicYear(activeYear)
                .program(program)
                .level(level)
                .promotion(promotion)
                .group(group)
                .semester(semester)
                .session(session)
                .statut(TimetableStatus.BROUILLON)
                .source(TimetableSource.MANUEL)
                .importePar(null)
                .build());

        saveDemoSeance(header, activeYear, program, level, promotion, group, semester,
                WeekDay.LUNDI, morning, "Module 1", SessionType.COURS, "Enseignant 1");
        saveDemoSeance(header, activeYear, program, level, promotion, group, semester,
                WeekDay.LUNDI, midday, "Module 2", SessionType.TD, "Enseignant 2");
        saveDemoSeance(header, activeYear, program, level, promotion, group, semester,
                WeekDay.MARDI, morning, "Module 3", SessionType.TP, "Enseignant 3");

        log.info("Emploi du temps de démonstration créé pour le groupe « {} » "
                        + "(filière « {} », semestre « {} », session « {} »).",
                group.getNom(), program.getNom(), semester.getNom(), session.getNom());
        return true;
    }

    private void saveDemoSeance(EmploiDuTemps header, AcademicYear year, Program program,
                                Level level, Promotion promotion, Group group, Semester semester,
                                WeekDay jour, TimeSlot timeSlot, String module,
                                SessionType type, String enseignant) {
        scheduleRepository.save(Schedule.builder()
                .jour(jour)
                .timeSlot(timeSlot)
                .space(null)                       // distanciel : aucune salle
                .program(program)
                .level(level)
                .promotion(promotion)
                .group(group)
                .semester(semester)
                .academicYear(year)
                .emploiDuTemps(header)
                .enseignant(enseignant)
                .matiere(module)
                .type(type)
                .typePresence(PresenceType.DISTANCIEL)
                .actif(true)
                .build());
    }
}
