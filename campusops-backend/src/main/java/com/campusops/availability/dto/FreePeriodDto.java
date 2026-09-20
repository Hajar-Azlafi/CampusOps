package com.campusops.availability.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Periode libre et reservable d'un espace (§8, §11), avec un libelle pret a
 * afficher (« 08:30 → 10:25 »).
 */
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
public class FreePeriodDto {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    /** Libelle pret a afficher, ex. « 08:30 → 10:25 ». */
    private String label;

    /** Duree en minutes, pratique pour l'affichage et le tri. */
    private long dureeMinutes;
}
