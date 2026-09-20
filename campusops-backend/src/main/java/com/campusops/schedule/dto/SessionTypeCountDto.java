package com.campusops.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Comptage des seances pour UN type de seance donne (cahier des charges §6/§19).
 *
 * <p>Les statistiques sont <b>reelles</b> : la valeur reflete le nombre exact de
 * seances du perimetre courant, jamais une donnee fictive. Un type sans aucune
 * seance apparait avec {@code value = 0} (ex. « Examen » = 0 tant qu'aucun
 * examen n'est programme) plutot que d'etre masque.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionTypeCountDto {

    /**
     * Identifiant du type de seance configurable. {@code null} pour le seul
     * agregat de repli « Non categorise » (seances dont le type n'a pu etre
     * rattache a aucun type connu).
     */
    private Long typeSeanceId;

    /** Code court du type (ex. « COURS », « EXAMEN »). */
    private String code;

    /** Libelle affichable (nom du type, ou libelle de l'agregat de repli). */
    private String label;

    /** Couleur d'affichage hexadecimale du type (facultative), pour le widget. */
    private String couleur;

    /** Vrai si le type est actif ; faux pour un type desactive mais encore utilise. */
    private boolean actif;

    /** Nombre de seances de ce type dans le perimetre + contexte demandes. */
    private long value;
}
