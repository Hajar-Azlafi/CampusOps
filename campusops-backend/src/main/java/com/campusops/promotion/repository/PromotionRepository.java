package com.campusops.promotion.repository;

import com.campusops.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByActif(boolean actif);

    List<Promotion> findByProgramId(Long programId);

    // Promotions des filieres d'un responsable pedagogique (perimetre RP).
    List<Promotion> findByProgramIdIn(java.util.Collection<Long> programIds);

    List<Promotion> findByAcademicYearId(Long academicYearId);

    List<Promotion> findByAcademicYearIdAndActif(Long academicYearId, boolean actif);

    boolean existsByProgramId(Long programId);

    boolean existsByLevelId(Long levelId);

    boolean existsByAcademicYearId(Long academicYearId);

    boolean existsByProgramIdAndLevelIdAndAcademicYearId(Long programId, Long levelId, Long academicYearId);

    boolean existsByProgramIdAndLevelIdAndAcademicYearIdAndIdNot(Long programId, Long levelId, Long academicYearId, Long id);

    Optional<Promotion> findByProgramIdAndLevelIdAndAcademicYearId(Long programId, Long levelId, Long academicYearId);

    Optional<Promotion> findByNom(String nom);

    @Query("SELECT p FROM Promotion p WHERE " +
            "LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Promotion> searchByKeyword(@Param("keyword") String keyword);

    /* ==================================================================
     *  PROPAGATION ET COMPTEURS D'IMPACT (regle ACTIF / INACTIF)
     *  Chemins : Promotion -> Program -> Department.
     * ================================================================== */

    /** Promotions d'un departement (cascade a la desactivation d'un departement). */
    List<Promotion> findByProgram_Department_Id(Long departmentId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------

    /** Promotions d'une filiere (enfants supprimes en cascade avec la filiere). */
    long countByProgramId(Long programId);

    /** Promotions d'un departement (enfants supprimes en cascade avec le departement). */
    long countByProgram_Department_Id(Long departmentId);

    /** Promotions rattachees a un niveau (bloque la suppression du niveau). */
    long countByLevelId(Long levelId);

    /** Promotions d'une annee universitaire (bloque la suppression de l'annee). */
    long countByAcademicYearId(Long academicYearId);

    long countByProgramIdAndActif(Long programId, boolean actif);

    long countByProgram_Department_IdAndActif(Long departmentId, boolean actif);

    /**
     * Promotions <b>utilisables</b> d'une filiere : la promotion, sa filiere,
     * le departement de celle-ci et l'annee universitaire doivent etre actifs.
     */
    @Query("SELECT p FROM Promotion p "
            + "JOIN p.program pr JOIN pr.department d JOIN p.academicYear ay "
            + "WHERE p.actif = true AND d.actif = true AND ay.actif = true "
            + "AND (pr.actif IS NULL OR pr.actif = true) "
            + "AND pr.id = :programId ORDER BY p.nom ASC")
    List<Promotion> findUsableByProgramId(@Param("programId") Long programId);
}
