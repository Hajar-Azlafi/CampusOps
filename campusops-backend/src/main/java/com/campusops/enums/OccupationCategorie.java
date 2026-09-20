package com.campusops.enums;

/**
 * Grande famille d'<b>occupation supplementaire</b> d'un espace, en dehors des
 * emplois du temps reguliers et des reservations.
 *
 * <p>Les trois valeurs correspondent exactement aux trois onglets du module
 * « Occupation supplementaire » cote frontend : <em>Planning examens</em>,
 * <em>Planning soutenances</em> et <em>Autre</em>. Elles ne definissent
 * <b>aucune</b> regle de disponibilite propre : quelle que soit la categorie,
 * une occupation passe par le meme moteur central
 * ({@code AvailabilityEngine}) et bloque donc la salle de la meme facon.</p>
 */
public enum OccupationCategorie {

    /** Examens dates (session normale ou rattrapage). */
    EXAMEN,

    /** Soutenances (PFE, stage, memoire...). */
    SOUTENANCE,

    /**
     * Occupations ponctuelles diverses : evenements, reunions, activites
     * pedagogiques, activites de clubs, conferences, etc.
     */
    AUTRE
}
