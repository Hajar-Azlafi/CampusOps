package com.campusops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recopie les examens de l'<b>ancienne table {@code examens}</b> vers la table
 * générique {@code occupations_supplementaires} issue de la restructuration
 * « Occupation supplémentaire ».
 *
 * <p><b>Pourquoi</b> : {@code Examen} est devenu un sous-type d'
 * {@code OccupationSupplementaire} (héritage {@code SINGLE_TABLE}). Hibernate en
 * mode {@code update} crée la nouvelle table mais ne déplace <em>aucune</em>
 * donnée : sans cette étape, les examens déjà saisis disparaîtraient de
 * l'application et — plus grave — cesseraient d'occuper leur salle dans le
 * moteur central de disponibilité.</p>
 *
 * <p><b>Non destructif</b> (§29) : l'ancienne table est <em>conservée
 * intacte</em>. Rien n'est supprimé, rien n'est modifié côté source ; on se
 * contente d'insérer les lignes manquantes dans la nouvelle table. En cas de
 * problème, l'ancienne table reste la trace de référence.</p>
 *
 * <p><b>Idempotent</b> : une ligne n'est recopiée que si aucune occupation de
 * type examen ne porte déjà la même clé naturelle
 * ({@code espace + date + heures + matière}). Plusieurs démarrages successifs ne
 * créent donc jamais de doublon, et un import partiel se termine tout seul au
 * démarrage suivant.</p>
 *
 * <p><b>Tolérant au schéma</b> : les colonnes de l'ancienne table sont détectées
 * dans {@code information_schema} avant de construire l'ordre SQL. Une colonne
 * absente est remplacée par une valeur neutre au lieu de faire échouer le
 * démarrage. Toute anomalie est journalisée sans jamais interrompre le boot —
 * l'application doit démarrer même si la migration ne peut pas s'exécuter.</p>
 *
 * <p>Peut être désactivé via
 * {@code campusops.migration.examens-vers-occupations=false}. S'exécute juste
 * après la reconstruction éventuelle du référentiel ({@code @Order 7}) et avant
 * les réductions de parc de salles ({@code @Order 12/13}), afin que les examens
 * recopiés soient traités comme n'importe quelle autre occupation.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(8)
public class ExamenOccupationMigrationInitializer implements CommandLineRunner {

    /** Ancienne table, propre aux examens (conservée, jamais modifiée). */
    private static final String LEGACY_TABLE = "examens";

    /** Nouvelle table générique, partagée par les trois catégories d'occupation. */
    private static final String TARGET_TABLE = "occupations_supplementaires";

    /** Noms possibles de la colonne de date dans l'ancienne table. */
    private static final List<String> DATE_COLUMN_CANDIDATES =
            List.of("date", "date_examen", "date_occupation", "exam_date");

    /** Colonnes sans lesquelles la recopie n'a aucun sens. */
    private static final List<String> REQUIRED_COLUMNS = List.of("id", "space_id", "time_slot_id");

    private final JdbcTemplate jdbcTemplate;

    @Value("${campusops.migration.examens-vers-occupations:true}")
    private boolean migrationEnabled;

    @Override
    public void run(String... args) {
        if (!migrationEnabled) {
            log.info("[migration-occupations] Désactivée "
                    + "(campusops.migration.examens-vers-occupations=false).");
            return;
        }
        try {
            migrate();
        } catch (Exception e) {
            // Le démarrage ne doit JAMAIS échouer à cause de la migration :
            // l'ancienne table est intacte, la reprise sera possible.
            log.error("[migration-occupations] Migration des examens abandonnée (aucune donnée "
                    + "perdue, l'ancienne table « {} » est intacte) : {}", LEGACY_TABLE, e.getMessage());
        }
    }

    // ----- Migration -----

    private void migrate() {
        if (!tableExists(TARGET_TABLE)) {
            log.warn("[migration-occupations] Table « {} » absente : le schéma n'a pas encore été "
                    + "mis à jour, migration reportée au prochain démarrage.", TARGET_TABLE);
            return;
        }
        if (!tableExists(LEGACY_TABLE)) {
            log.info("[migration-occupations] Aucune ancienne table « {} » : installation neuve, "
                    + "rien à migrer.", LEGACY_TABLE);
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_COLUMNS) {
            if (!columnExists(LEGACY_TABLE, required)) {
                missing.add(required);
            }
        }
        String dateColumn = resolveDateColumn();
        if (dateColumn == null) {
            missing.add("date");
        }
        if (!missing.isEmpty()) {
            log.warn("[migration-occupations] Ancienne table « {} » inattendue (colonnes absentes : "
                    + "{}) : migration ignorée, aucune donnée touchée.", LEGACY_TABLE, missing);
            return;
        }

        long legacyCount = count("SELECT COUNT(*) FROM " + LEGACY_TABLE);
        if (legacyCount == 0) {
            log.info("[migration-occupations] Ancienne table « {} » vide : rien à migrer.", LEGACY_TABLE);
            return;
        }

        long dejaPresents = count("SELECT COUNT(*) FROM " + TARGET_TABLE
                + " WHERE discriminant = 'EXAMEN'");
        int inserted = jdbcTemplate.update(buildInsertSql(dateColumn));
        long sansCreneau = count("SELECT COUNT(*) FROM " + LEGACY_TABLE + " e "
                + "LEFT JOIN time_slots ts ON ts.id = e.time_slot_id WHERE ts.id IS NULL");

        if (inserted == 0) {
            log.info("[migration-occupations] Examens déjà migrés ({} ancienne(s) ligne(s), {} "
                    + "occupation(s) de type examen en place) : rien à faire.",
                    legacyCount, dejaPresents);
            return;
        }
        log.warn("========================================");
        log.warn("[migration-occupations] {} examen(s) recopié(s) de « {} » vers « {} » "
                        + "(ancienne table conservée intacte).",
                inserted, LEGACY_TABLE, TARGET_TABLE);
        if (sansCreneau > 0) {
            log.warn("[migration-occupations] {} ancienne(s) ligne(s) sans créneau horaire valide "
                    + "n'ont PAS été migrées (heures indéterminables) : à ressaisir depuis "
                    + "l'onglet « Planning examens ».", sansCreneau);
        }
        log.warn("========================================");
    }

    // ----- Construction de l'ordre SQL -----

    /**
     * Construit l'{@code INSERT ... SELECT} de recopie. Les heures de l'occupation
     * sont dérivées du créneau officiel de l'examen (le socle générique raisonne
     * sur {@code heure_debut}/{@code heure_fin}, sans jointure). L'intitulé est
     * dérivé de la matière et reste <b>non nominatif</b> (§16). Le {@code NOT
     * EXISTS} sur la clé naturelle garantit l'idempotence.
     */
    private String buildInsertSql(String dateColumn) {
        String date = "e.\"" + dateColumn + "\"";
        String moduleId = legacyColumn("module_id", "NULL");
        return "INSERT INTO " + TARGET_TABLE + " ("
                + "discriminant, type_occupation, intitule, date_occupation, heure_debut, heure_fin, "
                + "space_id, program_id, promotion_id, group_id, academic_year_id, "
                + "responsable, commentaire, actif, created_at, updated_at, "
                + "time_slot_id, module_id, semester_id, session_id) "
                + "SELECT 'EXAMEN', 'EXAMEN', "
                + "       CASE WHEN m.nom IS NULL THEN 'Examen' ELSE 'Examen — ' || m.nom END, "
                + "       " + date + ", ts.heure_debut, ts.heure_fin, "
                + "       e.space_id, " + legacyColumn("program_id", "NULL") + ", "
                + legacyColumn("promotion_id", "NULL") + ", " + legacyColumn("group_id", "NULL") + ", "
                + legacyColumn("academic_year_id", "NULL") + ", "
                + "       NULL, " + legacyColumn("commentaire", "NULL") + ", "
                + "       COALESCE(" + legacyColumn("actif", "TRUE") + ", TRUE), "
                + "       COALESCE(" + legacyColumn("created_at", "NULL")
                + ", CURRENT_TIMESTAMP), COALESCE(" + legacyColumn("updated_at", "NULL")
                + ", CURRENT_TIMESTAMP), "
                + "       e.time_slot_id, " + moduleId + ", "
                + legacyColumn("semester_id", "NULL") + ", " + legacyColumn("session_id", "NULL") + " "
                + "FROM " + LEGACY_TABLE + " e "
                + "JOIN time_slots ts ON ts.id = e.time_slot_id "
                + "LEFT JOIN modules m ON m.id = " + moduleId + " "
                + "WHERE NOT EXISTS ("
                + "  SELECT 1 FROM " + TARGET_TABLE + " o "
                + "  WHERE o.discriminant = 'EXAMEN' "
                + "    AND o.space_id = e.space_id "
                + "    AND o.date_occupation = " + date + " "
                + "    AND o.heure_debut = ts.heure_debut "
                + "    AND o.heure_fin = ts.heure_fin "
                + "    AND (o.module_id = " + moduleId
                + "         OR (o.module_id IS NULL AND " + moduleId + " IS NULL)))";
    }

    /** Référence {@code e.<colonne>} si la colonne existe dans l'ancienne table, sinon {@code fallback}. */
    private String legacyColumn(String column, String fallback) {
        return columnExists(LEGACY_TABLE, column) ? "e." + column : fallback;
    }

    /** Première colonne de date reconnue dans l'ancienne table, ou {@code null}. */
    private String resolveDateColumn() {
        for (String candidate : DATE_COLUMN_CANDIDATES) {
            if (columnExists(LEGACY_TABLE, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // ----- Introspection du schéma -----

    private boolean tableExists(String table) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class, table);
        return found != null && found > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return found != null && found > 0;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value != null ? value : 0L;
    }
}
