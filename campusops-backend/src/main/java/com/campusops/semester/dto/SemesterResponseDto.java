package com.campusops.semester.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
public class SemesterResponseDto {

    private Long id;
    private String nom;
    private Integer ordre;
    private Long levelId;
    private String levelNom;
    private boolean actif;
    /**
     * Semestre courant de son niveau. Un niveau peut en avoir plusieurs
     * simultanés (années coexistantes). {@code Boolean} (et non {@code boolean})
     * pour tolérer les lignes historiques à NULL sans NPE au mapping ; interprété
     * comme « non courant » côté client.
     */
    private Boolean courant;
    /** Début de la période de validité du semestre (peut être nul). */
    private LocalDate dateDebut;
    /** Fin de la période de validité du semestre (peut être nul). */
    private LocalDate dateFin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
