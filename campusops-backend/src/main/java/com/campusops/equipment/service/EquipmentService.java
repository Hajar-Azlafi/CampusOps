package com.campusops.equipment.service;

import com.campusops.equipment.dto.EquipmentRequestDto;
import com.campusops.equipment.dto.EquipmentResponseDto;
import com.campusops.equipment.entity.Equipment;
import com.campusops.equipment.mapper.EquipmentMapper;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public EquipmentResponseDto createEquipment(EquipmentRequestDto request) {
        accessScope.requireAdmin();
        if (equipmentRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un équipement avec le nom " + request.getNom() + " existe déjà");
        }
        if (equipmentRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "Un équipement avec le code " + request.getCode() + " existe déjà");
        }

        Equipment equipment = equipmentMapper.toEntity(request);
        equipment.setActif(true);

        Equipment saved = equipmentRepository.save(equipment);
        return equipmentMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public EquipmentResponseDto getEquipmentById(Long id) {
        return equipmentMapper.toResponseDto(findEquipmentOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponseDto> filterEquipments(Boolean actif) {
        List<Equipment> equipments = (actif != null)
                ? equipmentRepository.findByActif(actif)
                : equipmentRepository.findAll();
        return equipments.stream().map(equipmentMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponseDto> searchEquipments(String keyword) {
        return equipmentRepository.searchByKeyword(keyword).stream()
                .map(equipmentMapper::toResponseDto)
                .toList();
    }

    public EquipmentResponseDto updateEquipment(Long id, EquipmentRequestDto request) {
        accessScope.requireAdmin();
        Equipment equipment = findEquipmentOrThrow(id);

        if (equipmentRepository.existsByNomAndIdNot(request.getNom(), id)) {
            throw new DuplicateResourceException(
                    "Un équipement avec le nom " + request.getNom() + " existe déjà");
        }
        if (equipmentRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Un équipement avec le code " + request.getCode() + " existe déjà");
        }

        equipment.setNom(request.getNom());
        equipment.setCode(request.getCode());
        equipment.setDescription(request.getDescription());

        Equipment updated = equipmentRepository.save(equipment);
        return equipmentMapper.toResponseDto(updated);
    }

    public void deactivateEquipment(Long id) {
        accessScope.requireAdmin();
        Equipment equipment = findEquipmentOrThrow(id);
        equipment.setActif(false);
        equipmentRepository.save(equipment);
    }

    public void activateEquipment(Long id) {
        accessScope.requireAdmin();
        Equipment equipment = findEquipmentOrThrow(id);
        equipment.setActif(true);
        equipmentRepository.save(equipment);
    }

    /**
     * Compteurs d'impact avant suppression : liste des salles qui referencent
     * cet equipement et dont il sera simplement retire. Association
     * <b>technique</b> : jamais bloquante. Sert a la modale de confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findEquipmentOrThrow(id));
    }

    /**
     * Supprime un equipement. Le lien salle <-> equipement est une association
     * <b>technique</b> (jamais un usage metier bloquant) : l'equipement est
     * d'abord retire des salles qui le possedent (cote proprietaire = Space),
     * puis supprime. Aucune salle n'est supprimee. Le parametre {@code cascade}
     * est sans effet (aucun enfant a supprimer) : il n'existe que pour
     * l'uniformite de l'API de suppression.
     */
    public void deleteEquipment(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Equipment equipment = findEquipmentOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(equipment);
        impact.requireConfirmed(cascade);
        deletionExecutor.deleteEquipment(equipment);
    }

    private Equipment findEquipmentOrThrow(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Équipement introuvable avec l'id " + id));
    }
}
