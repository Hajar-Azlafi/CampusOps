package com.campusops.department.repository;

import com.campusops.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByNom(String nom);

    boolean existsByCode(String code);

    boolean existsByNomAndIdNot(String nom, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Department> findByActif(boolean actif);

    @Query("SELECT d FROM Department d WHERE " +
            "LOWER(d.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Department> searchByKeyword(@Param("keyword") String keyword);
}
