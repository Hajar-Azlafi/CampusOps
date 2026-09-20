package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Taux d'occupation d'une entite (global, batiment ou etage) calcule a partir
 * de l'emploi du temps : rapport entre les creneaux occupes et les creneaux
 * theoriquement disponibles (nombre d'espaces x creneaux horaires x jours ouvres).
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
public class OccupancyRateDto {

    /** Identifiant de l'entite (batiment ou etage) ; null pour le taux global. */
    private Long id;

    private String nom;
    private String code;

    /** Nombre d'espaces actifs pris en compte. */
    private long nombreEspaces;

    /** Creneaux occupes (seances actives). */
    private long creneauxOccupes;

    /** Creneaux theoriquement disponibles. */
    private long creneauxDisponibles;

    /** Taux d'occupation en pourcentage, arrondi a une decimale. */
    private double tauxOccupation;
}
