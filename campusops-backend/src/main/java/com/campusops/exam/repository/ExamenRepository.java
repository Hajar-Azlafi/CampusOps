package com.campusops.exam.repository;

import com.campusops.exam.entity.Examen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * Accès aux examens. Depuis la restructuration « Occupation supplémentaire »,
 * {@code Examen} est un sous-type d'{@code OccupationSupplementaire} en
 * héritage SINGLE_TABLE : <b>toutes</b> les requêtes ci-dessous sont
 * automatiquement restreintes au discriminant {@code EXAMEN} par Hibernate. Les
 * requêtes qui doivent voir <em>toutes</em> les occupations (examens +
 * soutenances + autres) passent par
 * {@code OccupationSupplementaireRepository} — c'est ce que fait le moteur
 * central de disponibilité.
 *
 * <p>Les bornes horaires sont désormais lues sur l'occupation elle-même
 * ({@code heureDebut}/{@code heureFin}, recopiées du créneau) et non plus par
 * jointure sur le créneau : même convention, une jointure de moins.</p>
 */
@Repository
public interface ExamenRepository extends JpaRepository<Examen, Long> {

    List<Examen> findByActif(boolean actif);

    boolean existsByTimeSlotId(Long timeSlotId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------
    // Examen = sous-type SINGLE_TABLE : ces comptages sont restreints au
    // discriminant EXAMEN par Hibernate (module/semester/session/timeSlot ne
    // concernent que les examens).

    long countByModuleId(Long moduleId);

    long countBySemesterId(Long semesterId);

    long countBySessionId(Long sessionId);

    long countByTimeSlotId(Long timeSlotId);

    /** Examens des filières d'un responsable pédagogique (périmètre RP §12). */
    List<Examen> findByProgramIdIn(Collection<Long> programIds);

    /**
     * Examens d'une promotion à une date donnée — base du contrôle de conflit
     * « étudiant » : le filtrage fin (chevauchement horaire + logique de groupe)
     * est fait côté service, car un groupe nul (= promotion entière) et un groupe
     * précis se chevauchent selon des règles métier non exprimables simplement en
     * JPQL.
     */
    List<Examen> findByPromotionIdAndDate(Long promotionId, LocalDate date);

    // ----- Détection de conflit « salle » (même convention de chevauchement -----
    // ----- que les séances et les réservations : [debut, fin) demi-ouvert). -----

    /**
     * Autres examens actifs qui occupent la même salle, à la même date, sur un
     * créneau qui chevauche [debut, fin). Deux plages [d, f) se chevauchent
     * lorsque {@code debut existant < fin demandée} ET
     * {@code fin existante > debut demandé} (cahier des charges §9/§33).
     */
    @Query("SELECT e FROM Examen e WHERE e.actif = true "
            + "AND e.space.id = :spaceId "
            + "AND e.date = :date "
            + "AND e.heureDebut < :heureFin "
            + "AND e.heureFin > :heureDebut")
    List<Examen> findConflictingForSpace(@Param("spaceId") Long spaceId,
                                         @Param("date") LocalDate date,
                                         @Param("heureDebut") LocalTime heureDebut,
                                         @Param("heureFin") LocalTime heureFin);

    @Query("SELECT e FROM Examen e WHERE e.actif = true "
            + "AND e.space.id = :spaceId "
            + "AND e.date = :date "
            + "AND e.heureDebut < :heureFin "
            + "AND e.heureFin > :heureDebut "
            + "AND e.id <> :excludeId")
    List<Examen> findConflictingForSpaceExcludingId(@Param("spaceId") Long spaceId,
                                                    @Param("date") LocalDate date,
                                                    @Param("heureDebut") LocalTime heureDebut,
                                                    @Param("heureFin") LocalTime heureFin,
                                                    @Param("excludeId") Long excludeId);

    /**
     * Examens actifs occupant une salle à une date donnée. Le calcul de
     * disponibilité n'utilise plus cette méthode (il interroge désormais toutes
     * les occupations supplémentaires d'un coup) ; elle reste exposée pour les
     * besoins purement « examen » (diagnostic, seeders).
     */
    @Query("SELECT e FROM Examen e WHERE e.actif = true "
            + "AND e.space.id = :spaceId "
            + "AND e.date = :date")
    List<Examen> findActiveBySpaceIdAndDate(@Param("spaceId") Long spaceId,
                                            @Param("date") LocalDate date);
}
