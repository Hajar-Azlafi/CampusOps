package com.campusops.group.repository;

import com.campusops.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByActif(boolean actif);

    List<Group> findByPromotionId(Long promotionId);

    // Groupes des filieres d'un responsable pedagogique (perimetre RP) :
    // Group -> Promotion -> Program.
    List<Group> findByPromotion_Program_IdIn(java.util.Collection<Long> programIds);

    // Groupes d'une année universitaire donnée (via la promotion : Group -> Promotion -> AcademicYear).
    List<Group> findByPromotion_AcademicYear_Id(Long academicYearId);

    List<Group> findByPromotion_AcademicYear_IdAndActif(Long academicYearId, boolean actif);

    boolean existsByPromotionId(Long promotionId);

    boolean existsByPromotionIdAndNom(Long promotionId, String nom);

    boolean existsByPromotionIdAndNomAndIdNot(Long promotionId, String nom, Long id);

    Optional<Group> findByPromotionIdAndNom(Long promotionId, String nom);

    @Query("SELECT g FROM Group g WHERE " +
            "LOWER(g.nom) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Group> searchByKeyword(@Param("keyword") String keyword);

    /* ==================================================================
     *  PROPAGATION ET COMPTEURS D'IMPACT (regle ACTIF / INACTIF)
     *  Chemins : Group -> Promotion -> Program -> Department.
     * ================================================================== */

    /** Groupes d'une filiere (cascade a la desactivation d'une filiere). */
    List<Group> findByPromotion_Program_Id(Long programId);

    /** Groupes d'un departement (cascade a la desactivation d'un departement). */
    List<Group> findByPromotion_Program_Department_Id(Long departmentId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------

    /** Groupes d'une promotion (enfants supprimes en cascade avec la promotion). */
    long countByPromotionId(Long promotionId);

    /** Groupes d'une filiere (enfants supprimes en cascade avec la filiere). */
    long countByPromotion_Program_Id(Long programId);

    /** Groupes d'un departement (enfants supprimes en cascade avec le departement). */
    long countByPromotion_Program_Department_Id(Long departmentId);

    long countByPromotionIdAndActif(Long promotionId, boolean actif);

    long countByPromotion_Program_IdAndActif(Long programId, boolean actif);

    long countByPromotion_Program_Department_IdAndActif(Long departmentId, boolean actif);

    /**
     * Groupes <b>utilisables</b> d'une promotion : le groupe, sa promotion, sa
     * filiere, le departement de celle-ci et l'annee universitaire doivent tous
     * etre actifs (dependances multiniveaux, §15).
     */
    @Query("SELECT g FROM Group g "
            + "JOIN g.promotion p JOIN p.program pr JOIN pr.department d JOIN p.academicYear ay "
            + "WHERE g.actif = true AND p.actif = true AND d.actif = true AND ay.actif = true "
            + "AND (pr.actif IS NULL OR pr.actif = true) "
            + "AND p.id = :promotionId ORDER BY g.nom ASC")
    List<Group> findUsableByPromotionId(@Param("promotionId") Long promotionId);
}
