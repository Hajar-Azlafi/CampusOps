package com.campusops.availability.engine;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Periode <b>libre</b> et reservable d'un espace, calculee dans les bornes
 * d'ouverture de la journee (§8, §11). Convention demi-ouverte {@code [debut, fin)}.
 */
public record FreeInterval(LocalTime debut, LocalTime fin) {

    public long dureeMinutes() {
        return Duration.between(debut, fin).toMinutes();
    }

    /** Vrai si {@code [debut, fin)} contient entierement {@code [d, f)}. */
    public boolean contains(LocalTime d, LocalTime f) {
        return !d.isBefore(debut) && !f.isAfter(fin);
    }
}
