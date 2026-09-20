package com.campusops.occupation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Rapport de PRÉVISUALISATION d'un import d'<b>occupations supplémentaires</b>
 * (onglets « Planning soutenances » et « Autre »). Produit sans rien enregistrer :
 * l'utilisateur vérifie le résumé, corrige les lignes en erreur, puis confirme.
 *
 * <p>Rappelle le contexte choisi dans l'interface — catégorie, et éventuellement
 * filière / promotion — qui n'est PAS saisi dans le fichier mais rattaché
 * automatiquement à chaque ligne.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupationImportPreviewDto {

    private String fileName;

    // Rappel du contexte d'import (libellés lisibles).
    /** Libellé de la catégorie importée : « Soutenances » ou « Autres occupations ». */
    private String categorie;
    private String filiere;
    private String promotion;

    // Compteurs de synthèse.
    private int occupationsDetectees;
    private int occupationsValides;
    private int lignesEnErreur;
    /**
     * Lignes refusées par le moteur central de disponibilité : chevauchement avec
     * une occupation existante, une séance ou une réservation, mais aussi jour non
     * ouvrable, créneau hors horaires d'ouverture ou durée trop courte.
     */
    private int conflits;
    private int sallesInexistantes;
    private int sallesManquantes;
    private int datesInvalides;
    /** Lignes dont les heures sont illisibles, inversées ou hors horaires d'ouverture. */
    private int heuresInvalides;
    /** Lignes dont le type d'occupation n'appartient pas à la catégorie importée. */
    private int typesInvalides;
    /** Lignes référençant un groupe absent de la promotion du contexte. */
    private int groupesInexistants;

    /**
     * Vrai si l'import peut être confirmé : au moins une occupation détectée et
     * AUCUNE ligne en erreur. Un fichier contenant des erreurs n'est jamais
     * enregistré partiellement.
     */
    private boolean confirmable;

    /** Détail ligne par ligne (valides et en erreur). */
    private List<OccupationImportRowDto> lignes;
}
