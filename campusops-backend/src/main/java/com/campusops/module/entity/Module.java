package com.campusops.module.entity;

import com.campusops.program.entity.Program;
import com.campusops.semester.entity.Semester;
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

/**
 * Module d'enseignement (matière) rattaché à un <strong>contexte pédagogique</strong>
 * = filière ({@link Program}) + semestre ({@link Semester}) — cahier des charges
 * §8-§11, §15.
 *
 * <p><strong>Ancrage (filière, semestre)</strong> : le curriculum est
 * <em>réutilisable</em> et <em>indépendant de l'année</em>. Le semestre portant
 * déjà son niveau/cycle, il n'y a aucune redondance. Le même module sert chaque
 * année à toutes les promotions et à tous les groupes de ce contexte, ce qui
 * évite de dupliquer un module par groupe (§9). Un module peut être désactivé
 * lorsqu'une filière disparaît ou que le curriculum change (§12).</p>
 *
 * <p><strong>Unicité</strong> : un même libellé de module ne peut apparaître
 * qu'une fois par contexte (filière + semestre) — contrainte composite
 * {@code uk_module (program_id, semester_id, nom)}.</p>
 *
 * <p><strong>Générique</strong> (§14) : aucune donnée propre à un établissement
 * n'est codée en dur ; les modules de démonstration sont amorcés à partir du
 * référentiel existant et restent entièrement administrables.</p>
 */
@Entity
@Table(
        name = "modules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_module_program_semester_nom",
                columnNames = {"program_id", "semester_id", "nom"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"program", "semester"})
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Libellé du module, ex. « Algorithmique », « Bases de données ». */
    @Column(nullable = false)
    private String nom;

    /** Code court optionnel, ex. « INFO101 ». */
    @Column(length = 40)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Filière de rattachement (contexte pédagogique). Obligatoire. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** Semestre de rattachement (porte le niveau/cycle). Obligatoire. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

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
