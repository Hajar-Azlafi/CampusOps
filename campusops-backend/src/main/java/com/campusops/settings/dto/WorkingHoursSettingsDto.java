package com.campusops.settings.dto;

import com.campusops.enums.WeekDay;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Horaires de fonctionnement de l'universite (§6).
 *
 * <p>Ce fragment ne remplace PAS la grille de creneaux du module
 * {@code timeslot} : les bornes reelles de la journee restent derivees des
 * creneaux actifs quand il en existe. Les champs {@code creneauxDebut} /
 * {@code creneauxFin} sont exposes en lecture seule pour rendre cette
 * derivation visible a l'administrateur.</p>
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
public class WorkingHoursSettingsDto {

    @NotNull(message = "L'heure d'ouverture est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureOuverture;

    @NotNull(message = "L'heure de fermeture est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFermeture;

    @NotNull(message = "L'heure limite de recherche est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureLimiteRecherche;

    @NotEmpty(message = "Au moins un jour ouvrable doit être sélectionné")
    private Set<WeekDay> joursOuvrables;

    // ----- Lecture seule : etat reel de la grille de creneaux -----

    /** Debut du premier creneau actif, si la grille en definit un. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime creneauxDebut;

    /** Fin du dernier creneau actif, si la grille en definit un. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime creneauxFin;

    /** Libelles des creneaux actifs (« 08:30 - 10:25 »), pour information. */
    private List<String> creneauxActifs;
}
