package com.campusops.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Compte cree lors d'un import Excel. Le mot de passe temporaire est retourne
 * une seule fois dans le rapport reserve a l'administrateur.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatedAccountDto {
    private String email;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String temporaryPassword;

    private boolean emailSent;
}