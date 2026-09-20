package com.campusops.calendar.dto;

import com.campusops.enums.NonWorkingDayType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Creation / modification d'une journee (ou periode) non ouvrable (§4).
 * {@code dateFin} est facultative : elle vaut {@code dateDebut} par defaut.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NonWorkingDayRequestDto {

    @NotNull(message = "La date de début est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    /** Facultative : pour une periode (vacances). Par defaut = dateDebut. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;

    private NonWorkingDayType type;

    /** Date previsionnelle (fete religieuse non encore confirmee). */
    private Boolean previsionnel;

    /** Vrai pour un jour férié fixe réutilisé chaque année. */
    private Boolean recurrent;

    private String commentaire;
}
