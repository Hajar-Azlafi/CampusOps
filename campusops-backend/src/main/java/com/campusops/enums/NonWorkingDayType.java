package com.campusops.enums;

/**
 * Nature d'une journee non ouvrable du calendrier universitaire (§4).
 *
 * <p>Le calendrier est volontairement generique afin de convenir a n'importe
 * quelle universite : aucune date n'est codee en dur dans le code metier, tout
 * est administrable. Le type ne sert qu'a classer / expliquer la fermeture ;
 * c'est le drapeau {@code actif} de l'entite qui decide du blocage.</p>
 */
public enum NonWorkingDayType {

    /** Fete nationale a date fixe (ex. 1er janvier, 30 juillet). */
    FERIE_NATIONAL,

    /**
     * Fete religieuse dont la date depend de l'observation officielle : la date
     * enregistree est previsionnelle et doit rester facilement modifiable par
     * l'administrateur (§4).
     */
    FERIE_RELIGIEUX,

    /** Vacances universitaires (periode de plusieurs jours). */
    VACANCES,

    /** Fermeture exceptionnelle (greve, intemperies, evenement, travaux...). */
    FERMETURE_EXCEPTIONNELLE,

    /** Journee non ouvrable propre a l'etablissement. */
    PERSONNALISE
}
