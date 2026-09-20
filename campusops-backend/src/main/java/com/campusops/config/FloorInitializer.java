package com.campusops.config;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Initialise des etages de demonstration au premier demarrage.
 * L'insertion n'est effectuee que si la table est vide (idempotent).
 * Pour chaque batiment existant, un etage est cree pour chaque niveau
 * (de 0 = rez-de-chaussee jusqu'a nombreEtages - 1).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class FloorInitializer implements CommandLineRunner {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;

    @Override
    public void run(String... args) {
        if (floorRepository.count() > 0) {
            log.info("Des etages existent deja, initialisation ignoree.");
            return;
        }

        List<Building> buildings = buildingRepository.findAll();
        if (buildings.isEmpty()) {
            log.info("Aucun batiment disponible, initialisation des etages ignoree.");
            return;
        }

        List<Floor> floors = new ArrayList<>();
        for (Building building : buildings) {
            int niveaux = building.getNombreEtages() != null ? building.getNombreEtages() : 0;
            for (int numero = 0; numero < niveaux; numero++) {
                floors.add(Floor.builder()
                        .nom(nomEtage(numero))
                        .code(building.getCode() + "-" + codeSuffixe(numero))
                        .numero(numero)
                        .description(nomEtage(numero) + " du " + building.getNom())
                        .actif(true)
                        .building(building)
                        .build());
            }
        }

        floorRepository.saveAll(floors);

        log.info("========================================");
        log.info("{} etages de demonstration crees avec succes", floors.size());
        log.info("========================================");
    }

    private String nomEtage(int numero) {
        return switch (numero) {
            case 0 -> "Rez-de-chaussée";
            case 1 -> "1er étage";
            default -> numero + "ème étage";
        };
    }

    private String codeSuffixe(int numero) {
        return numero == 0 ? "RDC" : String.valueOf(numero);
    }
}
