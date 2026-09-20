package com.campusops.occupation.repository;

import com.campusops.enums.OccupationType;
import com.campusops.occupation.entity.OccupationSupplementaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * Accès <b>polymorphe</b> aux occupations supplémentaires. Comme
 * {@code Examen} est un sous-type d'{@link OccupationSupplementaire} (héritage
 * SINGLE_TABLE), toute requête de ce repository ramène <b>examens +
 * soutenances + autres occupations</b> en une seule fois : c'est exactement ce
 * dont le moteur central de disponibilité a besoin pour qu'une salle occupée par
 * l'un quelconque de ces événements ne soit jamais proposée comme libre.
 *
 * <p>Convention de chevauchement demi-ouverte {@code [debut, fin)}, identique
 * aux séances et aux réservations (§9/§33) :
 * {@code debut existant < fin demandée AND fin existante > debut demandé}.</p>
 */
@Repository
public interface OccupationSupplementaireRepository
        extends JpaRepository<OccupationSupplementaire, Long> {

    /**
     * <b>Requête du moteur de disponibilité</b> : toutes les occupations actives
     * d'un espace à une date, quelle que soit leur nature.
     */
    @Query("SELECT o FROM OccupationSupplementaire o WHERE o.actif = true "
            + "AND o.space.id = :spaceId "
            + "AND o.date = :date "
            + "ORDER BY o.heureDebut ASC")
    List<OccupationSupplementaire> findActiveBySpaceIdAndDate(@Param("spaceId") Long spaceId,
                                                              @Param("date") LocalDate date);

    /**
     * Occupations actives qui immobilisent la même salle, à la même date, sur un
     * créneau chevauchant {@code [heureDebut, heureFin)}. {@code excludeId} peut
     * être {@code null} (création) : on ne s'exclut alors de rien.
     */
    @Query("SELECT o FROM OccupationSupplementaire o WHERE o.actif = true "
            + "AND o.space.id = :spaceId "
            + "AND o.date = :date "
            + "AND o.heureDebut < :heureFin "
            + "AND o.heureFin > :heureDebut "
            + "AND (:excludeId IS NULL OR o.id <> :excludeId) "
            + "ORDER BY o.heureDebut ASC")
    List<OccupationSupplementaire> findConflictingForSpace(@Param("spaceId") Long spaceId,
                                                           @Param("date") LocalDate date,
                                                           @Param("heureDebut") LocalTime heureDebut,
                                                           @Param("heureFin") LocalTime heureFin,
                                                           @Param("excludeId") Long excludeId);

    /** Occupations d'un ensemble de types (= d'une catégorie), toutes filières. */
    List<OccupationSupplementaire> findByTypeIn(Collection<OccupationType> types);

    /**
     * Occupations d'un ensemble de types limitées aux filières indiquées —
     * périmètre du responsable pédagogique (§12). Les occupations « autre » sans
     * filière ne sont volontairement <b>pas</b> ramenées : elles relèvent de
     * l'administrateur.
     */
    @Query("SELECT o FROM OccupationSupplementaire o "
            + "WHERE o.type IN :types AND o.program.id IN :programIds")
    List<OccupationSupplementaire> findByTypeInAndProgramIdIn(
            @Param("types") Collection<OccupationType> types,
            @Param("programIds") Collection<Long> programIds);

    /** Vrai si un espace porte encore au moins une occupation (garde-fou suppression). */
    boolean existsBySpaceId(Long spaceId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------
    // Polymorphe : ces comptages couvrent occupations + examens (SINGLE_TABLE).
    // program/promotion/group/academicYear sont nullable : les occupations « autre »
    // sans contexte academique ne sont pas comptees par ces relations (voulu).

    long countBySpaceId(Long spaceId);

    long countByProgramId(Long programId);

    long countByPromotionId(Long promotionId);

    long countByGroupId(Long groupId);

    long countByAcademicYearId(Long academicYearId);

    /** Occupations rattachees a une filiere d'un departement (sous-arbre). */
    long countByProgram_Department_Id(Long departmentId);
}
