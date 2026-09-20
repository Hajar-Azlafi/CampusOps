package com.campusops.equipment.service;

import com.campusops.equipment.dto.EquipmentResponseDto;
import com.campusops.equipment.entity.Equipment;
import com.campusops.equipment.mapper.EquipmentMapper;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SpaceEquipmentService {

    private final SpaceRepository spaceRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final AccessScopeService accessScope;

    @Transactional(readOnly = true)
    public List<EquipmentResponseDto> getEquipmentsOfSpace(Long spaceId) {
        Space space = findSpaceOrThrow(spaceId);
        return space.getEquipments().stream()
                .sorted(Comparator.comparing(Equipment::getNom))
                .map(equipmentMapper::toResponseDto)
                .toList();
    }

    public List<EquipmentResponseDto> assignEquipments(Long spaceId, List<Long> equipmentIds) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(spaceId);
        for (Long equipmentId : equipmentIds) {
            Equipment equipment = findEquipmentOrThrow(equipmentId);
            // Un equipement desactive (retire du parc / hors service) n'est plus
            // associable a une salle : la garde rejette tout appel direct qui
            // contournerait le selecteur frontend actif-seul (§9/§12). Le detachement
            // (removeEquipment) reste au contraire toujours autorise.
            ReferentialStatus.requireUsable(equipment);
            space.getEquipments().add(equipment);
        }
        Space saved = spaceRepository.save(space);
        return saved.getEquipments().stream()
                .sorted(Comparator.comparing(Equipment::getNom))
                .map(equipmentMapper::toResponseDto)
                .toList();
    }

    public void removeEquipment(Long spaceId, Long equipmentId) {
        accessScope.requireAdmin();
        Space space = findSpaceOrThrow(spaceId);
        Equipment equipment = findEquipmentOrThrow(equipmentId);
        if (!space.getEquipments().remove(equipment)) {
            throw new ResourceNotFoundException(
                    "L'equipement " + equipmentId + " n'est pas associe a cet espace");
        }
        spaceRepository.save(space);
    }

    private Space findSpaceOrThrow(Long id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    private Equipment findEquipmentOrThrow(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Équipement introuvable avec l'id " + id));
    }
}
