package com.campusops.user.dto;

import com.campusops.enums.Role;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
public class UserResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String department;
    private String phoneNumber;

    @JsonProperty("isActive")
    private boolean isActive;

    /**
     * Indique si l'e-mail d'identifiants a bien ete envoye lors de la creation.
     * Champ non sensible (aucun mot de passe). Present uniquement dans la reponse
     * de creation ({@code true}/{@code false}), omis ailleurs.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Boolean emailSent;

    /** Mot de passe temporaire, renseigne uniquement lors d'une creation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String temporaryPassword;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}