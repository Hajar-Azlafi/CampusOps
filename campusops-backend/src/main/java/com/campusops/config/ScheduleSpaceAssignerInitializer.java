package com.campusops.config;

import com.campusops.enums.ReservationStatus;
import com.campusops.enums.SessionType;
import com.campusops.enums.SpaceType;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.DayOfWeek;
import com.campusops.reservation.entity.Reservation;
import com.campusops.enums.LabSpeciality;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Assigns spaces to existing schedules according to simple type rules:
 * - COURS: prefer AMPHITHEATER, fallback CLASSROOM
 * - TD: CLASSROOM
 * - TP: LABORATORY
 * - For "informatique" modules (name contains "info"), prefer COMPUTER_ROOM
 * - For TP informatique: COMPUTER_ROOM (not LAB)
 *
 * The assigner respects conflicts: it will not assign a space already used
 * at the same time or blocked by a reservation.
 * Controlled by property: campusops.seed.assign-spaces-by-type (default false).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(14)
public class ScheduleSpaceAssignerInitializer implements CommandLineRunner {

    private final ScheduleRepository scheduleRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;

    @Value("${campusops.seed.assign-spaces-by-type:false}")
    private boolean assignEnabled;

    private static final List<ReservationStatus> BLOCKING = Arrays.asList(ReservationStatus.PENDING, ReservationStatus.APPROVED);

    @Override
    @Transactional
    public void run(String... args) {
        if (!assignEnabled) {
            log.info("[assign-spaces] Désactivé (campusops.seed.assign-spaces-by-type=false)");
            return;
        }

        List<Space> allSpaces = spaceRepository.findByActif(true);
        if (allSpaces.isEmpty()) {
            log.info("[assign-spaces] Aucun espace actif trouvé.");
            return;
        }

        List<Schedule> schedules = scheduleRepository.findByActif(true);
        int assigned = 0;
        int skipped = 0;

        // sort spaces to distribute across buildings/floors
        allSpaces.sort(Comparator.comparing((Space s) -> s.getFloor().getBuilding().getCode() == null ? "" : s.getFloor().getBuilding().getCode())
                .thenComparing(s -> s.getFloor().getNumero())
                .thenComparing(Space::getCode));

        for (Schedule sch : schedules) {
            try {
                List<SpaceType> desired = desiredTypesForSchedule(sch);
                if (desired.isEmpty()) {
                    skipped++;
                    continue;
                }

                Space current = sch.getSpace();
                if (current != null && desired.contains(current.getType())) {
                    // current space type matches desired -> keep
                    continue;
                }

                // build candidate list
                List<Space> candidates = new ArrayList<>();
                for (Space sp : allSpaces) {
                    if (desired.contains(sp.getType())) candidates.add(sp);
                }

                // If TP -> try to infer lab speciality from module name and prefer matching labs
                LabSpeciality targetSpeciality = null;
                if (desired.size() == 1 && desired.get(0) == SpaceType.LABORATORY) {
                    targetSpeciality = inferLabSpeciality(sch.getModule() != null ? sch.getModule().getNom() : null);
                }
                if (targetSpeciality != null) {
                    List<Space> specCandidates = new ArrayList<>();
                    for (Space sp : candidates) {
                        if (sp.getSpeciality() == targetSpeciality) specCandidates.add(sp);
                    }
                    if (!specCandidates.isEmpty()) candidates = specCandidates;
                }

                // compute simple usage count to distribute load (least used first)
                final java.util.Map<Long, Integer> usage = new java.util.HashMap<>();
                for (Space s : candidates) {
                    int cnt = 0;
                    for (Schedule ex : scheduleRepository.findBySpaceId(s.getId())) {
                        if (!ex.isActif()) continue;
                        // count only schedules in same academic year and semester to be relevant
                        if (sch.getAcademicYear() != null && ex.getAcademicYear() != null
                                && sch.getSemester() != null && ex.getSemester() != null
                                && sch.getAcademicYear().getId().equals(ex.getAcademicYear().getId())
                                && sch.getSemester().getId().equals(ex.getSemester().getId())) {
                            cnt++;
                        }
                    }
                    usage.put(s.getId(), cnt);
                }

                // sort candidates by usage asc, then by building/floor/code to spread across blocks/etages
                candidates.sort((a, b) -> {
                    int ua = usage.getOrDefault(a.getId(), 0);
                    int ub = usage.getOrDefault(b.getId(), 0);
                    if (ua != ub) return Integer.compare(ua, ub);
                    String ak = (a.getFloor().getBuilding().getCode() == null ? "" : a.getFloor().getBuilding().getCode()) + "-" + a.getFloor().getNumero() + "-" + a.getCode();
                    String bk = (b.getFloor().getBuilding().getCode() == null ? "" : b.getFloor().getBuilding().getCode()) + "-" + b.getFloor().getNumero() + "-" + b.getCode();
                    return ak.compareTo(bk);
                });

                boolean placed = false;
                for (Space cand : candidates) {
                    // conflict with other schedules
                    boolean conflict = scheduleRepository.existsSpaceConflictExcludingId(
                            sch.getAcademicYear().getId(), sch.getSemester().getId(),
                            sch.getJour(), sch.getTimeSlot().getHeureDebut(), sch.getTimeSlot().getHeureFin(),
                            cand.getId(), sch.getId());
                    if (conflict) continue;

                    // reservations blocking for that space (filter by weekday and academic year window)
                        boolean reservationBlocks = false;
                        List<Reservation> blocking = reservationRepository.findBlockingForSpace(cand.getId(), sch.getTimeSlot().getHeureDebut(), sch.getTimeSlot().getHeureFin(), BLOCKING);
                        LocalDate from = sch.getAcademicYear() != null ? sch.getAcademicYear().getDateDebut() : null;
                        LocalDate to = sch.getAcademicYear() != null ? sch.getAcademicYear().getDateFin() : null;
                        for (Reservation r : blocking) {
                            DayOfWeek rDay = r.getDate().getDayOfWeek();
                            // compare weekday
                            if (toWeekDay(rDay) != sch.getJour()) continue;
                            if (from != null && r.getDate().isBefore(from)) continue;
                            if (to != null && r.getDate().isAfter(to)) continue;
                            reservationBlocks = true;
                            break;
                        }
                    if (reservationBlocks) continue;

                    // assign
                    sch.setSpace(cand);
                    scheduleRepository.save(sch);
                    assigned++;
                    placed = true;
                    break;
                }

                if (!placed) skipped++;
            } catch (Exception e) {
                log.warn("[assign-spaces] Failed to process schedule id={}: {}", sch.getId(), e.getMessage());
                skipped++;
            }
        }

        log.warn("[assign-spaces] Réaffectation terminée : {} séances affectées, {} ignorées.", assigned, skipped);
    }

    private List<SpaceType> desiredTypesForSchedule(Schedule s) {
        SessionType t = s.getType() == null ? SessionType.AUTRE : s.getType();
        String moduleName = "";
        if (s.getModule() != null && s.getModule().getNom() != null) moduleName = s.getModule().getNom().toLowerCase(Locale.ROOT);
        boolean isInfo = moduleName.contains("info") || moduleName.contains("informat") || moduleName.contains("programmation");

        switch (t) {
            case COURS:
                if (isInfo) return Arrays.asList(SpaceType.COMPUTER_ROOM, SpaceType.CLASSROOM, SpaceType.AMPHITHEATER);
                return Arrays.asList(SpaceType.AMPHITHEATER, SpaceType.CLASSROOM);
            case TD:
                if (isInfo) return Arrays.asList(SpaceType.COMPUTER_ROOM, SpaceType.CLASSROOM);
                return Arrays.asList(SpaceType.CLASSROOM);
            case TP:
                if (isInfo) return Arrays.asList(SpaceType.COMPUTER_ROOM);
                return Arrays.asList(SpaceType.LABORATORY);
            default:
                return new ArrayList<>();
        }
    }

    private LabSpeciality inferLabSpeciality(String moduleName) {
        if (moduleName == null) return null;
        String n = moduleName.toLowerCase(Locale.ROOT);
        if (n.contains("info") || n.contains("programm") || n.contains("web") || n.contains("développ") || n.contains("development") ) return LabSpeciality.INFORMATIQUE;
        if (n.contains("réseaux") || n.contains("network")) return LabSpeciality.RESEAUX;
        if (n.contains("physi") || n.contains("physics")) return LabSpeciality.PHYSIQUE;
        if (n.contains("élect") || n.contains("electr")) return LabSpeciality.ELECTRICITE;
        if (n.contains("électron") || n.contains("electron")) return LabSpeciality.ELECTRONIQUE;
        if (n.contains("chim") || n.contains("chem")) return LabSpeciality.CHIMIE;
        if (n.contains("bio") || n.contains("biol")) return LabSpeciality.BIOLOGIE;
        if (n.contains("mécan") || n.contains("mecani")) return LabSpeciality.MECANIQUE;
        return null;
    }

    private com.campusops.enums.WeekDay toWeekDay(DayOfWeek d) {
        if (d == DayOfWeek.MONDAY) return com.campusops.enums.WeekDay.LUNDI;
        if (d == DayOfWeek.TUESDAY) return com.campusops.enums.WeekDay.MARDI;
        if (d == DayOfWeek.WEDNESDAY) return com.campusops.enums.WeekDay.MERCREDI;
        if (d == DayOfWeek.THURSDAY) return com.campusops.enums.WeekDay.JEUDI;
        if (d == DayOfWeek.FRIDAY) return com.campusops.enums.WeekDay.VENDREDI;
        if (d == DayOfWeek.SATURDAY) return com.campusops.enums.WeekDay.SAMEDI;
        if (d == DayOfWeek.SUNDAY) return com.campusops.enums.WeekDay.DIMANCHE;
        return com.campusops.enums.WeekDay.LUNDI;
    }
}
