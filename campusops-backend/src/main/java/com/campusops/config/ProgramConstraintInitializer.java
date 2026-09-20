package com.campusops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepare, au niveau de la base de donnees, l'etat <b>ACTIF / INACTIF</b> des
 * filieres ({@code programs.actif}).
 *
 * <p>La filiere etait la seule entite du referentiel academique sans drapeau
 * {@code actif}, alors que la regle metier « Departement -> Filiere -> Promotion
 * -> Groupe » exige de pouvoir desactiver une filiere. La colonne est donc
 * ajoutee <b>sans rien supprimer</b> :
 * <ol>
 *   <li>{@code ADD COLUMN IF NOT EXISTS actif boolean} : filet de securite si le
 *       {@code ddl-auto=update} d'Hibernate n'a pas pu creer la colonne ;</li>
 *   <li>{@code UPDATE ... WHERE actif IS NULL} : les filieres existantes
 *       deviennent explicitement <b>ACTIVES</b>. Une migration ne doit jamais
 *       rendre inutilisable une filiere deja en service.</li>
 * </ol>
 *
 * <p>Idempotent (la deuxieme execution normalise 0 ligne) et non bloquant :
 * comme {@link SemesterConstraintInitializer}, toute erreur est journalisee
 * sans interrompre le demarrage — l'entite traite de toute facon NULL comme
 * ACTIF ({@code Program.isActif()}) et les requetes de filtrage acceptent
 * explicitement NULL.
 *
 * <p>Execute tot (@Order(2)), avant les seeders academiques (@Order(8)) qui
 * creent les filieres avec {@code actif = true}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class ProgramConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE programs ADD COLUMN IF NOT EXISTS actif boolean");
            int normalises = jdbcTemplate.update(
                    "UPDATE programs SET actif = true WHERE actif IS NULL");
            if (normalises > 0) {
                log.info("[program-constraint] Colonne 'actif' garantie sur programs : "
                        + "{} filiere(s) existante(s) marquee(s) ACTIVE(s) "
                        + "(migration non destructive).", normalises);
            } else {
                log.info("[program-constraint] Colonne 'actif' des filieres deja normalisee : "
                        + "rien a faire.");
            }
        } catch (Exception e) {
            // Ne jamais bloquer le demarrage : NULL est de toute facon traite comme ACTIF.
            log.warn("[program-constraint] Preparation de la colonne 'actif' des filieres "
                    + "echouee : {}", e.getMessage());
        }
    }
}
