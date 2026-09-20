package com.campusops.program.repository;

import com.campusops.program.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

    boolean existsByNom(String nom);

    boolean existsByCode(String code);

    boolean existsByNomAndIdNot(String nom, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** Unicite du nom au sein d'un meme cycle/niveau. */
    boolean existsByNomAndLevelId(String nom, Long levelId);

    boolean existsByNomAndLevelIdAndIdNot(String nom, Long levelId, Long id);

    List<Program> findByDepartmentId(Long departmentId);

    boolean existsByDepartmentId(Long departmentId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------

    /** Filieres d'un departement (enfants supprimes en cascade avec le departement). */
    long countByDepartmentId(Long departmentId);

    /** Filieres rattachees a un niveau (bloque la suppression du niveau). */
    long countByLevelId(Long levelId);

    /** Filieres dont l'utilisateur est responsable (bloque la suppression du compte). */
    long countByResponsableId(Long responsableId);

    /** Filieres dont l'utilisateur est responsable pedagogique (perimetre RP). */
    List<Program> findByResponsableId(Long responsableId);

    boolean existsByResponsableId(Long responsableId);

    /** Verifie qu'une filiere donnee est bien dans le perimetre d'un responsable. */
    boolean existsByIdAndResponsableId(Long id, Long responsableId);

    boolean existsByLevelId(Long levelId);

    Optional<Program> findByNom(String nom);

    /** Recherche d'une filiere par son code (ex. « GI », « ISI »). */
    Optional<Program> findByCode(String code);

    @Query("SELECT p FROM Program p WHERE " +
            "LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Program> searchByKeyword(@Param("keyword") String keyword);

    /* ==================================================================
     *  ETAT ACTIF / INACTIF
     *  La colonne « actif » a ete ajoutee apres coup (migration non
     *  destructive) : NULL signifie « jamais renseigne » et doit etre
     *  interprete comme ACTIF. Les requetes ci-dessous le prennent en
     *  compte, ce qui rend le filtrage correct meme avant le passage du
     *  {@code ProgramActifBackfillInitializer}.
     * ================================================================== */

    /** Filieres actives (NULL = actif), tri stable sur le nom. */
    @Query("SELECT p FROM Program p WHERE p.actif IS NULL OR p.actif = true ORDER BY p.nom ASC")
    List<Program> findAllActive();

    /** Filieres explicitement desactivees, tri stable sur le nom. */
    @Query("SELECT p FROM Program p WHERE p.actif = false ORDER BY p.nom ASC")
    List<Program> findAllInactive();

    /**
     * Filieres <b>utilisables</b> : actives et rattachees a un departement
     * actif (regle hierarchique Departement -> Filiere).
     */
    @Query("SELECT p FROM Program p JOIN p.department d "
            + "WHERE (p.actif IS NULL OR p.actif = true) AND d.actif = true "
            + "ORDER BY p.nom ASC")
    List<Program> findAllUsable();

    @Query("SELECT p FROM Program p WHERE p.department.id = :departmentId "
            + "AND (p.actif IS NULL OR p.actif = true) ORDER BY p.nom ASC")
    List<Program> findActiveByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT p FROM Program p WHERE p.department.id = :departmentId "
            + "AND p.actif = false ORDER BY p.nom ASC")
    List<Program> findInactiveByDepartmentId(@Param("departmentId") Long departmentId);

    /** Nombre de filieres encore actives dans un departement (compteur d'impact §17). */
    @Query("SELECT COUNT(p) FROM Program p WHERE p.department.id = :departmentId "
            + "AND (p.actif IS NULL OR p.actif = true)")
    long countActiveByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * Normalise les lignes anterieures a l'ajout de la colonne : NULL -> true.
     * Mise a jour en masse (aucun {@code @UpdateTimestamp} declenche, donc
     * aucune modification des dates de mise a jour) et idempotente.
     */
    @Modifying
    @Query("UPDATE Program p SET p.actif = true WHERE p.actif IS NULL")
    int normalizeActif();
}
