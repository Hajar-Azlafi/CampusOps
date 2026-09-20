package com.campusops.timeslot.repository;

import com.campusops.timeslot.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findByActif(boolean actif);

    List<TimeSlot> findAllByOrderByHeureDebutAsc();

    /** Tous les créneaux triés chronologiquement par ordre puis heure de début (§3). */
    List<TimeSlot> findAllByOrderByOrdreAscHeureDebutAsc();

    /** Créneaux filtrés par statut, triés par ordre puis heure de début (§3). */
    List<TimeSlot> findByActifOrderByOrdreAscHeureDebutAsc(boolean actif);

    boolean existsByHeureDebutAndHeureFin(LocalTime heureDebut, LocalTime heureFin);

    Optional<TimeSlot> findByHeureDebutAndHeureFin(LocalTime heureDebut, LocalTime heureFin);
}
