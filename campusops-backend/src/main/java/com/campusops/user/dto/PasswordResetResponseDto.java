package com.campusops.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Reponse d'une action administrateur sur le mot de passe d'un compte
 * (reinitialisation / renvoi des identifiants). Le mot de passe temporaire est
 * retourne une seule fois dans cette reponse reservee a l'administrateur.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetResponseDto {

    private String email;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String temporaryPassword;

    private boolean emailSent;
    private String message;
}