package com.campusops.level.repository;

import com.campusops.level.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {

    boolean existsByNom(String nom);

    boolean existsByNomAndIdNot(String nom, Long id);

    List<Level> findByActif(boolean actif);

    List<Level> findAllByOrderByOrdreAsc();

    @Query("SELECT l FROM Level l WHERE " +
            "LOWER(l.nom) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Level> searchByKeyword(@Param("keyword") String keyword);
}
