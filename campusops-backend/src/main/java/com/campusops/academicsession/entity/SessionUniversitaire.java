package com.campusops.academicsession.entity;

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
 * Session universitaire configurable (ex. « Session normale », « Session
 * d'automne », « Session de printemps », « Session de rattrapage »).
 *
 * <p><strong>Concept distinct</strong> du « type de seance »
 * ({@link com.campusops.enums.SessionType} = Cours / TD / TP / Examen / Autre) :
 * la session decrit la <em>periode d'examen ou de deroulement</em> a laquelle
 * appartient un emploi du temps, tandis que le type de seance qualifie une
 * seance individuelle.</p>
 *
 * <p><strong>Generique</strong> (cahier des charges §26) : aucun nom
 * d'etablissement n'est code en dur. Chaque etablissement definit ses propres
 * sessions via l'administration.</p>
 */
@Entity
@Table(
        name = "academic_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_academic_session_code",
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
public class SessionUniversitaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Libelle lisible, ex. « Session normale ». */
    @Column(nullable = false)
    private String nom;

    /** Code court unique, ex. « NORMALE », « RATTRAPAGE ». */
    @Column(nullable = false, length = 40)
    private String code;

    /** Ordre d'affichage. */
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
