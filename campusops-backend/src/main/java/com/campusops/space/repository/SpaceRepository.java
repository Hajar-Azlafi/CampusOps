package com.campusops.space.repository;

import com.campusops.enums.SpaceType;
import com.campusops.space.entity.Space;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {

        @EntityGraph(attributePaths = "equipments")
        @Query("SELECT s FROM Space s")
        List<Space> findAllWithEquipments();

    List<Space> findByFloorId(Long floorId);

    List<Space> findByFloorBuildingId(Long buildingId);

    List<Space> findByActif(boolean actif);

    List<Space> findByType(SpaceType type);

    boolean existsByFloorId(Long floorId);

    // --- Suppression (refonte suppressions) -----------------------------------

    /** Salles d'un etage (enfants supprimes en cascade avec l'etage). */
    long countByFloorId(Long floorId);

    /** Salles d'un batiment (enfants supprimes en cascade avec le batiment). */
    long countByFloorBuildingId(Long buildingId);

    /**
     * Salles equipees d'un equipement donne. Sert a DETACHER l'equipement de ses
     * salles avant de le supprimer (l'association {@code space_equipments} est
     * possedee par Space) : la suppression d'un equipement n'est jamais bloquee,
     * il est simplement retire des salles qui le referencaient.
     */
    List<Space> findByEquipments_Id(Long equipmentId);

    /**
     * Charge un espace en posant un <b>verrou d'ecriture pessimiste</b>
     * (SELECT ... FOR UPDATE). Toutes les operations de reservation sur une
     * meme salle passent par ce verrou : elles sont donc serialisees et deux
     * demandes concurrentes ne peuvent jamais aboutir a un double booking (§21).
     * Le verrou est tenu jusqu'au commit de la transaction appelante.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    Optional<Space> lockById(@Param("id") Long id);

    @Query("SELECT COUNT(s) > 0 FROM Space s WHERE s.floor.building.id = :buildingId "
            + "AND LOWER(s.code) = LOWER(:code)")
    boolean existsByBuildingAndCode(@Param("buildingId") Long buildingId,
                                    @Param("code") String code);

    @Query("SELECT COUNT(s) > 0 FROM Space s WHERE s.floor.building.id = :buildingId "
            + "AND LOWER(s.code) = LOWER(:code) AND s.id <> :id")
    boolean existsByBuildingAndCodeAndIdNot(@Param("buildingId") Long buildingId,
                                            @Param("code") String code,
                                            @Param("id") Long id);

    @Query("SELECT s FROM Space s WHERE "
            + "LOWER(s.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Space> searchByKeyword(@Param("keyword") String keyword);

    /* ==================================================================
     *  PROPAGATION ET COMPTEURS D'IMPACT (regle ACTIF / INACTIF)
     *  Chemin : Batiment -> Etage -> Salle.
     * ================================================================== */

    List<Space> findByFloorIdAndActif(Long floorId, boolean actif);

    List<Space> findByFloorBuildingIdAndActif(Long buildingId, boolean actif);

    List<Space> findByTypeAndActif(SpaceType type, boolean actif);

    long countByFloorIdAndActif(Long floorId, boolean actif);

    long countByFloorBuildingIdAndActif(Long buildingId, boolean actif);
}
