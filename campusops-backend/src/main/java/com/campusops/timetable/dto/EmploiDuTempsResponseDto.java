package com.campusops.timetable.dto;

import com.campusops.enums.TimetableSource;
import com.campusops.enums.TimetableStatus;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue « en-tete » d'un emploi du temps, telle qu'affichee sur la page centrale
 * (cahier des charges §17) : filiere, niveau, groupe, semestre, session, annee,
 * statut, importe par, derniere modification.
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
public class EmploiDuTempsResponseDto {

    private Long id;

    private Long academicYearId;
    private String academicYearLibelle;

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

    private Long sessionId;
    private String sessionNom;

    private TimetableStatus statut;
    private TimetableSource source;

    /** Periode de validite (§20). Passee {@link #dateFin}, les salles sont liberees. */
    private LocalDate dateDebut;
    private LocalDate dateFin;

    /**
     * Vrai lorsque la date de fin est passee : l'emploi du temps est expire et
     * ses salles sont automatiquement liberees. Calcule par le service.
     */
    private boolean expire;

    private Long importeParId;
    private String importeParNom;

    private long nombreSeances;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
