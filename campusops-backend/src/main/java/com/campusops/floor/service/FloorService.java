package com.campusops.floor.service;

import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.floor.dto.FloorRequestDto;
import com.campusops.floor.dto.FloorResponseDto;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.mapper.FloorMapper;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.validation.ReferentialCascadeService;
import com.campusops.validation.dto.DeactivationImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final FloorMapper floorMapper;
    private final AccessScopeService accessScope;
    private final ReferentialCascadeService cascade;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public FloorResponseDto createFloor(FloorRequestDto request) {
        accessScope.requireAdmin();
        Building building = findBuildingOrThrow(request.getBuildingId());

        if (!building.isActif()) {
            throw new BadRequestException(
                    "Impossible d'ajouter un étage à un bâtiment inactif");
        }
        if (floorRepository.existsByBuildingIdAndNumero(building.getId(), request.getNumero())) {
            throw new DuplicateResourceException(
                    "Un étage avec le numéro " + request.getNumero()
                            + " existe déjà dans ce bâtiment");
        }
        if (floorRepository.existsByBuildingIdAndCode(building.getId(), request.getCode())) {
            throw new DuplicateResourceException(
                    "Un étage avec le code " + request.getCode()
                            + " existe déjà dans ce bâtiment");
        }

        Floor floor = floorMapper.toEntity(request);
        floor.setBuilding(building);
        floor.setActif(true);

        Floor saved = floorRepository.save(floor);
        return floorMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public FloorResponseDto getFloorById(Long id) {
        return floorMapper.toResponseDto(findFloorOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<FloorResponseDto> filterFloors(Boolean actif) {
        List<Floor> floors = (actif != null)
                ? floorRepository.findByActif(actif)
                : floorRepository.findAll();
        return floors.stream().map(floorMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FloorResponseDto> getFloorsByBuilding(Long buildingId, Boolean actif) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Bâtiment introuvable avec l'id " + buildingId);
        }
        List<Floor> floors = (actif != null)
                ? floorRepository.findByBuildingIdAndActif(buildingId, actif)
                : floorRepository.findByBuildingId(buildingId);
        return floors.stream()
                .map(floorMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FloorResponseDto> searchFloors(String keyword) {
        return floorRepository.searchByKeyword(keyword).stream()
                .map(floorMapper::toResponseDto)
                .toList();
    }

    public FloorResponseDto updateFloor(Long id, FloorRequestDto request) {
        accessScope.requireAdmin();
        Floor floor = findFloorOrThrow(id);
        Building building = findBuildingOrThrow(request.getBuildingId());

        // Symetrie avec createFloor (§16) : on ne rattache pas un etage a un
        // batiment inactif. Un rattachement inchange reste autorise.
        boolean buildingChanged = floor.getBuilding() == null
                || !building.getId().equals(floor.getBuilding().getId());
        if (buildingChanged && !building.isActif()) {
            throw new BadRequestException(
                    "Impossible de rattacher un étage à un bâtiment inactif");
        }
        if (floorRepository.existsByBuildingIdAndNumeroAndIdNot(
                building.getId(), request.getNumero(), id)) {
            throw new DuplicateResourceException(
                    "Un étage avec le numéro " + request.getNumero()
                            + " existe déjà dans ce bâtiment");
        }
        if (floorRepository.existsByBuildingIdAndCodeAndIdNot(
                building.getId(), request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Un étage avec le code " + request.getCode()
                            + " existe déjà dans ce bâtiment");
        }

        floor.setNom(request.getNom());
        floor.setCode(request.getCode());
        floor.setNumero(request.getNumero());
        floor.setDescription(request.getDescription());
        floor.setBuilding(building);

        Floor updated = floorRepository.save(floor);
        return floorMapper.toResponseDto(updated);
    }

    /**
     * Desactive un etage (desactivation logique) et propage vers le bas : ses
     * salles deviennent inutilisables — absentes de la recherche, des
     * reservations, des selects et des nouveaux emplois du temps (§4, §5, §22).
     * Aucune salle n'est supprimee.
     */
    public void deactivateFloor(Long id) {
        accessScope.requireAdmin();
        Floor floor = findFloorOrThrow(id);
        floor.setActif(false);
        floorRepository.save(floor);
        cascade.onFloorDeactivated(id);
    }

    /**
     * Reactive un etage. Interdit tant que le batiment parent est inactif (§16),
     * et ne propage pas aux salles (elles gardent leur etat individuel).
     */
    public void activateFloor(Long id) {
        accessScope.requireAdmin();
        Floor floor = findFloorOrThrow(id);
        if (!floor.getBuilding().isActif()) {
            throw new BadRequestException(
                    "Impossible de réactiver cet étage : son bâtiment « "
                            + floor.getBuilding().getNom() + " » est désactivé. "
                            + "Réactivez d'abord le bâtiment.");
        }
        floor.setActif(true);
        floorRepository.save(floor);
        cascade.onFloorReactivated(id);
    }

    /** Compteurs d'impact d'une desactivation d'etage (§17). */
    @Transactional(readOnly = true)
    public DeactivationImpact getDeactivationImpact(Long id) {
        accessScope.requireAdmin();
        findFloorOrThrow(id);
        return cascade.floorImpact(id);
    }

    /**
     * Compteurs d'impact avant suppression : salles supprimees en cascade et
     * usages metier des salles qui la bloquent (seances, reservations,
     * occupations/examens). Sert a la modale de confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findFloorOrThrow(id));
    }

    /**
     * Supprime un etage. Suppression <b>hierarchique</b> : ses salles sont
     * supprimees en cascade apres confirmation ({@code cascade == true}).
     * Refusee, avec message metier, si une salle est encore utilisee (seances,
     * reservations, occupations/examens) : il faut d'abord liberer ces usages.
     * La suppression salle par salle vide au passage {@code space_equipments}.
     */
    public void deleteFloor(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Floor floor = findFloorOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(floor);
        impact.requireConfirmed(cascade);
        deletionExecutor.deleteFloor(floor);
    }

    private Floor findFloorOrThrow(Long id) {
        return floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Étage introuvable avec l'id " + id));
    }

    private Building findBuildingOrThrow(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bâtiment introuvable avec l'id " + id));
    }
}
