package com.campusops.dashboard.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.dashboard.dto.OccupancyRateDto;import com.campusops.dashboard.dto.ReportDescriptorDto;
import com.campusops.dashboard.dto.SpaceStatsDto;
import com.campusops.dashboard.report.ExcelReportWriter;
import com.campusops.dashboard.report.PdfReportWriter;
import com.campusops.dashboard.report.ReportData;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.ReservationType;
import com.campusops.enums.SessionType;
import com.campusops.enums.SpaceType;
import com.campusops.enums.WeekDay;
import com.campusops.equipment.entity.Equipment;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.exception.BadRequestException;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Construit les rapports exportables du tableau de bord sous forme de
 * {@link ReportData} neutres, puis delegue leur serialisation aux writers
 * PDF/Excel. Aucune mise en forme specifique a un format n'est realisee ici.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardReportService {

    /** Formats d'export supportes. */
    public enum ReportFormat { PDF, EXCEL }

    /**
     * Formats de repli, alignes sur les valeurs historiques de CampusOps. Ils ne
     * servent que si le motif d'affichage configure (Parametres -> Affichage) est
     * absent ou illisible : le format reellement applique est celui des
     * Parametres, via {@link #dateFormatter()} / {@link #timeFormatter()}.
     */
    private static final DateTimeFormatter DATE_FALLBACK = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FALLBACK = DateTimeFormatter.ofPattern("HH:mm");

    private static final String OCCUPATION_BATIMENTS = "occupation-batiments";
    private static final String RESERVATIONS = "reservations";
    private static final String EMPLOIS_DU_TEMPS = "emplois-du-temps";
    private static final String EQUIPEMENTS = "equipements";
    private static final String ESPACES = "espaces";

    private final SpaceRepository spaceRepository;
    private final EquipmentRepository equipmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;
    private final AcademicYearRepository academicYearRepository;
    private final DashboardService dashboardService;
    private final PdfReportWriter pdfReportWriter;
    private final ExcelReportWriter excelReportWriter;
    private final SettingsService settingsService;

    // ----- Catalogue des rapports -----

    public List<ReportDescriptorDto> getAvailableReports() {
        List<ReportDescriptorDto> reports = new ArrayList<>();
        reports.add(ReportDescriptorDto.builder()
                .code(OCCUPATION_BATIMENTS)
            .titre("Taux d'occupation des bâtiments")
            .description("Taux d'occupation calculé par bâtiment à partir de l'emploi du temps.")
                .build());
        reports.add(ReportDescriptorDto.builder()
                .code(RESERVATIONS)
            .titre("Réservations")
            .description("Liste détaillée des réservations avec statut, créneau et demandeur.")
                .build());
        reports.add(ReportDescriptorDto.builder()
                .code(EMPLOIS_DU_TEMPS)
                .titre("Emplois du temps")
            .description("Séances planifiées actives par espace, jour et créneau horaire.")
                .build());
        reports.add(ReportDescriptorDto.builder()
                .code(EQUIPEMENTS)
            .titre("Équipements")
            .description("Inventaire des équipements et nombre d'espaces équipés.")
                .build());
        reports.add(ReportDescriptorDto.builder()
                .code(ESPACES)
                .titre("Espaces")
            .description("Liste des espaces avec bâtiment, étage, capacité et utilisation.")
                .build());
        return reports;
    }

    // ----- Export -----

    public byte[] export(String code, ReportFormat format, Long academicYearId) {
        ReportData data = buildReport(code, academicYearId);
        return switch (format) {
            case PDF -> pdfReportWriter.write(data);
            case EXCEL -> excelReportWriter.write(data);
        };
    }

    public String fileName(String code, ReportFormat format) {
        String extension = format == ReportFormat.PDF ? "pdf" : "xlsx";
        return "rapport-" + normalizeCode(code) + "." + extension;
    }

    public ReportFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return ReportFormat.PDF;
        }
        return switch (format.trim().toLowerCase()) {
            case "pdf" -> ReportFormat.PDF;
            case "excel", "xlsx", "xls" -> ReportFormat.EXCEL;
            default -> throw new BadRequestException("Format d'export non supporte : " + format);
        };
    }

    // ----- Construction des rapports -----

    private ReportData buildReport(String code, Long academicYearId) {
        return switch (normalizeCode(code)) {
            case OCCUPATION_BATIMENTS -> buildOccupationBatiments(academicYearId);
            case RESERVATIONS -> buildReservations(academicYearId);
            case EMPLOIS_DU_TEMPS -> buildEmploisDuTemps(academicYearId);
            case EQUIPEMENTS -> buildEquipements();
            case ESPACES -> buildEspaces();
            default -> throw new BadRequestException("Rapport inconnu : " + code);
        };
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toLowerCase();
    }

    // ----- Portee par annee universitaire (classe A) -----

    /**
     * Portee de filtrage par annee : bornes de dates pour les reservations,
     * identifiant pour le lien direct des seances. Un {@code yearId} nul ou des
     * bornes nulles neutralisent le filtre (repli non cassant, historique visible).
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

    private ReportData buildOccupationBatiments(Long academicYearId) {
        SpaceStatsDto stats = dashboardService.getSpaceStats(academicYearId);
        List<List<String>> lignes = new ArrayList<>();
        for (OccupancyRateDto o : stats.getTauxOccupationParBatiment()) {
            lignes.add(List.of(
                    safe(o.getNom()),
                    safe(o.getCode()),
                    String.valueOf(o.getNombreEspaces()),
                    String.valueOf(o.getCreneauxOccupes()),
                    String.valueOf(o.getCreneauxDisponibles()),
                    formatPercent(o.getTauxOccupation())));
        }
        return ReportData.builder()
            .titre("Taux d'occupation des bâtiments")
            .sousTitre("Occupation calculée à partir des séances planifiées actives")
            .entetes(List.of("Bâtiment", "Code", "Espaces", "Créneaux occupés",
                "Créneaux disponibles", "Taux d'occupation"))
                .lignes(lignes)
                .build();
    }

    private ReportData buildReservations(Long academicYearId) {
        DateTimeFormatter date = dateFormatter();
        DateTimeFormatter time = timeFormatter();
        List<Reservation> reservations = new ArrayList<>(reservationsOf(resolveScope(academicYearId)));
        reservations.sort(Comparator.comparing(Reservation::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.getHeureDebut(), Comparator.nullsLast(Comparator.naturalOrder())));
        List<List<String>> lignes = new ArrayList<>();
        for (Reservation r : reservations) {
            lignes.add(List.of(
                    r.getSpace() != null ? safe(r.getSpace().getNom()) : "",
                    r.getDate() != null ? r.getDate().format(date) : "",
                    r.getHeureDebut() != null ? r.getHeureDebut().format(time) : "",
                    r.getHeureFin() != null ? r.getHeureFin().format(time) : "",
                    reservationTypeLabel(r.getType()),
                    reservationStatusLabel(r.getStatut()),
                    userName(r),
                    safe(r.getMotif())));
        }
        return ReportData.builder()
                .titre("Réservations")
                .sousTitre("Total : " + reservations.size() + " réservation(s)")
                .entetes(List.of("Espace", "Date", "Début", "Fin", "Type", "Statut",
                        "Demandeur", "Motif"))
                .lignes(lignes)
                .build();
    }

    private ReportData buildEmploisDuTemps(Long academicYearId) {
        DateTimeFormatter time = timeFormatter();
        List<Schedule> schedules = new ArrayList<>(schedulesOf(resolveScope(academicYearId)));
        schedules.sort(Comparator
                .comparing((Schedule s) -> s.getJour() != null ? s.getJour().ordinal() : Integer.MAX_VALUE)
                .thenComparing(s -> s.getTimeSlot() != null ? s.getTimeSlot().getHeureDebut() : null,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        List<List<String>> lignes = new ArrayList<>();
        for (Schedule s : schedules) {
            lignes.add(List.of(
                    weekDayLabel(s.getJour()),
                    creneau(s, time),
                    s.getSpace() != null ? safe(s.getSpace().getNom()) : "",
                    safe(s.getMatiere()),
                    safe(s.getEnseignant()),
                    sessionTypeLabel(s.getType())));
        }
        return ReportData.builder()
                .titre("Emplois du temps")
                .sousTitre("Séances planifiées actives : " + schedules.size())
                .entetes(List.of("Jour", "Créneau", "Espace", "Matière", "Enseignant", "Type"))
                .lignes(lignes)
                .build();
    }

    private ReportData buildEquipements() {
        List<Equipment> equipements = equipmentRepository.findAll();
        equipements.sort(Comparator.comparing(Equipment::getNom,
                Comparator.nullsLast(String::compareTo)));
        List<Space> spaces = spaceRepository.findAll();
        List<List<String>> lignes = new ArrayList<>();
        for (Equipment e : equipements) {
            long nbEspaces = spaces.stream()
                    .filter(sp -> sp.getEquipments() != null && sp.getEquipments().stream()
                            .anyMatch(eq -> eq.getId() != null && eq.getId().equals(e.getId())))
                    .count();
            lignes.add(List.of(
                    safe(e.getNom()),
                    safe(e.getCode()),
                    e.isActif() ? "Actif" : "Inactif",
                    String.valueOf(nbEspaces),
                    safe(e.getDescription())));
        }
        return ReportData.builder()
                .titre("Équipements")
                .sousTitre("Inventaire : " + equipements.size() + " équipement(s)")
                .entetes(List.of("Nom", "Code", "Statut", "Espaces équipés", "Description"))
                .lignes(lignes)
                .build();
    }

    private ReportData buildEspaces() {
        List<Space> spaces = spaceRepository.findAll();
        spaces.sort(Comparator.comparing(Space::getNom, Comparator.nullsLast(String::compareTo)));
        List<List<String>> lignes = new ArrayList<>();
        for (Space s : spaces) {
            lignes.add(List.of(
                    safe(s.getNom()),
                    safe(s.getCode()),
                    spaceTypeLabel(s.getType()),
                    buildingName(s),
                    s.getFloor() != null ? safe(s.getFloor().getNom()) : "",
                    s.getCapacite() != null ? String.valueOf(s.getCapacite()) : "",
                    s.isActif() ? "Actif" : "Inactif"));
        }
        return ReportData.builder()
                .titre("Espaces")
                .sousTitre("Total : " + spaces.size() + " espace(s)")
                .entetes(List.of("Nom", "Code", "Type", "Bâtiment", "Étage", "Capacité", "Statut"))
                .lignes(lignes)
                .build();
    }

    // ----- Utilitaires -----

    private String reservationStatusLabel(ReservationStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING -> "En attente";
            case APPROVED -> "Approuvée";
            case REJECTED -> "Refusée";
            case CANCELLED -> "Annulée";
            case COMPLETED -> "Terminée";
        };
    }

    private String reservationTypeLabel(ReservationType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case EXTRA_CLASS -> "Cours supplémentaire";
            case MAKEUP_CLASS -> "Cours de rattrapage";
            case EXAM -> "Examen";
            case CLUB_MEETING -> "Réunion de club";
            case EVENT -> "Événement";
            case MAINTENANCE -> "Maintenance";
            case OTHER -> "Autre";
        };
    }

    private String weekDayLabel(WeekDay day) {
        return day == null ? "" : day.name().toLowerCase(Locale.FRENCH);
    }

    private String sessionTypeLabel(SessionType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case COURS -> "Cours";
            case TD -> "TD";
            case TP -> "TP";
            case EXAMEN -> "Examen";
            case AUTRE -> "Autre";
        };
    }

    private String spaceTypeLabel(SpaceType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case CLASSROOM -> "Salle de cours";
            case COMPUTER_ROOM -> "Salle informatique";
            case LABORATORY -> "Laboratoire";
            case AMPHITHEATER -> "Amphithéâtre";
            case MEETING_ROOM -> "Salle de réunion";
            case MULTIPURPOSE_ROOM -> "Salle polyvalente";
            case CONFERENCE_ROOM -> "Salle de conférence";
            case OTHER -> "Autre";
        };
    }

    private String creneau(Schedule s, DateTimeFormatter time) {
        if (s.getTimeSlot() == null) {
            return "";
        }
        return s.getTimeSlot().getHeureDebut().format(time) + " - "
                + s.getTimeSlot().getHeureFin().format(time);
    }

    /**
     * Formateur de date derive du parametre d'affichage (Parametres -> Affichage,
     * §10). Resolu a chaque export : le rapport reflete le format choisi par
     * l'etablissement, sans motif code en dur (§20). La meme source de verite
     * ({@code SettingsService.motifDate()}) pilote deja l'affichage cote frontend.
     */
    private DateTimeFormatter dateFormatter() {
        return formatterFrom(settingsService.motifDate(), DATE_FALLBACK);
    }

    /** Formateur d'heure derive du parametre d'affichage (voir {@link #dateFormatter()}). */
    private DateTimeFormatter timeFormatter() {
        return formatterFrom(settingsService.motifHeure(), TIME_FALLBACK);
    }

    /**
     * Construit un formateur a partir du motif configure, avec repli non cassant
     * sur le format historique si le motif est absent ou syntaxiquement invalide.
     * {@link Locale#ENGLISH} rend le marqueur AM/PM du format 12 h de maniere
     * deterministe, quelle que soit la locale du serveur.
     */
    private DateTimeFormatter formatterFrom(String motif, DateTimeFormatter repli) {
        if (motif == null || motif.isBlank()) {
            return repli;
        }
        try {
            return DateTimeFormatter.ofPattern(motif, Locale.ENGLISH);
        } catch (IllegalArgumentException ex) {
            return repli;
        }
    }

    private String userName(Reservation r) {
        if (r.getUser() == null) {
            return "";
        }
        String first = r.getUser().getFirstName() != null ? r.getUser().getFirstName() : "";
        String last = r.getUser().getLastName() != null ? r.getUser().getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? safe(r.getUser().getEmail()) : full;
    }

    private String buildingName(Space s) {
        if (s.getFloor() != null && s.getFloor().getBuilding() != null) {
            return safe(s.getFloor().getBuilding().getNom());
        }
        return "";
    }

    private String formatPercent(double value) {
        return String.format("%.2f %%", value).replace(',', '.');
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
