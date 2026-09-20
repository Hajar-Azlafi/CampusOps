package com.campusops.validation.dto;

/**
 * Compteur d'<b>impact</b> d'une desactivation (cahier §17). Renseigne, selon
 * l'entite ciblee, le nombre d'elements descendants <b>actuellement actifs</b>
 * qui deviendront indisponibles pour les nouvelles operations si la
 * desactivation est confirmee.
 *
 * <p>Chaque champ vaut {@code 0} lorsqu'il ne s'applique pas a l'entite
 * consideree (ex. un batiment ne renseigne que {@code etages} et {@code salles}).
 * Le frontend n'affiche que les compteurs strictement positifs.
 */
public record DeactivationImpact(
        long filieres,
        long promotions,
        long groupes,
        long etages,
        long salles
) {
    public static DeactivationImpact empty() {
        return new DeactivationImpact(0, 0, 0, 0, 0);
    }
}
