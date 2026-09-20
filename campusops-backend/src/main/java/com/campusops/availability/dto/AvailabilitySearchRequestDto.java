package com.campusops.availability.dto;

import com.campusops.enums.SpaceType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Criteres de recherche des espaces disponibles (§6).
 *
 * <p>Seule la <b>date</b> est obligatoire. Les heures sont <b>optionnelles</b> :
 * sans elles, le moteur calcule les vraies periodes libres de la journee dans
 * les bornes d'ouverture (§6, §8, §11). La <b>duree souhaitee</b> est elle aussi
 * optionnelle et sert alors a ne garder que les espaces offrant un bloc libre
 * continu assez long. Les autres champs sont des filtres combinables (§18).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilitySearchRequestDto {

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    /** Heure de debut <b>optionnelle</b> (§6). Absente → periodes libres du jour. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    /** Heure de fin <b>optionnelle</b> (§6). */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    private Long buildingId;

    private Long floorId;

    private SpaceType type;

    @PositiveOrZero(message = "La capacité minimale ne peut pas être négative")
    private Integer capaciteMin;

    private List<Long> equipmentIds;

    /**
     * <b>Duree souhaitee</b>, en minutes, <b>optionnelle</b> (§ duree souhaitee).
     * Sert a optimiser la recherche quand aucune heure n'est saisie : seuls les
     * espaces disposant d'une periode libre <b>continue</b> d'au moins cette
     * duree sont retournes. Deux periodes libres separees par une occupation ne
     * sont jamais additionnees. Valeurs proposees par le client : 60, 90, 120,
     * 180, ou une valeur personnalisee.
     */
    @Positive(message = "La durée souhaitée doit être supérieure à zéro")
    private Integer dureeMinutes;

    /**
     * Requête textuelle considérée comme recherche "exacte" : si elle est fournie
     * et correspond exactement (code ou nom) à un espace réservé, alors cet
     * espace réservé sera inclus dans les résultats. Sinon les espaces réservés
     * restent exclus.
     */
    private String exactSpaceQuery;

    /**
     * Vrai si la plage horaire {@code [heureDebut, heureFin)} a ete explicitement
     * fournie par l'utilisateur (mode « recherche personnalisee » avec heures).
     * Faux → le moteur raisonne en periodes libres de la journee.
     */
    public boolean hasExplicitHours() {
        return heureDebut != null && heureFin != null;
    }

    /** Vrai si une duree souhaitee exploitable a ete fournie (§ duree souhaitee). */
    public boolean hasDuration() {
        return dureeMinutes != null && dureeMinutes > 0;
    }
}
