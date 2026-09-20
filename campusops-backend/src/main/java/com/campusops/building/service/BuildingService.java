package com.campusops.building.service;

import com.campusops.building.dto.BuildingRequestDto;
import com.campusops.building.dto.BuildingResponseDto;
import com.campusops.building.entity.Building;
import com.campusops.building.mapper.BuildingMapper;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
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
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuildingMapper buildingMapper;
    private final AccessScopeService accessScope;
    private final ReferentialCascadeService cascade;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public BuildingResponseDto createBuilding(BuildingRequestDto request) {
        accessScope.requireAdmin();
        if (buildingRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un bâtiment avec le nom " + request.getNom() + " existe déjà");
        }
        if (buildingRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "Un bâtiment avec le code " + request.getCode() + " existe déjà");
        }

        Building building = buildingMapper.toEntity(request);
        building.setActif(true);

        Building saved = buildingRepository.save(building);
        return buildingMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public BuildingResponseDto getBuildingById(Long id) {
        return buildingMapper.toResponseDto(findBuildingOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<BuildingResponseDto> getAllBuildings() {
        return buildingRepository.findAll().stream()
                .map(buildingMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuildingResponseDto> filterBuildings(Boolean actif) {
        List<Building> buildings = (actif != null)
                ? buildingRepository.findByActif(actif)
                : buildingRepository.findAll();
        return buildings.stream().map(buildingMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BuildingResponseDto> searchBuildings(String keyword) {
        return buildingRepository.searchByKeyword(keyword).stream()
                .map(buildingMapper::toResponseDto)
                .toList();
    }

    public BuildingResponseDto updateBuilding(Long id, BuildingRequestDto request) {
        accessScope.requireAdmin();
        Building building = findBuildingOrThrow(id);

        if (buildingRepository.existsByNomAndIdNot(request.getNom(), id)) {
            throw new DuplicateResourceException(
                    "Un bâtiment avec le nom " + request.getNom() + " existe déjà");
        }
        if (buildingRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Un bâtiment avec le code " + request.getCode() + " existe déjà");
        }

        building.setNom(request.getNom());
        building.setCode(request.getCode());
        building.setDescription(request.getDescription());
        building.setNombreEtages(request.getNombreEtages());

        Building updated = buildingRepository.save(building);
        return buildingMapper.toResponseDto(updated);
    }

    /**
     * Desactive un batiment (desactivation logique) et propage vers le bas :
     * ses etages et ses salles deviennent inutilisables — absents de la
     * recherche d'espaces libres, des reservations, des selects et des nouveaux
     * emplois du temps (§4, §15, §22). Aucune salle n'est supprimee ; les
     * reservations et emplois du temps anciens restent conserves.
     */
    public void deactivateBuilding(Long id) {
        accessScope.requireAdmin();
        Building building = findBuildingOrThrow(id);
        building.setActif(false);
        buildingRepository.save(building);
        cascade.onBuildingDeactivated(id);
    }

    /**
     * Reactive un batiment. Conformement au §16, la reactivation ne propage
     * <b>pas</b> aux etages/salles : chacun garde son etat individuel. Une salle
     * volontairement desactivee ne redevient pas disponible automatiquement.
     */
    public void activateBuilding(Long id) {
        accessScope.requireAdmin();
        Building building = findBuildingOrThrow(id);
        building.setActif(true);
        buildingRepository.save(building);
        cascade.onBuildingReactivated(id);
    }

    /** Compteurs d'impact d'une desactivation de batiment (§17). */
    @Transactional(readOnly = true)
    public DeactivationImpact getDeactivationImpact(Long id) {
        accessScope.requireAdmin();
        findBuildingOrThrow(id);
        return cascade.buildingImpact(id);
    }

    /**
     * Compteurs d'impact avant suppression : etages et salles supprimes en
     * cascade, et usages metier des salles qui la bloquent (seances,
     * reservations, occupations/examens). Sert a la modale de confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findBuildingOrThrow(id));
    }

    /**
     * Supprime un batiment. Suppression <b>hierarchique</b> : ses etages et
     * salles sont supprimes en cascade apres confirmation ({@code cascade ==
     * true}). Refusee, avec message metier, si une salle est encore utilisee
     * (seances, reservations, occupations/examens) : il faut d'abord liberer ces
     * usages. La suppression salle par salle vide au passage {@code space_equipments}.
     */
    public void deleteBuilding(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Building building = findBuildingOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(building);
        impact.requireConfirmed(cascade);
        deletionExecutor.deleteBuilding(building);
    }

    private Building findBuildingOrThrow(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bâtiment introuvable avec l'id " + id));
    }
}
