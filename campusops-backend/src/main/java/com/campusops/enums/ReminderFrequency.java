package com.campusops.enums;

/**
 * Frequence des rappels automatiques envoyes aux utilisateurs (§8).
 *
 * <p>La valeur exprime l'avance avec laquelle un rappel est emis avant la date
 * d'une reservation : {@link #QUOTIDIENNE} previent la veille,
 * {@link #HEBDOMADAIRE} une semaine avant, {@link #JAMAIS} desactive les
 * rappels sans toucher aux autres notifications.</p>
 */
public enum ReminderFrequency {

    /** Aucun rappel automatique. */
    JAMAIS(0),
    /** Rappel la veille de l'evenement. */
    QUOTIDIENNE(1),
    /** Rappel une semaine avant l'evenement. */
    HEBDOMADAIRE(7);

    private final int joursAvant;

    ReminderFrequency(int joursAvant) {
        this.joursAvant = joursAvant;
    }

    /** Nombre de jours d'avance du rappel (0 = rappels desactives). */
    public int getJoursAvant() {
        return joursAvant;
    }

    /** Vrai si la frequence produit reellement des rappels. */
    public boolean isActive() {
        return joursAvant > 0;
    }
}
