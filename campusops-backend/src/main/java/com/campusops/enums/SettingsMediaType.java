package com.campusops.enums;

/**
 * Type de media d'identite visuelle stocke par le module Parametres (§11).
 *
 * <p>Chaque type est unique en base : il n'existe au plus qu'un logo et qu'un
 * favicon pour l'application, a l'image de la configuration globale unique.</p>
 */
public enum SettingsMediaType {
    /** Logo affiche dans la barre laterale, l'en-tete et la page de connexion. */
    LOGO,
    /** Icone de l'onglet du navigateur. */
    FAVICON
}
