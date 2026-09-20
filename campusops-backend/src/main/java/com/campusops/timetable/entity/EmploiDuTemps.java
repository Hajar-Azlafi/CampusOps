package com.campusops.timetable.entity;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.enums.TimetableSource;
import com.campusops.enums.TimetableStatus;
import com.campusops.group.entity.Group;
import com.campusops.level.entity.Level;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.semester.entity.Semester;
import com.campusops.user.entity.User;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * En-tete d'un emploi du temps : regroupe les seances ({@code Schedule}) d'un
 * contexte unique = annee universitaire + filiere + niveau + promotion + groupe
 * + semestre + session universitaire.
 *
 * <p>Un meme groupe possede PLUSIEURS emplois du temps au fil du temps
 * (distingues par annee/semestre/session), sans jamais ecraser l'historique
 * (cahier des charges §1, §2, §20). L'unicite metier
 * {@code (annee, groupe, semestre, session)} garantit un seul emploi du temps
 * par contexte.</p>
 *
 * <p>Non destructif : les seances anterieures a l'introduction de cet en-tete
 * restent valides (leur FK {@code emploiDuTemps} est simplement nulle) et
 * peuvent etre regroupees a posteriori.</p>
 */
@Entity
@Table(
        name = "timetables",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_timetable_context",
                columnNames = {"academic_year_id", "group_id", "semester_id", "session_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {
        "academicYear", "program", "level", "promotion",
        "group", "semester", "session", "importePar"
})
public class EmploiDuTemps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

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
    @JoinColumn(name = "session_id", nullable = false)
    private SessionUniversitaire session;

    /**
     * Periode de validite de l'emploi du temps (§20 « dates / expiration »).
     *
     * <p>Au-dela de {@link #dateFin}, les salles occupees par les seances de cet
     * emploi du temps sont <b>automatiquement liberees</b> : elles redeviennent
     * disponibles a la reservation sans action manuelle. Un archivage anticipe
     * (statut {@code ARCHIVE}) libere les salles avant meme cette date.</p>
     *
     * <p><b>Migration non destructive</b> (§29) : colonnes <b>nullables</b> pour
     * ne pas casser les en-tetes existants lors de l'ajout via
     * {@code ddl-auto=update}. A defaut de dates saisies, le service retombe sur
     * la periode de l'annee universitaire.</p>
     */
    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimetableStatus statut = TimetableStatus.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimetableSource source = TimetableSource.MANUEL;

    /** Utilisateur ayant importe/cree l'emploi du temps (tracabilite §17/§34). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by_user_id")
    private User importePar;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
