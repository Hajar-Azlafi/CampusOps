package com.campusops.academicsession.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class SessionUniversitaireRequestDto {

    @NotBlank(message = "Le nom de la session est obligatoire")
    private String nom;

    @NotBlank(message = "Le code de la session est obligatoire")
    @Size(max = 40, message = "Le code ne doit pas dépasser 40 caractères")
    private String code;

    @NotNull(message = "L'ordre d'affichage est obligatoire")
    private Integer ordre;
}
