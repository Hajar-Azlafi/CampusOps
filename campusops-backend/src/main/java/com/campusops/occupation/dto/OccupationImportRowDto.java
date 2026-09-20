package com.campusops.occupation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Ligne d'un fichier d'import d'<b>occupations supplémentaires</b> (soutenances
 * ou occupations « autre »), telle qu'analysée en phase de PRÉVISUALISATION.
 * Même mécanique que l'import d'examens ou d'emploi du temps : chaque ligne est
 * renvoyée avec ses valeurs brutes, son verdict ({@code valide}) et la liste des
 * erreurs, afin que l'utilisateur corrige le fichier avant de confirmer.
 *
 * <p>Une occupation supplémentaire est un <b>événement daté</b> à heures libres :
 * la ligne porte une date précise et un couple {@code heureDebut}/{@code heureFin}
 * au format {@code HH:mm} (aucun créneau officiel n'est imposé, contrairement à un
 * examen).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupationImportRowDto {

    /** Numéro de ligne affichable dans le fichier (1 pour l'en-tête). */
    private int ligne;

    // Valeurs brutes lues dans le fichier (affichées telles quelles).
    private String date;
    private String heureDebut;
    private String heureFin;
    private String salle;
    private String type;
    private String intitule;
    private String groupe;
    private String responsable;
    private String description;

    /** Vrai si la ligne ne comporte aucune erreur ni conflit. */
    private boolean valide;

    /** Messages d'erreur/conflit pour cette ligne (vide si valide). */
    private List<String> erreurs;
}
