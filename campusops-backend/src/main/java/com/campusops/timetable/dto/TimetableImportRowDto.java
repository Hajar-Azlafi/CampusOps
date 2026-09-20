package com.campusops.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Ligne d'un fichier d'import d'emploi du temps, telle qu'analysee lors de la
 * phase de PREVISUALISATION (cahier des charges §8/§24). Chaque ligne est
 * renvoyee au frontend avec ses valeurs brutes, son verdict ({@code valide}) et
 * la liste des erreurs/conflits detectes, afin que le responsable pedagogique
 * puisse corriger le fichier avant de confirmer l'import.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableImportRowDto {

    /** Numero de ligne affichable dans le fichier (1 pour l'en-tete). */
    private int ligne;

    // Valeurs brutes lues dans le fichier (affichees telles quelles au RP).
    private String jour;
    private String heureDebut;
    private String heureFin;
    private String module;
    private String type;
    private String typePresence;
    private String salle;
    private String enseignant;

    /** Vrai si la ligne ne comporte aucune erreur ni conflit. */
    private boolean valide;

    /** Liste des messages d'erreur/conflit pour cette ligne (vide si valide). */
    private List<String> erreurs;
}
