package com.campusops.level.dto;

import com.campusops.enums.TypeFormation;
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
public class LevelResponseDto {

    private Long id;
    private String nom;
    private Integer ordre;
    private TypeFormation typeFormation;
    private Integer nombreAnnees;
    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
