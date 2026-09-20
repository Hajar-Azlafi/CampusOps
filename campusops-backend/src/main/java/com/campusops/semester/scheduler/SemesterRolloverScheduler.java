package com.campusops.semester.scheduler;

import com.campusops.semester.service.SemesterRolloverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bascule automatique au semestre suivant — <b>désactivée par défaut</b>.
 *
 * <p>Toute la machinerie de planification n'est activée que si
 * {@code campusops.semester.auto-rollover.enabled=true}. Tant que la propriété
 * est absente ou fausse, ce bean n'est pas créé, {@code @EnableScheduling}
 * n'est pas déclenché, et aucune tâche planifiée ne tourne : la bascule reste
 * alors <b>exclusivement manuelle</b> (bouton admin /
 * {@code POST /api/semesters/rollover}).
 *
 * <p><b>Différence importante avec le passage d'année</b> : une année possède
 * une {@code dateFin} qui garde l'automatisation (on ne bascule que si l'année
 * est terminée). Les <b>semestres n'ont pas de date</b> : cette tâche bascule
 * donc <b>à chaque déclenchement</b>, sans garde. Le cron doit par conséquent
 * viser une <b>date précise, une fois par an</b> (par défaut le 1er février à
 * 03h00, pour enchaîner S1→S2) et surtout pas une fréquence quotidienne, sous
 * peine d'avancer les semestres en boucle. Régler
 * {@code campusops.semester.auto-rollover.cron} en conséquence si activé.
 *
 * <p>Même activée, l'automatisation est <b>non destructive</b> : elle délègue à
 * {@link SemesterRolloverService}, qui fait avancer les semestres courants et
 * archive les emplois du temps du semestre quitté (salles libérées) sans jamais
 * rien supprimer.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "campusops.semester.auto-rollover.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SemesterRolloverScheduler {

    private final SemesterRolloverService semesterRolloverService;

    /**
     * Fait avancer tous les niveaux au semestre suivant. Par défaut le 1er
     * février à 03h00 (une seule fois par an) ; configurable via
     * {@code campusops.semester.auto-rollover.cron}. Attention : aucune garde de
     * date — chaque déclenchement bascule (voir la note de classe).
     */
    @Scheduled(cron = "${campusops.semester.auto-rollover.cron:0 0 3 1 2 *}")
    public void autoRollover() {
        log.info("Auto-bascule de semestre déclenchée (planificateur). Passage au semestre suivant.");
        semesterRolloverService.passerAuSemestreSuivant();
    }
}
