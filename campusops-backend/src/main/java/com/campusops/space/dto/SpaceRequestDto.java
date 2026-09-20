package com.campusops.space.dto;

import com.campusops.enums.LabSpeciality;
import com.campusops.enums.SpaceType;
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
public class SpaceRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le code est obligatoire")
    private String code;

    @NotNull(message = "Le type est obligatoire")
    private SpaceType type;

    /**
     * Spécialité du laboratoire (facultative ; pertinente pour les espaces de
     * type laboratoire, ignorée pour les salles de cours et amphithéâtres).
     */
    private LabSpeciality speciality;

    @NotNull(message = "La capacité est obligatoire")
    @Min(value = 1, message = "La capacité doit être supérieure à zéro")
    private Integer capacite;

    private String description;

    @NotNull(message = "L'étage est obligatoire")
    private Long floorId;
}
