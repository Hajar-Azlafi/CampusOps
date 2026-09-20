package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Statistiques generales du systeme : volumetrie des structures, des ressources,
 * des utilisateurs et des reservations recentes.
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
public class OverviewStatsDto {

    private long totalBatiments;
    private long totalEtages;
    private long totalEspaces;
    private long espacesActifs;
    private long espacesInactifs;
    private long totalEquipements;

    private long totalUtilisateurs;
    private long totalEnseignants;
    private long totalResponsablesClub;

    private long totalReservations;
    private long reservationsAujourdhui;
    private long reservationsCetteSemaine;
    private long reservationsCeMois;
}
