package com.campusops.enums;

/**
 * Theme d'interface propose par defaut aux utilisateurs (§10).
 *
 * <p>Le module Parametres ne cree PAS un second systeme de theme : le theme
 * clair/sombre est deja gere cote frontend (classe {@code dark} sur
 * {@code <html>} + variables CSS). Ce parametre fournit uniquement la valeur
 * <b>par defaut</b> appliquee tant que l'utilisateur n'a pas fait de choix
 * explicite (choix memorise dans son navigateur, qui reste prioritaire).</p>
 */
public enum AppTheme {

    /** Theme clair impose par defaut. */
    CLAIR("light"),
    /** Theme sombre impose par defaut. */
    SOMBRE("dark"),
    /** Suit la preference du systeme d'exploitation (comportement historique). */
    SYSTEME("system");

    private final String cssValue;

    AppTheme(String cssValue) {
        this.cssValue = cssValue;
    }

    /** Valeur consommee par le frontend ({@code light}, {@code dark}, {@code system}). */
    public String getCssValue() {
        return cssValue;
    }
}
