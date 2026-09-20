package com.campusops.typeseance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class TypeSeanceRequestDto {

    @NotBlank(message = "Le nom du type de séance est obligatoire")
    private String nom;

    @NotBlank(message = "Le code du type de séance est obligatoire")
    @Size(max = 40, message = "Le code ne doit pas dépasser 40 caractères")
    private String code;

    /** Couleur hexadécimale optionnelle, ex. « #2563EB » ou « #FFF ». */
    @Pattern(
            regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$",
            message = "La couleur doit être au format hexadécimal (ex. #2563EB)"
    )
    private String couleur;

    @NotNull(message = "L'ordre d'affichage est obligatoire")
    private Integer ordre;
}
