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
 * <b>Regles de saisie d'une recherche de disponibilite</b>, calculees cote
 * serveur et consommees par le formulaire : bornes du champ date, heure limite
 * de la journee en cours, horaires d'ouverture, durees proposees et creneaux
 * officiels.
 *
 * <p>Le client ne recalcule aucune de ces regles : il borne son formulaire avec
 * ce que le serveur annonce, et le serveur revalide de toute facon a chaque
 * recherche puis a chaque reservation (§2, §19).</p>
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
public class SearchWindowDto {

    /** Date du jour cote serveur (reference des regles de date). */
    private LocalDate aujourdHui;

    /** Premiere date ouverte a la recherche : borne {@code min} du champ date. */
    private LocalDate dateMin;

    /** Derniere date ouverte a la recherche, ou null si non bornee. */
    private LocalDate dateMax;

    /** Libelle francais de {@link #dateMin}, ex. « Mardi 1 septembre 2026 ». */
    private String libelleDateMin;

    /** Libelle francais de {@link #dateMax}, ou null. */
    private String libelleDateMax;

    /** Date proposee par defaut dans le formulaire (= {@link #dateMin}). */
    private LocalDate dateParDefaut;

    /** Heure limite de recherche pour la journee en cours. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureLimiteRecherche;

    /** Vrai si l'heure limite est passee : la journee en cours n'est plus proposee. */
    private boolean limiteJourneeDepassee;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureOuverture;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFermeture;

    /** Duree minimale exploitable d'un creneau, en minutes (§ micro-creneaux). */
    private int dureeMinimaleMinutes;

    /** Durees proposees par le selecteur « duree souhaitee », en minutes. */
    private List<Integer> dureesProposees;

    /** Creneaux officiels actifs, proposables a la reservation. */
    private List<OfficialSlotDto> creneaux;

    private Long anneeActiveId;

    private String anneeActiveLibelle;

    private LocalDate anneeActiveDebut;

    private LocalDate anneeActiveFin;

    /**
     * Message d'alerte de configuration : aucune annee universitaire active, ou
     * annee active deja terminee (plus aucune date recherchable). Null si tout
     * est coherent.
     */
    private String messageConfiguration;

    /** Faux si aucune date n'est ouverte a la recherche (annee active terminee). */
    private boolean exploitable;
}
