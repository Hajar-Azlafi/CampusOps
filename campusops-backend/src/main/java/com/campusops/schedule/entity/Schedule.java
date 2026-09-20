package com.campusops.schedule.entity;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.enums.PresenceType;
import com.campusops.enums.SessionType;
import com.campusops.enums.WeekDay;
import com.campusops.group.entity.Group;
import com.campusops.level.entity.Level;
import com.campusops.module.entity.Module;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.semester.entity.Semester;
import com.campusops.space.entity.Space;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.typeseance.entity.TypeSeance;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {
        "space", "program", "level", "promotion", "group",
        "semester", "academicYear", "timeSlot", "emploiDuTemps", "typeSeance", "module"
})
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WeekDay jour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    /**
     * Salle de la seance. Obligatoire en presentiel, facultative en distanciel :
     * la contrainte est donc appliquee par la couche service selon
     * {@link #typePresence}, pas par une contrainte {@code NOT NULL} en base.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    /**
     * En-tete d'emploi du temps regroupant cette seance. Nullable : les seances
     * creees avant l'introduction de l'en-tete restent valides et regroupables.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id")
    private EmploiDuTemps emploiDuTemps;

    @Column(nullable = false)
    private String enseignant;

    @Column(nullable = false)
    private String matiere;

    /**
     * Module d'enseignement (§8) auquel se rattache la séance. Sélectionné dans
     * le référentiel des modules du contexte pédagogique (filière + semestre).
     *
     * <p><b>Migration non destructive</b> (§22-§23) : le libellé {@link #matiere}
     * est conservé (source historique et cible de l'import Excel). Cette FK est
     * <b>nullable en base</b> pour ne pas casser les lignes existantes lors de
     * l'ajout de colonne via {@code ddl-auto=update} ; le service la renseigne
     * lorsqu'un module est choisi et aligne alors {@link #matiere} sur son nom.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private Module module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionType type;

    /**
     * Type de seance <b>configurable</b> (§7), ex. « Cours », « Examen »,
     * « Soutenance ». Remplace progressivement l'enumeration {@link #type}.
     *
     * <p><b>Migration non destructive</b> (§22-§23) : le champ {@link #type}
     * (enum) est conserve comme source historique ; cette FK est alimentee au
     * demarrage par {@code TypeSeanceInitializer} (retro-remplissage a partir du
     * code de l'enum) puis par le service lors des creations/modifications.
     * <b>Nullable en base</b> pour ne pas casser les lignes existantes lors de
     * l'ajout de colonne via {@code ddl-auto=update}.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_seance_id")
    private TypeSeance typeSeance;

    /**
     * Type de presence (§6). Nullable en base pour la retro-compatibilite ;
     * traite comme {@link PresenceType#PRESENTIEL} lorsqu'il est absent.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_presence", length = 20)
    @Builder.Default
    private PresenceType typePresence = PresenceType.PRESENTIEL;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
