package com.campusops.enums;

/**
 * Type de formation auquel se rattache un niveau/cycle academique.
 * La distinction est reelle sur le plan metier : la formation continue
 * peut utiliser les espaces le samedi, contrairement a la formation
 * initiale. Le libelle affichable est gere cote frontend.
 */
public enum TypeFormation {
    INITIALE,
    CONTINUE
}
