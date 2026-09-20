package com.campusops.occupation.entity;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.enums.OccupationCategorie;
import com.campusops.enums.OccupationType;
import com.campusops.group.entity.Group;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.space.entity.Space;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <b>Occupation supplementaire d'un espace</b> : toute immobilisation d'une
 * salle ou d'un amphitheatre <em>en dehors</em> des emplois du temps reguliers
 * et des reservations — examen, soutenance, evenement, reunion, activite de
 * club, conference, etc.
 *
 * <p><b>Modele unique volontaire.</b> Les trois onglets du module
 * (« Planning examens », « Planning soutenances », « Autre ») ne sont pas trois
 * systemes independants : ils partagent cette table et donc la meme notion
 * d'occupation. Le moteur central {@code AvailabilityEngine} n'a besoin que
 * d'<b>une</b> requete ({@code date + espace}) pour connaitre toutes les
 * occupations supplementaires, quelle que soit leur nature. Une salle occupee
 * par un examen, une soutenance ou une reunion n'est donc jamais proposee comme
 * libre a la reservation.</p>
 *
 * <p><b>Heritage.</b> Strategie {@link InheritanceType#SINGLE_TABLE} avec le
 * discriminant {@code discriminant} : les lignes « generiques » (soutenances et
 * autres occupations) sont des instances de cette classe, tandis que les
 * examens sont des instances de {@code Examen}, sous-type qui ajoute le
 * contexte pedagogique complet (matiere, semestre, session universitaire,
 * creneau officiel). Une requete polymorphe sur cette classe ramene donc
 * <em>aussi</em> les examens.</p>
 *
 * <p><b>Horaires.</b> Les bornes {@code heureDebut}/{@code heureFin} sont
 * portees directement par l'occupation (saisie libre au format HH:mm, bornee par
 * les horaires d'ouverture derives des creneaux). Elles sont <em>toujours</em>
 * renseignees, y compris pour un examen ou elles recopient les bornes du
 * creneau choisi : le calcul de chevauchement est ainsi uniforme et ne depend
 * d'aucune jointure. Convention demi-ouverte {@code [debut, fin)}, identique
 * aux seances et aux reservations.</p>
 *
 * <p><b>Non destructif</b> (§29) : {@code occupations_supplementaires} est une
 * nouvelle table. Les colonnes propres aux sous-types sont necessairement
 * nullables en base ; leur caractere obligatoire est porte par les services
 * ({@code ExamenService} pour les examens,
 * {@code OccupationSupplementaireService} pour les autres).</p>
 */
@Entity
@Table(
        name = "occupations_supplementaires",
        indexes = {
                @Index(name = "idx_occ_supp_space_date", columnList = "space_id,date_occupation"),
                @Index(name = "idx_occ_supp_date", columnList = "date_occupation"),
                @Index(name = "idx_occ_supp_program", columnList = "program_id")
        }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminant", discriminatorType = DiscriminatorType.STRING, length = 40)
@DiscriminatorValue(OccupationSupplementaire.DISCRIMINATOR_GENERIQUE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "type", "date", "heureDebut", "heureFin", "intitule"})
public class OccupationSupplementaire {

    /** Discriminant des occupations generiques (soutenances et « autre »). */
    public static final String DISCRIMINATOR_GENERIQUE = "OCCUPATION";

    /** Discriminant des examens ({@code Examen}). */
    public static final String DISCRIMINATOR_EXAMEN = "EXAMEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nature precise de l'occupation. Porte la categorie (examen / soutenance /
     * autre) et sert de filtre unique pour les onglets du module.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_occupation", nullable = false, length = 40)
    private OccupationType type;

    /** Intitule affiche (sujet de la soutenance, nom de l'evenement, matiere...). */
    @Column(length = 180)
    private String intitule;

    /** Date de l'occupation : evenement <b>date</b>, jamais recurrent. */
    @Column(name = "date_occupation", nullable = false)
    private LocalDate date;

    /** Debut inclus, convention {@code [debut, fin)}. */
    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    /** Fin exclue, convention {@code [debut, fin)}. */
    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    /** Espace immobilise. Une occupation occupe toujours un espace. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    /**
     * Filiere de rattachement — perimetre du responsable pedagogique (§12).
     * Obligatoire pour un examen et une soutenance, facultative pour les autres
     * occupations (sans filiere, seul un administrateur peut les gerer).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    /** Promotion concernee, facultative hors examen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    /** Groupe concerne, ou {@code null} pour toute la promotion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    /** Annee universitaire de rattachement (filtrage temporel des listes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    /**
     * Responsable de l'activite, en texte libre : peut etre un intervenant
     * externe, un club ou un service, donc volontairement pas une cle etrangere
     * vers {@code users}. Jamais expose dans les messages de disponibilite (§16).
     */
    @Column(length = 150)
    private String responsable;

    /** Description / motif libre de l'occupation. */
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

    /** Categorie deduite du type : jamais stockee, donc jamais desynchronisee. */
    @Transient
    public OccupationCategorie getCategorie() {
        return type != null ? type.getCategorie() : null;
    }

    /** Duree en minutes, ou 0 si les bornes sont incoherentes. */
    @Transient
    public long getDureeMinutes() {
        if (heureDebut == null || heureFin == null || !heureFin.isAfter(heureDebut)) {
            return 0L;
        }
        return Duration.between(heureDebut, heureFin).toMinutes();
    }

    /** Vrai si cette occupation chevauche {@code [debut, fin)} (§9/§33). */
    @Transient
    public boolean overlaps(LocalTime debut, LocalTime fin) {
        return heureDebut != null && heureFin != null
                && heureDebut.isBefore(fin) && heureFin.isAfter(debut);
    }
}
