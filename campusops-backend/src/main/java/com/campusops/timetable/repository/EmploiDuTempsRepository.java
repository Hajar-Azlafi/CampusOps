package com.campusops.timetable.repository;

import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.enums.TimetableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmploiDuTempsRepository extends JpaRepository<EmploiDuTemps, Long> {

    /** Emplois du temps des filieres d'un responsable pedagogique (perimetre RP). */
    List<EmploiDuTemps> findByProgramIdIn(Collection<Long> programIds);

    List<EmploiDuTemps> findByAcademicYearId(Long academicYearId);

    /**
     * Emplois du temps d'une annee pour un semestre donne, hors statut precise
     * (bascule de semestre : selectionne ceux a archiver, en excluant les deja
     * archives pour un archivage idempotent et un decompte exact).
     */
    List<EmploiDuTemps> findByAcademicYearIdAndSemesterIdAndStatutNot(
            Long academicYearId, Long semesterId, TimetableStatus statut);

    /**
     * Emploi du temps unique d'un contexte donne (find-or-create a l'import).
     */
    Optional<EmploiDuTemps> findByAcademicYearIdAndGroupIdAndSemesterIdAndSessionId(
            Long academicYearId, Long groupId, Long semesterId, Long sessionId);

    /** Garde-fou avant desactivation/suppression d'une reference. */
    boolean existsBySessionId(Long sessionId);

    boolean existsBySemesterId(Long semesterId);

    boolean existsByAcademicYearId(Long academicYearId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------
    // Toutes ces relations sont NOT NULL sur l'en-tete EDT -> comptages exacts.

    long countByGroupId(Long groupId);

    long countByPromotionId(Long promotionId);

    long countByProgramId(Long programId);

    long countBySemesterId(Long semesterId);

    long countBySessionId(Long sessionId);

    long countByAcademicYearId(Long academicYearId);

    long countByLevelId(Long levelId);

    /** Emplois du temps rattaches a une filiere d'un departement (sous-arbre). */
    long countByProgram_Department_Id(Long departmentId);

    /**
     * Detache l'utilisateur importateur (tracabilite) sans supprimer l'emploi du
     * temps : la colonne {@code imported_by_user_id} est nullable. Utilise a la
     * suppression d'un compte pour preserver l'historique des emplois du temps.
     */
    @Modifying
    @Query("UPDATE EmploiDuTemps e SET e.importePar = null WHERE e.importePar.id = :userId")
    int clearImporteParByUserId(@Param("userId") Long userId);
}
