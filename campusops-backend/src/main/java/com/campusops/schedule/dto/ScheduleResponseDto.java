package com.campusops.schedule.dto;

import com.campusops.enums.PresenceType;
import com.campusops.enums.SessionType;
import com.campusops.enums.WeekDay;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

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
public class ScheduleResponseDto {

    private Long id;
    private WeekDay jour;

    private Long timeSlotId;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime timeSlotHeureDebut;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime timeSlotHeureFin;

    private Long spaceId;
    private String spaceNom;
    private String spaceCode;

    private Long programId;
    private String programNom;

    private Long levelId;
    private String levelNom;

    private Long promotionId;
    private String promotionNom;

    private Long groupId;
    private String groupNom;

    private Long semesterId;
    private String semesterNom;

    private Long academicYearId;
    private String academicYearLibelle;

    private String enseignant;
    private String matiere;

    // Module d'enseignement de rattachement (null si séance en matière libre).
    private Long moduleId;
    private String moduleNom;

    // Type de séance historique (enum) conservé pour compatibilité.
    private SessionType type;

    // Type de séance configurable (§7) : null pour d'éventuelles séances legacy
    // non encore rétro-associées.
    private Long typeSeanceId;
    private String typeSeanceNom;
    private String typeSeanceCouleur;

    private PresenceType typePresence;
    private String commentaire;
    private boolean actif;

    // En-tête d'emploi du temps de rattachement (null si séance isolée).
    private Long emploiDuTempsId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
