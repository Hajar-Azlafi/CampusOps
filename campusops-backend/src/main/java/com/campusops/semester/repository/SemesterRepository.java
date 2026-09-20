package com.campusops.semester.repository;

import com.campusops.semester.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    boolean existsByNom(String nom);

    boolean existsByNomAndIdNot(String nom, Long id);

    // --- Unicité composite (nom + niveau) --------------------------------
    // Un même libellé peut exister dans plusieurs cycles ; il ne doit être
    // unique qu'au sein d'un même niveau. Les semestres sans niveau
    // (Formation continue flexible) sont contrôlés séparément par nom seul.

    boolean existsByNomAndLevelId(String nom, Long levelId);

    boolean existsByNomAndLevelIdAndIdNot(String nom, Long levelId, Long id);

    boolean existsByNomAndLevelIsNull(String nom);

    boolean existsByNomAndLevelIsNullAndIdNot(String nom, Long id);

    List<Semester> findByActif(boolean actif);

    /** Semestres d'un niveau (bloque la suppression du niveau). */
    long countByLevelId(Long levelId);

    List<Semester> findByLevelIdOrderByOrdreAsc(Long levelId);

    List<Semester> findByLevelIsNullOrderByOrdreAsc();

    List<Semester> findAllByOrderByOrdreAsc();

    List<Semester> findByLevelIdAndActifOrderByOrdreAsc(Long levelId, boolean actif);

    // --- Semestres « courants » par niveau (bascule de semestre, §20) ------
    // Un niveau peut avoir PLUSIEURS semestres courants simultanés (années
    // coexistantes : ex. S1 + S3, ou S1 + S3 + S5). Aucun index d'unicité :
    // l'ensemble des courants est géré par le service (ajout/retrait individuel).

    List<Semester> findByCourantTrue();

    List<Semester> findByLevelIdAndCourantTrue(Long levelId);

    List<Semester> findByLevelIsNullAndCourantTrue();

    // Amorçage : semestre actif d'ordre le plus bas d'un niveau (point de départ).
    Optional<Semester> findFirstByLevelIdAndActifTrueOrderByOrdreAsc(Long levelId);

    // Bascule : semestre actif suivant (ordre strictement supérieur) d'un niveau.
    Optional<Semester> findFirstByLevelIdAndActifTrueAndOrdreGreaterThanOrderByOrdreAsc(
            Long levelId, Integer ordre);

    // Bascule pour les semestres sans niveau (Formation continue flexible).
    Optional<Semester> findFirstByLevelIsNullAndActifTrueAndOrdreGreaterThanOrderByOrdreAsc(
            Integer ordre);

    @Query("SELECT s FROM Semester s WHERE " +
            "LOWER(s.nom) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Semester> searchByKeyword(@Param("keyword") String keyword);
}
