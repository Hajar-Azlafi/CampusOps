package com.campusops.enums;

/**
 * Origine d'une occupation de salle telle que calculee par le moteur central de
 * disponibilite (§1, §16).
 *
 * <p>Sert a expliquer <b>pourquoi</b> un espace est indisponible sans exposer
 * d'information personnelle sur les autres utilisateurs.</p>
 */
public enum OccupancySource {

    /** Seance d'un emploi du temps actif a cette date. */
    EMPLOI_DU_TEMPS,

    /**
     * Examen date occupant la salle — <b>occupation supplementaire</b> de
     * categorie {@link OccupationCategorie#EXAMEN}.
     */
    EXAMEN,

    /**
     * Soutenance occupant la salle — <b>occupation supplementaire</b> de
     * categorie {@link OccupationCategorie#SOUTENANCE}.
     */
    SOUTENANCE,

    /**
     * Autre occupation supplementaire ponctuelle (evenement, reunion, activite
     * pedagogique, activite de club, conference...), categorie
     * {@link OccupationCategorie#AUTRE}. Bloque la salle exactement comme un
     * examen : le moteur ne fait aucune difference de traitement.
     */
    AUTRE_OCCUPATION,

    /** Reservation acceptee : blocage definitif. */
    RESERVATION_ACCEPTEE,

    /**
     * Demande de reservation en attente de validation : ne bloque pas
     * definitivement mais doit etre signalee distinctement (§13).
     */
    RESERVATION_EN_ATTENTE
}
