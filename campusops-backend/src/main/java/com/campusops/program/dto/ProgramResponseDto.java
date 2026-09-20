package com.campusops.program.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
public class ProgramResponseDto {

    private Long id;
    private String nom;
    private String code;
    private String description;

    /** Etat ACTIF / INACTIF de la filiere (desactivation logique). */
    private boolean actif;

    /**
     * Filiere reellement <b>utilisable</b> pour une nouvelle operation :
     * {@code actif} ET departement de rattachement actif. Permet au frontend
     * d'exclure la filiere d'un select operationnel et d'afficher un badge
     * « Inactif » sans avoir a recharger le departement.
     */
    private boolean utilisable;

    private Long departmentId;
    private String departmentNom;
    private String departmentCode;

    private Long levelId;
    private String levelNom;
    private com.campusops.enums.TypeFormation levelTypeFormation;

    private Long responsableId;
    private String responsableFirstName;
    private String responsableLastName;
    private String responsableEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
