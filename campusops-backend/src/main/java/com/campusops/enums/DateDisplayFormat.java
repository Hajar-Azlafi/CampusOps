package com.campusops.enums;

/**
 * Format d'affichage des dates dans l'interface (§10).
 *
 * <p>Le motif est expose au frontend afin qu'une seule source de verite pilote
 * l'affichage des dates (aucun format code en dur dans les pages, §20).</p>
 */
public enum DateDisplayFormat {

    /** 04/09/2026 — usage francais, valeur historique de CampusOps. */
    JJ_MM_AAAA("dd/MM/yyyy"),
    /** 2026-09-04 — format ISO. */
    AAAA_MM_JJ("yyyy-MM-dd"),
    /** 09/04/2026 — usage anglo-saxon. */
    MM_JJ_AAAA("MM/dd/yyyy");

    private final String pattern;

    DateDisplayFormat(String pattern) {
        this.pattern = pattern;
    }

    /** Motif {@code DateTimeFormatter} correspondant. */
    public String getPattern() {
        return pattern;
    }
}
