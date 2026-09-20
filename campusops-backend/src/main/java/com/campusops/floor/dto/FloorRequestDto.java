package com.campusops.floor.dto;

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
public class FloorRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le code est obligatoire")
    private String code;

    @NotNull(message = "Le numéro est obligatoire")
    @Min(value = 0, message = "Le numéro doit être supérieur ou égal à 0")
    private Integer numero;

    private String description;

    @NotNull(message = "Le bâtiment est obligatoire")
    private Long buildingId;
}
