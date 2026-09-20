package com.campusops.availability.engine;

import com.campusops.enums.OccupancySource;
import com.campusops.enums.OccupationCategorie;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.TimetableStatus;
import com.campusops.enums.WeekDay;
import com.campusops.occupation.entity.OccupationSupplementaire;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.timetable.entity.EmploiDuTemps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>Moteur central de disponibilite</b> (§2, §19). Unique source de verite du
 * calcul d'occupation : il n'existe aucune autre implementation de ces regles
 * dans l'application. {@code AvailabilityService} (recherche) comme
 * {@code ReservationService} (validation finale) delegent ici.
 *
 * <p>Sources d'occupation agregees pour un espace et une date (§1) :</p>
 * <ol>
 *   <li>seances d'un emploi du temps <b>actif</b> a cette date (statut non
 *       archive, dans la periode de l'EDT et de l'annee de la seance) ;</li>
 *   <li><b>occupations supplementaires</b> — examens, soutenances et autres
 *       occupations ponctuelles (evenements, reunions, activites de club,
 *       conferences...) : une seule requete polymorphe les ramene toutes, et
 *       elles bloquent la salle de facon identique ;</li>
 *   <li>reservations <b>acceptees</b> (APPROVED) → bloquantes ;</li>
 *   <li>reservations <b>en attente</b> (PENDING) → signalees a part (§13) ;</li>
 *   <li>reservations <b>annulees</b> (CANCELLED/REJECTED) → <b>jamais</b>
 *       bloquantes (§13).</li>
 * </ol>
 *
 * <p>Consequence directe du modele unique d'occupation supplementaire : ajouter
 * un nouveau type d'occupation (une nouvelle valeur de {@code OccupationType})
 * ne demande <b>aucune</b> modification ici — la salle est immobilisee des que
 * l'occupation existe.</p>
 *
 * <p>Les messages produits sont <b>non nominatifs</b> : ils n'exposent aucune
 * information personnelle sur les autres utilisateurs (§16).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityEngine {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** Statuts reellement bloquants : uniquement les reservations acceptees. */
    public static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.APPROVED);

    /** Statuts « en attente » : signales distinctement, non bloquants (§13). */
    public static final List<ReservationStatus> PENDING_STATUSES =
            List.of(ReservationStatus.PENDING);

    private final ScheduleRepository scheduleRepository;
    private final OccupationSupplementaireRepository occupationRepository;
    private final ReservationRepository reservationRepository;
    private final SettingsService settingsService;

    // ----- Occupation d'un espace pour une date -----

    /**
     * Agrege toutes les occupations d'un espace a une date, triees par heure de
     * debut et separees en bloquantes / en attente.
     */
    public SpaceOccupancy computeOccupancy(Space space, LocalDate date) {
        return computeOccupancy(space, date, null);
    }

    /**
     * Variante excluant une reservation (par identifiant) du calcul d'occupation.
     * Utilisee lors de la <b>modification</b> d'une reservation pour ne pas la
     * compter comme un conflit avec elle-meme (§14). {@code null} n'exclut rien.
     */
    public SpaceOccupancy computeOccupancy(Space space, LocalDate date, Long excludeReservationId) {
        return computeOccupancy(space, date, excludeReservationId, null);
    }

    /**
     * Variante excluant une <b>occupation supplementaire</b> (examen, soutenance
     * ou autre) du calcul : meme raison que pour les reservations, une occupation
     * en cours de modification ne doit pas entrer en conflit avec elle-meme.
     */
    public SpaceOccupancy computeOccupancyExcludingOccupation(Space space, LocalDate date,
                                                              Long excludeOccupationId) {
        return computeOccupancy(space, date, null, excludeOccupationId);
    }

    /**
     * Calcul complet, avec exclusion facultative d'une reservation <em>et</em>
     * d'une occupation supplementaire. Les deux identifiants sont independants et
     * peuvent valoir {@code null}.
     */
    public SpaceOccupancy computeOccupancy(Space space, LocalDate date,
                                           Long excludeReservationId, Long excludeOccupationId) {
        WeekDay jour = toWeekDay(date.getDayOfWeek());
        List<OccupancyInterval> blocking = new ArrayList<>();
        List<OccupancyInterval> pending = new ArrayList<>();

        // 1) Seances de l'emploi du temps actives a cette date.
        for (Schedule schedule : scheduleRepository.findBySpaceId(space.getId())) {
            if (!schedule.isActif() || schedule.getJour() != jour
                    || !academicYearCoversDate(schedule, date)
                    || !timetableActiveOn(schedule, date)) {
                continue;
            }
            LocalTime d = schedule.getTimeSlot().getHeureDebut();
            LocalTime f = schedule.getTimeSlot().getHeureFin();
            blocking.add(new OccupancyInterval(d, f, OccupancySource.EMPLOI_DU_TEMPS,
                    seanceLabel(schedule)));
        }

        // 2) Occupations supplementaires : examens, soutenances et autres
        //    occupations ponctuelles, ramenees par une seule requete polymorphe
        //    (heritage SINGLE_TABLE). Toutes bloquent la salle de la meme facon.
        for (OccupationSupplementaire occupation
                : occupationRepository.findActiveBySpaceIdAndDate(space.getId(), date)) {
            if (excludeOccupationId != null
                    && excludeOccupationId.equals(occupation.getId())) {
                continue;
            }
            LocalTime d = occupation.getHeureDebut();
            LocalTime f = occupation.getHeureFin();
            if (d == null || f == null || !f.isAfter(d)) {
                continue;
            }
            blocking.add(new OccupancyInterval(d, f, occupancySource(occupation.getCategorie()),
                    occupationLabel(occupation)));
        }

        // 3) Reservations du jour et de cet espace : APPROVED bloquent, PENDING
        //    signalees, le reste (CANCELLED/REJECTED/COMPLETED) ignore (§13).
        for (Reservation reservation : reservationRepository.findBySpaceIdAndDateAndStatutIn(
            space.getId(), date, List.of(ReservationStatus.APPROVED, ReservationStatus.PENDING))) {
            if (excludeReservationId != null
                    && excludeReservationId.equals(reservation.getId())) {
                continue;
            }
            ReservationStatus statut = reservation.getStatut();
            LocalTime d = reservation.getHeureDebut();
            LocalTime f = reservation.getHeureFin();
            if (statut == ReservationStatus.APPROVED) {
                blocking.add(new OccupancyInterval(d, f, OccupancySource.RESERVATION_ACCEPTEE,
                        "Réservation acceptée"));
            } else if (statut == ReservationStatus.PENDING) {
                pending.add(new OccupancyInterval(d, f, OccupancySource.RESERVATION_EN_ATTENTE,
                        "Demande en attente"));
            }
        }

        blocking.sort(Comparator.comparing(OccupancyInterval::debut));
        pending.sort(Comparator.comparing(OccupancyInterval::debut));
        return new SpaceOccupancy(blocking, pending);
    }

    // ----- Periodes libres (§8, §11) -----

    /**
     * Calcule les periodes reellement libres dans les bornes
     * {@code [opening, closing)}, en ne conservant que celles qui atteignent la
     * <b>duree minimale exploitable</b> configuree ({@link #minSlotMinutes()}).
     * C'est la methode a utiliser par defaut : elle garantit qu'aucun
     * micro-creneau du type « 10:25 → 10:35 » n'est jamais calcule ni propose.
     */
    public List<FreeInterval> freePeriods(LocalTime opening, LocalTime closing,
                                          List<OccupancyInterval> blocking) {
        return freePeriods(opening, closing, blocking, minSlotMinutes());
    }

    /**
     * Variante avec duree minimale explicite (en minutes). Sert a la fois au
     * filtre anti micro-creneaux (§ creneaux exploitables) et a la recherche par
     * <b>duree souhaitee</b> : on ne retient alors que les periodes
     * <b>continues</b> assez longues, jamais deux periodes separees par une
     * occupation.
     *
     * <p>Les intervalles bloquants sont bornes a la plage puis fusionnes ; les
     * trous restants sont les periodes libres (§8).</p>
     */
    public List<FreeInterval> freePeriods(LocalTime opening, LocalTime closing,
                                          List<OccupancyInterval> blocking, long minDureeMinutes) {
        List<FreeInterval> free = new ArrayList<>();
        if (opening == null || closing == null || !opening.isBefore(closing)) {
            return free;
        }

        // Bornage a la plage d'ouverture + tri.
        List<LocalTime[]> clamped = new ArrayList<>();
        for (OccupancyInterval i : blocking) {
            LocalTime d = i.debut().isBefore(opening) ? opening : i.debut();
            LocalTime f = i.fin().isAfter(closing) ? closing : i.fin();
            if (d.isBefore(f)) {
                clamped.add(new LocalTime[]{d, f});
            }
        }
        clamped.sort(Comparator.comparing(a -> a[0]));

        // Fusion des intervalles bloquants qui se chevauchent ou se touchent.
        List<LocalTime[]> merged = new ArrayList<>();
        for (LocalTime[] cur : clamped) {
            if (merged.isEmpty()) {
                merged.add(new LocalTime[]{cur[0], cur[1]});
            } else {
                LocalTime[] last = merged.get(merged.size() - 1);
                if (!cur[0].isAfter(last[1])) {
                    if (cur[1].isAfter(last[1])) {
                        last[1] = cur[1];
                    }
                } else {
                    merged.add(new LocalTime[]{cur[0], cur[1]});
                }
            }
        }

        // Trous entre l'ouverture, les blocs fusionnes et la fermeture.
        LocalTime cursor = opening;
        for (LocalTime[] block : merged) {
            if (cursor.isBefore(block[0])) {
                addIfLongEnough(free, cursor, block[0], minDureeMinutes);
            }
            if (block[1].isAfter(cursor)) {
                cursor = block[1];
            }
        }
        if (cursor.isBefore(closing)) {
            addIfLongEnough(free, cursor, closing, minDureeMinutes);
        }
        return free;
    }

    private void addIfLongEnough(List<FreeInterval> free, LocalTime debut, LocalTime fin,
                                 long minDureeMinutes) {
        FreeInterval interval = new FreeInterval(debut, fin);
        if (interval.dureeMinutes() >= Math.max(0, minDureeMinutes)) {
            free.add(interval);
        }
    }

    // ----- Regle de duree exploitable (§ micro-creneaux, § duree souhaitee) -----

    /**
     * Duree minimale, en minutes, d'un creneau reellement exploitable. Lue depuis
     * la <b>configuration globale</b> (Parametres > Reservations, §5/§20) : une
     * periode plus courte n'est ni calculee, ni affichee, ni reservable.
     */
    public int minSlotMinutes() {
        return settingsService.minSlotMinutes();
    }

    /** Vrai si {@code [debut, fin)} atteint la duree minimale exploitable. */
    public boolean isLongEnough(LocalTime debut, LocalTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            return false;
        }
        return Duration.between(debut, fin).toMinutes() >= minSlotMinutes();
    }

    /** Message unique expliquant le refus d'un creneau trop court. */
    public String tooShortMessage() {
        return "Un créneau doit durer au moins " + DateLabels.duree(minSlotMinutes())
                + " : une réservation plus courte n'est pas exploitable.";
    }

    /**
     * Ne conserve que les periodes <b>continues</b> d'au moins {@code minutes}.
     * Deux periodes separees par une occupation ne sont jamais additionnees : une
     * demande de 2 h n'est satisfaite que par un seul bloc libre de 2 h.
     */
    public List<FreeInterval> atLeast(List<FreeInterval> periods, long minutes) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        return periods.stream().filter(p -> p.dureeMinutes() >= minutes).toList();
    }

    /** Duree du plus long bloc libre continu, en minutes (0 si aucun). */
    public long longestFreeMinutes(List<FreeInterval> periods) {
        if (periods == null || periods.isEmpty()) {
            return 0L;
        }
        return periods.stream().mapToLong(FreeInterval::dureeMinutes).max().orElse(0L);
    }

    // ----- Evaluation d'un creneau precis (§9, §14) -----

    /**
     * Verdict complet pour un creneau {@code [debut, fin)} : jour ouvrable,
     * bornes d'ouverture, occupations bloquantes puis demandes en attente.
     * Utilise aussi bien par la recherche « exacte » que par la validation
     * finale de reservation (§14) — logique unique.
     */
    public SlotVerdict evaluateSlot(DayContext ctx, SpaceOccupancy occ,
                                    LocalTime debut, LocalTime fin) {
        if (ctx.isClosed()) {
            return new SlotVerdict(SlotVerdict.Status.NON_WORKING_DAY, null, ctx.closedReason());
        }
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            return new SlotVerdict(SlotVerdict.Status.OUT_OF_HOURS, null,
                    "L'heure de fin doit être postérieure à l'heure de début.");
        }
        // Bornes d'ouverture (§7). La configuration globale (Parametres >
        // Reservations, Module 11 §5) peut autoriser explicitement les creneaux
        // hors horaires : dans ce cas la borne n'est plus un refus. La RECHERCHE
        // continue de ne proposer que des periodes situees dans la plage
        // d'ouverture — l'autorisation ouvre la saisie manuelle, elle ne
        // transforme pas la nuit en creneau suggere.
        if (debut.isBefore(ctx.opening()) || fin.isAfter(ctx.closing())) {
            if (!settingsService.current().isReservationsHorsHorairesAutorisees()) {
                return new SlotVerdict(SlotVerdict.Status.OUT_OF_HOURS, null, String.format(
                        "Les réservations sont possibles uniquement entre %s et %s.",
                        ctx.opening().format(HM), ctx.closing().format(HM)));
            }
        }
        for (OccupancyInterval i : occ.blocking()) {
            if (i.overlaps(debut, fin)) {
                String reason = String.format(
                        "Créneau indisponible : %s de %s à %s.",
                        i.sourceLabel(), i.debut().format(HM), i.fin().format(HM));
                return new SlotVerdict(SlotVerdict.Status.BLOCKED, i, reason);
            }
        }
        for (OccupancyInterval i : occ.pending()) {
            if (i.overlaps(debut, fin)) {
                String reason = String.format(
                        "Une demande de réservation est en attente sur ce créneau (%s à %s).",
                        i.debut().format(HM), i.fin().format(HM));
                return new SlotVerdict(SlotVerdict.Status.PENDING_OVERLAP, i, reason);
            }
        }
        return SlotVerdict.free();
    }

    /**
     * Prochaine occupation (bloquante ou en attente) commencant a {@code apres}
     * ou plus tard. Sert a borner le creneau « libre maintenant » (§10) et a
     * enrichir l'affichage.
     */
    public OccupancyInterval nextOccupation(SpaceOccupancy occ, LocalTime apres) {
        OccupancyInterval next = null;
        List<OccupancyInterval> all = new ArrayList<>(occ.blocking());
        all.addAll(occ.pending());
        for (OccupancyInterval i : all) {
            if (!i.debut().isBefore(apres) && (next == null || i.debut().isBefore(next.debut()))) {
                next = i;
            }
        }
        return next;
    }

    // ----- Helpers de regles (identiques a l'ancien AvailabilityService) -----

    /**
     * Une seance ne bloque a une date que si l'annee universitaire qui la porte
     * couvre cette date. <b>Null-safe</b> : une seance sans annee (historique)
     * conserve son comportement d'origine (bloque). Corrige la divergence
     * historique entre les deux anciens services.
     */
    public boolean academicYearCoversDate(Schedule schedule, LocalDate date) {
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
     * Une seance bloque sa salle a une date seulement si l'emploi du temps qui
     * la porte est actif a cette date : un EDT archive ou expire libere ses
     * salles (§20). Une seance sans en-tete conserve son comportement d'origine.
     */
    public boolean timetableActiveOn(Schedule schedule, LocalDate date) {
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

    /** Libelle non nominatif d'une seance : matiere (jamais l'enseignant). */
    private String seanceLabel(Schedule schedule) {
        String matiere = schedule.getMatiere();
        return (matiere == null || matiere.isBlank()) ? "Séance" : matiere;
    }

    /**
     * Traduit la categorie d'une occupation supplementaire en source
     * d'occupation. La categorie ne sert qu'a <em>expliquer</em> le blocage : le
     * calcul lui-meme est strictement identique pour les trois categories.
     */
    private OccupancySource occupancySource(OccupationCategorie categorie) {
        if (categorie == null) {
            return OccupancySource.AUTRE_OCCUPATION;
        }
        return switch (categorie) {
            case EXAMEN -> OccupancySource.EXAMEN;
            case SOUTENANCE -> OccupancySource.SOUTENANCE;
            case AUTRE -> OccupancySource.AUTRE_OCCUPATION;
        };
    }

    /**
     * Libelle <b>non nominatif</b> d'une occupation supplementaire (§16) : on
     * n'expose ni l'intitule (qui peut nommer un etudiant en soutenance) ni le
     * responsable, seulement la nature de l'occupation.
     */
    private String occupationLabel(OccupationSupplementaire occupation) {
        return occupation.getType() != null ? occupation.getType().getLibelle() : "Occupation";
    }

    public WeekDay toWeekDay(DayOfWeek dayOfWeek) {
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
}
