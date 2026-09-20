package com.campusops.availability.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Reponse complete d'une recherche de disponibilite. Encapsule le <b>contexte
 * de journee</b> (calcule une seule fois, §2, §19) et la liste des espaces.
 *
 * <p>Le contexte porte tout ce qu'il faut pour un affichage juste sans second
 * calcul cote client : jour ouvrable ou non et pourquoi (§3, §4), bornes
 * d'ouverture derivees des creneaux (§7), mode de recherche (§15), resolution
 * de l'annee universitaire couvrant la date (requete annexe), <b>validite de la
 * date recherchee</b> (§ dates ouvertes a la recherche) et <b>libelle de la date
 * concernee</b> a afficher en tete des resultats (§ ambiguite de date).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class AvailabilitySearchResponseDto {

    private LocalDate date;

    /**
     * Libelle francais de la date concernee, ex. « Mardi 1 septembre 2026 ».
     * Affiche en tete des resultats pour lever toute ambiguite sur le jour
     * reellement analyse (§ ambiguite de date).
     */
    private String libelleDate;

    /** Mode : {@code NOW}, {@code TODAY} ou {@code CUSTOM} (§10, §11, §6). */
    private String mode;

    /** Faux si dimanche ou jour ferie : aucune recherche/reservation possible (§3, §4). */
    private boolean jourOuvrable;

    /** Message expliquant la fermeture (dimanche/ferie), ou null. */
    private String messageFermeture;

    /** Code machine de fermeture : {@code DIMANCHE}, {@code SAMEDI}, {@code JOUR_FERIE}. */
    private String typeFermeture;

    // ----- Dates ouvertes a la recherche -----

    /**
     * Faux si la date demandee est refusee : date passee, journee en cours
     * terminee (heure limite depassee) ou date hors annee universitaire active.
     * Aucun espace n'est alors retourne, et {@link #messageDate} explique pourquoi.
     */
    private boolean dateValide;

    /** Motif de refus de la date, pret a afficher, ou null si la date est acceptee. */
    private String messageDate;

    /** Premiere date ouverte a la recherche : borne {@code min} du champ date. */
    private LocalDate dateMin;

    /** Derniere date ouverte a la recherche (fin de l'annee active), ou null. */
    private LocalDate dateMax;

    /**
     * Heure limite au-dela de laquelle la journee en cours n'est plus proposee
     * (Parametres &gt; Horaires, Module 11 §6 ; 18:30 a l'installation).
     */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureLimiteRecherche;

    /** Vrai si l'heure limite du jour est deja passee : la recherche demarre demain. */
    private boolean limiteJourneeDepassee;

    /** Message d'alerte de configuration (aucune annee active, annee terminee), ou null. */
    private String messageConfiguration;

    // ----- Horaires -----

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureOuverture;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFermeture;

    /** Plage horaire recherchee (mode CUSTOM avec heures), sinon null. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebutRecherche;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFinRecherche;

    /**
     * Fenetre reellement analysee dans la journee. Identique aux bornes
     * d'ouverture, sauf pour la journee en cours ou elle demarre a l'heure
     * courante : une periode deja passee n'est ni affichee ni reservable
     * (§ ambiguite de date).
     */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime fenetreJourDebut;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime fenetreJourFin;

    /** Message d'erreur de saisie (heures incoherentes, hors bornes, creneau trop court), ou null. */
    private String messageValidation;

    // ----- Duree -----

    /** Duree souhaitee retenue pour cette recherche, en minutes, ou null. */
    private Integer dureeSouhaiteeMinutes;

    /** Duree minimale exploitable d'une periode libre, en minutes (§ micro-creneaux). */
    private int dureeMinimaleMinutes;

    // ----- Annee universitaire -----

    /** Identifiant de l'annee universitaire couvrant la date (active ou non), ou null. */
    private Long anneeUniversitaireId;

    /** Libelle de l'annee couvrant la date, ou null. */
    private String anneeUniversitaireLibelle;

    /** Vrai si une annee universitaire couvre la date (requete annexe). */
    private boolean anneeUniversitaireResolue;

    /**
     * Message informatif quand aucune annee ne couvre la date : jamais « tout
     * libre », seulement une information (requete annexe).
     */
    private String messageAnneeUniversitaire;

    /** Annee universitaire <b>active</b>, qui borne les dates recherchables. */
    private Long anneeActiveId;

    private String anneeActiveLibelle;

    private LocalDate anneeActiveDebut;

    private LocalDate anneeActiveFin;

    /** Nombre d'espaces retournes. */
    private int total;

    private List<AvailableSpaceResponseDto> espaces;
}
