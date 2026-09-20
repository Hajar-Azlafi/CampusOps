package com.campusops.enums;

/**
 * Format d'affichage des heures dans l'interface (§10).
 *
 * <p>Les heures sont stockees en base au format 24 h ; ce parametre ne concerne
 * que leur <b>affichage</b>, afin qu'aucune page ne code son propre format
 * (§20).</p>
 */
public enum TimeDisplayFormat {

    /** 14:30 — valeur historique de CampusOps. */
    H24("HH:mm"),
    /** 02:30 PM. */
    H12("hh:mm a");

    private final String pattern;

    TimeDisplayFormat(String pattern) {
        this.pattern = pattern;
    }

    /** Motif {@code DateTimeFormatter} correspondant. */
    public String getPattern() {
        return pattern;
    }
}
