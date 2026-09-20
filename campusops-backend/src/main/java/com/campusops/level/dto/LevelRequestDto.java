package com.campusops.level.dto;

import com.campusops.enums.TypeFormation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotNull(message = "L'ordre est obligatoire")
    private Integer ordre;

    @NotNull(message = "Le type de formation est obligatoire")
    private TypeFormation typeFormation;

    /**
     * Nombre d'années d'étude du cycle (facultatif). Laissé libre pour les
     * niveaux à durée non fixe (ex. Formation continue) ; borné entre 1 et 8
     * lorsqu'il est renseigné.
     */
    @Min(value = 1, message = "Le nombre d'années doit être au moins 1")
    @Max(value = 8, message = "Le nombre d'années ne peut pas dépasser 8")
    private Integer nombreAnnees;
}
