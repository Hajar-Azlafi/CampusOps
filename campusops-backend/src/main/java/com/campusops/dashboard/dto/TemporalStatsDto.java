package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Statistiques temporelles des reservations : activite par jour, semaine, mois
 * et annee, ainsi que les heures de forte occupation et les jours les plus charges.
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
public class TemporalStatsDto {

    /** Activite des 7 derniers jours. */
    private List<CountItemDto> activiteQuotidienne;

    /** Activite des 8 dernieres semaines. */
    private List<CountItemDto> activiteHebdomadaire;

    /** Activite des 12 derniers mois. */
    private List<CountItemDto> activiteMensuelle;

    /** Activite par annee. */
    private List<CountItemDto> activiteAnnuelle;

    /** Heures de forte occupation (par heure de debut de reservation). */
    private List<CountItemDto> heuresDeForteOccupation;

    /** Jours de la semaine les plus charges. */
    private List<CountItemDto> joursLesPlusCharges;
}
