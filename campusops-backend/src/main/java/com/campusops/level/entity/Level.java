package com.campusops.level.entity;

import com.campusops.enums.TypeFormation;
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
@Table(name = "levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(nullable = false)
    private Integer ordre;

    /** Type de formation (initiale ou continue) auquel appartient ce cycle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_formation", length = 20)
    @Builder.Default
    private TypeFormation typeFormation = TypeFormation.INITIALE;

    /**
     * Nombre d'années d'étude que dure ce cycle (ex. Licence = 1, Master = 2,
     * Tronc commun = 2, Cycle ingénieur = 3). Source de vérité qui pilote le
     * nombre de semestres générés (2 × {@code nombreAnnees}) et le nombre
     * d'années d'étude des groupes, en remplacement de l'ancienne déduction
     * codée en dur par nom de niveau.
     *
     * <p><b>Migration non destructive ({@code ddl-auto=update})</b> : colonne
     * <b>nullable</b>. Les niveaux préexistants prennent la valeur NULL ; la
     * couche de structure académique retombe alors sur une déduction par nom
     * pour rester rétro-compatible.
     */
    @Column(name = "nombre_annees")
    private Integer nombreAnnees;

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
