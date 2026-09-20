package com.campusops.enums;

/**
 * Spécialité d'un laboratoire, permettant de distinguer les laboratoires
 * au-delà de leur simple type {@link SpaceType#LABORATORY}.
 *
 * <p>Facultative : ne concerne que les espaces de type laboratoire (ou, le cas
 * échéant, une salle machine spécialisée). Les salles de cours, amphithéâtres
 * et autres espaces non spécialisés n'ont pas de spécialité.
 */
public enum LabSpeciality {
    INFORMATIQUE,
    RESEAUX,
    PHYSIQUE,
    ELECTRICITE,
    ELECTRONIQUE,
    CHIMIE,
    BIOLOGIE,
    MECANIQUE
}
