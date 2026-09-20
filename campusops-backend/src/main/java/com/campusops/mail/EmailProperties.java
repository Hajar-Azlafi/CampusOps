package com.campusops.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Parametres applicatifs de l'envoi d'e-mails, externalises via
 * {@code campusops.mail.*} et {@code campusops.app.*}. Les identifiants SMTP
 * eux-memes restent geres par Spring ({@code spring.mail.*}) et proviennent de
 * variables d'environnement (jamais ecrits en dur).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "campusops")
public class EmailProperties {

    private final Mail mail = new Mail();
    private final App app = new App();

    @Getter
    @Setter
    public static class Mail {
        /** Adresse expediteur affichee dans les e-mails. */
        private String from = "no-reply@campusops.ma";
        /** Interrupteur global : false = e-mails seulement journalises (dev/tests). */
        private boolean enabled = true;
        /** Duree de validite (minutes) d'un lien de reinitialisation. */
        private long resetTokenExpirationMinutes = 30;
    }

    @Getter
    @Setter
    public static class App {
        /** URL de connexion cote frontend. */
        private String loginUrl = "http://localhost:5173/login";
        /** URL de base de la page « definir un nouveau mot de passe » cote frontend. */
        private String resetPasswordUrl = "http://localhost:5173/reset-password";
    }
}
