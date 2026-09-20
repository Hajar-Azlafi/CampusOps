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
import java.util.Comparator;
import java.util.List;

/**
 * Initialise les espaces pedagogiques de demonstration au premier demarrage.
 * L'insertion n'est effectuee que si la table est vide (idempotent).
 * Les espaces sont generes a partir de la structure reelle des batiments et
 * des etages presents en base : aucune donnee n'est codee en dur.
 * Tous les espaces restent entierement administrables par l'administrateur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class SpaceInitializer implements CommandLineRunner {

    private final SpaceRepository spaceRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    /** Modele d'un groupe d'espaces a creer sur un etage donne. */
    private record SpaceTemplate(String nom, SpaceType type, int capacite, int nombre) {
    }

    @Override
    public void run(String... args) {
        if (spaceRepository.count() > 0) {
            log.info("Des espaces existent deja, initialisation ignoree.");
            return;
        }

        List<Building> buildings = buildingRepository.findAll();
        if (buildings.isEmpty()) {
            log.info("Aucun batiment disponible, initialisation des espaces ignoree.");
            return;
        }

        List<Space> spaces = new ArrayList<>();
        for (Building building : buildings) {
            List<Floor> floors = new ArrayList<>(floorRepository.findByBuildingId(building.getId()));
            floors.sort(Comparator.comparing(Floor::getNumero));

            for (Floor floor : floors) {
                int roomIndex = 1;
                for (SpaceTemplate template : templatesFor(floor.getNumero())) {
                    for (int i = 0; i < template.nombre(); i++, roomIndex++) {
                        String code = building.getCode() + floor.getNumero() + roomIndex;
                        spaces.add(Space.builder()
                                .nom(template.nom())
                                .code(code)
                                .type(template.type())
                                .capacite(template.capacite())
                                .description(template.nom() + " - " + floor.getNom()
                                        + " (" + building.getNom() + ")")
                                .actif(true)
                                .reserve(false)
                                .floor(floor)
                                .build());
                    }
                }
            }
        }

        spaceRepository.saveAll(spaces);

        log.info("========================================");
        log.info("{} espaces pedagogiques de demonstration crees avec succes", spaces.size());
        log.info("========================================");
    }

    /**
     * Retourne la composition d'espaces a creer pour un etage donne, selon son
     * numero, en reproduisant la structure fictive du cahier des charges :
     * - rez-de-chaussee (0) : salles informatiques ;
     * - premier etage (1)   : salles de cours ;
     * - deuxieme etage (2)  : salles de cours et laboratoires ;
     * - troisieme etage (3) : amphitheatres, salles de conference et de reunion.
     */
    private List<SpaceTemplate> templatesFor(int numero) {
        return switch (numero) {
            case 0 -> List.of(
                    new SpaceTemplate("Salle informatique", SpaceType.COMPUTER_ROOM, 30, 4)
            );
            case 1 -> List.of(
                    new SpaceTemplate("Salle de cours", SpaceType.CLASSROOM, 40, 5)
            );
            case 2 -> List.of(
                    new SpaceTemplate("Salle de cours", SpaceType.CLASSROOM, 40, 3),
                    new SpaceTemplate("Laboratoire", SpaceType.LABORATORY, 25, 2)
            );
            default -> List.of(
                    new SpaceTemplate("Amphitheatre", SpaceType.AMPHITHEATER, 150, 2),
                    new SpaceTemplate("Salle de conference", SpaceType.CONFERENCE_ROOM, 80, 1),
                    new SpaceTemplate("Salle de reunion", SpaceType.MEETING_ROOM, 20, 2)
            );
        };
    }
}
