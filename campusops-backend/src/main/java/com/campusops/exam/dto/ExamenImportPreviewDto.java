package com.campusops.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Rapport de PREVISUALISATION d'un import de planning d'examens (meme esprit que
 * {@code TimetableImportPreviewDto}, §8/§24). Produit sans rien enregistrer : le
 * RP verifie le resume, corrige les lignes en erreur si besoin, puis confirme.
 *
 * <p>Rappelle le contexte d'import (annee/filiere/promotion/semestre/session)
 * choisi dans l'interface — ce contexte n'est PAS present dans le fichier, il est
 * lie automatiquement (§5). Le <b>groupe</b> n'est PAS dans le contexte : il est
 * facultatif et se saisit ligne par ligne (vide = toute la promotion).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamenImportPreviewDto {

    private String fileName;

    // Rappel du contexte d'import (libelles lisibles).
    private String anneeUniversitaire;
    private String filiere;
    private String niveau;
    private String promotion;
    private String semestre;
    private String session;

    // Compteurs de synthese (§8/§24).
    private int examensDetectes;
    private int examensValides;
    private int lignesEnErreur;
    private int conflits;
    private int sallesInexistantes;
    private int sallesManquantes;
    private int datesInvalides;
    /** Lignes dont les heures ne correspondent a aucun creneau configure (§21). */
    private int creneauxInexistants;
    /** Lignes referencant un module absent/desactive dans le contexte (§21). */
    private int modulesInexistants;
    /** Lignes referencant un groupe absent de la promotion. */
    private int groupesInexistants;

    /**
     * Vrai si l'import peut etre confirme : au moins un examen detecte et AUCUNE
     * ligne en erreur. Un fichier contenant des erreurs critiques n'est jamais
     * enregistre automatiquement (§8).
     */
    private boolean confirmable;

    /** Detail ligne par ligne (valides et en erreur). */
    private List<ExamenImportRowDto> lignes;
}
