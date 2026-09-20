package com.campusops.config;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.enums.SpaceType;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cree un pole d'amphitheatres autonome, independant des blocs pedagogiques
 * (A, B, C, D) : 4 amphitheatres « normaux » (150 places) et 1 amphitheatre
 * central pour les grandes reunions (500 places).
 *
 * <p>Le modele impose qu'un espace appartienne a un etage lui-meme rattache a
 * un batiment (floor_id / building_id NON NULL). Ces amphitheatres « hors bloc »
 * sont donc regroupes dans un batiment dedie « Amphitheatres » (code AMP), sur
 * un unique rez-de-chaussee. Aucune donnee existante n'est modifiee ni
 * supprimee : on reutilise les entites et depots deja en place.</p>
 *
 * <p>Idempotent : le batiment, l'etage et chaque amphitheatre ne sont crees que
 * s'ils n'existent pas deja (verification par code). Le seeder peut donc
 * s'executer a chaque demarrage sans jamais creer de doublon. S'execute apres
 * les initialiseurs Batiment (@Order(2)), Etage (@Order(3)) et Espace
 * (@Order(4)).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(9)
public class StandaloneAmphitheaterInitializer implements CommandLineRunner {

    private static final String BUILDING_CODE = "AMP";
    private static final String BUILDING_NOM = "Amphithéâtres";
    private static final String FLOOR_CODE = "AMP-RDC";

    private static final int NB_AMPHIS_NORMAUX = 4;
    private static final int CAPACITE_NORMALE = 150;
    private static final int CAPACITE_CENTRALE = 500;
    private static final String CODE_CENTRAL = "AMPC";

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;

    /** Modele d'un amphitheatre a creer. */
    private record AmphiTemplate(String nom, String code, int capacite, String description) {
    }

    @Override
    public void run(String... args) {
        Building building = resolveBuilding();
        Floor floor = resolveFloor(building);

        List<Space> aCreer = new ArrayList<>();
        for (AmphiTemplate template : amphiTemplates()) {
            // Idempotence : on ne recree pas un amphi deja present (par code,
            // dans ce batiment). L'admin peut ensuite le renommer/desactiver.
            if (spaceRepository.existsByBuildingAndCode(building.getId(), template.code())) {
                continue;
            }
            aCreer.add(Space.builder()
                    .nom(template.nom())
                    .code(template.code())
                    .type(SpaceType.AMPHITHEATER)
                    .capacite(template.capacite())
                    .description(template.description())
                    .actif(true)
                    .reserve(false)
                    .floor(floor)
                    .build());
        }

        if (aCreer.isEmpty()) {
            log.info("Amphitheatres autonomes deja presents, initialisation ignoree.");
            return;
        }

        spaceRepository.saveAll(aCreer);

        log.info("========================================");
        log.info("{} amphitheatre(s) autonome(s) cree(s) dans le batiment « {} » (code {})",
                aCreer.size(), BUILDING_NOM, BUILDING_CODE);
        log.info("========================================");
    }

    /** Recupere le batiment dedie aux amphitheatres, ou le cree s'il n'existe pas. */
    private Building resolveBuilding() {
        return buildingRepository.findByCode(BUILDING_CODE)
                .orElseGet(() -> buildingRepository.save(Building.builder()
                        .nom(BUILDING_NOM)
                        .code(BUILDING_CODE)
                        .nombreEtages(1)
                        .description("Amphithéâtres autonomes du campus, hors blocs pédagogiques.")
                        .actif(true)
                        .build()));
    }

    /** Recupere l'unique etage du batiment amphi, ou le cree s'il n'existe pas. */
    private Floor resolveFloor(Building building) {
        List<Floor> floors = floorRepository.findByBuildingId(building.getId());
        if (!floors.isEmpty()) {
            return floors.get(0);
        }
        return floorRepository.save(Floor.builder()
                .nom("Rez-de-chaussée")
                .code(FLOOR_CODE)
                .numero(0)
                .description("Rez-de-chaussée du pôle Amphithéâtres")
                .actif(true)
                .building(building)
                .build());
    }

    /** Les 4 amphitheatres normaux + l'amphitheatre central des grandes reunions. */
    private List<AmphiTemplate> amphiTemplates() {
        List<AmphiTemplate> templates = new ArrayList<>();
        for (int i = 1; i <= NB_AMPHIS_NORMAUX; i++) {
            templates.add(new AmphiTemplate(
                    "Amphithéâtre " + i,
                    "AMP" + i,
                    CAPACITE_NORMALE,
                    "Amphithéâtre autonome, hors bloc pédagogique."));
        }
        templates.add(new AmphiTemplate(
                "Amphithéâtre central",
                CODE_CENTRAL,
                CAPACITE_CENTRALE,
                "Amphithéâtre central pour les grandes réunions."));
        return templates;
    }
}
