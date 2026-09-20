package com.campusops.availability.engine;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * <b>Fenetre de dates ouvertes a la recherche</b> — unique source de verite des
 * regles de date d'une recherche de disponibilite.
 *
 * <p>Deux regles s'appliquent, dans cet ordre :</p>
 * <ol>
 *   <li><b>Jamais dans le passe</b> : la date recherchee doit etre superieure ou
 *       egale a la date du jour. De plus, passe l'<b>heure limite</b>
 *       (Parametres &gt; Horaires, Module 11 §6 ; 18:30 a l'installation), la
 *       journee en cours n'est plus proposee : la recherche commence au
 *       lendemain. Cela supprime toute ambiguite du type « a 18h35, les creneaux
 *       affiches sont-ils ceux d'aujourd'hui ou de demain ? ».</li>
 *   <li><b>Dans l'annee universitaire active</b> : la date doit etre comprise
 *       dans la periode de l'annee universitaire <b>active</b>. Si aucune annee
 *       active n'est definie (ou si ses bornes sont vides), la regle est
 *       ignoree — on ne bloque jamais l'application sur une configuration
 *       incomplete, seule la regle « pas de date passee » subsiste.</li>
 * </ol>
 *
 * <p>Les messages produits sont directement affichables et expliquent quoi
 * faire ; ils ne sont jamais dupliques cote client.</p>
 */
public record SearchDateWindow(
        /** Date du jour utilisee comme reference (injectee, donc testable). */
        LocalDate aujourdHui,
        /** Heure limite au-dela de laquelle la journee en cours est fermee a la recherche. */
        LocalTime heureLimite,
        /** Vrai si l'heure limite est deja passee : la journee en cours n'est plus proposee. */
        boolean limiteDepassee,
        /** Premiere date recherchable (aujourd'hui, demain apres l'heure limite, ou debut d'annee). */
        LocalDate dateMin,
        /** Derniere date recherchable = fin de l'annee active, ou {@code null} si non bornee. */
        LocalDate dateMax,
        Long anneeActiveId,
        String anneeActiveLibelle,
        LocalDate anneeActiveDebut,
        LocalDate anneeActiveFin) {

    /** Vrai si la date est ouverte a la recherche. */
    public boolean accepte(LocalDate date) {
        return motifRefus(date) == null;
    }

    /**
     * Motif de refus d'une date, pret a afficher, ou {@code null} si la date est
     * acceptee. Une date nulle est acceptee : l'appelant lui substitue le jour
     * courant, qui est ensuite soumis aux memes regles.
     */
    public String motifRefus(LocalDate date) {
        if (date == null) {
            return null;
        }
        if (date.isBefore(aujourdHui)) {
            return "Vous ne pouvez pas rechercher une date passée. La recherche commence au "
                    + DateLabels.libelleLong(dateMin) + ".";
        }
        if (date.equals(aujourdHui) && limiteDepassee) {
            return "Les recherches pour aujourd'hui ne sont plus possibles après "
                    + DateLabels.heure(heureLimite)
                    + " : la journée est terminée. La recherche commence au "
                    + DateLabels.libelleLong(dateMin) + ".";
        }
        if (anneeActiveDebut != null && date.isBefore(anneeActiveDebut)) {
            return motifHorsAnnee();
        }
        if (anneeActiveFin != null && date.isAfter(anneeActiveFin)) {
            return motifHorsAnnee();
        }
        return null;
    }

    private String motifHorsAnnee() {
        StringBuilder message = new StringBuilder(
                "La date doit être comprise dans l'année universitaire active ")
                .append(anneeActiveLibelle);
        if (anneeActiveDebut != null && anneeActiveFin != null) {
            message.append(" (du ").append(DateLabels.libelleCourt(anneeActiveDebut))
                    .append(" au ").append(DateLabels.libelleCourt(anneeActiveFin)).append(")");
        } else if (anneeActiveDebut != null) {
            message.append(" (à partir du ").append(DateLabels.libelleCourt(anneeActiveDebut)).append(")");
        } else if (anneeActiveFin != null) {
            message.append(" (jusqu'au ").append(DateLabels.libelleCourt(anneeActiveFin)).append(")");
        }
        return message.append(".").toString();
    }

    /**
     * Vrai si la fenetre est exploitable. Elle ne l'est pas quand l'annee active
     * est deja terminee : plus aucune date n'est alors recherchable, et il faut
     * effectuer le passage a l'annee suivante.
     */
    public boolean exploitable() {
        return dateMax == null || !dateMin.isAfter(dateMax);
    }

    /** Message d'alerte de configuration, ou {@code null} si tout est coherent. */
    public String messageConfiguration() {
        if (anneeActiveId == null) {
            return "Aucune année universitaire active n'est définie : la recherche n'est bornée "
                    + "que par la date du jour.";
        }
        if (!exploitable()) {
            return "L'année universitaire active " + anneeActiveLibelle + " est terminée depuis le "
                    + DateLabels.libelleCourt(anneeActiveFin)
                    + " : aucune date n'est ouverte à la recherche. Effectuez le passage à "
                    + "l'année universitaire suivante.";
        }
        return null;
    }
}
