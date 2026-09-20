package com.campusops.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultat de la phase de CONFIRMATION d'un import d'emploi du temps : les
 * seances valides ont ete enregistrees et rattachees a l'en-tete
 * {@code EmploiDuTemps} (cree ou reutilise) du contexte.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableImportResultDto {

    /** Identifiant de l'en-tete d'emploi du temps cree ou complete. */
    private Long emploiDuTempsId;

    private String fileName;

    /** Nombre de seances effectivement enregistrees. */
    private int seancesEnregistrees;

    /** Vrai si l'en-tete d'emploi du temps a ete cree par cet import. */
    private boolean emploiDuTempsCree;

    private String message;
}
