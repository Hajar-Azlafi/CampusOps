package com.campusops.module.dto;

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
public class ModuleResponseDto {

    private Long id;
    private String nom;
    private String code;
    private String description;

    // Contexte pédagogique dénormalisé pour l'affichage frontend.
    private Long programId;
    private String programNom;
    private String programCode;
    private Long semesterId;
    private String semesterNom;

    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
