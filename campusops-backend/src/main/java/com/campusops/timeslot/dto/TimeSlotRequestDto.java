package com.campusops.timeslot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlotRequestDto {

    /** Libellé optionnel (ex. « Créneau 1 »). */
    private String nom;

    @NotNull(message = "L'heure de début est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @NotNull(message = "L'heure de fin est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    /** Ordre d'affichage (optionnel : auto-attribué en fin de liste si absent). */
    private Integer ordre;
}
