package com.campusops.department.service;

import com.campusops.department.dto.DepartmentRequestDto;
import com.campusops.department.dto.DepartmentResponseDto;
import com.campusops.department.entity.Department;
import com.campusops.department.mapper.DepartmentMapper;
import com.campusops.department.repository.DepartmentRepository;
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
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;
    private final AccessScopeService accessScope;
    private final ReferentialCascadeService cascade;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public DepartmentResponseDto createDepartment(DepartmentRequestDto request) {
        accessScope.requireAdmin();
        if (departmentRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un département avec le nom " + request.getNom() + " existe déjà");
        }
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "Un département avec le code " + request.getCode() + " existe déjà");
        }

        Department department = departmentMapper.toEntity(request);
        department.setActif(true);

        Department saved = departmentRepository.save(department);
        return departmentMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public DepartmentResponseDto getDepartmentById(Long id) {
        return departmentMapper.toResponseDto(findDepartmentOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponseDto> filterDepartments(Boolean actif) {
        List<Department> departments = (actif != null)
                ? departmentRepository.findByActif(actif)
                : departmentRepository.findAll();
        return departments.stream().map(departmentMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponseDto> searchDepartments(String keyword) {
        return departmentRepository.searchByKeyword(keyword).stream()
                .map(departmentMapper::toResponseDto)
                .toList();
    }

    public DepartmentResponseDto updateDepartment(Long id, DepartmentRequestDto request) {
        accessScope.requireAdmin();
        Department department = findDepartmentOrThrow(id);

        if (departmentRepository.existsByNomAndIdNot(request.getNom(), id)) {
            throw new DuplicateResourceException(
                    "Un département avec le nom " + request.getNom() + " existe déjà");
        }
        if (departmentRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Un département avec le code " + request.getCode() + " existe déjà");
        }

        department.setNom(request.getNom());
        department.setCode(request.getCode());
        department.setDescription(request.getDescription());

        Department updated = departmentRepository.save(department);
        return departmentMapper.toResponseDto(updated);
    }

    /**
     * Desactive un departement (desactivation logique) et propage vers le bas :
     * ses filieres, promotions et groupes deviennent inutilisables pour les
     * nouvelles operations (§3, §15). Aucune donnee historique n'est supprimee ;
     * les emplois du temps, examens et reservations anciens restent intacts.
     */
    public void deactivateDepartment(Long id) {
        accessScope.requireAdmin();
        Department department = findDepartmentOrThrow(id);
        department.setActif(false);
        departmentRepository.save(department);
        cascade.onDepartmentDeactivated(id);
    }

    /**
     * Reactive un departement. Conformement au §16, la reactivation ne propage
     * <b>pas</b> aux filieres : chaque filiere garde son etat individuel (une
     * filiere volontairement desactivee ne doit pas etre reactivee
     * automatiquement). L'administrateur reactive ensuite les filieres voulues.
     */
    public void activateDepartment(Long id) {
        accessScope.requireAdmin();
        Department department = findDepartmentOrThrow(id);
        department.setActif(true);
        departmentRepository.save(department);
        cascade.onDepartmentReactivated(id);
    }

    /** Compteurs d'impact d'une desactivation de departement (§17). */
    @Transactional(readOnly = true)
    public DeactivationImpact getDeactivationImpact(Long id) {
        accessScope.requireAdmin();
        findDepartmentOrThrow(id);
        return cascade.departmentImpact(id);
    }

    /**
     * Compteurs d'impact avant suppression : enfants supprimes en cascade
     * (filieres, promotions, groupes, modules) et usages metier qui la
     * bloquent (seances, emplois du temps, occupations/examens, reservations).
     * Sert a la modale de confirmation cote client (§ preview).
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findDepartmentOrThrow(id));
    }

    /**
     * Supprime un departement. Suppression <b>hierarchique</b> : ses filieres,
     * promotions, groupes et modules sont supprimes en cascade — seulement apres
     * confirmation ({@code cascade == true}) lorsqu'un sous-arbre existe. La
     * suppression est refusee, avec un message metier explicite, si un usage
     * metier (seances, emplois du temps, occupations/examens, reservations)
     * subsiste : il faut d'abord le retirer (« suppression apres modification »).
     * Tout se fait dans une transaction : succes total ou aucun changement.
     */
    public void deleteDepartment(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Department department = findDepartmentOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(department);
        impact.requireConfirmed(cascade);
        deletionExecutor.deleteDepartment(department);
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Département introuvable avec l'id " + id));
    }
}
