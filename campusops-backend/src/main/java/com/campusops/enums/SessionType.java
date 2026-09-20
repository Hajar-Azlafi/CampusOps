package com.campusops.enums;

/**
 * Types de seance d'un emploi du temps (le "type de seance" du cahier des
 * charges). A NE PAS confondre avec la "session universitaire"
 * ({@link com.campusops.academicsession.entity.SessionUniversitaire} =
 * session normale / rattrapage / automne / printemps), qui est un concept
 * distinct modelise par une entite configurable.
 */
public enum SessionType {
    COURS,
    TD,
    TP,
    EXAMEN,
    AUTRE
}
