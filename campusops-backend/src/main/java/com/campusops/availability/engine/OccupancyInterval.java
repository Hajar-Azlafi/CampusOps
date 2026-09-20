package com.campusops.availability.engine;

import com.campusops.enums.OccupancySource;

import java.time.LocalTime;

/**
 * Intervalle horaire pendant lequel un espace est occupe, avec sa source (§16).
 *
 * <p>Le {@code label} est un texte <b>non nominatif</b> : il n'expose jamais
 * l'identite d'un autre utilisateur (cahier des charges §16). On y met le motif
 * d'une reservation ou le libelle d'une seance/examen, jamais un nom de
 * personne.</p>
 *
 * <p>Convention d'intervalle demi-ouvert {@code [debut, fin)}.</p>
 */
public record OccupancyInterval(
        LocalTime debut,
        LocalTime fin,
        OccupancySource source,
        String label) {

    /** Vrai si cet intervalle chevauche {@code [autreDebut, autreFin)}. */
    public boolean overlaps(LocalTime autreDebut, LocalTime autreFin) {
        return debut.isBefore(autreFin) && fin.isAfter(autreDebut);
    }

    /** Libelle lisible de la source, pour les messages d'indisponibilite. */
    public String sourceLabel() {
        return switch (source) {
            case EMPLOI_DU_TEMPS -> "séance de l'emploi du temps";
            case EXAMEN -> "examen";
            case SOUTENANCE -> "soutenance";
            case AUTRE_OCCUPATION -> "occupation de l'espace";
            case RESERVATION_ACCEPTEE -> "réservation acceptée";
            case RESERVATION_EN_ATTENTE -> "demande de réservation en attente";
        };
    }
}
