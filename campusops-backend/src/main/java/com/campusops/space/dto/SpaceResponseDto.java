package com.campusops.space.dto;

import com.campusops.enums.LabSpeciality;
import com.campusops.enums.SpaceType;
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
public class SpaceResponseDto {

    private Long id;
    private String nom;
    private String code;
    private SpaceType type;
    private LabSpeciality speciality;
    private Integer capacite;
    private String description;
    private boolean actif;
    private boolean reserve;

    private Long floorId;
    private String floorNom;
    private Integer floorNumero;

    private Long buildingId;
    private String buildingNom;
    private String buildingCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
