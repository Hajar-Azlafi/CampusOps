package com.campusops.building.repository;

import com.campusops.building.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    boolean existsByNom(String nom);

    boolean existsByCode(String code);

    Optional<Building> findByCode(String code);

    boolean existsByNomAndIdNot(String nom, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Building> findByActif(boolean actif);

    @Query("SELECT b FROM Building b WHERE " +
            "LOWER(b.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(b.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Building> searchByKeyword(@Param("keyword") String keyword);
}
