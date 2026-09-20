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
 * Seeder/Initializer that permanently removes all spaces located on floors
 * whose {@code numero} = 2 across all buildings. It deletes dependent rows
 * first (space_equipments, schedules, reservations, occupations
 * supplémentaires) to remain FK-safe.
 *
 * <p>Depuis la restructuration « Occupation supplémentaire », les examens
 * partagent la table {@code occupations_supplementaires} avec les soutenances et
 * les autres occupations : une seule suppression couvre les trois catégories.
 * L'ancienne table {@code examens} n'est purgée que si elle subsiste encore.</p>
 *
 * Controlled by the property {@code campusops.seed.remove-second-floor-spaces}
 * (defaults to false).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(13)
public class SecondFloorRemovalInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${campusops.seed.remove-second-floor-spaces:false}")
    private boolean removalEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!removalEnabled) {
            log.info("[remove-2nd-floor] Désactivé (campusops.seed.remove-second-floor-spaces=false)");
            return;
        }

        List<Long> targetSpaceIds = jdbcTemplate.queryForList(
                "SELECT s.id FROM spaces s JOIN floors f ON s.floor_id = f.id WHERE f.numero = 2",
                Long.class);

        if (targetSpaceIds.isEmpty()) {
            log.info("[remove-2nd-floor] Aucune salle à supprimer (pas d'étage numéro=2 trouvé).");
            return;
        }

        int deleted = deleteSpaces(targetSpaceIds);
        log.warn("[remove-2nd-floor] Suppression terminée : {} salle(s) supprimée(s).", deleted);
    }

    private int deleteSpaces(List<Long> spaceIds) {
        if (spaceIds.isEmpty()) return 0;
        String in = inClause(spaceIds);
        jdbcTemplate.update("DELETE FROM space_equipments WHERE space_id IN (" + in + ")");
        int seances = jdbcTemplate.update("DELETE FROM schedules WHERE space_id IN (" + in + ")");
        int reservations = jdbcTemplate.update("DELETE FROM reservations WHERE space_id IN (" + in + ")");
        int occupations = deleteIfTableExists("occupations_supplementaires", in)
                + deleteIfTableExists("examens", in);
        int spaces = jdbcTemplate.update("DELETE FROM spaces WHERE id IN (" + in + ")");
        log.warn("[remove-2nd-floor] Dépendances retirées : {} séance(s), {} réservation(s), "
                + "{} occupation(s) supplémentaire(s).", seances, reservations, occupations);
        return spaces;
    }

    /** Supprime dans une table optionnelle (absente sur une installation neuve ou migrée). */
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
            log.warn("[remove-2nd-floor] Suppression dans « {} » ignorée : {}", table, e.getMessage());
            return 0;
        }
    }

    private String inClause(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i).longValue());
        }
        return sb.toString();
    }
}
