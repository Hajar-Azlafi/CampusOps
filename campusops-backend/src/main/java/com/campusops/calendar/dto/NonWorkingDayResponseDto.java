package com.campusops.calendar.dto;

import com.campusops.enums.NonWorkingDayType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class NonWorkingDayResponseDto {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private String libelle;
    private NonWorkingDayType type;
    private String typeLibelle;
    private boolean previsionnel;
    private boolean recurrent;
    private String commentaire;
    private boolean actif;

    /** Nombre de jours fermes (bornes incluses), pratique pour l'affichage. */
    private Integer nombreJours;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
