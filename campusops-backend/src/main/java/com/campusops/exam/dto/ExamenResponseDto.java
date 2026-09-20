package com.campusops.exam.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Représentation d'un examen daté renvoyée par l'API. Les clés étrangères sont
 * aplaties (id + libellé) pour un affichage direct côté frontend, dans le même
 * style que {@code ScheduleResponseDto} et {@code EmploiDuTempsResponseDto}.
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
public class ExamenResponseDto {

    private Long id;

    private LocalDate date;

    private Long timeSlotId;
    private String timeSlotNom;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime timeSlotHeureDebut;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime timeSlotHeureFin;

    private Long spaceId;
    private String spaceNom;
    private String spaceCode;

    private Long moduleId;
    private String moduleNom;

    private Long programId;
    private String programNom;

    private Long promotionId;
    private String promotionNom;

    // Null lorsque l'examen concerne toute la promotion.
    private Long groupId;
    private String groupNom;

    private Long semesterId;
    private String semesterNom;

    private Long academicYearId;
    private String academicYearLibelle;

    private Long sessionId;
    private String sessionNom;
    private String sessionCode;

    private String commentaire;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
