package com.campusops.settings.dto;

import com.campusops.enums.AppTheme;
import com.campusops.enums.DateDisplayFormat;
import com.campusops.enums.TimeDisplayFormat;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Preferences d'affichage globales (§10 / §17).
 *
 * <p>Le theme clair/sombre existant n'est pas duplique : {@code themeParDefaut}
 * fournit seulement la valeur appliquee tant que l'utilisateur n'a pas choisi
 * lui-meme, et les deux couleurs alimentent les variables CSS deja consommees
 * par toute l'interface.</p>
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
public class DisplaySettingsDto {

    @NotNull(message = "Le thème par défaut est obligatoire")
    private AppTheme themeParDefaut;

    @NotNull(message = "La couleur principale est obligatoire")
    @Pattern(regexp = "^#([0-9a-fA-F]{6})$",
            message = "La couleur principale doit être un code hexadécimal valide (ex. #0B1D33)")
    private String couleurPrincipale;

    @NotNull(message = "La couleur secondaire est obligatoire")
    @Pattern(regexp = "^#([0-9a-fA-F]{6})$",
            message = "La couleur secondaire doit être un code hexadécimal valide (ex. #E3A008)")
    private String couleurSecondaire;

    @NotNull(message = "L'activation de la pagination est obligatoire")
    private Boolean paginationActivee;

    @NotNull(message = "Le nombre d'éléments par page est obligatoire")
    @Min(value = 5, message = "Le nombre d'éléments par page doit être d'au moins 5")
    @Max(value = 200, message = "Le nombre d'éléments par page ne peut pas dépasser 200")
    private Integer elementsParPage;

    @NotNull(message = "Le format de date est obligatoire")
    private DateDisplayFormat formatDate;

    @NotNull(message = "Le format d'heure est obligatoire")
    private TimeDisplayFormat formatHeure;

    // ----- Lecture seule : motifs derives, consommes par le frontend -----

    /** Motif de date correspondant a {@code formatDate} (ex. {@code dd/MM/yyyy}). */
    private String motifDate;

    /** Motif d'heure correspondant a {@code formatHeure} (ex. {@code HH:mm}). */
    private String motifHeure;
}
