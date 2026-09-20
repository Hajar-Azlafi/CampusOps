package com.campusops.typeseance.repository;

import com.campusops.typeseance.entity.TypeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TypeSeanceRepository extends JpaRepository<TypeSeance, Long> {

    /** Consultation triee chronologiquement (§3), jamais aleatoire. */
    List<TypeSeance> findAllByOrderByOrdreAsc();

    /** Types actifs uniquement, tries par ordre (pour les listes deroulantes). */
    List<TypeSeance> findByActifOrderByOrdreAsc(boolean actif);

    boolean existsByCode(String code);

    Optional<TypeSeance> findByCode(String code);

    Optional<TypeSeance> findByNomIgnoreCase(String nom);
}
