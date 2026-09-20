package com.campusops.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Ligne d'un fichier d'import de PLANNING D'EXAMENS, telle qu'analysee lors de la
 * phase de PREVISUALISATION (meme mecanique que l'import d'emploi du temps,
 * §5/§8/§24). Chaque ligne est renvoyee au frontend avec ses valeurs brutes, son
 * verdict ({@code valide}) et la liste des erreurs/conflits detectes, afin que le
 * responsable pedagogique puisse corriger le fichier avant de confirmer.
 *
 * <p>Contrairement a une seance recurrente (qui porte un <em>jour</em> de la
 * semaine), un examen est un <b>evenement date</b> : la colonne cle est donc une
 * <b>date</b> precise, et le public peut etre un groupe precis ou — colonne vide
 * — toute la promotion.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamenImportRowDto {

    /** Numero de ligne affichable dans le fichier (1 pour l'en-tete). */
    private int ligne;

    // Valeurs brutes lues dans le fichier (affichees telles quelles au RP).
    private String date;
    private String heureDebut;
    private String heureFin;
    private String module;
    private String salle;
    private String groupe;
    private String commentaire;

    /** Vrai si la ligne ne comporte aucune erreur ni conflit. */
    private boolean valide;

    /** Liste des messages d'erreur/conflit pour cette ligne (vide si valide). */
    private List<String> erreurs;
}
