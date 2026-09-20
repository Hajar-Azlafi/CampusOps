package com.campusops.group.dto;

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
public class GroupRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotNull(message = "La promotion est obligatoire")
    private Long promotionId;

    /**
     * Année d'étude du groupe dans son cycle (1..{@code Level.nombreAnnees}).
     * Facultatif : borné entre 1 et 8 lorsqu'il est renseigné.
     */
    @Min(value = 1, message = "L'année du niveau doit être au moins 1")
    @Max(value = 8, message = "L'année du niveau ne peut pas dépasser 8")
    private Integer anneeNiveau;

    /**
     * Effectif du groupe (nombre d'étudiants). Facultatif ; borné entre 1 et 500
     * lorsqu'il est renseigné. Utilisé pour l'affectation des salles
     * (effectif ≤ capacité) et la génération des emplois du temps.
     */
    @Min(value = 1, message = "L'effectif doit être au moins 1")
    @Max(value = 500, message = "L'effectif ne peut pas dépasser 500")
    private Integer effectif;
}
