package com.campusops.notification.service;

import com.campusops.enums.NotificationType;
import com.campusops.enums.ReminderFrequency;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.Role;
import com.campusops.notification.repository.ReservationReminderSentRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

/**
 * Rappels automatiques des reservations a venir — pilotes par le reglage
 * « frequence des rappels » (Module 11, §8).
 *
 * <p>Regles appliquees, toutes issues de la configuration centrale :</p>
 * <ul>
 *   <li>{@link ReminderFrequency#JAMAIS} : aucun rappel ;</li>
 *   <li>{@link ReminderFrequency#QUOTIDIENNE} : rappel la veille de la
 *       reservation ; {@link ReminderFrequency#HEBDOMADAIRE} : une semaine
 *       avant ;</li>
 *   <li>{@code notificationsActivees} / {@code notificationsAutomatiquesActivees}
 *       a faux : aucun rappel (double garde : ici pour eviter le travail inutile,
 *       et dans {@link NotificationService#notifySystemUser} qui reste la seule
 *       autorite en la matiere).</li>
 * </ul>
 *
 * <p>Le traitement ne cible qu'une <b>date precise</b> (aujourd'hui + l'avance
 * configuree), ce qui garantit au plus un rappel par reservation. Un garde-fou
 * memoire empeche un second envoi le meme jour (redemarrage, declenchement
 * manuel).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    /** Roles charges de valider les reservations (rappel des demandes en attente). */
    private static final EnumSet<Role> APPROVAL_ROLES = EnumSet.of(Role.ADMIN);

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final ReservationReminderSentRepository reminderSentRepository;

    /** Derniere date d'execution effective : evite un doublon le meme jour. */
    private volatile LocalDate derniereExecution;

    /**
     * Emet les rappels du jour.
     *
     * @return nombre de rappels individuels emis (0 si les rappels sont
     *         desactives ou deja emis aujourd'hui).
     */
    @Transactional
    public int envoyerRappelsDuJour() {
        AppSettings reglages = settingsService.current();
        ReminderFrequency frequence = reglages.getFrequenceRappels();

        if (frequence == null || !frequence.isActive()) {
            log.debug("Rappels automatiques desactives (frequence = {})", frequence);
            return 0;
        }
        if (!reglages.isNotificationsActivees() || !reglages.isNotificationsAutomatiquesActivees()) {
            log.debug("Rappels automatiques ignores : notifications automatiques desactivees");
            return 0;
        }

        ZoneId zone = ZoneId.of(reglages.getFuseauHoraire());
        LocalDate aujourdHui = LocalDate.now(zone);
        if (aujourdHui.equals(derniereExecution)) {
            log.debug("Rappels deja emis aujourd'hui ({}) : execution ignoree", aujourdHui);
            return 0;
        }
        derniereExecution = aujourdHui;

        LocalDate cible = aujourdHui.plusDays(frequence.getJoursAvant());
        List<Reservation> duJour = reservationRepository.findByDate(cible);

        int rappels = 0;
        int enAttente = 0;
        for (Reservation reservation : duJour) {
            if (!reservation.isActif()) {
                continue;
            }
            if (reservation.getStatut() == ReservationStatus.APPROVED) {
                String reminderKey = buildReminderKey(reservation.getId(), frequence);
                if (reminderSentRepository.existsByReservationIdAndReminderKey(reservation.getId(), reminderKey)) {
                    continue;
                }
                notificationService.notifySystemUser(reservation.getUser(), NotificationType.INFO,
                        "Rappel de réservation",
                        describe(reservation),
                        "/reservations");
                reminderSentRepository.save(
                        com.campusops.notification.entity.ReservationReminderSent.builder()
                                .reservationId(reservation.getId())
                                .reminderKey(reminderKey)
                                .build());
                rappels++;
            } else if (reservation.getStatut() == ReservationStatus.PENDING) {
                enAttente++;
            }
        }

        if (enAttente > 0) {
            notificationService.notifySystemRoles(APPROVAL_ROLES, NotificationType.WARNING,
                    "Réservations en attente de validation",
                    String.format("%d réservation(s) prévue(s) le %s attendent encore une validation.",
                            enAttente, cible),
                    "/reservations");
        }

        log.info("Rappels de reservation emis : {} pour le {} ({} demande(s) en attente signalee(s))",
                rappels, cible, enAttente);
        return rappels;
    }

    /** Libelle lisible du rappel (espace, date, horaires). */
    private String describe(Reservation reservation) {
        String espace = reservation.getSpace() == null ? "un espace" : reservation.getSpace().getNom();
        String date = reservation.getDate() == null ? "la date prévue" : reservation.getDate().toString();
        return String.format(
                "Rappel : votre réservation de %s est prévue le %s de %s à %s.",
                espace, date, reservation.getHeureDebut(), reservation.getHeureFin());
    }

    private String buildReminderKey(Long reservationId, ReminderFrequency frequency) {
        return "reservation-reminder-" + reservationId + "-" + frequency.name();
    }
}
