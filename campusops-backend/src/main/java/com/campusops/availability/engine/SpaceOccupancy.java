package com.campusops.availability.engine;

import java.util.List;

/**
 * Occupation calculee d'un espace pour une date donnee, separee en deux
 * familles selon leur effet sur la disponibilite (§1, §13, §20) :
 *
 * <ul>
 *   <li>{@link #blocking()} : occupations qui rendent l'espace <b>reellement
 *       indisponible</b> — seances d'emploi du temps actives, examens dates et
 *       reservations <b>acceptees</b> (APPROVED). Les reservations annulees
 *       (CANCELLED) n'y figurent jamais (§13) ;</li>
 *   <li>{@link #pending()} : demandes de reservation <b>en attente</b> (PENDING).
 *       Elles ne bloquent pas definitivement mais doivent etre signalees
 *       distinctement (§13) et sont arbitrees par la priorite lors d'une
 *       demande concurrente (§12).</li>
 * </ul>
 */
public record SpaceOccupancy(
        List<OccupancyInterval> blocking,
        List<OccupancyInterval> pending) {

    public boolean hasBlockingOverlap(java.time.LocalTime debut, java.time.LocalTime fin) {
        return blocking.stream().anyMatch(i -> i.overlaps(debut, fin));
    }

    public boolean hasPendingOverlap(java.time.LocalTime debut, java.time.LocalTime fin) {
        return pending.stream().anyMatch(i -> i.overlaps(debut, fin));
    }
}
