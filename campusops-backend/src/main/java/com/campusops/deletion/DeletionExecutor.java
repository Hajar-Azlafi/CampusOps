package com.campusops.deletion;

import com.campusops.audit.repository.AuditLogRepository;
import com.campusops.auth.repository.PasswordResetTokenRepository;
import com.campusops.building.entity.Building;
import com.campusops.building.repository.BuildingRepository;
import com.campusops.department.entity.Department;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.equipment.entity.Equipment;
import com.campusops.equipment.repository.EquipmentRepository;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.group.repository.GroupRepository;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.notification.repository.NotificationRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Execute les suppressions <b>en cascade metier</b> une fois l'analyse
 * ({@link DeletionAnalyzer}) validee cote service. Tout se fait dans une seule
 * transaction : <b>tout reussit, ou rien ne change</b> — jamais de suppression
 * partielle, jamais d'erreur SQL brute laissee remonter.
 *
 * <p>Chaque methode supprime les enfants dans l'<b>ordre inverse des
 * dependances</b> (feuilles d'abord), de sorte qu'aucune contrainte de cle
 * etrangere n'est violee. On supprime <b>entite par entite</b>
 * ({@code deleteAll(List)} appelle {@code delete()} sur chaque element) plutot
 * qu'en lot ({@code deleteAllInBatch}) : c'est indispensable pour que Hibernate
 * vide d'abord les tables de jointure (ex. {@code space_equipments}).</p>
 *
 * <p><b>Prealable.</b> Ces methodes pressupposent que le service appelant a
 * deja verifie via {@link DeletionAnalyzer} qu'aucun usage metier ne bloque la
 * suppression (sinon la transaction echouera et sera annulee, puis traduite en
 * message metier par le {@code GlobalExceptionHandler}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeletionExecutor {

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final ModuleRepository moduleRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;

    /* ==================================================================
     *  STRUCTURE ACADEMIQUE (cascade hierarchique)
     *  Chaine : Departement -> Filiere -> Promotion -> Groupe
     *                       \-> Module (rattache Filiere + Semestre)
     * ================================================================== */

    /** Supprime un departement et tout son sous-arbre pedagogique. */
    public void deleteDepartment(Department department) {
        Long id = department.getId();
        // Feuilles d'abord : modules et groupes, puis promotions, puis filieres.
        moduleRepository.deleteAll(moduleRepository.findByProgram_Department_Id(id));
        groupRepository.deleteAll(groupRepository.findByPromotion_Program_Department_Id(id));
        promotionRepository.deleteAll(promotionRepository.findByProgram_Department_Id(id));
        programRepository.deleteAll(programRepository.findByDepartmentId(id));
        departmentRepository.delete(department);
    }

    /** Supprime une filiere et tout son sous-arbre (promotions, groupes, modules). */
    public void deleteProgram(Program program) {
        Long id = program.getId();
        moduleRepository.deleteAll(moduleRepository.findByProgramId(id));
        groupRepository.deleteAll(groupRepository.findByPromotion_Program_Id(id));
        promotionRepository.deleteAll(promotionRepository.findByProgramId(id));
        programRepository.delete(program);
    }

    /** Supprime une promotion et ses groupes. */
    public void deletePromotion(Promotion promotion) {
        groupRepository.deleteAll(groupRepository.findByPromotionId(promotion.getId()));
        promotionRepository.delete(promotion);
    }

    /* ==================================================================
     *  ESPACES PHYSIQUES (cascade hierarchique)
     *  Chaine : Batiment -> Etage -> Salle (+ jointure space_equipments)
     * ================================================================== */

    /** Supprime un batiment, ses etages et ses salles. */
    public void deleteBuilding(Building building) {
        Long id = building.getId();
        // Suppression salle par salle : vide d'abord space_equipments.
        spaceRepository.deleteAll(spaceRepository.findByFloorBuildingId(id));
        floorRepository.deleteAll(floorRepository.findByBuildingId(id));
        buildingRepository.delete(building);
    }

    /** Supprime un etage et ses salles. */
    public void deleteFloor(Floor floor) {
        spaceRepository.deleteAll(spaceRepository.findByFloorId(floor.getId()));
        floorRepository.delete(floor);
    }

    /* ==================================================================
     *  ASSOCIATION TECHNIQUE (detacher puis supprimer)
     * ================================================================== */

    /**
     * Supprime un equipement. L'association {@code space_equipments} est
     * <b>technique</b> : on retire d'abord l'equipement de chaque salle qui le
     * possede (cote proprietaire = {@link Space}), puis on supprime l'equipement.
     * Aucune salle n'est supprimee.
     */
    public void deleteEquipment(Equipment equipment) {
        List<Space> spaces = spaceRepository.findByEquipments_Id(equipment.getId());
        for (Space space : spaces) {
            space.getEquipments().removeIf(e -> e.getId().equals(equipment.getId()));
        }
        spaceRepository.saveAll(spaces);
        equipmentRepository.delete(equipment);
    }

    /* ==================================================================
     *  COMPTE UTILISATEUR (nettoyage puis suppression)
     * ================================================================== */

    /**
     * Supprime un compte utilisateur apres avoir nettoye ses dependances
     * <b>techniques</b> : jetons de reinitialisation et notifications (FK NOT
     * NULL, ephemeres) sont supprimes ; le journal d'audit et le champ
     * « importe par » des emplois du temps (FK nullable, historiques) sont
     * <b>anonymises</b> (mis a null) et non supprimes.
     *
     * <p>Prealable : l'appelant a verifie via l'analyseur qu'aucune reservation
     * n'appartient a ce compte et qu'il n'est responsable d'aucune filiere.</p>
     */
    public void deleteUserAccount(User user) {
        Long id = user.getId();
        passwordResetTokenRepository.deleteByUserId(id);
        notificationRepository.deleteByUserId(id);
        auditLogRepository.detachUser(id);
        emploiDuTempsRepository.clearImporteParByUserId(id);
        userRepository.delete(user);
    }
}
