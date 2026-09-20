package com.campusops.availability.engine;

/**
 * Verdict d'evaluation d'un creneau precis {@code [debut, fin)} pour un espace
 * (§9, §13, §14, §16).
 */
public record SlotVerdict(
        Status status,

        /** Occupation bloquante rencontree (source APPROVED/EDT/examen), ou null. */
        OccupancyInterval blocker,

        /** Message pret a afficher expliquant le refus, ou null si libre. */
        String reason) {

    public enum Status {
        /** Creneau entierement libre : reservable immediatement. */
        FREE,
        /** Une demande en attente chevauche : reservable mais arbitrage de priorite (§12). */
        PENDING_OVERLAP,
        /** Occupation bloquante : creneau refuse (§14). */
        BLOCKED,
        /** Hors des bornes d'ouverture derivees des creneaux (§7). */
        OUT_OF_HOURS,
        /** Jour non ouvrable : dimanche ou ferie (§3, §4). */
        NON_WORKING_DAY
    }

    public boolean isFree() {
        return status == Status.FREE;
    }

    public boolean isBookable() {
        return status == Status.FREE || status == Status.PENDING_OVERLAP;
    }

    public static SlotVerdict free() {
        return new SlotVerdict(Status.FREE, null, null);
    }
}
