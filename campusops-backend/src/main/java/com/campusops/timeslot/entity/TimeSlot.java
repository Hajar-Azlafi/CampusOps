package com.campusops.timeslot.entity;

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
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Libellé optionnel du créneau, ex. « Créneau 1 » ou « Matinée ». */
    @Column(name = "nom")
    private String nom;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    /**
     * Ordre d'affichage chronologique (1, 2, 3, ...). Le référentiel affiche et
     * renvoie toujours les créneaux triés par {@code ordre} croissant (cahier
     * des charges §3), jamais dans un ordre aléatoire venu de la base.
     *
     * <p>Volontairement <b>nullable en base</b> pour rester non destructif
     * (§23) vis-à-vis des créneaux déjà présents : la colonne est ajoutée sans
     * contrainte {@code NOT NULL}, puis rétro-remplie au démarrage par
     * {@code TimeSlotInitializer}. Tout nouveau créneau reçoit un ordre côté
     * service.</p>
     */
    @Column(name = "ordre")
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
