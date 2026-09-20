package com.campusops.config;

import com.campusops.enums.LabSpeciality;
import com.campusops.enums.SpaceType;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Rétro-remplit la <b>spécialité</b> des laboratoires existants (cahier des
 * charges §7 : distinguer les laboratoires par spécialité) sans jamais supprimer
 * ni recréer un espace — les espaces sont conservés tels quels (exigence
 * explicite : « ne supprime pas les espaces existants »).
 *
 * <p><b>Portée</b> : seuls les espaces de type {@link SpaceType#LABORATORY} sont
 * spécialisés. Les salles informatiques ({@link SpaceType#COMPUTER_ROOM}) restent
 * de simples salles informatiques <b>sans spécialité</b> (exigence : « les salles
 * infos restent des salles informatiques seulement ») : toute spécialité qui leur
 * aurait été affectée par une version antérieure est <b>effacée</b> ici. Les
 * autres types (salles de cours, amphithéâtres, salles de réunion/conférence…)
 * n'ont pas de spécialité et restent inchangés ({@code speciality = null}).</p>
 *
 * <p><b>Répartition déterministe</b> (jamais aléatoire, donc reproductible) : les
 * laboratoires tournent sur les spécialités scientifiques et techniques
 * ({@code PHYSIQUE}, {@code CHIMIE}, {@code ELECTRICITE}, {@code ELECTRONIQUE},
 * {@code BIOLOGIE}, {@code MECANIQUE}). L'ordre stable (tri par identifiant) et le
 * tour de rôle par position garantissent un résultat identique à chaque
 * exécution.</p>
 *
 * <p><b>Idempotent &amp; non destructif</b> : seuls les laboratoires dont la
 * spécialité est <b>nulle</b> sont renseignés ; une spécialité déjà définie
 * (rétro-remplissage précédent ou choix manuel de l'administrateur) n'est jamais
 * écrasée. S'exécute tardivement ({@code @Order(14)}), après la création des
 * espaces ({@code @Order(4)}) et des amphithéâtres autonomes ({@code @Order(9)}).
 * Désactivable via {@code campusops.seed.lab-speciality=false}.</p>
 */
@Component
@Order(14)
@RequiredArgsConstructor
@Slf4j
public class LabSpecialityBackfillInitializer implements CommandLineRunner {

    private final SpaceRepository spaceRepository;

    @Value("${campusops.seed.lab-speciality:true}")
    private boolean enabled;

    /** Spécialités des laboratoires (type LABORATORY), en tour de rôle. */
    private static final List<LabSpeciality> LABORATORY_SPECIALITIES = List.of(
            LabSpeciality.PHYSIQUE,
            LabSpeciality.CHIMIE,
            LabSpeciality.ELECTRICITE,
            LabSpeciality.ELECTRONIQUE,
            LabSpeciality.BIOLOGIE,
            LabSpeciality.MECANIQUE
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Rétro-remplissage des spécialités de laboratoire désactivé "
                    + "(campusops.seed.lab-speciality=false).");
            return;
        }

        int computerRoomsCleared = clearComputerRoomSpecialities();
        int laboratories = backfill(SpaceType.LABORATORY, LABORATORY_SPECIALITIES);

        if (computerRoomsCleared + laboratories > 0) {
            log.info("Spécialités : {} laboratoire(s) rétro-rempli(s), {} salle(s) informatique(s) "
                    + "remise(s) sans spécialité (espaces conservés, aucune suppression).",
                    laboratories, computerRoomsCleared);
        } else {
            log.info("Spécialités de laboratoire : rien à faire "
                    + "(laboratoires déjà renseignés, salles informatiques sans spécialité).");
        }
    }

    /**
     * Efface la spécialité des salles informatiques : une salle informatique
     * reste une simple salle informatique, sans spécialité. Ne touche que les
     * {@link SpaceType#COMPUTER_ROOM} dont la spécialité est non nulle.
     *
     * @return le nombre de salles informatiques remises à {@code null}.
     */
    private int clearComputerRoomSpecialities() {
        int cleared = 0;
        for (Space space : spaceRepository.findByType(SpaceType.COMPUTER_ROOM)) {
            if (space.getSpeciality() != null) {
                space.setSpeciality(null);
                spaceRepository.save(space);
                cleared++;
            }
        }
        return cleared;
    }

    /**
     * Renseigne la spécialité des laboratoires dont la spécialité est nulle, par
     * tour de rôle déterministe sur {@code specialities}. L'ordre est stabilisé
     * par tri sur l'identifiant, et l'index de position (incrémenté pour chaque
     * espace, y compris ceux déjà renseignés) garantit une affectation
     * reproductible et insensible aux exécutions partielles.
     *
     * @return le nombre d'espaces effectivement mis à jour.
     */
    private int backfill(SpaceType type, List<LabSpeciality> specialities) {
        List<Space> spaces = spaceRepository.findByType(type);
        spaces.sort(Comparator.comparing(Space::getId));

        int updated = 0;
        int position = 0;
        for (Space space : spaces) {
            LabSpeciality target = specialities.get(position % specialities.size());
            position++;
            if (space.getSpeciality() != null) {
                continue; // déjà renseignée (rétro-remplissage précédent ou choix manuel) : on n'écrase pas.
            }
            space.setSpeciality(target);
            spaceRepository.save(space);
            updated++;
        }
        return updated;
    }
}
