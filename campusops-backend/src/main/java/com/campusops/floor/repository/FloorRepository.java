package com.campusops.floor.repository;

import com.campusops.floor.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByBuildingId(Long buildingId);

    List<Floor> findByActif(boolean actif);

    boolean existsByBuildingId(Long buildingId);

    /** Etages d'un batiment (enfants supprimes en cascade avec le batiment). */
    long countByBuildingId(Long buildingId);

    boolean existsByBuildingIdAndNumero(Long buildingId, Integer numero);

    boolean existsByBuildingIdAndCode(Long buildingId, String code);

    boolean existsByBuildingIdAndNumeroAndIdNot(Long buildingId, Integer numero, Long id);

    boolean existsByBuildingIdAndCodeAndIdNot(Long buildingId, String code, Long id);

    @Query("SELECT f FROM Floor f WHERE " +
            "LOWER(f.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(f.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Floor> searchByKeyword(@Param("keyword") String keyword);

    /* ==================================================================
     *  PROPAGATION ET COMPTEURS D'IMPACT (regle ACTIF / INACTIF)
     *  Chemin : Batiment -> Etage -> Salle.
     * ================================================================== */

    List<Floor> findByBuildingIdAndActif(Long buildingId, boolean actif);

    long countByBuildingIdAndActif(Long buildingId, boolean actif);
}
