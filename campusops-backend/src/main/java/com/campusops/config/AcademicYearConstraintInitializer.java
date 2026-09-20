package com.campusops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Garantit, au niveau de la base de données, l'invariant métier
 * « une seule année universitaire active à la fois ».
 *
 * <p>Deux actions idempotentes au démarrage :
 * <ol>
 *   <li>Normalisation : si des données existantes contiennent plusieurs années
 *       actives (héritage d'une ancienne logique), on ne conserve que la plus
 *       récente comme active et on désactive les autres — sans rien supprimer.</li>
 *   <li>Filet de sécurité : création d'un index unique partiel PostgreSQL
 *       interdisant physiquement deux lignes {@code actif = true}. Même en cas
 *       d'accès concurrent ou de bug applicatif, la base refuse l'incohérence.</li>
 * </ol>
 *
 * <p>Exécuté très tôt (@Order(1)), avant les seeders académiques (@Order(6)),
 * une fois le schéma créé par Hibernate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AcademicYearConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer actives = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM academic_years WHERE actif = true", Integer.class);
            if (actives != null && actives > 1) {
                jdbcTemplate.update(
                        "UPDATE academic_years SET actif = false "
                                + "WHERE actif = true AND id <> "
                                + "(SELECT MAX(id) FROM academic_years WHERE actif = true)");
                log.warn("Plusieurs années universitaires actives détectées : "
                        + "normalisation effectuée (une seule conservée, aucune donnée supprimée).");
            }

            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_academic_year_single_active "
                            + "ON academic_years (actif) WHERE actif = true");
            log.info("Index unique partiel 'une seule année active' garanti.");
        } catch (Exception e) {
            // Ne jamais bloquer le démarrage : la règle reste appliquée par la couche service.
            log.warn("Impossible d'appliquer la contrainte 'une seule année active' en base : {}",
                    e.getMessage());
        }
    }
}
