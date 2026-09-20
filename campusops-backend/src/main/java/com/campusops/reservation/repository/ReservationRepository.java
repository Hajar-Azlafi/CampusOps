package com.campusops.reservation.repository;

import com.campusops.enums.ReservationStatus;
import com.campusops.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(Long userId);

    List<Reservation> findBySpaceId(Long spaceId);

    List<Reservation> findByDate(LocalDate date);

        /**
         * Reservations qui peuvent influencer la disponibilite d'un espace a une
         * date donnee. Le filtrage par statut reste explicite : seules les
         * reservations acceptees bloquent, tandis que les demandes en attente sont
         * exposees separement par le moteur de disponibilite.
         */
        List<Reservation> findBySpaceIdAndDateAndStatutIn(Long spaceId, LocalDate date,
                                                                                                          List<ReservationStatus> statuts);

    List<Reservation> findByDateBetween(LocalDate start, LocalDate end);

    List<Reservation> findByStatut(ReservationStatus statut);

    boolean existsBySpaceId(Long spaceId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------
    // Une reservation (meme ancienne/annulee) est un historique : sa presence
    // bloque la suppression de l'utilisateur ou de la salle. program/group/
    // semester/academicYear sont nullable : le filet DataIntegrityViolation
    // couvre les rares reservations sans contexte academique dans un sous-arbre.

    long countByUserId(Long userId);

    long countBySpaceId(Long spaceId);

    long countByProgramId(Long programId);

    long countByGroupId(Long groupId);

    long countBySemesterId(Long semesterId);

    long countByAcademicYearId(Long academicYearId);

    /** Reservations rattachees a une filiere d'un departement (sous-arbre). */
    long countByProgram_Department_Id(Long departmentId);

    /**
     * Reservations d'un espace, pour une date donnee, dont le statut bloque le
     * creneau (statuts fournis) et dont la plage horaire chevauche l'intervalle
     * [heureDebut, heureFin). Deux plages se chevauchent lorsque
     * debut existant < fin demandee ET fin existante > debut demande.
     */
    @Query("SELECT r FROM Reservation r WHERE r.space.id = :spaceId " +
            "AND r.date = :date " +
            "AND r.statut IN :statuts " +
            "AND r.heureDebut < :heureFin " +
            "AND r.heureFin > :heureDebut")
    List<Reservation> findConflicting(@Param("spaceId") Long spaceId,
                                      @Param("date") LocalDate date,
                                      @Param("heureDebut") LocalTime heureDebut,
                                      @Param("heureFin") LocalTime heureFin,
                                      @Param("statuts") List<ReservationStatus> statuts);

    @Query("SELECT r FROM Reservation r WHERE r.space.id = :spaceId " +
            "AND r.date = :date " +
            "AND r.statut IN :statuts " +
            "AND r.heureDebut < :heureFin " +
            "AND r.heureFin > :heureDebut " +
            "AND r.id <> :excludeId")
    List<Reservation> findConflictingExcludingId(@Param("spaceId") Long spaceId,
                                                 @Param("date") LocalDate date,
                                                 @Param("heureDebut") LocalTime heureDebut,
                                                 @Param("heureFin") LocalTime heureFin,
                                                 @Param("statuts") List<ReservationStatus> statuts,
                                                 @Param("excludeId") Long excludeId);

    /**
     * Reservations d'un espace dont le statut bloque le creneau et dont la plage
     * horaire chevauche [heureDebut, heureFin), tous jours confondus. Sert a
     * verifier qu'une NOUVELLE seance d'emploi du temps ne tombe pas sur une
     * salle deja reservee (cahier des charges §23) : l'appelant filtre ensuite
     * par jour de la semaine et par periode de l'annee universitaire.
     */
    @Query("SELECT r FROM Reservation r WHERE r.space.id = :spaceId " +
            "AND r.statut IN :statuts " +
            "AND r.heureDebut < :heureFin " +
            "AND r.heureFin > :heureDebut")
    List<Reservation> findBlockingForSpace(@Param("spaceId") Long spaceId,
                                           @Param("heureDebut") LocalTime heureDebut,
                                           @Param("heureFin") LocalTime heureFin,
                                           @Param("statuts") List<ReservationStatus> statuts);

    @Query("SELECT r FROM Reservation r WHERE " +
            "LOWER(r.motif) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.space.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.space.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.user.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.user.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Reservation> searchByKeyword(@Param("keyword") String keyword);

    /**
     * Nombre de reservations <b>a venir</b> d'un utilisateur, c'est-a-dire dont la
     * date est aujourd'hui ou plus tard et dont le statut mobilise encore un
     * creneau. Sert au quota « nombre maximal de reservations par utilisateur »
     * de la configuration globale (Module 11, §5) : les reservations annulees,
     * refusees, terminees ou passees ne consomment aucun quota.
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.user.id = :userId " +
            "AND r.date >= :aPartirDe " +
            "AND r.statut IN :statuts")
    long countUpcomingForUser(@Param("userId") Long userId,
                              @Param("aPartirDe") LocalDate aPartirDe,
                              @Param("statuts") List<ReservationStatus> statuts);
}
