package com.campusops.config;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initialise des batiments de demonstration au premier demarrage.
 * L'insertion n'est effectuee que si la table est vide (idempotent).
 * Les donnees restent entierement administrables : l'administrateur peut
 * ensuite ajouter, modifier, activer/desactiver ou supprimer ces exemples.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class BuildingInitializer implements CommandLineRunner {

    private final BuildingRepository buildingRepository;

    @Override
    public void run(String... args) {
        if (buildingRepository.count() > 0) {
            log.info("Des batiments existent deja, initialisation ignoree.");
            return;
        }

        List<Building> buildings = List.of(
                Building.builder()
                        .nom("Bloc A")
                        .code("A")
                        .nombreEtages(3)
                        .description("Salles de cours, salles informatiques, laboratoires.")
                        .actif(true)
                        .build(),
                Building.builder()
                        .nom("Bloc B")
                        .code("B")
                        .nombreEtages(3)
                        .description("Salles de cours, laboratoires scientifiques.")
                        .actif(true)
                        .build(),
                Building.builder()
                        .nom("Bloc C")
                        .code("C")
                        .nombreEtages(3)
                        .description("Salles de cours, enseignements scientifiques.")
                        .actif(true)
                        .build(),
                Building.builder()
                        .nom("Bloc D")
                        .code("D")
                        .nombreEtages(3)
                        .description("Salles de cours, laboratoires, salles spécialisées.")
                        .actif(true)
                        .build()
        );

        buildingRepository.saveAll(buildings);

        log.info("========================================");
        log.info("{} batiments de demonstration crees avec succes", buildings.size());
        log.info("========================================");
    }
}
