package com.campusops.typeseance.entity;

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
 * Type de seance <strong>configurable</strong> (cahier des charges §7) :
 * « Cours », « TD », « TP », « Examen », « Controle », « Soutenance »,
 * « Autre »... L'administrateur peut en ajouter d'autres a tout moment.
 *
 * <p><strong>Concept distinct</strong> de :</p>
 * <ul>
 *   <li>la <em>session universitaire</em>
 *       ({@link com.campusops.academicsession.entity.SessionUniversitaire} =
 *       session normale / rattrapage) qui decrit une periode ;</li>
 *   <li>le <em>type de presence</em>
 *       ({@link com.campusops.enums.PresenceType} = presentiel / distanciel).</li>
 * </ul>
 *
 * <p><strong>Migration non destructive</strong> (§22-§23) : cette entite
 * remplace progressivement l'enumeration
 * {@link com.campusops.enums.SessionType}. Les seances existantes conservent
 * leur enum ; un initialiseur reconstitue la FK a partir du code de l'enum.</p>
 *
 * <p><strong>Generique</strong> (§14) : aucun libelle propre a un etablissement
 * n'est code en dur cote metier ; seuls les sept types canoniques sont amorces,
 * modifiables et desactivables par l'administrateur.</p>
 */
@Entity
@Table(
        name = "seance_types",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seance_type_code",
                columnNames = "code"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class TypeSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Libelle lisible, ex. « Cours », « Travaux diriges ». */
    @Column(nullable = false)
    private String nom;

    /** Code court unique, ex. « COURS », « TD », « EXAMEN ». */
    @Column(nullable = false, length = 40)
    private String code;

    /**
     * Couleur d'affichage optionnelle au format hexadecimal (ex. « #2563EB »),
     * utilisee par les vues (grille, statistiques). Facultative.
     */
    @Column(length = 9)
    private String couleur;

    /** Ordre d'affichage (§3), toujours croissant, jamais aleatoire. */
    @Column(nullable = false)
    private Integer ordre;

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
