package com.campusops.semester.entity;

import com.campusops.level.entity.Level;
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
 * Semestre rattaché à un niveau/cycle. Un même libellé (« S1 ») peut donc
 * exister dans plusieurs cycles sans collision : S1 du Tronc commun est
 * distinct de S1 du Cycle ingénieur. L'unicité est composite (nom + niveau).
 *
 * <p>Le niveau est volontairement optionnel : le Doctorat n'a pas de semestres
 * et la Formation continue reste flexible (semestres libres sans cycle imposé).
 */
@Entity
@Table(
        name = "semesters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_semester_nom_level",
                columnNames = {"nom", "level_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "level")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private Integer ordre;

    /**
     * Niveau/cycle auquel appartient ce semestre. Nullable : le Doctorat n'a
     * pas de semestres et la Formation continue peut définir des semestres
     * libres non rattachés à un cycle précis.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private Level level;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    /**
     * Semestre « courant » de son niveau : marque un semestre en cours pour le
     * cycle. Un niveau peut avoir <b>plusieurs</b> semestres courants simultanés
     * lorsqu'il couvre plusieurs années (ex. Tronc commun = S1 + S3 ; Cycle
     * ingénieur = S1 + S3 + S5), car des cohortes de 1re, 2e… année coexistent.
     * Sert de point de départ à la bascule de semestre (§20).
     *
     * <p><b>Migration non destructive (§29)</b> : colonne <b>nullable</b> ajoutée
     * via {@code ddl-auto=update}. Les semestres existants prennent la valeur
     * NULL (traitée comme « non courant »), puis sont normalisés à {@code false}
     * par {@link com.campusops.config.SemesterConstraintInitializer}.
     */
    @Column(name = "courant")
    @Builder.Default
    private Boolean courant = false;

    /**
     * Début de la période de validité du semestre (facultatif). Les emplois du
     * temps et les examens de ce semestre ne peuvent pas déborder de cette
     * fenêtre [{@code dateDebut}, {@code dateFin}] (§20). Colonne nullable (§29).
     */
    @Column(name = "date_debut")
    private LocalDate dateDebut;

    /**
     * Fin de la période de validité du semestre (facultatif). Passée cette date,
     * les emplois du temps du semestre sont considérés expirés (salles libérées).
     * Colonne nullable (§29).
     */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
