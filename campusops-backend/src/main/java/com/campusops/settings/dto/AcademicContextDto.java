package com.campusops.settings.dto;

import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.semester.dto.SemesterResponseDto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Contexte academique courant, expose <b>en lecture seule</b> par la page
 * Parametres (§4).
 *
 * <p>Le module Parametres ne recree ni annee universitaire ni semestre : il
 * reutilise les DTO des modules existants et delegue toute modification a
 * {@code AcademicYearService} / {@code SemesterService}, dont les regles metier
 * (une seule annee active a la fois, historique conserve, semestre inactif
 * refuse comme courant) restent la seule autorite.</p>
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
public class AcademicContextDto {

    /** Annee universitaire active, ou {@code null} si aucune n'est definie. */
    private AcademicYearResponseDto anneeActive;

    /**
     * Semestres actuellement courants. Un niveau peut en compter plusieurs
     * lorsque des cohortes d'annees differentes coexistent : la liste est donc
     * volontairement plurielle.
     */
    private List<SemesterResponseDto> semestresCourants;

    /** Nombre d'annees universitaires conservees dans l'historique. */
    private Integer nombreAnneesHistorisees;
}
