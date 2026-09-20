package com.campusops.academicyear.scheduler;

import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.academicyear.service.AcademicYearRolloverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;

/**
 * Passage automatique à l'année suivante — <b>désactivé par défaut</b>.
 *
 * <p>Toute la machinerie de planification n'est activée que si
 * {@code campusops.academic-year.auto-rollover.enabled=true}. Tant que la
 * propriété est absente ou fausse, ce bean n'est pas créé, {@code @EnableScheduling}
 * n'est pas déclenché, et aucune tâche planifiée ne tourne : la bascule reste
 * alors <b>exclusivement manuelle</b> (bouton admin / {@code POST /api/academic-years/rollover}).
 *
 * <p>Même activée, l'automatisation est <b>non destructive</b> : elle délègue à
 * {@link AcademicYearRolloverService} qui crée/active l'année suivante sans jamais
 * supprimer l'année précédente ni sa structure (promotions, groupes, emplois du
 * temps, réservations restent consultables).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "campusops.academic-year.auto-rollover.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AcademicYearRolloverScheduler {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearRolloverService rolloverService;

    /**
     * Vérifie périodiquement (par défaut chaque jour à 03h00) si l'année active
     * est terminée ({@code dateFin} dépassée) et, le cas échéant, bascule vers
     * l'année suivante. La fréquence est configurable via
     * {@code campusops.academic-year.auto-rollover.cron}.
     */
    @Scheduled(cron = "${campusops.academic-year.auto-rollover.cron:0 0 3 * * *}")
    public void autoRolloverIfYearEnded() {
        academicYearRepository.findFirstByActifTrue().ifPresent(active -> {
            LocalDate today = LocalDate.now();
            if (active.getDateFin() != null && today.isAfter(active.getDateFin())) {
                log.info("Auto-rollover : l'année active « {} » est terminée (fin le {}). "
                                + "Passage automatique à l'année suivante.",
                        active.getLibelle(), active.getDateFin());
                rolloverService.passerAAnneeSuivante();
            } else {
                log.debug("Auto-rollover : année active « {} » encore en cours, aucune bascule.",
                        active.getLibelle());
            }
        });
    }
}
