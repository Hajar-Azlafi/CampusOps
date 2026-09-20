package com.campusops.enums;

/**
 * Type de presence d'une seance.
 *
 * <ul>
 *   <li>{@code PRESENTIEL} : la seance a lieu dans une salle physique.
 *       Une salle est alors OBLIGATOIRE (regle du cahier des charges).</li>
 *   <li>{@code DISTANCIEL} : la seance a lieu a distance. La salle n'est
 *       pas obligatoire.</li>
 * </ul>
 */
public enum PresenceType {
    PRESENTIEL,
    DISTANCIEL
}
