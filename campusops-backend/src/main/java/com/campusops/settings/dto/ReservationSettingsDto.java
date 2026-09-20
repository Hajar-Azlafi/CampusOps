package com.campusops.settings.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regles globales de reservation (§5). Ces valeurs sont reellement appliquees
 * par {@code ReservationService} et {@code AvailabilityEngine} : aucune limite
 * equivalente ne subsiste en dur dans les services (§20).
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
public class ReservationSettingsDto {

    @NotNull(message = "La durée maximale d'une réservation est obligatoire")
    @Min(value = 15, message = "La durée maximale d'une réservation doit être d'au moins 15 minutes")
    @Max(value = 1440, message = "La durée maximale d'une réservation ne peut pas dépasser 24 heures")
    private Integer dureeMaxReservationMinutes;

    @NotNull(message = "La durée minimale d'une réservation est obligatoire")
    @Min(value = 0, message = "La durée minimale d'une réservation ne peut pas être négative")
    @Max(value = 480, message = "La durée minimale d'une réservation ne peut pas dépasser 8 heures")
    private Integer dureeMinReservationMinutes;

    @NotNull(message = "Le délai minimum avant réservation est obligatoire")
    @Min(value = 0, message = "Le délai minimum avant réservation ne peut pas être négatif")
    @Max(value = 10080, message = "Le délai minimum avant réservation ne peut pas dépasser 7 jours")
    private Integer delaiMinAvantReservationMinutes;

    @NotNull(message = "Le nombre maximal de réservations par utilisateur est obligatoire")
    @Min(value = 0, message = "Le nombre maximal de réservations doit être positif (0 = illimité)")
    @Max(value = 500, message = "Le nombre maximal de réservations ne peut pas dépasser 500")
    private Integer maxReservationsParUtilisateur;

    @NotNull(message = "L'autorisation des réservations le week-end est obligatoire")
    private Boolean reservationsWeekEndAutorisees;

    @NotNull(message = "L'autorisation des réservations hors horaires est obligatoire")
    private Boolean reservationsHorsHorairesAutorisees;

    @NotNull(message = "La durée maximale d'une séance est obligatoire")
    @Min(value = 15, message = "La durée maximale d'une séance doit être d'au moins 15 minutes")
    @Max(value = 1440, message = "La durée maximale d'une séance ne peut pas dépasser 24 heures")
    private Integer dureeMaxSeanceMinutes;
}
