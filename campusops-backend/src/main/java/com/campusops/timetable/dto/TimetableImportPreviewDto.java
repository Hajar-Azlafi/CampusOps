package com.campusops.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Rapport de PREVISUALISATION d'un import d'emploi du temps (cahier des charges
 * §8/§24). Produit sans rien enregistrer : le RP verifie le resume, corrige les
 * lignes en erreur si besoin, puis confirme.
 *
 * <p>Rappelle le contexte d'import (annee/filiere/niveau/promotion/groupe/
 * semestre/session) choisi dans l'interface — ce contexte n'est PAS present dans
 * le fichier, il est lie automatiquement (§5).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableImportPreviewDto {

    private String fileName;

    // Rappel du contexte d'import (libelles lisibles).
    private String anneeUniversitaire;
    private String filiere;
    private String niveau;
    private String promotion;
    private String groupe;
    private String semestre;
    private String session;

    // Compteurs de synthese (§8/§24).
    private int seancesDetectees;
    private int seancesValides;
    private int lignesEnErreur;
    private int conflits;
    private int sallesInexistantes;
    private int sallesManquantes;
    private int horairesInvalides;
    /** Lignes dont les heures ne correspondent à aucun créneau configuré (§21). */
    private int creneauxInexistants;
    /** Lignes référençant un module absent/désactivé dans le contexte (§21). */
    private int modulesInexistants;

    /**
     * Vrai si l'import peut etre confirme : au moins une seance detectee et
     * AUCUNE ligne en erreur. Un fichier contenant des erreurs critiques n'est
     * jamais enregistre automatiquement (§8).
     */
    private boolean confirmable;

    /** Detail ligne par ligne (valides et en erreur). */
    private List<TimetableImportRowDto> lignes;
}
