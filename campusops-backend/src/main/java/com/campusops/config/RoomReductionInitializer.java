package com.campusops.config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Réduit le parc de salles pour que la <b>rareté</b> soit réelle : c'est la
 * raison d'être de l'application (trouver une salle libre quand elles sont peu
 * nombreuses et souvent occupées). Avec trop de salles, presque tout est libre
 * en permanence et l'outil perd son intérêt.
 *
 * <p><b>Ce qui est retiré</b> (une seule fois, idempotent) :
 * <ul>
 *   <li>le bâtiment « Nouveau Bâtiment » (code {@code NB}) avec tous ses étages
 *       et toutes ses salles ;</li>
 *   <li>les amphithéâtres autonomes surnuméraires : on ne conserve que
 *       {@code AMP1}..{@code AMP4} et l'amphithéâtre central {@code AMPC}
 *       (les grandes réunions) — {@code AMP5} et {@code AMP6} sont supprimés.</li>
 * </ul>
 *
 * <p><b>Suppression FK-safe</b> : pour chaque salle retirée, on supprime d'abord
 * ses dépendances (liens équipements, séances d'emploi du temps, réservations,
 * occupations supplémentaires — examens, soutenances et autres) puis la salle ;
 * ensuite les étages puis le bâtiment {@code NB}.
 * Rien d'autre n'est touché (autres bâtiments, comptes, référentiels).
 *
 * <p><b>Idempotent</b> : si {@code NB} est déjà absent et qu'aucun amphi
 * surnuméraire ne subsiste, l'exécution ne fait rien. Peut être désactivé via
 * {@code campusops.seed.reduce-rooms=false}. S'exécute après la création des
 * bâtiments/étages/salles ({@code @Order 2/3/4}), des amphis autonomes
 * ({@code @Order 9}) et de l'amorçage des emplois du temps ({@code @Order 11}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(12)
public class RoomReductionInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Codes des amphithéâtres autonomes à conserver (les autres sont retirés). */
    private static final List<String> AMPHIS_A_CONSERVER =
            List.of("AMP1", "AMP2", "AMP3", "AMP4", "AMPC");

    @Value("${campusops.seed.reduce-rooms:true}")
    private boolean reductionEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!reductionEnabled) {
            log.info("[reduce-rooms] Réduction du parc de salles désactivée "
                    + "(campusops.seed.reduce-rooms=false).");
            return;
        }

        // 1) Salles ciblées : toutes celles du bâtiment NB + les amphis autonomes
        //    hors liste de conservation.
        List<Long> targetSpaceIds = jdbcTemplate.queryForList(
                "SELECT s.id FROM spaces s "
                        + "JOIN floors f ON s.floor_id = f.id "
                        + "JOIN buildings b ON f.building_id = b.id "
                        + "WHERE b.code = 'NB' "
                        + "   OR (b.code = 'AMP' AND s.code NOT IN ('AMP1','AMP2','AMP3','AMP4','AMPC'))",
                Long.class);

        boolean nbPresent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM buildings WHERE code = 'NB'", Integer.class) > 0;

        if (targetSpaceIds.isEmpty() && !nbPresent) {
            log.info("[reduce-rooms] Parc déjà réduit (NB absent, amphis conformes) : rien à faire.");
            return;
        }

        int removedSpaces = deleteSpaces(targetSpaceIds);
        int removedFloors = 0;
        int removedBuildings = 0;
        if (nbPresent) {
            // Les salles du bâtiment NB ont été retirées ci-dessus ; on peut donc
            // supprimer ses étages puis le bâtiment lui-même.
            removedFloors = jdbcTemplate.update(
                    "DELETE FROM floors WHERE building_id IN (SELECT id FROM buildings WHERE code = 'NB')");
            removedBuildings = jdbcTemplate.update("DELETE FROM buildings WHERE code = 'NB'");
        }

        log.warn("========================================");
        log.warn("[reduce-rooms] Parc de salles réduit : {} salle(s) supprimée(s), "
                        + "{} étage(s), {} bâtiment(s) (NB). Amphis conservés : {}.",
                removedSpaces, removedFloors, removedBuildings, AMPHIS_A_CONSERVER);
        log.warn("========================================");
    }

    /**
     * Supprime un ensemble de salles et toutes leurs dépendances, dans l'ordre
     * des clés étrangères (enfants → parent). Renvoie le nombre de salles
     * effectivement supprimées.
     *
     * <p>Depuis la restructuration « Occupation supplémentaire », les examens ne
     * sont plus une table à part : ils partagent
     * {@code occupations_supplementaires} avec les soutenances et les autres
     * occupations. Une seule suppression couvre donc les trois catégories.
     * L'ancienne table {@code examens}, conservée par la migration non
     * destructive, est purgée séparément et seulement si elle existe encore.</p>
     */
    private int deleteSpaces(List<Long> spaceIds) {
        if (spaceIds.isEmpty()) {
            return 0;
        }
        String in = inClause(spaceIds);
        jdbcTemplate.update("DELETE FROM space_equipments WHERE space_id IN (" + in + ")");
        int seances = jdbcTemplate.update("DELETE FROM schedules WHERE space_id IN (" + in + ")");
        int reservations = jdbcTemplate.update("DELETE FROM reservations WHERE space_id IN (" + in + ")");
        int occupations = deleteOccupations(in);
        int spaces = jdbcTemplate.update("DELETE FROM spaces WHERE id IN (" + in + ")");
        log.warn("[reduce-rooms] Dépendances retirées avec les salles : {} séance(s), "
                + "{} réservation(s), {} occupation(s) supplémentaire(s).",
                seances, reservations, occupations);
        return spaces;
    }

    /**
     * Supprime les occupations supplémentaires des salles ciblées, puis les
     * lignes résiduelles de l'ancienne table {@code examens} si elle subsiste
     * (installation migrée). Chaque suppression est indépendante : une table
     * absente n'interrompt pas la réduction du parc.
     */
    private int deleteOccupations(String in) {
        int total = deleteIfTableExists("occupations_supplementaires", in);
        total += deleteIfTableExists("examens", in);
        return total;
    }

    private int deleteIfTableExists(String table, String in) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_name = ?",
                    Integer.class, table);
            if (exists == null || exists == 0) {
                return 0;
            }
            return jdbcTemplate.update("DELETE FROM " + table + " WHERE space_id IN (" + in + ")");
        } catch (Exception e) {
            log.warn("[reduce-rooms] Suppression dans « {} » ignorée : {}", table, e.getMessage());
            return 0;
        }
    }

    /** Construit une liste de valeurs numériques sûre pour une clause IN (ids déjà typés Long). */
    private String inClause(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i).longValue());
        }
        return sb.toString();
    }
}
