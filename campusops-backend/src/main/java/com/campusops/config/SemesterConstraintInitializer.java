package com.campusops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapte, au niveau de la base de données, le passage du modèle « semestre
 * unique par nom » au modèle « semestre rattaché à un niveau/cycle ».
 *
 * <p>Un même libellé (« S1 ») doit pouvoir exister dans plusieurs cycles : S1
 * du Tronc commun est distinct de S1 du Cycle ingénieur. L'unicité devient donc
 * composite (nom + niveau). Hibernate en mode {@code update} ne supprime jamais
 * l'ancienne contrainte d'unicité mono-colonne sur {@code semesters.nom} :
 * laissée en place, elle interdirait à tort ce partage de libellé entre cycles.
 *
 * <p>On supprime dynamiquement toute contrainte UNIQUE mono-colonne portant sur
 * {@code semesters.nom} (y compris les noms générés par Hibernate). La nouvelle
 * contrainte composite {@code uk_semester_nom_level} (2 colonnes) est créée par
 * Hibernate et n'est pas concernée. Aucune donnée n'est supprimée.
 *
 * <p>Exécuté tôt (@Order(2)), après {@link AcademicYearConstraintInitializer}
 * et avant les seeders académiques.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class SemesterConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            List<String> nomConstraints = jdbcTemplate.queryForList(
                    "SELECT tc.constraint_name "
                            + "FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.constraint_column_usage ccu "
                            + "  ON tc.constraint_name = ccu.constraint_name "
                            + " AND tc.table_schema = ccu.table_schema "
                            + "WHERE tc.table_name = 'semesters' "
                            + "  AND tc.constraint_type = 'UNIQUE' "
                            + "GROUP BY tc.constraint_name "
                            + "HAVING COUNT(*) = 1 AND MAX(ccu.column_name) = 'nom'",
                    String.class);
            for (String constraint : nomConstraints) {
                jdbcTemplate.execute(
                        "ALTER TABLE semesters DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");
                log.warn("[semester-constraint] Contrainte d'unicité mono-colonne sur "
                        + "semesters.nom supprimée : {}", constraint);
            }
        } catch (Exception e) {
            // Ne jamais bloquer le démarrage : l'unicité reste appliquée par la couche service.
            log.warn("[semester-constraint] Détection/suppression de la contrainte unique sur "
                    + "semesters.nom échouée : {}", e.getMessage());
        }

        // Filet de sécurité : anciens noms explicites éventuels.
        for (String constraint : List.of("semesters_nom_key", "uk_semesters_nom", "semesters_nom_unique")) {
            try {
                jdbcTemplate.execute("ALTER TABLE semesters DROP CONSTRAINT IF EXISTS " + constraint);
            } catch (Exception e) {
                log.debug("[semester-constraint] Contrainte {} non supprimée : {}", constraint, e.getMessage());
            }
        }

        // --- Semestre « courant » par niveau + période (bascule de semestre, §20) ---
        // Migration non destructive (§29) :
        //   1. garantit la colonne 'courant' nullable et normalise les NULL à false ;
        //   2. SUPPRIME l'ancien index unique partiel « un seul courant par niveau » :
        //      un niveau qui couvre plusieurs années (tronc commun, master, cycle
        //      ingénieur) doit pouvoir avoir PLUSIEURS semestres courants simultanés
        //      (ex. S1 + S3, voire S1 + S3 + S5), car des cohortes d'années
        //      différentes coexistent. L'exclusivité n'est donc plus imposée ;
        //   3. garantit les colonnes de période 'date_debut'/'date_fin' (nullable) :
        //      les emplois du temps et examens d'un semestre ne peuvent pas déborder
        //      de cette fenêtre.
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE semesters ADD COLUMN IF NOT EXISTS courant boolean");
            int normalises = jdbcTemplate.update(
                    "UPDATE semesters SET courant = false WHERE courant IS NULL");
            jdbcTemplate.execute(
                    "DROP INDEX IF EXISTS uk_semester_courant_level");
            jdbcTemplate.execute(
                    "ALTER TABLE semesters ADD COLUMN IF NOT EXISTS date_debut date");
            jdbcTemplate.execute(
                    "ALTER TABLE semesters ADD COLUMN IF NOT EXISTS date_fin date");
            log.info("[semester-constraint] Colonne 'courant' garantie ({} ligne(s) "
                    + "normalisée(s)), index unique partiel 'un seul courant par niveau' "
                    + "supprimé (plusieurs courants autorisés) + colonnes de période "
                    + "'date_debut'/'date_fin' garanties.", normalises);
        } catch (Exception e) {
            // Ne jamais bloquer le démarrage : les invariants restent gérés par le service.
            log.warn("[semester-constraint] Adaptation des colonnes 'courant'/période des "
                    + "semestres échouée : {}", e.getMessage());
        }
    }
}
