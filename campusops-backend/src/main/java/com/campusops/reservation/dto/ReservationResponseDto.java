package com.campusops.reservation.dto;

import com.campusops.enums.ReservationPriority;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.ReservationType;
import com.campusops.enums.Role;
import com.campusops.enums.SpaceType;
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
public class ReservationResponseDto {

    private Long id;

    private Long spaceId;
    private String spaceNom;
    private String spaceCode;
    private SpaceType spaceType;
    private Integer spaceCapacite;
    private String buildingNom;
    private String floorNom;

    private Long userId;
    private String userNomComplet;
    private String userEmail;
    private Role userRole;

    private ReservationType type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    private String motif;
    private String commentaire;
    private ReservationStatus statut;
    private ReservationPriority priorite;
    private boolean actif;

    // Contexte pédagogique (peut être nul pour une réservation non pédagogique)
    private Long programId;
    private String programNom;
    private String programCode;
    private Long groupId;
    private String groupNom;
    private Long semesterId;
    private String semesterNom;
    private Long academicYearId;
    private String academicYearLibelle;
    private String contenuPedagogique;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
