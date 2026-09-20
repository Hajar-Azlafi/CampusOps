package com.campusops.calendar.entity;

import com.campusops.enums.NonWorkingDayType;
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
 * Journee (ou periode) non ouvrable du calendrier universitaire (§4-§5).
 *
 * <p>Une entree couvre l'intervalle <b>ferme</b> {@code [dateDebut, dateFin]}.
 * Pour un jour unique, {@code dateFin} vaut {@code dateDebut} (le service
 * normalise automatiquement une {@code dateFin} nulle). Une periode permet de
 * declarer des vacances universitaires en une seule ligne.</p>
 *
 * <p>Aucune date n'est codee en dur dans la logique metier : le calendrier est
 * entierement administrable (CRUD ADMIN), ce qui rend l'application utilisable
 * par n'importe quelle universite. Les fetes religieuses, dont la date reelle
 * depend de l'observation officielle, sont enregistrees comme
 * <b>previsionnelles</b> ({@link #previsionnel}) et restent modifiables.</p>
 *
 * <p>Seules les entrees <b>actives</b> bloquent : desactiver une entree la
 * conserve dans l'historique sans fermer le campus (non destructif).</p>
 */
@Entity
@Table(
        name = "non_working_days",
        indexes = {
                @Index(name = "idx_nwd_date_debut", columnList = "date_debut"),
                @Index(name = "idx_nwd_date_fin", columnList = "date_fin")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class NonWorkingDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Premier jour ferme (inclus). */
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Dernier jour ferme (inclus). Egal a {@link #dateDebut} pour un jour seul. */
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private NonWorkingDayType type = NonWorkingDayType.PERSONNALISE;

    /**
     * Date previsionnelle : vrai pour les fetes dont la date officielle n'est
     * pas encore confirmee (fetes religieuses). Purement informatif pour
     * l'affichage ; le blocage depend de {@link #actif}.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean previsionnel = false;

    /** Vrai pour un jour fixe qui se répète chaque année, indépendamment de l'année. */
    @Column(nullable = false)
    @Builder.Default
    private boolean recurrent = false;

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

    /** Vrai si la date fournie tombe dans l'intervalle ferme (bornes incluses). */
    public boolean covers(LocalDate date) {
        if (date == null || dateDebut == null) {
            return false;
        }
        if (recurrent) {
            return date.getMonthValue() == dateDebut.getMonthValue()
                    && date.getDayOfMonth() == dateDebut.getDayOfMonth();
        }
        LocalDate fin = (dateFin != null) ? dateFin : dateDebut;
        return !date.isBefore(dateDebut) && !date.isAfter(fin);
    }

    public boolean overlaps(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || dateDebut == null) {
            return false;
        }
        if (recurrent) {
            LocalDate cursor = debut;
            while (!cursor.isAfter(fin)) {
                if (covers(cursor)) {
                    return true;
                }
                cursor = cursor.plusDays(1);
            }
            return false;
        }
        LocalDate end = dateFin != null ? dateFin : dateDebut;
        return !dateDebut.isAfter(fin) && !end.isBefore(debut);
    }
}
