package com.campusops.promotion.dto;

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
public class PromotionRequestDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotNull(message = "La filière est obligatoire")
    private Long programId;

    @NotNull(message = "Le niveau est obligatoire")
    private Long levelId;

    @NotNull(message = "L'année universitaire est obligatoire")
    private Long academicYearId;
}
