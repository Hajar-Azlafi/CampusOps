package com.campusops.space.service;

import com.campusops.enums.LabSpeciality;
import com.campusops.enums.SpaceType;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.space.dto.SpaceRequestDto;
import com.campusops.space.dto.SpaceResponseDto;
import com.campusops.space.entity.Space;
import com.campusops.space.mapper.SpaceMapper;
import com.campusops.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final FloorRepository floorRepository;
    private final SpaceMapper spaceMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public SpaceResponseDto createSpace(SpaceRequestDto request) {
        accessScope.requireAdmin();
        Floor floor = findFloorOrThrow(request.getFloorId());
        validateFloorAndBuildingActive(floor);

        Long buildingId = floor.getBuilding().getId();
        if (spaceRepository.existsByBuildingAndCode(buildingId, request.getCode())) {
            throw new DuplicateResourceException(
                    "Un espace avec le code " + request.getCode()
                            + " existe déjà dans ce bâtiment");
        }

        Space space = new Space();
        space.setNom(request.getNom());
        space.setCode(request.getCode());
        space.setType(request.getType());
        space.setCapacite(request.getCapacite());
        space.setDescription(request.getDescription());
        space.setFloor(floor);
        space.setSpeciality(specialityForType(request.getType(), request.getSpeciality()));
        space.setActif(true);
        space.setReserve(false);

        Space saved = spaceRepository.save(space);
        return spaceMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public SpaceResponseDto getSpaceById(Long id) {
        return spaceMapper.toResponseDto(findSpaceOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SpaceResponseDto> filterSpaces(Boolean actif) {
        List<Space> spaces = (actif != null)
                ? spaceRepository.findByActif(actif)
                : spaceRepository.findAll();
        return spaces.stream().map(spaceMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceResponseDto> getSpacesByFloor(Long floorId, Boolean actif) {
        if (!floorRepository.existsById(floorId)) {
            throw new ResourceNotFoundException("Étage introuvable avec l'id " + floorId);
        }
        List<Space> spaces = (actif != null)
                ? spaceRepository.findByFloorIdAndActif(floorId, actif)
                : spaceRepository.findByFloorId(floorId);
        return spaces.stream()
                .map(spaceMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceResponseDto> getSpacesByBuilding(Long buildingId, Boolean actif) {
        List<Space> spaces = (actif != null)
                ? spaceRepository.findByFloorBuildingIdAndActif(buildingId, actif)
                : spaceRepository.findByFloorBuildingId(buildingId);
        return spaces.stream()
                .map(spaceMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceResponseDto> getSpacesByType(SpaceType type, Boolean actif) {
        List<Space> spaces = (actif != null)
                ? spaceRepository.findByTypeAndActif(type, actif)
                : spaceRepository.findByType(type);
        return spaces.stream()
                .map(spaceMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceResponseDto> searchSpaces(String keyword) {
        List<Space> found = spaceRepository.searchByKeyword(keyword);
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return found.stream()
                .filter(s -> {
                    if (!s.isReserve()) return true;
                    if (k.isEmpty()) return false;
                    String code = s.getCode() == null ? "" : s.getCode().trim().toLowerCase();
                    String nom = s.getNom() == null ? "" : s.getNom().trim().toLowerCase();
                    return k.equals(code) || k.equals(nom);
                })
                .map(spaceMapper::toResponseDto)
                .toList();
    }

    public SpaceResponseDto updateSpace(Long id, SpaceRequestDto request) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(id);
        Floor floor = findFloorOrThrow(request.getFloorId());
        validateFloorAndBuildingActive(floor);

        Long buildingId = floor.getBuilding().getId();
        if (spaceRepository.existsByBuildingAndCodeAndIdNot(buildingId, request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Un espace avec le code " + request.getCode()
                            + " existe déjà dans ce bâtiment");
        }

        space.setNom(request.getNom());
        space.setCode(request.getCode());
        space.setType(request.getType());
        space.setSpeciality(specialityForType(request.getType(), request.getSpeciality()));
        space.setCapacite(request.getCapacite());
        space.setDescription(request.getDescription());
        space.setFloor(floor);

        Space updated = spaceRepository.save(space);
        return spaceMapper.toResponseDto(updated);
    }

    /**
     * Desactive une salle (desactivation logique, §4/§5). Elle devient
     * immediatement inutilisable : absente de la recherche d'espaces libres, non
     * reservable, non proposee dans les selects, exclue des nouveaux emplois du
     * temps / examens / occupations. Aucune reservation ni EDT ancien n'est
     * supprime. C'est une feuille : pas de cascade descendante.
     */
    public void deactivateSpace(Long id) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(id);
        space.setActif(false);
        spaceRepository.save(space);
    }

    /**
     * Reactive une salle. Interdit tant que son etage ou son batiment parent est
     * inactif (§16) : une salle ne peut redevenir utilisable dans un bloc/etage
     * lui-meme desactive. L'administrateur reactive d'abord le parent.
     */
    public void activateSpace(Long id) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(id);
        Floor floor = space.getFloor();
        if (floor != null && floor.getBuilding() != null && !floor.getBuilding().isActif()) {
            throw new BadRequestException(
                    "Impossible de réactiver cette salle : son bâtiment « "
                            + floor.getBuilding().getNom() + " » est désactivé. "
                            + "Réactivez d'abord le bâtiment.");
        }
        if (floor != null && !floor.isActif()) {
            throw new BadRequestException(
                    "Impossible de réactiver cette salle : son étage « "
                            + floor.getNom() + " » est désactivé. "
                            + "Réactivez d'abord l'étage.");
        }
        space.setActif(true);
        spaceRepository.save(space);
    }

    /**
     * Compteurs d'impact avant suppression d'une salle : usages metier qui la
     * bloquent (seances, reservations, occupations/examens). La salle « A12 »
     * utilisee dans 12 seances / 3 reservations / 1 examen est ainsi bloquee.
     * Sert a la modale de confirmation. Preferer la <b>desactivation</b> pour une
     * salle porteuse d'historique.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findSpaceOrThrow(id));
    }

    /**
     * Supprime une salle. Feuille des espaces physiques : aucun enfant en
     * cascade. Le lien salle <-> equipement (technique) est vide au passage
     * (space_equipments). La suppression est refusee, avec message metier, tant
     * qu'un usage (seances, reservations, occupations/examens) la reference : il
     * faut d'abord liberer ces usages. Le parametre {@code cascade} est sans
     * effet (aucun enfant) : il n'existe que pour l'uniformite de l'API.
     */
    public void deleteSpace(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(space);
        impact.requireConfirmed(cascade);
        spaceRepository.delete(space);
    }

    /**
     * Seuls les laboratoires portent une spécialité. Pour tout autre type — en
     * particulier une salle informatique, qui reste une simple salle
     * informatique — la spécialité est forcée à {@code null}, quel que soit ce
     * qu'envoie l'appelant.
     */
    private LabSpeciality specialityForType(SpaceType type, LabSpeciality speciality) {
        return type == SpaceType.LABORATORY ? speciality : null;
    }

    private void validateFloorAndBuildingActive(Floor floor) {
        if (!floor.getBuilding().isActif()) {
            throw new BadRequestException(
                    "Impossible d'ajouter un espace à un bâtiment inactif");
        }
        if (!floor.isActif()) {
            throw new BadRequestException(
                    "Impossible d'ajouter un espace à un étage inactif");
        }
    }

    private Space findSpaceOrThrow(Long id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    private Floor findFloorOrThrow(Long id) {
        return floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Étage introuvable avec l'id " + id));
    }
}
