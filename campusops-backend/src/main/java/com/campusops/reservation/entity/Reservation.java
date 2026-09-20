package com.campusops.reservation.entity;

import com.campusops.enums.ReservationPriority;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.ReservationType;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.group.entity.Group;
import com.campusops.program.entity.Program;
import com.campusops.semester.entity.Semester;
import com.campusops.space.entity.Space;
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
import java.time.LocalTime;

/**
 * Reservation d'un espace pedagogique par un utilisateur autorise.
 * Une salle ne peut jamais etre reservee deux fois sur le meme creneau :
 * les conflits sont detectes automatiquement au moment de la creation,
 * de la modification et de la validation (voir ReservationService).
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"space", "user", "program", "group", "semester", "academicYear"})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ----- Contexte pédagogique (facultatif) -----
    // Renseigné notamment lorsqu'un responsable pédagogique demande une salle
    // pour une de SES filières. Toutes ces relations sont nullable : une
    // réservation « simple » (enseignant, club) peut ne pas en avoir.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(name = "contenu_pedagogique", columnDefinition = "TEXT")
    private String contenuPedagogique;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationType type;

    @Column(name = "date_reservation", nullable = false)
    private LocalDate date;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(nullable = false)
    private String motif;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReservationPriority priorite;

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
