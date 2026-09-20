package com.campusops.timetable.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Creation d'un en-tete d'emploi du temps. Le contexte (annee, filiere, niveau,
 * promotion, groupe, semestre, session) est selectionne dans l'interface, jamais
 * saisi dans un fichier (cahier des charges §5/§24).
 *
 * <p>La periode de validite [debut, fin] n'est PAS saisie ici : elle est derivee
 * du semestre choisi (§20). Un emploi du temps — et donc ses seances — ne peut
 * couvrir que la fenetre de dates de son semestre.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploiDuTempsRequestDto {

    @NotNull(message = "L'année universitaire est obligatoire")
    private Long academicYearId;

    @NotNull(message = "La filière est obligatoire")
    private Long programId;

    @NotNull(message = "Le niveau est obligatoire")
    private Long levelId;

    @NotNull(message = "La promotion est obligatoire")
    private Long promotionId;

    @NotNull(message = "Le groupe est obligatoire")
    private Long groupId;

    @NotNull(message = "Le semestre est obligatoire")
    private Long semesterId;

    @NotNull(message = "La session universitaire est obligatoire")
    private Long sessionId;
}
