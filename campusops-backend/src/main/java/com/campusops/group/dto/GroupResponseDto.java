package com.campusops.group.dto;

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
public class GroupResponseDto {

    private Long id;
    private String nom;

    private Integer anneeNiveau;

    private Integer effectif;

    private Long promotionId;
    private String promotionNom;

    private Long programId;
    private String programNom;

    private Long levelId;
    private String levelNom;
    private Integer levelNombreAnnees;

    private Long academicYearId;
    private String academicYearLibelle;

    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
