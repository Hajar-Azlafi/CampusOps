package com.campusops.notification.scheduler;

import com.campusops.enums.ReminderFrequency;
import com.campusops.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Declencheur quotidien des rappels de reservation (Module 11, §8 — reglage
 * « frequence des rappels »).
 *
 * <p>Simple minuterie : toute la logique — et la transaction — vit dans
 * {@link ReminderService}, conformement a la convention du projet
 * (cf. {@code SemesterRolloverScheduler} qui delegue de la meme facon). Cela
 * garde le declenchement testable independamment de la regle metier.</p>
 *
 * <p>Contrairement aux planificateurs de bascule (annee, semestre) qui sont
 * <b>opt-in par propriete</b> parce qu'ils modifient la structure academique,
 * cette tache est purement informative : elle cree des notifications et ne
 * touche a aucune donnee metier. Elle est donc active par defaut, mais
 * entierement gouvernee par la configuration : {@link ReminderFrequency#JAMAIS}
 * ou les interrupteurs de notifications suffisent a la rendre silencieuse.</p>
 *
 * <p>Le declenchement peut malgre tout etre coupe par
 * {@code campusops.reminders.enabled=false} (utile en test) et son heure reglee
 * par {@code campusops.reminders.cron}.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "campusops.reminders.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ReservationReminderScheduler {

    private final ReminderService reminderService;

    /**
     * Emet les rappels du jour. Par defaut a 07h00 ; reglable via
     * {@code campusops.reminders.cron}.
     */
    @Scheduled(cron = "${campusops.reminders.cron:0 0 7 * * *}", zone = "${campusops.app.timezone:Africa/Casablanca}")
    public void envoyerRappels() {
        log.debug("Declenchement planifie des rappels de reservation");
        reminderService.envoyerRappelsDuJour();
    }
}
