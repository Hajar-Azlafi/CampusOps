package com.campusops.space.entity;

import com.campusops.enums.LabSpeciality;
import com.campusops.enums.SpaceType;
import com.campusops.equipment.entity.Equipment;
import com.campusops.floor.entity.Floor;
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
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "spaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"floor", "equipments"})
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SpaceType type;

    /**
     * Spécialité d'un laboratoire (informatique, réseaux, physique, chimie…),
     * qui distingue les laboratoires entre eux au-delà du seul type
     * {@link SpaceType#LABORATORY}. Facultative : nulle pour les salles de
     * cours, amphithéâtres et autres espaces non spécialisés.
     *
     * <p><b>Migration non destructive ({@code ddl-auto=update})</b> : colonne
     * <b>nullable</b>. Les espaces existants prennent la valeur NULL ; les
     * laboratoires sont ensuite rétro-remplis par
     * {@code LabSpecialityBackfillInitializer} sans être supprimés ni recréés.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "speciality", length = 30)
    private LabSpeciality speciality;

    @Column(nullable = false)
    private Integer capacite;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean reserve = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "space_equipments",
            joinColumns = @JoinColumn(name = "space_id"),
            inverseJoinColumns = @JoinColumn(name = "equipment_id")
    )
    @Builder.Default
    private Set<Equipment> equipments = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
