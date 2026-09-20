package com.campusops.config;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.PresenceType;
import com.campusops.enums.SessionType;
import com.campusops.enums.SpaceType;
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
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Occupe quelques amphithéâtres avec des cours magistraux afin que la recherche
 * de disponibilité montre une occupation réaliste : c'est la raison d'être de
 * l'application (trouver une salle libre quand la ressource est <b>rare et
 * souvent occupée</b>). Sans occupation, tous les amphis apparaîtraient toujours
 * libres et l'outil perdrait son intérêt.
 *
 * <p>Chaque amphithéâtre retenu ({@code AMP1..AMP4}, puis l'amphi central
 * {@code AMPC}) reçoit <b>un</b> cours magistral hebdomadaire, rattaché à un
 * groupe réel de l'année active, sur un jour et un créneau distincts pour étaler
 * l'occupation sur la semaine. Les séances sont en présentiel (statut
 * {@code PUBLIE}) : elles occupent donc effectivement la salle dans le calcul de
 * disponibilité.</p>
 *
 * <p><b>Idempotent</b> : si un amphithéâtre est déjà référencé par une séance, on
 * considère l'occupation déjà amorcée et on ne fait rien. <b>Non destructif</b>
 * (§20) : aucune donnée existante n'est modifiée ni supprimée. Désactivable via
 * {@code campusops.seed.occupy-amphitheaters=false}. S'exécute après la réduction
 * du parc de salles ({@code @Order 12}) pour ne cibler que les amphis conservés.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(13)
public class AmphiOccupancyInitializer implements CommandLineRunner {

    private final AcademicYearRepository academicYearRepository;
    private final SpaceRepository spaceRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SessionUniversitaireRepository sessionRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final ScheduleRepository scheduleRepository;

    private static final String SESSION_NORMALE_CODE = "NORMALE";

    /** Jours d'occupation, pour étaler les cours magistraux sur la semaine. */
    private static final List<WeekDay> JOURS = List.of(
            WeekDay.LUNDI, WeekDay.MARDI, WeekDay.MERCREDI, WeekDay.JEUDI, WeekDay.VENDREDI);

    @Value("${campusops.seed.occupy-amphitheaters:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("[occupy-amphis] Occupation des amphithéâtres désactivée "
                    + "(campusops.seed.occupy-amphitheaters=false).");
            return;
        }

        List<Space> amphis = spaceRepository.findByType(SpaceType.AMPHITHEATER).stream()
                .filter(Space::isActif)
                .sorted(Comparator.comparing(Space::getCode))
                .toList();
        if (amphis.isEmpty()) {
            log.info("[occupy-amphis] Aucun amphithéâtre : rien à occuper.");
            return;
        }

        // Idempotence : si un amphi est déjà occupé par une séance, on ne réamorce pas.
        boolean dejaOccupe = amphis.stream().anyMatch(a -> scheduleRepository.existsBySpaceId(a.getId()));
        if (dejaOccupe) {
            log.info("[occupy-amphis] Des amphithéâtres sont déjà occupés : amorçage ignoré.");
            return;
        }

        AcademicYear activeYear = academicYearRepository.findFirstByActifTrue().orElse(null);
        if (activeYear == null) {
            log.info("[occupy-amphis] Aucune année universitaire active : amorçage ignoré.");
            return;
        }

        SessionUniversitaire session = sessionRepository.findByCode(SESSION_NORMALE_CODE).orElse(null);
        if (session == null) {
            log.info("[occupy-amphis] Session « {} » absente : amorçage ignoré.", SESSION_NORMALE_CODE);
            return;
        }

        List<TimeSlot> slots = timeSlotRepository.findAllByOrderByOrdreAscHeureDebutAsc();
        if (slots.isEmpty()) {
            log.info("[occupy-amphis] Aucun créneau de référence : amorçage ignoré.");
            return;
        }

        // Groupes réels de l'année active (un cours magistral s'adresse à un public).
        List<Group> groups = groupRepository.findAll().stream()
                .filter(g -> g.getPromotion() != null
                        && g.getPromotion().getAcademicYear() != null
                        && g.getPromotion().getAcademicYear().getId().equals(activeYear.getId()))
                .sorted(Comparator.comparing(Group::getId))
                .toList();
        if (groups.isEmpty()) {
            log.info("[occupy-amphis] Aucun groupe pour l'année active : amorçage ignoré.");
            return;
        }

        int occupes = 0;
        for (int i = 0; i < amphis.size(); i++) {
            Space amphi = amphis.get(i);
            Group group = groups.get(i % groups.size());
            WeekDay jour = JOURS.get(i % JOURS.size());
            TimeSlot slot = slots.get(i % slots.size());

            EmploiDuTemps header = resolveHeader(activeYear, session, group);
            if (header == null) {
                continue;
            }
            scheduleRepository.save(Schedule.builder()
                    .jour(jour)
                    .timeSlot(slot)
                    .space(amphi)
                    .program(header.getProgram())
                    .level(header.getLevel())
                    .promotion(header.getPromotion())
                    .group(group)
                    .semester(header.getSemester())
                    .academicYear(activeYear)
                    .emploiDuTemps(header)
                    .enseignant("Enseignant CM")
                    .matiere("Cours magistral")
                    .type(SessionType.COURS)
                    .typePresence(PresenceType.PRESENTIEL)
                    .actif(true)
                    .build());
            occupes++;
            log.info("[occupy-amphis] Amphi « {} » occupé : CM le {} ({}) pour le groupe « {} ».",
                    amphi.getCode(), jour, slot.getHeureDebut(), group.getNom());
        }

        log.warn("========================================");
        log.warn("[occupy-amphis] {} amphithéâtre(s) occupé(s) par un cours magistral.", occupes);
        log.warn("========================================");
    }

    /**
     * Récupère l'en-tête d'emploi du temps du contexte (année + groupe + semestre
     * courant + session), ou le crée (statut {@code PUBLIE}) s'il n'existe pas. La
     * période de validité est dérivée du semestre courant du niveau (§20).
     */
    private EmploiDuTemps resolveHeader(AcademicYear activeYear, SessionUniversitaire session, Group group) {
        Promotion promotion = group.getPromotion();
        Program program = promotion.getProgram();
        Level level = promotion.getLevel();

        Semester semester = currentSemester(level);
        if (semester == null) {
            return null;
        }

        return emploiDuTempsRepository
                .findByAcademicYearIdAndGroupIdAndSemesterIdAndSessionId(
                        activeYear.getId(), group.getId(), semester.getId(), session.getId())
                .orElseGet(() -> {
                    LocalDate debut = semester.getDateDebut() != null
                            ? semester.getDateDebut() : activeYear.getDateDebut();
                    LocalDate fin = semester.getDateFin() != null
                            ? semester.getDateFin() : activeYear.getDateFin();
                    return emploiDuTempsRepository.save(EmploiDuTemps.builder()
                            .academicYear(activeYear)
                            .program(program)
                            .level(level)
                            .promotion(promotion)
                            .group(group)
                            .semester(semester)
                            .session(session)
                            .dateDebut(debut)
                            .dateFin(fin)
                            .statut(TimetableStatus.PUBLIE)
                            .source(TimetableSource.MANUEL)
                            .importePar(null)
                            .build());
                });
    }

    /** Semestre courant du niveau (le plus petit ordre), à défaut son 1er semestre. */
    private Semester currentSemester(Level level) {
        if (level == null) {
            return null;
        }
        return semesterRepository.findByLevelIdAndCourantTrue(level.getId()).stream()
                .min(Comparator.comparing(Semester::getOrdre))
                .orElseGet(() -> semesterRepository
                        .findFirstByLevelIdAndActifTrueOrderByOrdreAsc(level.getId())
                        .orElse(null));
    }
}
