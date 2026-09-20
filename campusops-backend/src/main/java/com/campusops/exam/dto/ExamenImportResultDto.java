package com.campusops.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultat de la phase de CONFIRMATION d'un import de planning d'examens : les
 * examens valides ont ete enregistres (chacun est un evenement date autonome ;
 * il n'y a pas d'en-tete unique comme pour un emploi du temps).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamenImportResultDto {

    private String fileName;

    /** Nombre d'examens effectivement enregistres. */
    private int examensEnregistres;

    private String message;
}
