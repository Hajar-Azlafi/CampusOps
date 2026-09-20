package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Statistiques des reservations : volumes par statut, repartition par type et
 * classement des utilisateurs les plus actifs.
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
public class ReservationStatsDto {

    private long total;
    private long approuvees;
    private long refusees;
    private long annulees;
    private long enAttente;
    private long terminees;

    private List<CountItemDto> repartitionParType;

    /** Classement des utilisateurs les plus actifs (5 premiers). */
    private List<UserActivityDto> utilisateursLesPlusActifs;

    /**
     * Nombre total d'utilisateurs ayant au moins une reservation sur la periode :
     * permet d'indiquer combien d'utilisateurs le classement ne montre pas.
     */
    private long nombreUtilisateursActifs;
}
