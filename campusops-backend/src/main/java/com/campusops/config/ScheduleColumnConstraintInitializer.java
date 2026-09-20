package com.campusops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapte, au niveau de la base, l'introduction des seances « distancielles ».
 *
 * <p>A l'origine, une seance ({@code schedules}) exigeait toujours une salle :
 * la colonne {@code space_id} etait {@code NOT NULL}. Le module « Emplois du
 * temps » introduit le type de presence : en <strong>distanciel</strong>, la
 * salle n'est pas obligatoire (cahier des charges §6). Hibernate en mode
 * {@code update} ne relache jamais une contrainte {@code NOT NULL} existante ;
 * on la supprime donc explicitement pour autoriser {@code space_id} nul.</p>
 *
 * <p>La regle « presentiel ⇒ salle obligatoire » reste appliquee par la couche
 * service. Aucune donnee n'est modifiee ni supprimee.</p>
 *
 * <p>Execute tot (@Order(3)), apres le schema Hibernate et les autres
 * initialiseurs de contraintes.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class ScheduleColumnConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE schedules ALTER COLUMN space_id DROP NOT NULL");
            log.info("[schedule-constraint] Contrainte NOT NULL sur schedules.space_id "
                    + "relachee : les seances distancielles peuvent ne pas avoir de salle.");
        } catch (Exception e) {
            // Ne jamais bloquer le demarrage : la regle presentiel/distanciel
            // reste appliquee par la couche service.
            log.warn("[schedule-constraint] Impossible de relacher NOT NULL sur "
                    + "schedules.space_id (peut etre deja nullable) : {}", e.getMessage());
        }
    }
}
