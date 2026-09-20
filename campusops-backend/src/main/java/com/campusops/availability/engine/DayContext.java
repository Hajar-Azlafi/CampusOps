package com.campusops.availability.engine;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Contexte d'une journee calcule une seule fois par recherche (§2, §19) :
 * caractere ouvrable, motif de fermeture eventuel, bornes d'ouverture derivees
 * des creneaux (§7) et annee universitaire couvrant la date (§ requete annexe).
 *
 * <p>Ce contexte est <b>partage</b> par tous les espaces d'une meme recherche :
 * les regles de jour (dimanche, jour ferie) et les horaires d'ouverture sont
 * globaux, ils ne dependent pas de l'espace.</p>
 */
public record DayContext(
        LocalDate date,

        /** Faux si dimanche ou jour ferie / non ouvrable : recherche et reservation impossibles. */
        boolean working,

        /** Code machine de la fermeture : {@code DIMANCHE}, {@code JOUR_FERIE}, ou null si ouvrable. */
        String closedType,

        /** Message pret a afficher expliquant la fermeture, ou null si ouvrable. */
        String closedReason,

        /** Debut d'ouverture = debut du premier creneau actif (§7). */
        LocalTime opening,

        /** Fin d'ouverture = fin du dernier creneau actif (§7). */
        LocalTime closing,

        /** Identifiant de l'annee universitaire couvrant la date (active OU non), ou null. */
        Long academicYearId,

        /** Libelle de l'annee couvrant la date, ou null si aucune ne la couvre. */
        String academicYearLibelle,

        /** Vrai si une annee universitaire couvre la date ; faux → message informatif (jamais « tout libre »). */
        boolean academicYearResolved) {

    public boolean isClosed() {
        return !working;
    }
}
