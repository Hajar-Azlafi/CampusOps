package com.campusops.availability.engine;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.calendar.entity.NonWorkingDay;
import com.campusops.calendar.repository.NonWorkingDayRepository;
import com.campusops.reservation.config.ReservationHoursProperties;
import com.campusops.settings.service.SettingsService;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Regles de calendrier et d'horaires <b>centralisees</b> (§2, §3, §4, §7).
 *
 * <p>Source unique de verite pour :</p>
 * <ul>
 *   <li>les <b>bornes d'ouverture</b> de la journee, <b>derivees des creneaux
 *       actifs</b> (§7) — debut du premier creneau, fin du dernier — avec repli
 *       sur la configuration globale (Parametres &gt; Horaires) si aucun creneau
 *       n'est defini ;</li>
 *   <li>le caractere <b>ouvrable</b> d'une date : jours ouvrables configures
 *       (§3, Module 11 §6) et jours du <b>calendrier non ouvrable</b>
 *       administrable (§4) ;</li>
 *   <li>la <b>resolution de l'annee universitaire</b> couvrant une date, active
 *       ou non (requete annexe) ;</li>
 *   <li>la <b>fenetre de dates ouvertes a la recherche</b> : jamais dans le
 *       passe, plus la journee en cours passe l'heure limite, et toujours dans
 *       l'annee universitaire <b>active</b> ({@link SearchDateWindow}).</li>
 * </ul>
 *
 * <p>Ne fait aucune hypothese sur un espace : ces regles sont globales et ne
 * doivent jamais etre dupliquees ailleurs (§19).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkingCalendarService {

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TimeSlotRepository timeSlotRepository;
    private final NonWorkingDayRepository nonWorkingDayRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ReservationHoursProperties hoursProperties;
    private final SettingsService settingsService;

    // ----- Horaires derives des creneaux (§7) -----

    /**
     * Heure d'ouverture = <b>debut du premier creneau actif</b>. Repli sur
     * l'heure d'ouverture de la <b>configuration globale</b> (Parametres >
     * Horaires, Module 11 §6) si aucun creneau actif n'existe. Aucune valeur
     * codee en dur ailleurs dans le code (§7, §20).
     */
    public LocalTime openingTime() {
        return activeSlots().stream()
                .map(TimeSlot::getHeureDebut)
                .min(LocalTime::compareTo)
                .orElseGet(() -> settingsService.current().getHeureOuverture());
    }

    /** Heure de fermeture = <b>fin du dernier creneau actif</b> (§7). */
    public LocalTime closingTime() {
        return activeSlots().stream()
                .map(TimeSlot::getHeureFin)
                .max(LocalTime::compareTo)
                .orElseGet(() -> settingsService.current().getHeureFermeture());
    }

    private List<TimeSlot> activeSlots() {
        return timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true);
    }

    /**
     * Creneaux officiels actifs, tries chronologiquement (§3, §7). Exposes pour
     * proposer a la reservation les creneaux institutionnels contenus dans une
     * periode libre, plutot que de forcer une saisie horaire libre.
     */
    public List<TimeSlot> creneauxActifs() {
        return activeSlots();
    }

    /** Duree minimale exploitable d'une periode libre, en minutes (§ micro-creneaux). */
    public int minSlotMinutes() {
        return settingsService.minSlotMinutes();
    }

    /** Durees proposees par le formulaire de recherche (indicatif, en minutes). */
    public List<Integer> dureesProposees() {
        List<Integer> options = hoursProperties.getDurationOptions();
        return (options == null || options.isEmpty()) ? List.of(60, 90, 120, 180) : options;
    }

    /** Heure limite de recherche pour la journee en cours (§ ambiguite de date). */
    public LocalTime heureLimiteRecherche() {
        LocalTime limite = settingsService.current().getHeureLimiteRecherche();
        return limite != null ? limite : closingTime();
    }

    // ----- Jours ouvrables (§3, §4) -----

    /**
     * Vrai si la date tombe un jour <b>non ouvrable de la semaine</b>. La liste
     * des jours ouvrables vient de la configuration globale (Parametres >
     * Horaires, Module 11 §6) : par defaut lundi -> samedi, dimanche ferme (§3).
     * Une universite peut y fermer le vendredi ou ouvrir le dimanche sans
     * toucher au code.
     */
    public boolean isWeeklyClosed(LocalDate date) {
        return !settingsService.isJourOuvrable(date);
    }

    /** Premiere entree active du calendrier non ouvrable couvrant la date, s'il y en a une. */
    public Optional<NonWorkingDay> findClosure(LocalDate date) {
        List<NonWorkingDay> covering = nonWorkingDayRepository.findActiveCovering(date);
        return covering.isEmpty() ? Optional.empty() : Optional.of(covering.get(0));
    }

    // ----- Annee universitaire par date (requete annexe) -----

    /**
     * Annee universitaire couvrant la date, <b>active ou non</b>. Une annee
     * inactive reste exploitable pour consulter l'historique et la disponibilite
     * de sa periode. Renvoie vide si aucune annee ne couvre la date : l'appelant
     * affiche alors un message clair, sans jamais presenter toutes les salles
     * comme libres.
     */
    public Optional<AcademicYear> resolveAcademicYear(LocalDate date) {
        List<AcademicYear> covering = academicYearRepository.findCoveringDate(date);
        return covering.isEmpty() ? Optional.empty() : Optional.of(covering.get(0));
    }

    /**
     * Annee universitaire <b>active</b> (celle en cours d'exploitation), qui borne
     * les dates ouvertes a la recherche et a la reservation. Distincte de
     * {@link #resolveAcademicYear(LocalDate)} qui, elle, renvoie l'annee couvrant
     * une date, active ou non (consultation de l'historique).
     */
    public Optional<AcademicYear> activeAcademicYear() {
        return academicYearRepository.findFirstByActifTrue();
    }

    // ----- Fenetre de dates ouvertes a la recherche -----

    /** Fenetre de recherche calculee sur l'instant present. */
    public SearchDateWindow searchDateWindow() {
        return searchDateWindow(LocalDate.now(), LocalTime.now().withSecond(0).withNano(0));
    }

    /**
     * Fenetre de dates ouvertes a la recherche pour une reference donnee
     * (parametres injectes pour rester testable) :
     * <ul>
     *   <li>borne basse = aujourd'hui, ou <b>demain</b> si l'heure limite est
     *       depassee, remontee au debut de l'annee active si celui-ci est encore
     *       a venir ;</li>
     *   <li>borne haute = <b>fin de l'annee universitaire active</b>, ou aucune
     *       borne si aucune annee active n'est definie.</li>
     * </ul>
     */
    public SearchDateWindow searchDateWindow(LocalDate today, LocalTime now) {
        LocalTime limite = heureLimiteRecherche();
        boolean limiteDepassee = now != null && !now.isBefore(limite);

        Optional<AcademicYear> active = activeAcademicYear();
        Long anneeId = active.map(AcademicYear::getId).orElse(null);
        String anneeLibelle = active.map(AcademicYear::getLibelle).orElse(null);
        LocalDate anneeDebut = active.map(AcademicYear::getDateDebut).orElse(null);
        LocalDate anneeFin = active.map(AcademicYear::getDateFin).orElse(null);

        LocalDate min = limiteDepassee ? today.plusDays(1) : today;
        if (anneeDebut != null && anneeDebut.isAfter(min)) {
            min = anneeDebut;
        }

        return new SearchDateWindow(today, limite, limiteDepassee, min, anneeFin,
                anneeId, anneeLibelle, anneeDebut, anneeFin);
    }

    // ----- Contexte de journee (assemble tout) -----

    /**
     * Calcule le contexte complet d'une date : ouvrable ou non (dimanche/ferie),
     * bornes d'ouverture (creneaux), annee universitaire couvrante. Appele une
     * seule fois par recherche puis partage entre tous les espaces (§2, §19).
     */
    public DayContext describeDay(LocalDate date) {
        LocalTime opening = openingTime();
        LocalTime closing = closingTime();

        Optional<AcademicYear> year = resolveAcademicYear(date);
        Long yearId = year.map(AcademicYear::getId).orElse(null);
        String yearLibelle = year.map(AcademicYear::getLibelle).orElse(null);
        boolean yearResolved = year.isPresent();

        // §3 / Module 11 §6 : jour hors des jours ouvrables configures.
        if (isWeeklyClosed(date)) {
            String jour = SettingsService.nomJour(date);
            String reason = "Les réservations ne sont pas disponibles le " + jour + ".";
            String type = SettingsService.weekDayOf(date).name();
            return new DayContext(date, false, type, reason, opening, closing,
                    yearId, yearLibelle, yearResolved);
        }

        // §4 : jour ferie / non ouvrable administrable.
        Optional<NonWorkingDay> closure = findClosure(date);
        if (closure.isPresent()) {
            NonWorkingDay nwd = closure.get();
            String reason = String.format(
                    "Les réservations ne sont pas disponibles le %s (%s).",
                    date.format(FR_DATE), nwd.getLibelle());
            return new DayContext(date, false, "JOUR_FERIE", reason, opening, closing,
                    yearId, yearLibelle, yearResolved);
        }

        // Jour ouvrable.
        return new DayContext(date, true, null, null, opening, closing,
                yearId, yearLibelle, yearResolved);
    }
}
