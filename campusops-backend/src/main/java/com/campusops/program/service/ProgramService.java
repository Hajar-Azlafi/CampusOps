package com.campusops.program.service;

import com.campusops.department.entity.Department;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.program.dto.ProgramRequestDto;
import com.campusops.program.dto.ProgramResponseDto;
import com.campusops.program.entity.Program;
import com.campusops.program.mapper.ProgramMapper;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.validation.ReferentialCascadeService;
import com.campusops.validation.dto.DeactivationImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final LevelRepository levelRepository;
    private final ProgramMapper programMapper;
    private final AccessScopeService accessScope;
    private final UserRepository userRepository;
    private final ReferentialCascadeService cascade;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public ProgramResponseDto createProgram(ProgramRequestDto request) {
        accessScope.requireAdmin();
        Department department = findDepartmentOrThrow(request.getDepartmentId());

        if (!department.isActif()) {
            throw new BadRequestException(
                    "Impossible d'ajouter une filière à un département inactif.");
        }
        if (isDuplicateName(request.getNom(), request.getLevelId(), null)) {
            throw new DuplicateResourceException(
                    "Une filière avec le nom « " + request.getNom()
                            + " » existe déjà dans ce cycle.");
        }
        if (programRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "Une filière avec le code « " + request.getCode() + " » existe déjà.");
        }

        Program program = programMapper.toEntity(request);
        program.setDepartment(department);
        program.setLevel(resolveLevel(request.getLevelId()));
        program.setActif(true);

        Program saved = programRepository.save(program);
        return programMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ProgramResponseDto getProgramById(Long id) {
        Program program = findProgramOrThrow(id);
        accessScope.assertProgramAccessible(program.getId());
        return programMapper.toResponseDto(program);
    }

    /**
     * Liste les filieres selon le filtre de statut demande (§18) :
     * <ul>
     *   <li>{@code actif == null} : toutes (ecran d'administration « Tous ») ;</li>
     *   <li>{@code actif == true} : filieres actives (les lignes anterieures a
     *       l'ajout de la colonne, valeur NULL, sont traitees comme actives) —
     *       c'est le jeu destine aux <b>selects operationnels</b> ;</li>
     *   <li>{@code actif == false} : filieres desactivees.</li>
     * </ul>
     * Le perimetre du responsable pedagogique est ensuite applique.
     */
    @Transactional(readOnly = true)
    public List<ProgramResponseDto> filterPrograms(Boolean actif) {
        List<Program> programs;
        if (actif == null) {
            programs = programRepository.findAll();
        } else if (actif) {
            programs = programRepository.findAllActive();
        } else {
            programs = programRepository.findAllInactive();
        }
        if (!accessScope.isAdmin()) {
            Set<Long> mine = accessScope.myProgramIdSet();
            programs = programs.stream().filter(p -> mine.contains(p.getId())).toList();
        }
        return programs.stream().map(programMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramResponseDto> getProgramsByDepartment(Long departmentId, Boolean actif) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Département introuvable avec l'id " + departmentId);
        }
        List<Program> programs;
        if (actif == null) {
            programs = programRepository.findByDepartmentId(departmentId);
        } else if (actif) {
            programs = programRepository.findActiveByDepartmentId(departmentId);
        } else {
            programs = programRepository.findInactiveByDepartmentId(departmentId);
        }
        if (!accessScope.isAdmin()) {
            Set<Long> mine = accessScope.myProgramIdSet();
            programs = programs.stream()
                    .filter(p -> mine.contains(p.getId()))
                    .toList();
        }
        return programs.stream()
                .map(programMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramResponseDto> searchPrograms(String keyword) {
        List<Program> programs = programRepository.searchByKeyword(keyword);
        if (!accessScope.isAdmin()) {
            Set<Long> mine = accessScope.myProgramIdSet();
            programs = programs.stream()
                    .filter(p -> mine.contains(p.getId()))
                    .toList();
        }
        return programs.stream()
                .map(programMapper::toResponseDto)
                .toList();
    }

    public ProgramResponseDto updateProgram(Long id, ProgramRequestDto request) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(id);
        Department department = findDepartmentOrThrow(request.getDepartmentId());

        // §16 : on n'edite pas une filiere en la rattachant a un departement
        // inactif. Le rattachement inchange reste autorise (edition de champs).
        boolean departmentChanged = program.getDepartment() == null
                || !department.getId().equals(program.getDepartment().getId());
        if (departmentChanged && !department.isActif()) {
            throw new BadRequestException(
                    "Impossible de rattacher une filière à un département inactif.");
        }
        if (isDuplicateName(request.getNom(), request.getLevelId(), id)) {
            throw new DuplicateResourceException(
                    "Une filière avec le nom « " + request.getNom()
                            + " » existe déjà dans ce cycle.");
        }
        if (programRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "Une filière avec le code « " + request.getCode() + " » existe déjà.");
        }

        program.setNom(request.getNom());
        program.setCode(request.getCode());
        program.setDescription(request.getDescription());
        program.setDepartment(department);
        program.setLevel(resolveLevel(request.getLevelId()));

        Program updated = programRepository.save(program);
        return programMapper.toResponseDto(updated);
    }

    /**
     * Desactive une filiere (desactivation logique) et propage vers le bas :
     * ses promotions et groupes deviennent inutilisables pour les nouvelles
     * operations (§3, §6, §15). Aucune donnee historique n'est supprimee.
     */
    public void deactivateProgram(Long id) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(id);
        program.setActif(false);
        programRepository.save(program);
        cascade.onProgramDeactivated(id);
    }

    /**
     * Reactive une filiere. Conformement au §16, la reactivation ne propage
     * <b>pas</b> aux promotions/groupes (ils gardent leur etat individuel) et
     * elle est interdite tant que le departement parent est inactif.
     */
    public void activateProgram(Long id) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(id);
        if (!program.getDepartment().isActif()) {
            throw new BadRequestException(
                    "Impossible de réactiver cette filière : son département « "
                            + program.getDepartment().getNom() + " » est désactivé. "
                            + "Réactivez d'abord le département.");
        }
        program.setActif(true);
        programRepository.save(program);
        cascade.onProgramReactivated(id);
    }

    /** Compteurs d'impact d'une desactivation de filiere (§17). */
    @Transactional(readOnly = true)
    public DeactivationImpact getDeactivationImpact(Long id) {
        accessScope.requireAdmin();
        findProgramOrThrow(id);
        return cascade.programImpact(id);
    }

    /**
     * Compteurs d'impact avant suppression : promotions, groupes et modules
     * supprimes en cascade, et usages metier qui la bloquent. Sert a la modale
     * de confirmation cote client (§ preview).
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findProgramOrThrow(id));
    }

    /**
     * Supprime une filiere. Suppression <b>hierarchique</b> : ses promotions,
     * groupes et modules sont supprimes en cascade apres confirmation
     * ({@code cascade == true}). Refusee, avec message metier, si un usage
     * (seances, emplois du temps, occupations/examens, reservations) subsiste.
     */
    public void deleteProgram(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(program);
        impact.requireConfirmed(cascade);
        deletionExecutor.deleteProgram(program);
    }

    /**
     * Affecte (ou retire) le responsable pedagogique d'une filiere. Reserve a
     * l'administrateur. Un {@code userId} nul retire le responsable actuel.
     * L'utilisateur designe doit exister, posseder le role
     * RESPONSABLE_PEDAGOGIQUE et etre <b>actif</b> (§10 : un compte desactive
     * n'est plus propose pour un nouvel usage).
     */
    public ProgramResponseDto assignResponsable(Long programId, Long userId) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(programId);
        if (userId == null) {
            program.setResponsable(null);
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Utilisateur introuvable avec l'id " + userId));
            if (user.getRole() != Role.RESPONSABLE_PEDAGOGIQUE) {
                throw new BadRequestException(
                        "Seul un utilisateur ayant le rôle « Responsable pédagogique » "
                                + "peut être affecté à une filière.");
            }
            if (!user.isActive()) {
                throw new BadRequestException(
                        "Impossible d'affecter un compte désactivé comme responsable "
                                + "pédagogique. Réactivez le compte au préalable.");
            }
            program.setResponsable(user);
        }
        Program saved = programRepository.save(program);
        return programMapper.toResponseDto(saved);
    }

    /**
     * Liste les filieres dont l'utilisateur donne est responsable pedagogique.
     * Reserve a l'administrateur (consultation du perimetre d'un RP depuis
     * l'ecran d'administration des responsables).
     */
    @Transactional(readOnly = true)
    public List<ProgramResponseDto> getProgramsByResponsable(Long userId) {
        accessScope.requireAdmin();
        return programRepository.findByResponsableId(userId).stream()
                .map(programMapper::toResponseDto)
                .toList();
    }

    /**
     * Verifie l'unicite du nom de filiere. L'unicite s'applique au sein d'un
     * meme cycle/niveau : une filiere de meme nom est autorisee dans des cycles
     * differents. Sans niveau, on retombe sur une unicite parmi les filieres
     * sans niveau. {@code currentId} est exclu (mise a jour).
     */
    private boolean isDuplicateName(String nom, Long levelId, Long currentId) {
        if (levelId == null) {
            return currentId == null
                    ? programRepository.existsByNom(nom)
                    : programRepository.existsByNomAndIdNot(nom, currentId);
        }
        return currentId == null
                ? programRepository.existsByNomAndLevelId(nom, levelId)
                : programRepository.existsByNomAndLevelIdAndIdNot(nom, levelId, currentId);
    }

    private Level resolveLevel(Long levelId) {
        if (levelId == null) {
            return null;
        }
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + levelId));
    }

    private Program findProgramOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + id));
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Département introuvable avec l'id " + id));
    }
}
