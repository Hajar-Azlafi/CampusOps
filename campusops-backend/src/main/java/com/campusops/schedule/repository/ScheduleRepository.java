package com.campusops.schedule.repository;

import com.campusops.enums.WeekDay;
import com.campusops.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByActif(boolean actif);

    List<Schedule> findByPromotionId(Long promotionId);

    // Seances des filieres d'un responsable pedagogique (perimetre RP).
    List<Schedule> findByProgramIdIn(java.util.Collection<Long> programIds);

    List<Schedule> findByGroupId(Long groupId);

    List<Schedule> findBySpaceId(Long spaceId);

    List<Schedule> findByAcademicYearId(Long academicYearId);

    // Seances rattachees a un en-tete d'emploi du temps.
    List<Schedule> findByEmploiDuTempsId(Long emploiDuTempsId);

    // Comptage des seances d'un emploi du temps (colonne « nombre de seances » §17).
    long countByEmploiDuTempsId(Long emploiDuTempsId);

    boolean existsBySpaceId(Long spaceId);

    boolean existsByProgramId(Long programId);

    boolean existsByLevelId(Long levelId);

    boolean existsByPromotionId(Long promotionId);

    boolean existsByGroupId(Long groupId);

    boolean existsBySemesterId(Long semesterId);

    boolean existsByAcademicYearId(Long academicYearId);

    boolean existsByTimeSlotId(Long timeSlotId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------
    // Une seance est un usage metier fort : sa presence bloque la suppression de
    // l'entite referencee. program/level/promotion/group/semester/academicYear
    // sont NOT NULL -> le comptage par ces relations est exact et complet.

    long countBySpaceId(Long spaceId);

    long countByModuleId(Long moduleId);

    long countByTypeSeanceId(Long typeSeanceId);

    long countByGroupId(Long groupId);

    long countByPromotionId(Long promotionId);

    long countByProgramId(Long programId);

    long countBySemesterId(Long semesterId);

    long countByTimeSlotId(Long timeSlotId);

    long countByLevelId(Long levelId);

    long countByAcademicYearId(Long academicYearId);

    /** Seances rattachees a une filiere d'un departement (sous-arbre, program NOT NULL). */
    long countByProgram_Department_Id(Long departmentId);

    /**
     * Conflit de salle : la meme salle ne peut pas accueillir deux seances qui se
     * chevauchent (meme annee universitaire, semestre et jour). Le chevauchement
     * horaire est apprecie sur les bornes du creneau : deux plages
     * [debut, fin) se chevauchent lorsque {@code debut existant < fin demandee}
     * ET {@code fin existante > debut demande} — meme convention que les
     * reservations et les disponibilites (cahier des charges §9/§33).
     *
     * <p>Les seances distancielles (sans salle) sont naturellement exclues :
     * la navigation {@code s.space.id} impose une jointure interne.</p>
     */
    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND s.space.id = :spaceId " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsSpaceConflict(@Param("academicYearId") Long academicYearId,
                                @Param("semesterId") Long semesterId,
                                @Param("jour") WeekDay jour,
                                @Param("heureDebut") LocalTime heureDebut,
                                @Param("heureFin") LocalTime heureFin,
                                @Param("spaceId") Long spaceId);

    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND s.space.id = :spaceId " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND s.id <> :id " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsSpaceConflictExcludingId(@Param("academicYearId") Long academicYearId,
                                           @Param("semesterId") Long semesterId,
                                           @Param("jour") WeekDay jour,
                                           @Param("heureDebut") LocalTime heureDebut,
                                           @Param("heureFin") LocalTime heureFin,
                                           @Param("spaceId") Long spaceId,
                                           @Param("id") Long id);

    /**
     * Conflit de groupe : un groupe ne peut pas suivre deux seances qui se
     * chevauchent (meme convention de chevauchement horaire).
     */
    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND s.group.id = :groupId " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsGroupConflict(@Param("academicYearId") Long academicYearId,
                                @Param("semesterId") Long semesterId,
                                @Param("jour") WeekDay jour,
                                @Param("heureDebut") LocalTime heureDebut,
                                @Param("heureFin") LocalTime heureFin,
                                @Param("groupId") Long groupId);

    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND s.group.id = :groupId " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND s.id <> :id " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsGroupConflictExcludingId(@Param("academicYearId") Long academicYearId,
                                           @Param("semesterId") Long semesterId,
                                           @Param("jour") WeekDay jour,
                                           @Param("heureDebut") LocalTime heureDebut,
                                           @Param("heureFin") LocalTime heureFin,
                                           @Param("groupId") Long groupId,
                                           @Param("id") Long id);

    /**
     * Conflit d'enseignant : un enseignant ne peut pas assurer deux seances qui
     * se chevauchent (comparaison insensible a la casse).
     */
    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND LOWER(s.enseignant) = LOWER(:enseignant) " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsTeacherConflict(@Param("academicYearId") Long academicYearId,
                                  @Param("semesterId") Long semesterId,
                                  @Param("jour") WeekDay jour,
                                  @Param("heureDebut") LocalTime heureDebut,
                                  @Param("heureFin") LocalTime heureFin,
                                  @Param("enseignant") String enseignant);

    @Query("SELECT COUNT(s) > 0 FROM Schedule s LEFT JOIN s.emploiDuTemps edt WHERE s.actif = true " +
            "AND s.academicYear.id = :academicYearId " +
            "AND s.semester.id = :semesterId " +
            "AND s.jour = :jour " +
            "AND LOWER(s.enseignant) = LOWER(:enseignant) " +
            "AND s.timeSlot.heureDebut < :heureFin " +
            "AND s.timeSlot.heureFin > :heureDebut " +
            "AND s.id <> :id " +
            "AND (edt IS NULL OR edt.statut <> com.campusops.enums.TimetableStatus.ARCHIVE)")
    boolean existsTeacherConflictExcludingId(@Param("academicYearId") Long academicYearId,
                                             @Param("semesterId") Long semesterId,
                                             @Param("jour") WeekDay jour,
                                             @Param("heureDebut") LocalTime heureDebut,
                                             @Param("heureFin") LocalTime heureFin,
                                             @Param("enseignant") String enseignant,
                                             @Param("id") Long id);

    @Query("SELECT s FROM Schedule s WHERE " +
            "LOWER(s.matiere) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.enseignant) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Schedule> searchByKeyword(@Param("keyword") String keyword);
}
