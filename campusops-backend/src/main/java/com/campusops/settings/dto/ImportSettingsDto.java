package com.campusops.settings.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Politique d'import de fichiers (§9). Appliquee par
 * {@code ImportPolicyService}, appele en tete de chacun des cinq imports Excel
 * existants (utilisateurs, emplois du temps, seances, examens, occupations) : le
 * module ne cree aucun second systeme d'import.
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
public class ImportSettingsDto {

    @NotNull(message = "La taille maximale des fichiers est obligatoire")
    @Min(value = 1, message = "La taille maximale doit être supérieure à 0 Mo")
    @Max(value = 25, message = "La taille maximale ne peut pas dépasser 25 Mo")
    private Integer tailleMaxFichierMo;

    @NotEmpty(message = "Au moins un format de fichier doit être autorisé")
    private List<String> formatsAutorises;

    @NotNull(message = "L'option de validation automatique est obligatoire")
    private Boolean validationAutomatique;

    @NotNull(message = "L'option d'écrasement des données est obligatoire")
    private Boolean ecrasementDonneesAutorise;

    /**
     * Lecture seule : plafond technique du serveur (en Mo) impose par
     * {@code spring.servlet.multipart.max-file-size}. La taille configurable
     * ci-dessus ne peut pas le depasser.
     */
    private Integer plafondServeurMo;

    /** Lecture seule : formats que le systeme sait techniquement lire. */
    private List<String> formatsSupportes;
}
