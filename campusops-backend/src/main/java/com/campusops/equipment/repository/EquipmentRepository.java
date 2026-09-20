package com.campusops.equipment.repository;

import com.campusops.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByActif(boolean actif);

    boolean existsByNom(String nom);

    boolean existsByCode(String code);

    boolean existsByNomAndIdNot(String nom, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT e FROM Equipment e WHERE "
            + "LOWER(e.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(e.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Equipment> searchByKeyword(@Param("keyword") String keyword);
}
