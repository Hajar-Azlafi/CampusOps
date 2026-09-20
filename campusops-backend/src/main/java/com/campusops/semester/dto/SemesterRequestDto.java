package com.campusops.semester.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemesterRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotNull(message = "L'ordre est obligatoire")
    private Integer ordre;

    /**
     * Niveau/cycle du semestre. Optionnel : laisser vide pour un semestre
     * libre (Formation continue). Deux semestres de même nom sont autorisés
     * s'ils appartiennent à des niveaux différents.
     */
    private Long levelId;

    /**
     * Début de la période de validité du semestre (facultatif). Borne les dates
     * des emplois du temps et examens rattachés (§20).
     */
    private LocalDate dateDebut;

    /**
     * Fin de la période de validité du semestre (facultatif). Doit être
     * postérieure ou égale à {@code dateDebut}.
     */
    private LocalDate dateFin;
}
