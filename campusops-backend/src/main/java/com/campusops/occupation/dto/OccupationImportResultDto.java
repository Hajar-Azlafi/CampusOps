package com.campusops.occupation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Résultat de la phase de CONFIRMATION d'un import d'occupations
 * supplémentaires : chaque occupation valide a été enregistrée comme un
 * événement daté autonome (il n'y a pas d'en-tête unique comme pour un emploi du
 * temps).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupationImportResultDto {

    private String fileName;

    /** Nombre d'occupations effectivement enregistrées. */
    private int occupationsEnregistrees;

    private String message;
}
