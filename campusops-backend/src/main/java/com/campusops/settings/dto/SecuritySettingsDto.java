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
 * Politique de securite des comptes (§7).
 *
 * <p>Ces valeurs sont reellement appliquees : la complexite par
 * {@code PasswordService}, le verrouillage par {@code LoginAttemptService} lors
 * de l'authentification, l'expiration par le mecanisme existant
 * {@code mustChangePassword}. Aucun mot de passe n'est stocke en clair (BCrypt
 * inchange) et le fonctionnement du JWT n'est pas modifie.</p>
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
public class SecuritySettingsDto {

    @NotNull(message = "La durée de validité du mot de passe est obligatoire")
    @Min(value = 0, message = "La durée de validité du mot de passe ne peut pas être négative (0 = illimitée)")
    @Max(value = 3650, message = "La durée de validité du mot de passe ne peut pas dépasser 10 ans")
    private Integer dureeValiditeMotDePasseJours;

    @NotNull(message = "Le nombre maximal de tentatives de connexion est obligatoire")
    @Min(value = 0, message = "Le nombre maximal de tentatives doit être positif (0 = pas de verrouillage)")
    @Max(value = 100, message = "Le nombre maximal de tentatives ne peut pas dépasser 100")
    private Integer maxTentativesConnexion;

    @NotNull(message = "La durée de verrouillage est obligatoire")
    @Min(value = 1, message = "La durée de verrouillage doit être d'au moins 1 minute")
    @Max(value = 1440, message = "La durée de verrouillage ne peut pas dépasser 24 heures")
    private Integer dureeVerrouillageMinutes;

    @NotNull(message = "La longueur minimale du mot de passe est obligatoire")
    @Min(value = 6, message = "La longueur minimale du mot de passe doit être d'au moins 6 caractères")
    @Max(value = 64, message = "La longueur minimale du mot de passe ne peut pas dépasser 64 caractères")
    private Integer longueurMinMotDePasse;

    @NotNull(message = "L'exigence de majuscule est obligatoire")
    private Boolean majusculeObligatoire;

    @NotNull(message = "L'exigence de minuscule est obligatoire")
    private Boolean minusculeObligatoire;

    @NotNull(message = "L'exigence de chiffre est obligatoire")
    private Boolean chiffreObligatoire;

    @NotNull(message = "L'exigence de caractère spécial est obligatoire")
    private Boolean caractereSpecialObligatoire;
}
