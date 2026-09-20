package com.campusops.calendar.repository;

import com.campusops.calendar.entity.NonWorkingDay;
import com.campusops.enums.NonWorkingDayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NonWorkingDayRepository extends JpaRepository<NonWorkingDay, Long> {

    List<NonWorkingDay> findAllByOrderByDateDebutAsc();

    List<NonWorkingDay> findByActifOrderByDateDebutAsc(boolean actif);

    /**
     * Entrees <b>actives</b> couvrant la date donnee (intervalle inclusif).
     * Requete de reference du moteur de disponibilite : une seule ligne suffit
     * pour declarer la journee non ouvrable.
     */
    default List<NonWorkingDay> findActiveCovering(LocalDate date) {
        return findByActifOrderByDateDebutAsc(true).stream()
                .filter(day -> day.covers(date))
                .toList();
    }

    /** Entrees (actives ou non) chevauchant la periode demandee. */
    default List<NonWorkingDay> findOverlapping(LocalDate debut, LocalDate fin) {
        return findAllByOrderByDateDebutAsc().stream()
                .filter(day -> day.overlaps(debut, fin))
                .toList();
    }

    /** Entrees actives chevauchant la periode demandee (affichage calendrier). */
    default List<NonWorkingDay> findActiveOverlapping(LocalDate debut, LocalDate fin) {
        return findByActifOrderByDateDebutAsc(true).stream()
                .filter(day -> day.overlaps(debut, fin))
                .toList();
    }

    /** Garde d'idempotence du seeder : meme date de debut + meme type. */
    boolean existsByDateDebutAndType(LocalDate dateDebut, NonWorkingDayType type);

    boolean existsByDateDebutAndLibelleIgnoreCase(LocalDate dateDebut, String libelle);
}
