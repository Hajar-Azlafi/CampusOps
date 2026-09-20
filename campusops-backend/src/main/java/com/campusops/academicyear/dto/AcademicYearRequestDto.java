package com.campusops.academicyear.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class AcademicYearRequestDto {

    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;

    @NotNull(message = "La date de début est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    /**
     * Optionnel : si vrai, la nouvelle année devient l'année active courante
     * (l'ancienne année active est automatiquement désactivée). Par défaut,
     * une nouvelle année est créée inactive afin de préserver l'année en cours.
     */
    private Boolean definirCommeActive;
}
