package com.campusops.occupation.dto;

import com.campusops.enums.OccupationCategorie;
import com.campusops.enums.OccupationType;
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
 * Représentation d'une <b>occupation supplémentaire</b> générique (soutenance ou
 * « autre ») renvoyée par l'API. Clés étrangères aplaties (id + libellé) pour un
 * affichage direct côté frontend, comme {@code ExamenResponseDto}.
 *
 * <p>Ce DTO ne couvre <b>pas</b> les examens : ceux-ci gardent leur propre
 * représentation enrichie (matière, semestre, session, créneau officiel).</p>
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
public class OccupationResponseDto {

    private Long id;

    private OccupationType type;
    /** Libellé français du type (« Soutenance », « Réunion »...). */
    private String typeLibelle;
    /** Catégorie déduite du type (EXAMEN / SOUTENANCE / AUTRE), pour l'onglet. */
    private OccupationCategorie categorie;

    private String intitule;

    private LocalDate date;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;
    private long dureeMinutes;

    private Long spaceId;
    private String spaceNom;
    private String spaceCode;

    // Rattachement académique facultatif.
    private Long programId;
    private String programNom;

    private Long promotionId;
    private String promotionNom;

    private Long groupId;
    private String groupNom;

    private Long academicYearId;
    private String academicYearLibelle;

    private String responsable;
    private String commentaire;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
