package com.campusops.settings.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identite de l'universite (§3 / §16). Sert a la fois de corps de requete
 * ({@code PUT /api/settings/university}) et de fragment de reponse.
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
public class UniversitySettingsDto {

    @NotBlank(message = "Le nom de l'université est obligatoire")
    @Size(max = 150, message = "Le nom de l'université ne peut pas dépasser 150 caractères")
    private String nom;

    @Size(max = 40, message = "Le nom court ne peut pas dépasser 40 caractères")
    private String nomCourt;

    @Size(max = 180, message = "Le slogan ne peut pas dépasser 180 caractères")
    private String slogan;

    @Size(max = 255, message = "L'adresse ne peut pas dépasser 255 caractères")
    private String adresse;

    @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères")
    private String ville;

    @Size(max = 100, message = "Le pays ne peut pas dépasser 100 caractères")
    private String pays;

    @Pattern(
            regexp = "^$|^[+0-9][0-9 .\\-()]{5,24}$",
            message = "Le numéro de téléphone n'est pas valide (chiffres, espaces, + . - ( ) autorisés)")
    private String telephone;

    @Email(message = "L'adresse e-mail n'est pas valide")
    @Size(max = 150, message = "L'adresse e-mail ne peut pas dépasser 150 caractères")
    private String email;

    @Pattern(
            regexp = "^$|^https?://[^\\s]{3,}$",
            message = "Le site web doit être une URL valide commençant par http:// ou https://")
    private String siteWeb;

    @NotBlank(message = "Le fuseau horaire est obligatoire")
    private String fuseauHoraire;

    private String devise;
}
