package com.campusops.academicyear.repository;

import com.campusops.academicyear.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    boolean existsByLibelle(String libelle);

    boolean existsByLibelleAndIdNot(String libelle, Long id);

    List<AcademicYear> findByActif(boolean actif);

    Optional<AcademicYear> findByLibelle(String libelle);

    Optional<AcademicYear> findFirstByActifTrue();

    long countByActif(boolean actif);

    /**
     * Annee(s) universitaire(s) couvrant la date fournie, <b>qu'elles soient
     * actives ou non</b> (bornes incluses). Utilise par la resolution d'annee
     * par date : une annee inactive doit rester consultable pour l'historique et
     * la disponibilite de sa periode. Triee sur l'annee active d'abord, puis la
     * plus recente, afin que le premier resultat soit le meilleur candidat.
     */
    @Query("SELECT a FROM AcademicYear a WHERE a.dateDebut IS NOT NULL AND a.dateFin IS NOT NULL "
            + "AND a.dateDebut <= :date AND a.dateFin >= :date "
            + "ORDER BY a.actif DESC, a.dateDebut DESC")
    List<AcademicYear> findCoveringDate(@Param("date") LocalDate date);

    @Query("SELECT a FROM AcademicYear a WHERE " +
            "LOWER(a.libelle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<AcademicYear> searchByKeyword(@Param("keyword") String keyword);
}
