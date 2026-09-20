package com.campusops.dashboard.service;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.dashboard.dto.*;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.Role;
import com.campusops.enums.WeekDay;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Service d'agregation des statistiques du tableau de bord. Les calculs sont
 * realises en memoire a partir des donnees existantes (aucune logique metier
 * n'est dupliquee dans les controleurs). Le service est en lecture seule.
 * <p>
 * Le taux d'occupation est defini a partir de l'emploi du temps : pour un
 * ensemble d'espaces actifs, il correspond au rapport entre les seances actives
 * et les creneaux theoriquement disponibles (nombre d'espaces x nombre de
 * creneaux horaires actifs x nombre de jours ouvres).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** Jours ouvres pris en compte pour le calcul du taux d'occupation. */
    private static final List<WeekDay> WORKING_DAYS = List.of(
            WeekDay.LUNDI, WeekDay.MARDI, WeekDay.MERCREDI,
            WeekDay.JEUDI, WeekDay.VENDREDI, WeekDay.SAMEDI);

    private static final Locale FR = Locale.FRENCH;
    private static final int TOP_LIMIT = 5;

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AcademicYearRepository academicYearRepository;

    // ----- Resolution de l'annee active -----

    /**
     * Fenetre temporelle d'une annee universitaire, utilisee pour scoper les
     * statistiques <b>annuelles</b> (classe A) : reservations et seances. Les
     * bornes sont derivees de {@link AcademicYear#getDateDebut()} /
     * {@link AcademicYear#getDateFin()}. Un {@code yearId} nul (aucune annee
     * active) ou des bornes nulles neutralisent le filtre (repli non cassant).
     */
    private record YearScope(Long yearId, LocalDate debut, LocalDate fin) {
        boolean coversDate(LocalDate d) {
            if (d == null) {
                return false;
            }
            if (debut != null && d.isBefore(debut)) {
                return false;
            }
            return fin == null || !d.isAfter(fin);
        }

        boolean hasDateWindow() {
            return debut != null || fin != null;
        }
    }

    /** Resout l'annee demandee, sinon l'annee active, sinon une portee « toutes annees ». */
    private YearScope resolveScope(Long academicYearId) {
        AcademicYear year = (academicYearId != null)
                ? academicYearRepository.findById(academicYearId).orElse(null)
                : academicYearRepository.findFirstByActifTrue().orElse(null);
        if (year == null) {
            return new YearScope(null, null, null);
        }
        return new YearScope(year.getId(), year.getDateDebut(), year.getDateFin());
    }

    /** Reservations de l'annee (classe A), derivees de la date de reservation. */
    private List<Reservation> reservationsOf(YearScope scope) {
        List<Reservation> all = reservationRepository.findAll();
        if (!scope.hasDateWindow()) {
            return all;
        }
        return all.stream().filter(r -> scope.coversDate(r.getDate())).toList();
    }

    /** Seances actives de l'annee (classe A), via le lien direct {@code academicYear}. */
    private List<Schedule> schedulesOf(YearScope scope) {
        List<Schedule> active = scheduleRepository.findByActif(true);
        if (scope.yearId() == null) {
            return active;
        }
        return active.stream()
                .filter(s -> s.getAcademicYear() != null
                        && scope.yearId().equals(s.getAcademicYear().getId()))
                .toList();
    }

    // ----- Points d'entree -----

    public DashboardStatsDto getStatistics(Long academicYearId) {
        return DashboardStatsDto.builder()
                .overview(getOverview(academicYearId))
                .espaces(getSpaceStats(academicYearId))
                .reservations(getReservationStats(academicYearId))
                .temporel(getTemporalStats(academicYearId))
                .build();
    }

    public OverviewStatsDto getOverview(Long academicYearId) {
        YearScope scope = resolveScope(academicYearId);
        List<Space> spaces = spaceRepository.findAll();
        long activeSpaces = spaces.stream().filter(Space::isActif).count();

        // Classe A : les reservations sont scopees a l'annee active (§22-A).
        List<Reservation> reservations = reservationsOf(scope);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(WeekFields.of(FR).dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        YearMonth currentMonth = YearMonth.from(today);

        long todayCount = reservations.stream().filter(r -> today.equals(r.getDate())).count();
        long weekCount = reservations.stream()
                .filter(r -> r.getDate() != null
                        && !r.getDate().isBefore(weekStart) && !r.getDate().isAfter(weekEnd))
                .count();
        long monthCount = reservations.stream()
                .filter(r -> r.getDate() != null && currentMonth.equals(YearMonth.from(r.getDate())))
                .count();

        return OverviewStatsDto.builder()
                // Classe B : donnees structurelles, globales (independantes de l'annee).
                .totalBatiments(buildingRepository.count())
                .totalEtages(floorRepository.count())
                .totalEspaces(spaces.size())
                .espacesActifs(activeSpaces)
                .espacesInactifs(spaces.size() - activeSpaces)
                .totalEquipements(equipmentRepository.count())
                .totalUtilisateurs(userRepository.count())
                .totalEnseignants(userRepository.findByRole(Role.ENSEIGNANT).size())
                .totalResponsablesClub(userRepository.findByRole(Role.RESPONSABLE_CLUB).size())
                // Classe A : compteurs de reservations de l'annee active.
                .totalReservations(reservations.size())
                .reservationsAujourdhui(todayCount)
                .reservationsCetteSemaine(weekCount)
                .reservationsCeMois(monthCount)
                .build();
    }

    // ----- Statistiques des espaces -----

    public SpaceStatsDto getSpaceStats(Long academicYearId) {
        YearScope scope = resolveScope(academicYearId);
        List<Space> activeSpaces = spaceRepository.findByActif(true);
        List<Schedule> activeSchedules = schedulesOf(scope);
        List<Reservation> reservations = reservationsOf(scope);
        int timeSlotCount = timeSlotRepository.findByActif(true).size();

        // Comptages par espace
        Map<Long, Long> seancesBySpace = new LinkedHashMap<>();
        for (Schedule s : activeSchedules) {
            if (s.getSpace() != null) {
                seancesBySpace.merge(s.getSpace().getId(), 1L, Long::sum);
            }
        }
        Map<Long, Long> reservationsBySpace = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getSpace() != null) {
                reservationsBySpace.merge(r.getSpace().getId(), 1L, Long::sum);
            }
        }

        List<SpaceUsageDto> usages = new ArrayList<>();
        for (Space space : activeSpaces) {
            long seances = seancesBySpace.getOrDefault(space.getId(), 0L);
            long resa = reservationsBySpace.getOrDefault(space.getId(), 0L);
            usages.add(SpaceUsageDto.builder()
                    .spaceId(space.getId())
                    .nom(space.getNom())
                    .code(space.getCode())
                    .buildingNom(buildingNameOf(space))
                    .floorNom(space.getFloor() != null ? space.getFloor().getNom() : null)
                    .seances(seances)
                    .reservations(resa)
                    .totalUtilisations(seances + resa)
                    .build());
        }

        List<SpaceUsageDto> mostUsed = usages.stream()
                .sorted(Comparator.comparingLong(SpaceUsageDto::getTotalUtilisations).reversed()
                        .thenComparing(SpaceUsageDto::getNom, Comparator.nullsLast(String::compareTo)))
                .limit(TOP_LIMIT)
                .toList();
        List<SpaceUsageDto> leastUsed = usages.stream()
                .sorted(Comparator.comparingLong(SpaceUsageDto::getTotalUtilisations)
                        .thenComparing(SpaceUsageDto::getNom, Comparator.nullsLast(String::compareTo)))
                .limit(TOP_LIMIT)
                .toList();

        long capaciteTotale = activeSpaces.stream()
                .filter(s -> s.getCapacite() != null)
                .mapToLong(Space::getCapacite)
                .sum();

        return SpaceStatsDto.builder()
                .espacesLesPlusUtilises(mostUsed)
                .espacesLesMoinsUtilises(leastUsed)
                .tauxOccupationGlobal(globalOccupancy(activeSpaces, seancesBySpace, timeSlotCount))
                .tauxOccupationParBatiment(occupancyByBuilding(activeSpaces, seancesBySpace, timeSlotCount))
                .tauxOccupationParEtage(occupancyByFloor(activeSpaces, seancesBySpace, timeSlotCount))
                .repartitionParType(spacesByType(activeSpaces))
                .capaciteTotale(capaciteTotale)
                .build();
    }

    private OccupancyRateDto globalOccupancy(List<Space> spaces, Map<Long, Long> seancesBySpace, int timeSlotCount) {
        long occupied = spaces.stream().mapToLong(s -> seancesBySpace.getOrDefault(s.getId(), 0L)).sum();
        long available = (long) spaces.size() * timeSlotCount * WORKING_DAYS.size();
        return OccupancyRateDto.builder()
                .id(null)
                .nom("Global")
                .nombreEspaces(spaces.size())
                .creneauxOccupes(occupied)
                .creneauxDisponibles(available)
                .tauxOccupation(percentage(occupied, available))
                .build();
    }

    private List<OccupancyRateDto> occupancyByBuilding(List<Space> spaces, Map<Long, Long> seancesBySpace, int timeSlotCount) {
        Map<Long, List<Space>> byBuilding = new LinkedHashMap<>();
        for (Space s : spaces) {
            Building b = s.getFloor() != null ? s.getFloor().getBuilding() : null;
            if (b != null) {
                byBuilding.computeIfAbsent(b.getId(), k -> new ArrayList<>()).add(s);
            }
        }
        List<OccupancyRateDto> result = new ArrayList<>();
        for (Building b : buildingRepository.findAll()) {
            List<Space> list = byBuilding.getOrDefault(b.getId(), List.of());
            if (list.isEmpty()) continue;
            long occupied = list.stream().mapToLong(s -> seancesBySpace.getOrDefault(s.getId(), 0L)).sum();
            long available = (long) list.size() * timeSlotCount * WORKING_DAYS.size();
            result.add(OccupancyRateDto.builder()
                    .id(b.getId()).nom(b.getNom()).code(b.getCode())
                    .nombreEspaces(list.size())
                    .creneauxOccupes(occupied)
                    .creneauxDisponibles(available)
                    .tauxOccupation(percentage(occupied, available))
                    .build());
        }
        result.sort(Comparator.comparingDouble(OccupancyRateDto::getTauxOccupation).reversed());
        return result;
    }

    private List<OccupancyRateDto> occupancyByFloor(List<Space> spaces, Map<Long, Long> seancesBySpace, int timeSlotCount) {
        Map<Long, List<Space>> byFloor = new LinkedHashMap<>();
        for (Space s : spaces) {
            Floor f = s.getFloor();
            if (f != null) {
                byFloor.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(s);
            }
        }
        List<OccupancyRateDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<Space>> entry : byFloor.entrySet()) {
            List<Space> list = entry.getValue();
            Floor f = list.get(0).getFloor();
            long occupied = list.stream().mapToLong(s -> seancesBySpace.getOrDefault(s.getId(), 0L)).sum();
            long available = (long) list.size() * timeSlotCount * WORKING_DAYS.size();
            String nom = f.getNom() + (buildingNameOf(list.get(0)) != null ? " - " + buildingNameOf(list.get(0)) : "");
            result.add(OccupancyRateDto.builder()
                    .id(f.getId()).nom(nom).code(f.getCode())
                    .nombreEspaces(list.size())
                    .creneauxOccupes(occupied)
                    .creneauxDisponibles(available)
                    .tauxOccupation(percentage(occupied, available))
                    .build());
        }
        result.sort(Comparator.comparingDouble(OccupancyRateDto::getTauxOccupation).reversed());
        return result;
    }

    private List<CountItemDto> spacesByType(List<Space> spaces) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Space s : spaces) {
            if (s.getType() != null) {
                counts.merge(s.getType().name(), 1L, Long::sum);
            }
        }
        List<CountItemDto> result = new ArrayList<>();
        counts.forEach((k, v) -> result.add(CountItemDto.builder().key(k).label(k).value(v).build()));
        result.sort(Comparator.comparingLong(CountItemDto::getValue).reversed());
        return result;
    }

    private String buildingNameOf(Space space) {
        if (space.getFloor() != null && space.getFloor().getBuilding() != null) {
            return space.getFloor().getBuilding().getNom();
        }
        return null;
    }

    // ----- Statistiques des reservations -----

    public ReservationStatsDto getReservationStats(Long academicYearId) {
        YearScope scope = resolveScope(academicYearId);
        List<Reservation> reservations = reservationsOf(scope);

        Map<ReservationStatus, Long> byStatus = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getStatut() != null) {
                byStatus.merge(r.getStatut(), 1L, Long::sum);
            }
        }

        return ReservationStatsDto.builder()
                .total(reservations.size())
                .approuvees(byStatus.getOrDefault(ReservationStatus.APPROVED, 0L))
                .refusees(byStatus.getOrDefault(ReservationStatus.REJECTED, 0L))
                .annulees(byStatus.getOrDefault(ReservationStatus.CANCELLED, 0L))
                .enAttente(byStatus.getOrDefault(ReservationStatus.PENDING, 0L))
                .terminees(byStatus.getOrDefault(ReservationStatus.COMPLETED, 0L))
                .repartitionParType(reservationsByType(reservations))
                .utilisateursLesPlusActifs(topActiveUsers(reservations))
                .nombreUtilisateursActifs(countActiveUsers(reservations))
                .build();
    }

    private List<CountItemDto> reservationsByType(List<Reservation> reservations) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getType() != null) {
                counts.merge(r.getType().name(), 1L, Long::sum);
            }
        }
        List<CountItemDto> result = new ArrayList<>();
        counts.forEach((k, v) -> result.add(CountItemDto.builder().key(k).label(k).value(v).build()));
        result.sort(Comparator.comparingLong(CountItemDto::getValue).reversed());
        return result;
    }

    /**
     * Classement des utilisateurs les plus actifs sur l'annee consultee.
     * <p>
     * Le total agrege <b>toutes</b> les demandes de l'utilisateur, quel que soit
     * leur statut, mais la decomposition (accordees / en attente / non abouties)
     * est renvoyee avec, sinon le chiffre n'est pas verifiable : une demande
     * refusee ou annulee gonflait un classement d'« activite » sans qu'on le voie.
     * <p>
     * Le tri est <b>deterministe</b> (total, puis reservations accordees, puis
     * nom) : a egalite, l'ordre ne depend plus de celui de la base, ce qui rendait
     * le top 5 instable d'un rafraichissement a l'autre.
     */
    private List<UserActivityDto> topActiveUsers(List<Reservation> reservations) {
        Map<Long, UserActivityDto> byUser = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            User u = r.getUser();
            if (u == null) {
                continue;
            }
            UserActivityDto acc = byUser.computeIfAbsent(u.getId(), id -> UserActivityDto.builder()
                    .userId(id)
                    .nom(userDisplayName(u))
                    .email(u.getEmail())
                    .role(u.getRole() != null ? u.getRole().name() : null)
                    .build());
            acc.setTotal(acc.getTotal() + 1);
            ReservationStatus statut = r.getStatut();
            if (statut == ReservationStatus.APPROVED || statut == ReservationStatus.COMPLETED) {
                acc.setValidees(acc.getValidees() + 1);
            } else if (statut == ReservationStatus.PENDING) {
                acc.setEnAttente(acc.getEnAttente() + 1);
            } else if (statut == ReservationStatus.REJECTED || statut == ReservationStatus.CANCELLED) {
                acc.setNonAbouties(acc.getNonAbouties() + 1);
            }
        }

        Comparator<UserActivityDto> parValidees = Comparator.comparingLong(UserActivityDto::getValidees);
        Comparator<UserActivityDto> classement = Comparator
                .comparingLong(UserActivityDto::getTotal).reversed()
                .thenComparing(parValidees.reversed())
                .thenComparing(UserActivityDto::getNom, Comparator.nullsLast(String::compareToIgnoreCase));

        return byUser.values().stream().sorted(classement).limit(TOP_LIMIT).toList();
    }

    /** Nombre d'utilisateurs distincts ayant au moins une reservation sur la periode. */
    private long countActiveUsers(List<Reservation> reservations) {
        return reservations.stream()
                .map(Reservation::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .count();
    }

    private String userDisplayName(User u) {
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }

    // ----- Statistiques temporelles -----

    public TemporalStatsDto getTemporalStats(Long academicYearId) {
        YearScope scope = resolveScope(academicYearId);
        List<Reservation> reservations = reservationsOf(scope);
        return TemporalStatsDto.builder()
                .activiteQuotidienne(dailyActivity(reservations, scope))
                .activiteHebdomadaire(weeklyActivity(reservations, scope))
                .activiteMensuelle(monthlyActivity(reservations, scope))
                .activiteAnnuelle(yearlyActivity(reservationRepository.findAll()))
                .heuresDeForteOccupation(peakHours(reservations))
                .joursLesPlusCharges(busiestDays(reservations))
                .build();
    }

    /**
     * Point de reference des fenetres glissantes (jour / semaine), cale sur
     * l'annee consultee : aujourd'hui si l'on regarde l'annee en cours, sinon la
     * borne de l'annee (fin pour une annee passee, debut pour une annee future).
     * Sans cet ancrage, une annee passee afficherait des fenetres entierement
     * vides puisqu'elles pointeraient vers la date du jour, hors de l'annee.
     */
    private LocalDate temporalAnchor(YearScope scope, LocalDate today) {
        if (scope.fin() != null && today.isAfter(scope.fin())) {
            return scope.fin();
        }
        if (scope.debut() != null && today.isBefore(scope.debut())) {
            return scope.debut();
        }
        return today;
    }

    /**
     * Activite des 7 derniers jours (du plus ancien au plus recent), <b>bornee a
     * l'annee consultee</b> : les jours anterieurs au debut de l'annee sont omis
     * plutot qu'affiches a zero, sinon ils se lisent comme une absence d'activite
     * alors qu'ils sont simplement hors perimetre.
     */
    private List<CountItemDto> dailyActivity(List<Reservation> reservations, YearScope scope) {
        LocalDate anchor = temporalAnchor(scope, LocalDate.now());
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM", FR);
        List<CountItemDto> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = anchor.minusDays(i);
            if (scope.debut() != null && day.isBefore(scope.debut())) {
                continue;
            }
            if (scope.fin() != null && day.isAfter(scope.fin())) {
                continue;
            }
            long count = reservations.stream().filter(r -> day.equals(r.getDate())).count();
            result.add(CountItemDto.builder()
                    .key(day.toString())
                    .label(day.format(dayFmt))
                    .value(count)
                    .build());
        }
        return result;
    }

    /**
     * Activite des 8 dernieres semaines (ISO), du plus ancien au plus recent,
     * <b>bornee a l'annee consultee</b> : une semaine entierement anterieure au
     * debut de l'annee est omise (le classement reste contigu, sans trou).
     */
    private List<CountItemDto> weeklyActivity(List<Reservation> reservations, YearScope scope) {
        LocalDate anchor = temporalAnchor(scope, LocalDate.now());
        List<CountItemDto> result = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate ref = anchor.minusWeeks(i);
            LocalDate weekEnd = ref.with(WeekFields.of(FR).dayOfWeek(), 7);
            if (scope.debut() != null && weekEnd.isBefore(scope.debut())) {
                continue;
            }
            int week = ref.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int weekYear = ref.get(IsoFields.WEEK_BASED_YEAR);
            long count = reservations.stream()
                    .filter(r -> r.getDate() != null
                            && r.getDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == week
                            && r.getDate().get(IsoFields.WEEK_BASED_YEAR) == weekYear)
                    .count();
            result.add(CountItemDto.builder()
                    .key(weekYear + "-S" + week)
                    .label("S" + week)
                    .value(count)
                    .build());
        }
        return result;
    }

    /**
     * Activite mensuelle <b>sur toute l'annee universitaire</b> (de sa date de
     * debut a sa date de fin), un point par mois. Les mois a venir apparaissent a
     * zero, ce qui est attendu : c'est la chronologie de l'annee, pas une fenetre
     * glissante qui deborderait hors de l'annee. Repli sur les 12 derniers mois
     * glissants si aucune annee n'est resolue (perimetre « toutes annees »).
     */
    private List<CountItemDto> monthlyActivity(List<Reservation> reservations, YearScope scope) {
        if (scope.debut() == null || scope.fin() == null) {
            return rollingMonthlyActivity(reservations);
        }
        YearMonth start = YearMonth.from(scope.debut());
        YearMonth end = YearMonth.from(scope.fin());
        List<CountItemDto> result = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            final YearMonth month = ym;
            long count = reservations.stream()
                    .filter(r -> r.getDate() != null && month.equals(YearMonth.from(r.getDate())))
                    .count();
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, FR) + " " + ym.getYear();
            result.add(CountItemDto.builder()
                    .key(ym.toString())
                    .label(label)
                    .value(count)
                    .build());
        }
        return result;
    }

    /** Repli hors annee resolue : activite des 12 derniers mois glissants. */
    private List<CountItemDto> rollingMonthlyActivity(List<Reservation> reservations) {
        YearMonth current = YearMonth.from(LocalDate.now());
        List<CountItemDto> result = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            long count = reservations.stream()
                    .filter(r -> r.getDate() != null && ym.equals(YearMonth.from(r.getDate())))
                    .count();
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, FR) + " " + ym.getYear();
            result.add(CountItemDto.builder()
                    .key(ym.toString())
                    .label(label)
                    .value(count)
                    .build());
        }
        return result;
    }

    /** Activite par annee (croissante). */
    private List<CountItemDto> yearlyActivity(List<Reservation> reservations) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getDate() != null) {
                counts.merge(r.getDate().getYear(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> CountItemDto.builder()
                        .key(String.valueOf(e.getKey()))
                        .label(String.valueOf(e.getKey()))
                        .value(e.getValue())
                        .build())
                .toList();
    }

    /** Heures de forte occupation, ordonnees par heure de debut de reservation. */
    private List<CountItemDto> peakHours(List<Reservation> reservations) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getHeureDebut() != null) {
                counts.merge(r.getHeureDebut().getHour(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> CountItemDto.builder()
                        .key(String.valueOf(e.getKey()))
                        .label(String.format("%02dh", e.getKey()))
                        .value(e.getValue())
                        .build())
                .toList();
    }

    /** Jours de la semaine les plus charges, dans l'ordre naturel de la semaine. */
    private List<CountItemDto> busiestDays(List<Reservation> reservations) {
        Map<DayOfWeek, Long> counts = new LinkedHashMap<>();
        for (Reservation r : reservations) {
            if (r.getDate() != null) {
                counts.merge(r.getDate().getDayOfWeek(), 1L, Long::sum);
            }
        }
        List<CountItemDto> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            long count = counts.getOrDefault(day, 0L);
            result.add(CountItemDto.builder()
                    .key(toWeekDay(day).name())
                    .label(day.getDisplayName(TextStyle.FULL, FR))
                    .value(count)
                    .build());
        }
        return result;
    }

    private WeekDay toWeekDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> WeekDay.LUNDI;
            case TUESDAY -> WeekDay.MARDI;
            case WEDNESDAY -> WeekDay.MERCREDI;
            case THURSDAY -> WeekDay.JEUDI;
            case FRIDAY -> WeekDay.VENDREDI;
            case SATURDAY -> WeekDay.SAMEDI;
            case SUNDAY -> WeekDay.DIMANCHE;
        };
    }

    // ----- Utilitaires -----

    /** Arrondi a deux decimales du pourcentage occupe/disponible. */
    private double percentage(long occupied, long available) {
        if (available <= 0) {
            return 0d;
        }
        double raw = (double) occupied * 100d / (double) available;
        return Math.round(raw * 100d) / 100d;
    }
}
