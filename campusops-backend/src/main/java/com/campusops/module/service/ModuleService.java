package com.campusops.module.service;

import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.module.dto.ModuleRequestDto;
import com.campusops.module.dto.ModuleResponseDto;
import com.campusops.module.entity.Module;
import com.campusops.module.mapper.ModuleMapper;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Gestion des <b>modules d'enseignement</b> (cahier des charges §8-§13).
 *
 * <p>Un module est ancré sur un <b>contexte pédagogique</b> = filière + semestre.
 * L'ADMIN gère tous les modules ; un <b>responsable pédagogique</b> ne gère que
 * ceux de <b>ses</b> filières (§12-§13). Le contrôle de périmètre s'appuie sur
 * {@link AccessScopeService#assertProgramAccessible(Long)} :</p>
 * <ul>
 *   <li>ADMIN : accès à toute filière ;</li>
 *   <li>RESPONSABLE_PEDAGOGIQUE : uniquement ses filières (sinon 403) ;</li>
 *   <li>autres rôles : aucun accès en écriture (403).</li>
 * </ul>
 *
 * <p>Les listes sont toujours triées (filière, semestre, nom) — jamais dans un
 * ordre aléatoire (§3).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final ModuleMapper moduleMapper;
    private final ProgramRepository programRepository;
    private final SemesterRepository semesterRepository;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public ModuleResponseDto createModule(ModuleRequestDto request) {
        // Périmètre : ADMIN partout, RP uniquement sa filière, autres 403.
        accessScope.assertProgramAccessible(request.getProgramId());

        Program program = findProgramOrThrow(request.getProgramId());
        Semester semester = findSemesterOrThrow(request.getSemesterId());
        // §6/§20 : on ne cree pas de module sous une filiere inactive (ou dont
        // le departement est inactif), ni sur un semestre inutilisable.
        com.campusops.validation.ReferentialStatus.requireUsable(program);
        com.campusops.validation.ReferentialStatus.requireUsable(semester);
        String nom = request.getNom().trim();
        assertNoDuplicate(program.getId(), semester.getId(), nom, null);

        Module module = Module.builder()
                .nom(nom)
                .code(normalizeCode(request.getCode()))
                .description(normalizeDescription(request.getDescription()))
                .program(program)
                .semester(semester)
                .actif(true)
                .build();
        return moduleMapper.toResponseDto(moduleRepository.save(module));
    }

    @Transactional(readOnly = true)
    public ModuleResponseDto getModuleById(Long id) {
        Module module = findModuleOrThrow(id);
        accessScope.assertProgramAccessible(module.getProgram().getId());
        return moduleMapper.toResponseDto(module);
    }

    /**
     * Liste des modules filtrée par contexte (§10-§11) et bornée au périmètre du
     * demandeur.
     *
     * <ul>
     *   <li><b>programId fourni</b> : la filière doit être accessible (ADMIN, ou
     *       RP propriétaire) ; on renvoie ses modules, éventuellement restreints
     *       au semestre et/ou au statut actif.</li>
     *   <li><b>programId absent</b> : ADMIN → tous les modules ; RP → tous les
     *       modules de ses filières ; autres → liste vide.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<ModuleResponseDto> listModules(Long programId, Long semesterId, Boolean actif) {
        List<Module> modules = (programId != null)
                ? modulesForProgram(programId, semesterId, actif)
                : modulesForScope(actif);
        return modules.stream().map(moduleMapper::toResponseDto).toList();
    }

    public ModuleResponseDto updateModule(Long id, ModuleRequestDto request) {
        Module module = findModuleOrThrow(id);
        // L'ancien contexte doit rester dans le périmètre (un RP ne touche pas un
        // module hors de ses filières)...
        accessScope.assertProgramAccessible(module.getProgram().getId());
        // ...et le nouveau contexte demandé aussi (pas de transfert hors périmètre).
        accessScope.assertProgramAccessible(request.getProgramId());

        Program program = findProgramOrThrow(request.getProgramId());
        Semester semester = findSemesterOrThrow(request.getSemesterId());
        String nom = request.getNom().trim();
        assertNoDuplicate(program.getId(), semester.getId(), nom, id);

        // §16 : rattachement inchange autorise, mais pas de deplacement vers une
        // filiere/semestre inutilisable.
        boolean programChanged = module.getProgram() == null
                || !program.getId().equals(module.getProgram().getId());
        if (programChanged) {
            com.campusops.validation.ReferentialStatus.requireUsable(program);
        }
        boolean semesterChanged = module.getSemester() == null
                || !semester.getId().equals(module.getSemester().getId());
        if (semesterChanged) {
            com.campusops.validation.ReferentialStatus.requireUsable(semester);
        }

        module.setNom(nom);
        module.setCode(normalizeCode(request.getCode()));
        module.setDescription(normalizeDescription(request.getDescription()));
        module.setProgram(program);
        module.setSemester(semester);
        return moduleMapper.toResponseDto(moduleRepository.save(module));
    }

    /**
     * Desactive un module (desactivation logique). Il n'est plus propose pour de
     * nouvelles seances d'emploi du temps (§20) ; les EDT existants qui le
     * referencent restent intacts (historique).
     */
    public void deactivateModule(Long id) {
        Module module = findModuleOrThrow(id);
        accessScope.assertProgramAccessible(module.getProgram().getId());
        module.setActif(false);
        moduleRepository.save(module);
    }

    /**
     * Reactive un module. Interdit tant que sa filiere parente n'est pas
     * utilisable (§16) : filiere active et departement actif. On ne reactive pas
     * un module rattache a une filiere desactivee.
     */
    public void activateModule(Long id) {
        Module module = findModuleOrThrow(id);
        accessScope.assertProgramAccessible(module.getProgram().getId());
        if (!com.campusops.validation.ReferentialStatus.usable(module.getProgram())) {
            throw new com.campusops.exception.BadRequestException(
                    "Impossible de réactiver ce module : sa filière « "
                            + safeName(module.getProgram() == null
                                    ? null : module.getProgram().getNom())
                            + " » est désactivée. Réactivez d'abord la filière.");
        }
        module.setActif(true);
        moduleRepository.save(module);
    }

    /**
     * Compteurs d'impact avant suppression d'un module : usages metier qui la
     * bloquent (seances d'emploi du temps, examens). Le module « Java » utilise
     * dans un EDT est ainsi bloque tant que l'EDT n'a pas ete modifie. Sert a la
     * modale de confirmation. Perimetre : ADMIN, ou RP proprietaire de la filiere.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        Module module = findModuleOrThrow(id);
        accessScope.assertProgramAccessible(module.getProgram().getId());
        return deletionAnalyzer.analyze(module);
    }

    /**
     * Supprime un module. Aucun enfant en cascade. La suppression est refusee,
     * avec message metier, tant qu'une seance ou un examen le reference : il faut
     * d'abord retirer le module de l'emploi du temps ou du planning d'examens
     * (« suppression apres modification »). Le parametre {@code cascade} est sans
     * effet (aucun enfant) : il n'existe que pour l'uniformite de l'API.
     */
    public void deleteModule(Long id, boolean cascade) {
        Module module = findModuleOrThrow(id);
        accessScope.assertProgramAccessible(module.getProgram().getId());
        DeletionImpact impact = deletionAnalyzer.analyze(module);
        impact.requireConfirmed(cascade);
        moduleRepository.delete(module);
    }

    // ----- Helpers de lecture -----

    private List<Module> modulesForProgram(Long programId, Long semesterId, Boolean actif) {
        accessScope.assertProgramAccessible(programId);
        if (semesterId != null) {
            return (actif != null)
                    ? moduleRepository.findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
                            programId, semesterId, actif)
                    : moduleRepository.findByProgramIdAndSemesterIdOrderByNomAsc(programId, semesterId);
        }
        List<Module> byProgram = moduleRepository.findByProgramIdOrderBySemester_OrdreAscNomAsc(programId);
        return applyActifFilter(byProgram, actif);
    }

    private List<Module> modulesForScope(Boolean actif) {
        List<Module> modules;
        if (accessScope.isAdmin()) {
            modules = moduleRepository.findAllByOrderByProgram_NomAscSemester_OrdreAscNomAsc();
        } else if (accessScope.isResponsablePedagogique()) {
            Set<Long> scope = accessScope.myProgramIdSet();
            modules = scope.isEmpty()
                    ? List.of()
                    : moduleRepository.findByProgramIdInOrderByProgram_NomAscSemester_OrdreAscNomAsc(scope);
        } else {
            modules = List.of();
        }
        return applyActifFilter(modules, actif);
    }

    private List<Module> applyActifFilter(List<Module> modules, Boolean actif) {
        if (actif == null) {
            return modules;
        }
        return modules.stream().filter(m -> m.isActif() == actif).toList();
    }

    // ----- Helpers de normalisation / validation -----

    private void assertNoDuplicate(Long programId, Long semesterId, String nom, Long excludeId) {
        moduleRepository.findByProgramIdAndSemesterIdAndNomIgnoreCase(programId, semesterId, nom)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Un module « " + nom + " » existe déjà pour ce contexte "
                                    + "(filière + semestre).");
                });
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private static String safeName(String nom) {
        return (nom == null || nom.isBlank()) ? "sans nom" : nom;
    }

    private Module findModuleOrThrow(Long id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Module introuvable avec l'id " + id));
    }

    private Program findProgramOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + id));
    }

    private Semester findSemesterOrThrow(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semestre introuvable avec l'id " + id));
    }
}
