package com.campusops.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Nature precise d'une <b>occupation supplementaire</b> d'un espace.
 *
 * <p>Chaque type appartient a une {@link OccupationCategorie categorie} qui
 * pilote l'onglet d'affichage et le niveau d'exigence du formulaire :</p>
 * <ul>
 *   <li>{@link #EXAMEN} — contexte pedagogique complet obligatoire (matiere,
 *       promotion, session, creneau) : gere par le module examens ;</li>
 *   <li>{@link #SOUTENANCE} — rattachement a une filiere obligatoire, promotion
 *       facultative ;</li>
 *   <li>les autres valeurs — rattachement a une filiere <em>facultatif</em>
 *       (sans filiere, seul un administrateur peut gerer l'occupation).</li>
 * </ul>
 *
 * <p>Ajouter un type ne demande aucune modification du moteur de disponibilite :
 * seule la categorie est interpretee, jamais le type lui-meme.</p>
 */
public enum OccupationType {

    EXAMEN(OccupationCategorie.EXAMEN, "Examen"),
    SOUTENANCE(OccupationCategorie.SOUTENANCE, "Soutenance"),
    EVENEMENT(OccupationCategorie.AUTRE, "Événement"),
    REUNION(OccupationCategorie.AUTRE, "Réunion"),
    ACTIVITE_PEDAGOGIQUE(OccupationCategorie.AUTRE, "Activité pédagogique"),
    ACTIVITE_CLUB(OccupationCategorie.AUTRE, "Activité de club"),
    CONFERENCE(OccupationCategorie.AUTRE, "Conférence"),
    AUTRE(OccupationCategorie.AUTRE, "Autre activité");

    private final OccupationCategorie categorie;
    private final String libelle;

    OccupationType(OccupationCategorie categorie, String libelle) {
        this.categorie = categorie;
        this.libelle = libelle;
    }

    public OccupationCategorie getCategorie() {
        return categorie;
    }

    /** Libelle francais affichable tel quel (listes deroulantes, messages). */
    public String getLibelle() {
        return libelle;
    }

    /** Types appartenant a une categorie donnee, dans l'ordre de declaration. */
    public static List<OccupationType> ofCategorie(OccupationCategorie categorie) {
        return Arrays.stream(values())
                .filter(t -> t.categorie == categorie)
                .toList();
    }
}
