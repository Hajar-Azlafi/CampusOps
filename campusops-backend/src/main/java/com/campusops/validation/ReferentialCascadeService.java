package com.campusops.validation;

import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.validation.dto.DeactivationImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propagation <b>DESCENDANTE</b> du drapeau ACTIF / INACTIF et calcul de
 * l'impact d'une desactivation (cahier §3, §4, §15, §16, §17).
 *
 * <p><b>Principe.</b> Desactiver un element rend ses descendants inutilisables.
 * Pour que la coherence soit garantie meme apres coup (tri, historique,
 * requetes directes), on propage le drapeau vers le bas :
 * <pre>
 *   Departement -> Filieres -> Promotions -> Groupes
 *                            -> Modules
 *   Filiere     -> Promotions -> Groupes
 *               -> Modules
 *   Promotion   -> Groupes
 *   Batiment    -> Etages -> Salles
 *   Etage       -> Salles
 * </pre>
 * On ne <b>flippe que le drapeau</b> ({@code actif = false}) : aucune donnee
 * n'est supprimee, les reservations / emplois du temps / examens / occupations
 * anciens restent intacts (§2, §4, §5, §19).
 *
 * <p><b>Propagation de la reactivation.</b> Lorsque le parent est reactive,
 * l'ensemble de la hierarchie sous-jacente est reactive en cascade pour
 * remettre le referentiel dans un etat coherent : un departement reactive
 * reactive ses filieres, promotions, groupes et modules ; un batiment reactive
 * ses etages et salles ; etc.
 *
 * <p>Injecte uniquement des repositories (aucun service metier) : pas de cycle
 * de dependances. Les boucles portent sur des volumes de campus (faibles) et
 * conservent {@code @UpdateTimestamp}. Idempotent : un descendant deja inactif
 * est ignore.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReferentialCascadeService {

    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;
    private final ModuleRepository moduleRepository;

    /* ==================================================================
     *  CASCADE A LA DESACTIVATION
     * ================================================================== */

    /** Departement desactive : filieres, promotions, groupes et modules descendants desactives. */
    public void onDepartmentDeactivated(Long departmentId) {
        for (Program program : programRepository.findByDepartmentId(departmentId)) {
            if (program.isActif()) {
                program.setActif(false);
                programRepository.save(program);
            }
        }
        deactivatePromotions(promotionRepository.findByProgram_Department_Id(departmentId));
        deactivateGroups(groupRepository.findByPromotion_Program_Department_Id(departmentId));
        deactivateModules(moduleRepository.findByProgram_Department_Id(departmentId));
    }

    /** Filiere desactivee : promotions, groupes et modules descendants desactives. */
    public void onProgramDeactivated(Long programId) {
        deactivatePromotions(promotionRepository.findByProgramId(programId));
        deactivateGroups(groupRepository.findByPromotion_Program_Id(programId));
        deactivateModules(moduleRepository.findByProgramId(programId));
    }

    /** Promotion desactivee : groupes descendants desactives. */
    public void onPromotionDeactivated(Long promotionId) {
        deactivateGroups(groupRepository.findByPromotionId(promotionId));
    }

    /** Batiment desactive : etages et salles descendants desactives. */
    public void onBuildingDeactivated(Long buildingId) {
        for (Floor floor : floorRepository.findByBuildingId(buildingId)) {
            if (floor.isActif()) {
                floor.setActif(false);
                floorRepository.save(floor);
            }
        }
        deactivateSpaces(spaceRepository.findByFloorBuildingId(buildingId));
    }

    /** Etage desactive : salles descendantes desactivees. */
    public void onFloorDeactivated(Long floorId) {
        deactivateSpaces(spaceRepository.findByFloorId(floorId));
    }

    /* ==================================================================
     *  CASCADE A LA REACTIVATION
     * ================================================================== */

    /** Departement reactive : les filieres, promotions, groupes et modules descendants sont reactives. */
    public void onDepartmentReactivated(Long departmentId) {
        for (Program program : programRepository.findByDepartmentId(departmentId)) {
            if (!program.isActif()) {
                program.setActif(true);
                programRepository.save(program);
            }
        }
        reactivatePromotions(promotionRepository.findByProgram_Department_Id(departmentId));
        reactivateGroups(groupRepository.findByPromotion_Program_Department_Id(departmentId));
        reactivateModules(moduleRepository.findByProgram_Department_Id(departmentId));
    }

    /** Filiere reactivee : promotions, groupes et modules descendants reactives. */
    public void onProgramReactivated(Long programId) {
        reactivatePromotions(promotionRepository.findByProgramId(programId));
        reactivateGroups(groupRepository.findByPromotion_Program_Id(programId));
        reactivateModules(moduleRepository.findByProgramId(programId));
    }

    /** Promotion reactivee : groupes descendants reactives. */
    public void onPromotionReactivated(Long promotionId) {
        reactivateGroups(groupRepository.findByPromotionId(promotionId));
    }

    /** Batiment reactive : etages et salles descendants reactives. */
    public void onBuildingReactivated(Long buildingId) {
        for (Floor floor : floorRepository.findByBuildingId(buildingId)) {
            if (!floor.isActif()) {
                floor.setActif(true);
                floorRepository.save(floor);
            }
        }
        reactivateSpaces(spaceRepository.findByFloorBuildingId(buildingId));
    }

    /** Etage reactive : salles descendantes reactives. */
    public void onFloorReactivated(Long floorId) {
        reactivateSpaces(spaceRepository.findByFloorId(floorId));
    }

    /* ==================================================================
     *  IMPACT (§17) : descendants ACTIFS qui deviendront indisponibles.
     * ================================================================== */

    public DeactivationImpact departmentImpact(Long departmentId) {
        return new DeactivationImpact(
                programRepository.countActiveByDepartmentId(departmentId),
                promotionRepository.countByProgram_Department_IdAndActif(departmentId, true),
                groupRepository.countByPromotion_Program_Department_IdAndActif(departmentId, true),
                0,
                0);
    }

    public DeactivationImpact programImpact(Long programId) {
        return new DeactivationImpact(
                0,
                promotionRepository.countByProgramIdAndActif(programId, true),
                groupRepository.countByPromotion_Program_IdAndActif(programId, true),
                0,
                0);
    }

    public DeactivationImpact promotionImpact(Long promotionId) {
        return new DeactivationImpact(
                0, 0,
                groupRepository.countByPromotionIdAndActif(promotionId, true),
                0, 0);
    }

    public DeactivationImpact buildingImpact(Long buildingId) {
        return new DeactivationImpact(
                0, 0, 0,
                floorRepository.countByBuildingIdAndActif(buildingId, true),
                spaceRepository.countByFloorBuildingIdAndActif(buildingId, true));
    }

    public DeactivationImpact floorImpact(Long floorId) {
        return new DeactivationImpact(
                0, 0, 0, 0,
                spaceRepository.countByFloorIdAndActif(floorId, true));
    }

    /* ==================================================================
     *  OUTILS INTERNES
     * ================================================================== */

    private void deactivatePromotions(Iterable<Promotion> promotions) {
        for (Promotion promotion : promotions) {
            if (promotion.isActif()) {
                promotion.setActif(false);
                promotionRepository.save(promotion);
            }
        }
    }

    private void deactivateGroups(Iterable<Group> groups) {
        for (Group group : groups) {
            if (group.isActif()) {
                group.setActif(false);
                groupRepository.save(group);
            }
        }
    }

    private void deactivateSpaces(Iterable<Space> spaces) {
        for (Space space : spaces) {
            if (space.isActif()) {
                space.setActif(false);
                spaceRepository.save(space);
            }
        }
    }

    private void deactivateModules(Iterable<Module> modules) {
        for (Module module : modules) {
            if (module.isActif()) {
                module.setActif(false);
                moduleRepository.save(module);
            }
        }
    }

    private void reactivatePromotions(Iterable<Promotion> promotions) {
        for (Promotion promotion : promotions) {
            if (!promotion.isActif()) {
                promotion.setActif(true);
                promotionRepository.save(promotion);
            }
        }
    }

    private void reactivateGroups(Iterable<Group> groups) {
        for (Group group : groups) {
            if (!group.isActif()) {
                group.setActif(true);
                groupRepository.save(group);
            }
        }
    }

    private void reactivateSpaces(Iterable<Space> spaces) {
        for (Space space : spaces) {
            if (!space.isActif()) {
                space.setActif(true);
                spaceRepository.save(space);
            }
        }
    }

    private void reactivateModules(Iterable<Module> modules) {
        for (Module module : modules) {
            if (!module.isActif()) {
                module.setActif(true);
                moduleRepository.save(module);
            }
        }
    }
}
