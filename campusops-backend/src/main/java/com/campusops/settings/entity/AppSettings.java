package com.campusops.settings.entity;

import com.campusops.enums.AppTheme;
import com.campusops.enums.DateDisplayFormat;
import com.campusops.enums.ReminderFrequency;
import com.campusops.enums.TimeDisplayFormat;
import com.campusops.enums.WeekDay;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration globale <b>unique</b> de la plateforme (Module 11).
 *
 * <h2>Pourquoi une seule entite ?</h2>
 * <p>Le cahier des charges (§12) autorise plusieurs entites mais demande
 * d'abord d'analyser l'existant et d'eviter une multiplication inutile de
 * tables. Tous ces parametres sont des <b>scalaires</b> lus ensemble (page
 * Parametres) ou individuellement dans un chemin chaud (calcul de
 * disponibilite, authentification, import). Six tables a une ligne chacune
 * imposeraient six requetes et six invariants d'unicite a garantir. Une seule
 * table a <b>ligne unique</b> ({@link #SINGLETON_ID}) satisfait directement
 * l'exigence §3 « il ne doit pas etre possible d'avoir plusieurs configurations
 * globales actives simultanement » : l'unicite est structurelle, pas
 * conventionnelle. Les champs restent regroupes par section et les DTO sont, eux,
 * decoupes par section pour garder des formulaires et des endpoints cibles.</p>
 *
 * <p>Les seuls binaires (logo, favicon) sont isoles dans
 * {@link SettingsMedia} afin que la lecture tres frequente de cette ligne ne
 * charge jamais d'octets inutiles.</p>
 *
 * <h2>Ce que cette entite ne fait pas</h2>
 * <p>Elle ne duplique aucune logique existante (§2) : l'annee universitaire
 * active reste geree par {@code AcademicYearService}, les semestres courants par
 * {@code SemesterService} et la grille de creneaux par le module
 * {@code timeslot}. La configuration ne stocke donc ni annee, ni semestre, ni
 * creneau.</p>
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class AppSettings {

    /**
     * Identifiant fixe de la configuration : la table ne contient qu'une ligne.
     * L'identifiant n'est volontairement pas genere, ce qui rend impossible la
     * creation d'une seconde configuration par la couche de persistance.
     */
    public static final long SINGLETON_ID = 1L;

    /** Couleur principale par defaut : le bleu marine institutionnel du theme. */
    public static final String DEFAULT_COULEUR_PRINCIPALE = "#0B1D33";

    /** Couleur secondaire par defaut : l'accent d'action orange du theme. */
    public static final String DEFAULT_COULEUR_SECONDAIRE = "#E3A008";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private Long id = SINGLETON_ID;

    // ===================== §3 — Identite de l'universite =====================

    @Column(name = "nom", nullable = false)
    @Builder.Default
    private String nom = "CampusOps University";

    @Column(name = "nom_court")
    @Builder.Default
    private String nomCourt = "CampusOps";

    @Column(name = "slogan")
    private String slogan;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "ville")
    private String ville;

    @Column(name = "pays")
    @Builder.Default
    private String pays = "Maroc";

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "email")
    private String email;

    @Column(name = "site_web")
    private String siteWeb;

    /** Fuseau horaire IANA (ex. {@code Africa/Casablanca}). */
    @Column(name = "fuseau_horaire", nullable = false)
    @Builder.Default
    private String fuseauHoraire = "Africa/Casablanca";

    /** Code ISO de la devise affichee (ex. {@code MAD}). */
    @Column(name = "devise", nullable = false)
    @Builder.Default
    private String devise = "MAD";

    // ===================== §5 — Regles de reservation ========================

    /** Duree maximale d'une reservation, en minutes. */
    @Column(name = "duree_max_reservation_minutes", nullable = false)
    @Builder.Default
    private Integer dureeMaxReservationMinutes = 240;

    /**
     * Duree minimale exploitable d'un creneau, en minutes (0 = pas de plancher).
     * Remplace l'ancienne propriete {@code campusops.reservation.min-slot-minutes}
     * codee dans {@code application.properties} (§20).
     */
    @Column(name = "duree_min_reservation_minutes", nullable = false)
    @Builder.Default
    private Integer dureeMinReservationMinutes = 30;

    /**
     * Delai minimum entre l'instant de la demande et le debut du creneau
     * reserve, en minutes (0 = reservation immediate autorisee).
     */
    @Column(name = "delai_min_avant_reservation_minutes", nullable = false)
    @Builder.Default
    private Integer delaiMinAvantReservationMinutes = 0;

    /**
     * Nombre maximal de reservations <b>a venir</b> (en attente ou approuvees)
     * par utilisateur. 0 = illimite.
     */
    @Column(name = "max_reservations_par_utilisateur", nullable = false)
    @Builder.Default
    private Integer maxReservationsParUtilisateur = 10;

    /**
     * Autorise les reservations le samedi et le dimanche. Distinct des jours
     * ouvrables (§6) : l'universite peut ouvrir le samedi pour les cours tout en
     * interdisant les reservations ce jour-la.
     */
    @Column(name = "reservations_week_end_autorisees", nullable = false)
    @Builder.Default
    private boolean reservationsWeekEndAutorisees = true;

    /**
     * Autorise les reservations en dehors de la plage d'ouverture. Quand ce
     * drapeau est faux, un creneau debordant les bornes de la journee est refuse
     * (message explicite) et n'est jamais propose par la recherche.
     */
    @Column(name = "reservations_hors_horaires_autorisees", nullable = false)
    @Builder.Default
    private boolean reservationsHorsHorairesAutorisees = false;

    /** Duree maximale d'une seance planifiee (emploi du temps), en minutes. */
    @Column(name = "duree_max_seance_minutes", nullable = false)
    @Builder.Default
    private Integer dureeMaxSeanceMinutes = 240;

    // ===================== §6 — Horaires de fonctionnement ===================

    /**
     * Heure d'ouverture de reference. Les bornes reelles de la journee restent
     * derivees des creneaux actifs quand ils existent (module {@code timeslot}) ;
     * cette valeur sert de reference configurable et de repli.
     */
    @Column(name = "heure_ouverture", nullable = false)
    @Builder.Default
    private LocalTime heureOuverture = LocalTime.of(8, 30);

    /** Heure de fermeture de reference. */
    @Column(name = "heure_fermeture", nullable = false)
    @Builder.Default
    private LocalTime heureFermeture = LocalTime.of(18, 0);

    /**
     * Heure limite de recherche pour la journee en cours : passe cette heure, la
     * journee n'est plus proposee et la recherche demarre au lendemain.
     */
    @Column(name = "heure_limite_recherche", nullable = false)
    @Builder.Default
    private LocalTime heureLimiteRecherche = LocalTime.of(18, 30);

    /** Jours ouvrables de l'universite (par defaut lundi -> samedi). */
    @Convert(converter = WeekDaySetConverter.class)
    @Column(name = "jours_ouvrables", nullable = false)
    @Builder.Default
    private Set<WeekDay> joursOuvrables = EnumSet.of(
            WeekDay.LUNDI, WeekDay.MARDI, WeekDay.MERCREDI,
            WeekDay.JEUDI, WeekDay.VENDREDI, WeekDay.SAMEDI);

    // ===================== §7 — Securite / utilisateurs ======================

    /** Duree de validite d'un mot de passe, en jours (0 = illimitee). */
    @Column(name = "duree_validite_mot_de_passe_jours", nullable = false)
    @Builder.Default
    private Integer dureeValiditeMotDePasseJours = 0;

    /** Nombre maximal de tentatives de connexion echouees (0 = pas de verrouillage). */
    @Column(name = "max_tentatives_connexion", nullable = false)
    @Builder.Default
    private Integer maxTentativesConnexion = 5;

    /** Duree de verrouillage du compte apres depassement, en minutes. */
    @Column(name = "duree_verrouillage_minutes", nullable = false)
    @Builder.Default
    private Integer dureeVerrouillageMinutes = 15;

    /** Longueur minimale exigee pour un mot de passe. */
    @Column(name = "longueur_min_mot_de_passe", nullable = false)
    @Builder.Default
    private Integer longueurMinMotDePasse = 8;

    @Column(name = "majuscule_obligatoire", nullable = false)
    @Builder.Default
    private boolean majusculeObligatoire = true;

    @Column(name = "minuscule_obligatoire", nullable = false)
    @Builder.Default
    private boolean minusculeObligatoire = true;

    @Column(name = "chiffre_obligatoire", nullable = false)
    @Builder.Default
    private boolean chiffreObligatoire = true;

    @Column(name = "caractere_special_obligatoire", nullable = false)
    @Builder.Default
    private boolean caractereSpecialObligatoire = false;

    // ===================== §8 — Notifications ================================

    /** Interrupteur general des notifications applicatives. */
    @Column(name = "notifications_activees", nullable = false)
    @Builder.Default
    private boolean notificationsActivees = true;

    /**
     * Interrupteur des e-mails sortants. Complete la configuration SMTP
     * existante ({@code campusops.mail.enabled}) sans la remplacer : il suffit
     * qu'un des deux soit faux pour qu'aucun e-mail ne soit envoye.
     */
    @Column(name = "emails_actives", nullable = false)
    @Builder.Default
    private boolean emailsActives = true;

    /** Notifications emises automatiquement par le systeme (diffusions, rappels). */
    @Column(name = "notifications_automatiques_activees", nullable = false)
    @Builder.Default
    private boolean notificationsAutomatiquesActivees = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequence_rappels", nullable = false)
    @Builder.Default
    private ReminderFrequency frequenceRappels = ReminderFrequency.QUOTIDIENNE;

    // ===================== §9 — Imports ======================================

    /** Taille maximale acceptee pour un fichier importe, en megaoctets. */
    @Column(name = "taille_max_fichier_mo", nullable = false)
    @Builder.Default
    private Integer tailleMaxFichierMo = 5;

    /** Extensions autorisees a l'import (sans point). */
    @Convert(converter = LowerCaseListConverter.class)
    @Column(name = "formats_autorises", nullable = false)
    @Builder.Default
    private List<String> formatsAutorises = new java.util.ArrayList<>(List.of("xlsx", "xls"));

    /**
     * Validation automatique stricte : un fichier comportant au moins une ligne
     * invalide est refuse en bloc au lieu d'importer partiellement.
     */
    @Column(name = "validation_automatique", nullable = false)
    @Builder.Default
    private boolean validationAutomatique = true;

    /**
     * Autorise l'ecrasement des donnees existantes par l'import (mise a jour des
     * enregistrements deja presents au lieu de les signaler en doublon).
     */
    @Column(name = "ecrasement_donnees_autorise", nullable = false)
    @Builder.Default
    private boolean ecrasementDonneesAutorise = false;

    // ===================== §10 — Affichage ===================================

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_par_defaut", nullable = false)
    @Builder.Default
    private AppTheme themeParDefaut = AppTheme.SYSTEME;

    @Column(name = "couleur_principale", nullable = false)
    @Builder.Default
    private String couleurPrincipale = DEFAULT_COULEUR_PRINCIPALE;

    @Column(name = "couleur_secondaire", nullable = false)
    @Builder.Default
    private String couleurSecondaire = DEFAULT_COULEUR_SECONDAIRE;

    @Column(name = "pagination_activee", nullable = false)
    @Builder.Default
    private boolean paginationActivee = true;

    @Column(name = "elements_par_page", nullable = false)
    @Builder.Default
    private Integer elementsParPage = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_date", nullable = false)
    @Builder.Default
    private DateDisplayFormat formatDate = DateDisplayFormat.JJ_MM_AAAA;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_heure", nullable = false)
    @Builder.Default
    private TimeDisplayFormat formatHeure = TimeDisplayFormat.H24;

    // ===================== Tracabilite ======================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Configuration par defaut de la premiere installation (§19), entierement
     * renseignee : « CampusOps University », fuseau
     * {@code Africa/Casablanca}, devise MAD, 08:30 -> 18:00, lundi -> samedi,
     * dimanche desactive.
     *
     * <p>A utiliser <b>systematiquement</b> plutot que {@code new AppSettings()} :
     * Lombok deplace les valeurs de {@code @Builder.Default} dans le builder, si
     * bien qu'une instance obtenue par le constructeur sans argument aurait tous
     * ses champs nuls. Le constructeur sans argument reste requis par JPA, qui
     * renseigne ensuite chaque colonne depuis la base.</p>
     */
    public static AppSettings defaults() {
        return AppSettings.builder().build();
    }
}
