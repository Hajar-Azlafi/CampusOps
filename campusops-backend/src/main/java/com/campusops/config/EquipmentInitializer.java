package com.campusops.config;

import com.campusops.enums.SpaceType;
import com.campusops.equipment.entity.Equipment;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Initialise le catalogue d'equipements de demonstration au premier demarrage,
 * puis associe des equipements coherents aux espaces selon leur type.
 * L'insertion des equipements n'est effectuee que si la table est vide (idempotent).
 * Tous les equipements restent entierement administrables par l'administrateur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class EquipmentInitializer implements CommandLineRunner {

    private final EquipmentRepository equipmentRepository;
    private final SpaceRepository spaceRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Modele d'un equipement du catalogue a creer. */
    private record EquipmentTemplate(String code, String nom, String description) {
    }

    @Override
    public void run(String... args) {
        removeLegacyOtherEquipment();

        if (equipmentRepository.count() > 0) {
            log.info("Des equipements existent deja, initialisation ignoree.");
            return;
        }

        Map<String, Equipment> catalog = createCatalog();
        associateEquipmentsToSpaces(catalog);
    }

    /**
     * Supprime, de maniere idempotente, l'equipement generique « Autre » (code OTHER)
     * ainsi que ses associations aux espaces. Cet equipement fourre-tout a ete retire
     * du modele : un simple redemarrage le nettoie des donnees existantes.
     */
    private void removeLegacyOtherEquipment() {
        jdbcTemplate.update(
                "DELETE FROM space_equipments WHERE equipment_id IN "
                        + "(SELECT id FROM equipments WHERE code = 'OTHER')");
        int removed = jdbcTemplate.update("DELETE FROM equipments WHERE code = 'OTHER'");
        if (removed > 0) {
            log.info("Equipement generique « Autre » (OTHER) supprime ({} ligne(s)).", removed);
        }
    }


    private Map<String, Equipment> createCatalog() {
        List<EquipmentTemplate> templates = List.of(
                new EquipmentTemplate("PROJECTOR", "Vidéoprojecteur",
                        "Vidéoprojecteur pour la diffusion de supports de cours."),
                new EquipmentTemplate("WHITEBOARD", "Tableau blanc",
                        "Tableau blanc effaçable."),
                new EquipmentTemplate("SMART_BOARD", "Tableau interactif",
                        "Tableau numérique interactif tactile."),
                new EquipmentTemplate("AIR_CONDITIONER", "Climatisation",
                        "Système de climatisation et de chauffage."),
                new EquipmentTemplate("SOUND_SYSTEM", "Sonorisation",
                        "Système de sonorisation et de haut-parleurs."),
                new EquipmentTemplate("WIFI", "Wi-Fi",
                        "Point d'accès réseau sans fil."),
                new EquipmentTemplate("TEACHER_COMPUTER", "Ordinateur enseignant",
                        "Poste informatique dédié à l'enseignant."),
                new EquipmentTemplate("STUDENT_COMPUTERS", "Ordinateurs étudiants",
                        "Postes informatiques à disposition des étudiants."),
                new EquipmentTemplate("PRINTER", "Imprimante",
                        "Imprimante réseau."),
                new EquipmentTemplate("SCANNER", "Scanner",
                        "Scanner de documents."),
                new EquipmentTemplate("TV_SCREEN", "Écran de télévision",
                        "Écran d'affichage grand format."),
                new EquipmentTemplate("CAMERA", "Caméra",
                        "Caméra pour la captation et la visioconférence."),
                new EquipmentTemplate("MICROPHONE", "Microphone",
                        "Microphone pour la prise de son."),
                new EquipmentTemplate("POWER_OUTLETS", "Prises électriques",
                        "Prises électriques à disposition des utilisateurs."),
                new EquipmentTemplate("PMR_ACCESS", "Accès PMR",
                        "Accès adapté aux personnes à mobilité réduite.")
        );

        Map<String, Equipment> catalog = new LinkedHashMap<>();
        List<Equipment> toSave = new ArrayList<>();
        for (EquipmentTemplate template : templates) {
            Equipment equipment = Equipment.builder()
                    .code(template.code())
                    .nom(template.nom())
                    .description(template.description())
                    .actif(true)
                    .build();
            toSave.add(equipment);
        }
        for (Equipment saved : equipmentRepository.saveAll(toSave)) {
            catalog.put(saved.getCode(), saved);
        }

        log.info("{} equipements de demonstration crees avec succes", catalog.size());
        return catalog;
    }

    private void associateEquipmentsToSpaces(Map<String, Equipment> catalog) {
                List<Space> spaces = spaceRepository.findAllWithEquipments();
        if (spaces.isEmpty()) {
            log.info("Aucun espace disponible, association des equipements ignoree.");
            return;
        }

        int associations = 0;
        for (Space space : spaces) {
            List<String> codes = equipmentCodesFor(space.getType());
            for (String code : codes) {
                Equipment equipment = catalog.get(code);
                if (equipment != null && space.getEquipments().add(equipment)) {
                    associations++;
                }
            }
        }

        spaceRepository.saveAll(spaces);

        log.info("========================================");
        log.info("{} associations espace-equipement creees avec succes", associations);
        log.info("========================================");
    }

    /**
     * Retourne les codes des equipements a associer a un espace selon son type,
     * en reproduisant les dotations coherentes du cahier des charges.
     */
    private List<String> equipmentCodesFor(SpaceType type) {
        return switch (type) {
            case COMPUTER_ROOM -> List.of(
                    "STUDENT_COMPUTERS", "TEACHER_COMPUTER", "PROJECTOR", "WHITEBOARD", "WIFI");
            case LABORATORY -> List.of(
                    "PROJECTOR", "WHITEBOARD", "AIR_CONDITIONER", "WIFI");
            case AMPHITHEATER -> List.of(
                    "SOUND_SYSTEM", "MICROPHONE", "CAMERA", "PROJECTOR", "WIFI");
            case CLASSROOM -> List.of(
                    "PROJECTOR", "WHITEBOARD", "WIFI");
            case CONFERENCE_ROOM -> List.of(
                    "PROJECTOR", "SOUND_SYSTEM", "TV_SCREEN", "MICROPHONE", "WIFI");
            case MEETING_ROOM -> List.of(
                    "TV_SCREEN", "WHITEBOARD", "WIFI");
            case MULTIPURPOSE_ROOM -> List.of(
                    "PROJECTOR", "SOUND_SYSTEM", "WHITEBOARD", "WIFI");
            default -> List.of("WIFI");
        };
    }
}
