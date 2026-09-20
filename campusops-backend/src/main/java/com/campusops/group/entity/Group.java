package com.campusops.group.entity;

import com.campusops.promotion.entity.Promotion;
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
@Table(
        name = "student_groups",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_group_promotion_nom", columnNames = {"promotion_id", "nom"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "promotion")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    /**
     * Année d'étude du groupe au sein de son cycle (1 = 1re année, 2 = 2e…),
     * bornée par {@code Level.nombreAnnees} de la promotion. Distingue par
     * exemple un groupe de M1 (1) d'un groupe de M2 (2) d'un même Master.
     * Renseignée à la création par la structure académique ; joue un rôle clé
     * dans la gestion et l'import des emplois du temps.
     *
     * <p><b>Migration non destructive ({@code ddl-auto=update})</b> : colonne
     * <b>nullable</b>. Les groupes préexistants prennent la valeur NULL puis
     * sont rétro-remplis par la structure académique lors de son exécution.
     */
    @Column(name = "annee_niveau")
    private Integer anneeNiveau;

    /**
     * Effectif du groupe : nombre d'étudiants qui le composent. Sert de base à
     * l'affectation des salles et à la recherche de disponibilité — la règle de
     * compatibilité étant {@code effectif du groupe <= capacité de la salle} —
     * ainsi qu'à la génération et l'import des emplois du temps.
     *
     * <p><b>Migration non destructive ({@code ddl-auto=update})</b> : colonne
     * <b>nullable</b>. Les groupes préexistants prennent la valeur NULL puis
     * sont rétro-remplis par la structure académique (valeur réaliste par
     * cycle) lors de son exécution ; l'administrateur peut ensuite l'ajuster.
     */
    @Column(name = "effectif")
    private Integer effectif;

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
