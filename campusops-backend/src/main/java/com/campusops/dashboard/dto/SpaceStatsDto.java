package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Statistiques centrees sur les espaces pedagogiques : classements d'utilisation,
 * taux d'occupation (global, par batiment, par etage), repartition par type et
 * capacite totale.
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
public class SpaceStatsDto {

    private List<SpaceUsageDto> espacesLesPlusUtilises;
    private List<SpaceUsageDto> espacesLesMoinsUtilises;

    private OccupancyRateDto tauxOccupationGlobal;
    private List<OccupancyRateDto> tauxOccupationParBatiment;
    private List<OccupancyRateDto> tauxOccupationParEtage;

    private List<CountItemDto> repartitionParType;

    /** Somme des capacites des espaces actifs. */
    private long capaciteTotale;
}
