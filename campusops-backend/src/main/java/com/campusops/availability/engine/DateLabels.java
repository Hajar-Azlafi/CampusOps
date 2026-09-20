package com.campusops.availability.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Libelles de dates et d'heures <b>en francais</b>, centralises une seule fois
 * pour toute la disponibilite. Ils sont calcules cote serveur et transmis dans
 * la reponse : le client se contente de les afficher, ce qui garantit un texte
 * identique partout (en-tete des resultats, messages de refus, e-mails).
 *
 * <p>Exemple : {@code libelleLong(2026-09-01)} → « Mardi 1 septembre 2026 ».</p>
 */
public final class DateLabels {

    /** « mardi 1 septembre 2026 » (sans majuscule initiale). */
    private static final DateTimeFormatter LONG_FR =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    /** « 01/09/2026 ». */
    private static final DateTimeFormatter SHORT_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    /** « 08:30 ». */
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private DateLabels() {
    }

    /** Date longue avec majuscule initiale : « Mardi 1 septembre 2026 ». */
    public static String libelleLong(LocalDate date) {
        if (date == null) {
            return null;
        }
        String libelle = date.format(LONG_FR);
        return libelle.substring(0, 1).toUpperCase(Locale.FRENCH) + libelle.substring(1);
    }

    /** Date courte : « 01/09/2026 ». */
    public static String libelleCourt(LocalDate date) {
        return date == null ? null : date.format(SHORT_FR);
    }

    /** Heure au format « HH:mm ». */
    public static String heure(LocalTime heure) {
        return heure == null ? null : heure.format(HM);
    }

    /**
     * Duree lisible a partir de minutes : « 45 min », « 1 h », « 1 h 30 »,
     * « 2 h ». Utilisee dans les messages de duree souhaitee (§ duree).
     */
    public static String duree(long minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        long heures = minutes / 60;
        long reste = minutes % 60;
        return reste == 0 ? heures + " h" : heures + " h " + String.format("%02d", reste);
    }
}
