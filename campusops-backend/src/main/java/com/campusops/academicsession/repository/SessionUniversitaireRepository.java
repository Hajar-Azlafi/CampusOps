package com.campusops.academicsession.repository;

import com.campusops.academicsession.entity.SessionUniversitaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionUniversitaireRepository extends JpaRepository<SessionUniversitaire, Long> {

    List<SessionUniversitaire> findByActif(boolean actif);

    List<SessionUniversitaire> findAllByOrderByOrdreAsc();

    boolean existsByCode(String code);

    Optional<SessionUniversitaire> findByCode(String code);

    Optional<SessionUniversitaire> findByNomIgnoreCase(String nom);
}
