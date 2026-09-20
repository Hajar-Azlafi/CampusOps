package com.campusops.module.dto;

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
public class ModuleRequestDto {

    @NotBlank(message = "Le nom du module est obligatoire")
    private String nom;

    @Size(max = 40, message = "Le code ne doit pas dépasser 40 caractères")
    private String code;

    private String description;

    @NotNull(message = "La filière (contexte) est obligatoire")
    private Long programId;

    @NotNull(message = "Le semestre (contexte) est obligatoire")
    private Long semesterId;
}
