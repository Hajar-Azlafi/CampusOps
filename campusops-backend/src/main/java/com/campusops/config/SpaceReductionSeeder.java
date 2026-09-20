package com.campusops.config;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.SpaceType;
import com.campusops.exam.entity.Examen;
import com.campusops.exam.repository.ExamenRepository;
import com.campusops.occupation.entity.OccupationSupplementaire;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.floor.entity.Floor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Seeder utilitaire pour reduire le parc de salles : remplace les references
 * aux espaces a supprimer par des espaces restants, en evitant les conflits.
 * <p>
 * Mode d'emploi : cette classe est idempotente. Elle est conservative : si aucune
 * salle de remplacement libre n'est trouvee pour un evenement, la reference est
 * laissée en l'etat et un avertissement est journalise.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class SpaceReductionSeeder implements CommandLineRunner {

    @Value("${campusops.seed.reduce-rooms:true}")
    private boolean reductionEnabled;

    private final BuildingRepository buildingRepository;
    private final com.campusops.floor.repository.FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;
    private final ExamenRepository examenRepository;
    /**
     * Vue polymorphe sur TOUTES les occupations supplémentaires (examens,
     * soutenances, autres) : sert à vérifier qu'une salle candidate est
     * réellement libre et qu'une salle retirée n'est plus référencée.
     */
    private final OccupationSupplementaireRepository occupationRepository;

    // --- Configuration: modifier si besoin ---
    private static final Set<String> BLOCKS_TO_REMOVE = Set.of("C", "D");
    private static final Set<String> BLOCKS_TO_KEEP = Set.of("A", "B");
    private static final Set<String> AMPHIS_TO_KEEP = Set.of("AMP1", "AMP2", "AMP3", "AMPC");
    private static final String AMPHI_CENTRAL_CODE = "AMPC";

    @Override
    @Transactional
    public void run(String... args) {
        if (!reductionEnabled) {
            log.info("[reduce-rooms] SpaceReductionSeeder désactivé (campusops.seed.reduce-rooms=false).");
            return;
        }

        log.info("SpaceReductionSeeder: démarrage");

        List<Building> allBuildings = buildingRepository.findAll();

        // Detecter les buildings cibles a supprimer (C, D) par heuristique sur le code/nom
        Set<Long> buildingIdsToRemove = allBuildings.stream()
                .filter(b -> matchesBlockToRemove(b))
                .map(Building::getId)
                .collect(Collectors.toSet());

        if (buildingIdsToRemove.isEmpty()) {
            log.info("Aucun bâtiment correspondant aux blocs à supprimer trouvé. Rien à faire.");
            return;
        }

        log.info("Bâtiments marqués pour suppression : {}",
                allBuildings.stream().filter(b -> buildingIdsToRemove.contains(b.getId()))
                        .map(Building::getCode).collect(Collectors.toList()));

        // Réserver l'amphi central pour événements : marquer AMPC en reserve=true
        spaceRepository.findAll().stream()
            .filter(Space::isActif)
            .filter(s -> AMPHI_CENTRAL_CODE.equalsIgnoreCase(s.getCode()))
            .findFirst()
            .ifPresent(ampc -> {
                if (!ampc.isReserve()) {
                ampc.setReserve(true);
                spaceRepository.save(ampc);
                log.info("Amphithéâtre central {} marqué comme réservé pour événements.", ampc.getCode());
                }
            });

        // Rassemble les espaces pouvant servir de remplacements : blocs A/B + amphitheatres conserves,
        // mais EXCLURE l'amphi central (réservé) pour ne pas l'utiliser comme pool de remplacement.
        List<Space> replacementPool = spaceRepository.findAll().stream()
            .filter(Space::isActif)
            .filter(s -> !s.isReserve())
            .filter(s -> {
                // Exclure explicitement les espaces qui seront supprimés (2e étage des blocs A/B)
                if (s.getFloor() != null && Integer.valueOf(2).equals(s.getFloor().getNumero())) {
                return false;
                }
                String bcode = Optional.ofNullable(s.getFloor())
                    .map(f -> f.getBuilding())
                    .map(Building::getCode)
                    .orElse("");
                boolean inKeepBlock = BLOCKS_TO_KEEP.stream().anyMatch(k -> k.equalsIgnoreCase(bcode));
                boolean isKeptAmphi = AMPHIS_TO_KEEP.contains(s.getCode()) && !AMPHI_CENTRAL_CODE.equals(s.getCode());
                return inKeepBlock || isKeptAmphi;
            })
            .collect(Collectors.toList());

        // Group replacements by type for faster lookup
        Map<SpaceType, List<Space>> replacementsByType = replacementPool.stream()
                .collect(Collectors.groupingBy(Space::getType, Collectors.toList()));

        // Trouve les espaces a eliminer : appartenant aux buildings cibles
        // + amphitheatres hors liste + 2e étage des blocs A/B
        // 1) Espaces dans buildings ciblés
        List<Space> spacesInRemovedBuildings = spaceRepository.findAll().stream()
            .filter(s -> {
                Building b = s.getFloor().getBuilding();
                return b != null && buildingIdsToRemove.contains(b.getId());
            })
            .collect(Collectors.toList());

        // 2) Amphithéâtres hors liste
        List<Space> extraAmphis = spaceRepository.findAll().stream()
            .filter(s -> s.getType() == SpaceType.AMPHITHEATER && !AMPHIS_TO_KEEP.contains(s.getCode()))
            .collect(Collectors.toList());

        // 3) 2e étages des blocs A et B
        List<Floor> floorsToRemove = buildingRepository.findAll().stream()
            .filter(b -> BLOCKS_TO_KEEP.stream().anyMatch(k -> k.equalsIgnoreCase(b.getCode())))
            .flatMap(b -> floorRepository.findByBuildingId(b.getId()).stream())
            .filter(f -> Integer.valueOf(2).equals(f.getNumero()))
            .collect(Collectors.toList());

        List<Space> spacesOnSecondFloorAB = floorsToRemove.stream()
            .flatMap(f -> spaceRepository.findByFloorId(f.getId()).stream())
            .collect(Collectors.toList());

        List<Space> spacesToRemove = new ArrayList<>();
        spacesToRemove.addAll(spacesInRemovedBuildings);
        spacesToRemove.addAll(extraAmphis);
        spacesToRemove.addAll(spacesOnSecondFloorAB);

        // Deduplicate
        spacesToRemove = spacesToRemove.stream().distinct().collect(Collectors.toList());

        log.info("Espaces candidats à remplacer : {}", spacesToRemove.stream().map(Space::getCode).collect(Collectors.toList()));

        // Replacement loop
        for (Space old : spacesToRemove) {
            SpaceType type = old.getType();
            List<Space> candidates = replacementsByType.getOrDefault(type, Collections.emptyList());
            if (candidates.isEmpty()) {
                log.warn("Aucun espace de remplacement disponible pour le type {} (espace {}). Ignoré.", type, old.getCode());
                continue;
            }

            // (1) schedules
            List<Schedule> schedules = scheduleRepository.findBySpaceId(old.getId());
            for (Schedule s : schedules) {
                boolean moved = tryMoveSchedule(s, candidates);
                if (!moved) {
                    log.warn("Impossible de déplacer la séance id={} (espace {}) : aucun remplacement libre trouvé.", s.getId(), old.getCode());
                }
            }

            // (2) reservations
            List<Reservation> reservations = reservationRepository.findBySpaceId(old.getId());
            for (Reservation r : reservations) {
                boolean moved = tryMoveReservation(r, candidates);
                if (!moved) {
                    log.warn("Impossible de déplacer la réservation id={} (espace {}) : aucun remplacement libre trouvé.", r.getId(), old.getCode());
                }
            }

            // (3) examens
            List<Examen> examens = examenRepository.findAll().stream()
                    .filter(e -> e.getSpace() != null && e.getSpace().getId().equals(old.getId()))
                    .collect(Collectors.toList());
            for (Examen ex : examens) {
                boolean moved = tryMoveExamen(ex, candidates);
                if (!moved) {
                    log.warn("Impossible de déplacer l'examen id={} (espace {}) : aucun remplacement libre trouvé.", ex.getId(), old.getCode());
                }
            }

            // Tenter de supprimer l'espace si plus aucune reference n'y pointe.
            // Les occupations supplémentaires sont interrogées globalement
            // (examens + soutenances + autres) : une seule d'entre elles suffit à
            // retenir la salle.
            boolean stillUsed = scheduleRepository.existsBySpaceId(old.getId())
                    || reservationRepository.existsBySpaceId(old.getId())
                    || occupationRepository.existsBySpaceId(old.getId());
            if (!stillUsed) {
                try {
                    spaceRepository.delete(old);
                    log.info("Espace {} supprime (id={}).", old.getCode(), old.getId());
                } catch (Exception ex) {
                    log.warn("Echec suppression espace {} : {}", old.getCode(), ex.getMessage());
                }
            } else {
                log.warn("Espace {} non supprime parce qu'il reste des references.", old.getCode());
            }
        }

        // --- Après les réaffectations : remplir quelques amphithéâtres (hors AMPC)
        List<Space> amphisToFill = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceType.AMPHITHEATER)
                .filter(s -> !AMPHI_CENTRAL_CODE.equalsIgnoreCase(s.getCode()))
                .filter(Space::isActif)
                .collect(Collectors.toList());

        if (!amphisToFill.isEmpty()) {
            // Récupérer des séances sans salle pour les affecter
            List<Schedule> withoutSpace = scheduleRepository.findAll().stream()
                    .filter(sc -> sc.getSpace() == null)
                    .limit(amphisToFill.size() * 2)
                    .collect(Collectors.toList());
            int assigned = 0;
            for (int i = 0; i < withoutSpace.size(); i++) {
                Schedule sc = withoutSpace.get(i);
                Space target = amphisToFill.get(i % amphisToFill.size());
                // vérifier conflit simple
                boolean conflict = scheduleRepository.existsSpaceConflict(
                    sc.getAcademicYear().getId(), sc.getSemester().getId(), sc.getJour(), sc.getTimeSlot().getHeureDebut(), sc.getTimeSlot().getHeureFin(), target.getId());
                if (!conflict) {
                    sc.setSpace(target);
                    scheduleRepository.save(sc);
                    assigned++;
                }
                if (assigned >= amphisToFill.size()) break;
            }
            log.info("Affectées {} séances sans salle vers des amphithéâtres.", assigned);
        }

        // --- Modifier les types d'espaces restants pour couvrir tous les SpaceType
        Set<SpaceType> existingTypes = spaceRepository.findAll().stream()
                .map(Space::getType)
                .collect(Collectors.toSet());
        List<SpaceType> allTypes = List.of(SpaceType.values());
        List<Space> candidatesForTypeChange = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .filter(s -> !AMPHI_CENTRAL_CODE.equalsIgnoreCase(s.getCode()))
                .collect(Collectors.toList());
        int idx = 0;
        for (SpaceType t : allTypes) {
            if (!existingTypes.contains(t)) {
                if (idx >= candidatesForTypeChange.size()) break;
                Space s = candidatesForTypeChange.get(idx++);
                s.setType(t);
                spaceRepository.save(s);
                log.info("Espace {} (id={}) changé en type {}.", s.getCode(), s.getId(), t);
            }
        }

        log.info("SpaceReductionSeeder: terminé.");
    }

    private boolean matchesBlockToRemove(Building b) {
        if (b == null) return false;
        String code = Optional.ofNullable(b.getCode()).orElse("").trim();
        String name = Optional.ofNullable(b.getNom()).orElse("").toLowerCase();
        for (String blk : BLOCKS_TO_REMOVE) {
            if (code.equalsIgnoreCase(blk) || code.equalsIgnoreCase("BLOC-" + blk) || name.contains("bloc " + blk.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean tryMoveSchedule(Schedule s, List<Space> candidates) {
        Long yearId = s.getAcademicYear() != null ? s.getAcademicYear().getId() : null;
        Long semId = s.getSemester() != null ? s.getSemester().getId() : null;
        LocalTime debut = s.getTimeSlot().getHeureDebut();
        LocalTime fin = s.getTimeSlot().getHeureFin();
        for (Space cand : candidates) {
            // Skip candidate if it no longer exists
            if (!spaceRepository.existsById(cand.getId())) continue;
            boolean conflict = scheduleRepository.existsSpaceConflictExcludingId(
                    yearId, semId, s.getJour(), debut, fin, cand.getId(), s.getId());
            if (!conflict) {
                s.setSpace(cand);
                scheduleRepository.save(s);
                log.info("Séance id={} déplacée vers {}.", s.getId(), cand.getCode());
                return true;
            }
        }
        return false;
    }

    private boolean tryMoveReservation(Reservation r, List<Space> candidates) {
        LocalDate date = r.getDate();
        LocalTime debut = r.getHeureDebut();
        LocalTime fin = r.getHeureFin();
        List<ReservationStatus> blocking = List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED);
        for (Space cand : candidates) {
            if (!spaceRepository.existsById(cand.getId())) continue;
            List<Reservation> conflicts = reservationRepository.findConflicting(cand.getId(), date, debut, fin, blocking);
            if (conflicts.isEmpty()) {
                r.setSpace(cand);
                reservationRepository.save(r);
                log.info("Réservation id={} déplacée vers {}.", r.getId(), cand.getCode());
                return true;
            }
        }
        return false;
    }

    /**
     * Déplace un examen vers une salle de remplacement réellement libre : le
     * contrôle porte sur TOUTES les occupations supplémentaires de la salle
     * candidate (examens, soutenances, autres), pas seulement sur les examens.
     */
    private boolean tryMoveExamen(Examen ex, List<Space> candidates) {
        LocalDate date = ex.getDate();
        LocalTime debut = ex.getTimeSlot().getHeureDebut();
        LocalTime fin = ex.getTimeSlot().getHeureFin();
        for (Space cand : candidates) {
            if (!spaceRepository.existsById(cand.getId())) continue;
            List<OccupationSupplementaire> conflicts = occupationRepository.findConflictingForSpace(
                    cand.getId(), date, debut, fin, ex.getId());
            if (conflicts.isEmpty()) {
                ex.setSpace(cand);
                examenRepository.save(ex);
                log.info("Examen id={} déplacé vers {}.", ex.getId(), cand.getCode());
                return true;
            }
        }
        return false;
    }
}
