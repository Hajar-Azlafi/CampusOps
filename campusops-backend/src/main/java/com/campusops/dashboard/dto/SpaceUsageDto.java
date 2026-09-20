package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Espace accompagne de son nombre d'utilisations (reservations bloquantes et
 * seances de l'emploi du temps), pour les classements les plus / moins utilises.
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
public class SpaceUsageDto {

    private Long spaceId;
    private String nom;
    private String code;
    private String buildingNom;
    private String floorNom;

    /** Nombre de seances de l'emploi du temps actives sur cet espace. */
    private long seances;

    /** Nombre de reservations (tous statuts confondus) sur cet espace. */
    private long reservations;

    /** Total des utilisations (seances + reservations). */
    private long totalUtilisations;
}
