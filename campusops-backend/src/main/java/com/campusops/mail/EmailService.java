package com.campusops.mail;

import com.campusops.enums.Role;
import com.campusops.settings.service.SettingsService;
import com.campusops.user.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

/**
 * Service centralise d'envoi d'e-mails. Il isole toute la logique SMTP et de
 * rendu des templates : les services metier (UserService, import Excel,
 * reinitialisation) se contentent de l'appeler.
 *
 * <p>Les e-mails sont rendus a partir de templates Thymeleaf
 * ({@code resources/templates/email/*.html}). L'envoi est tolerant aux pannes :
 * une defaillance SMTP renvoie {@code false} et est journalisee SANS exposer de
 * donnee sensible (jamais le mot de passe temporaire ni le lien de reset). Le
 * booleen retourne permet a l'appelant de decider de la suite (ex. informer
 * l'administrateur qu'un renvoi est necessaire) sans annuler l'operation metier.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String WELCOME_SUBJECT = "Bienvenue sur CampusOps - Vos identifiants";
    private static final String ADMIN_RESET_SUBJECT = "Votre mot de passe CampusOps a été réinitialisé";
    private static final String RESET_SUBJECT = "Réinitialisation de votre mot de passe - CampusOps";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailProperties properties;
    private final SettingsService settingsService;

    /** Identifiant SMTP configure (jamais journalise en entier). */
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.port:}")
    private String smtpPort;

    /** Authentification SMTP exigee par le serveur configure. */
    @org.springframework.beans.factory.annotation.Value("${spring.mail.properties.mail.smtp.auth:false}")
    private boolean smtpAuthRequired;

    /**
     * Envoie l'e-mail de bienvenue contenant le mot de passe temporaire (en clair,
     * uniquement dans cet e-mail) et le lien de connexion.
     *
     * @return {@code true} si l'e-mail est parti, {@code false} en cas d'echec.
     */
    public boolean sendUserCreatedEmail(User user, String temporaryPassword) {
        Context context = new Context();
        context.setVariable("fullName", fullName(user));
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("email", user.getEmail());
        context.setVariable("role", roleLabel(user.getRole()));
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("loginUrl", properties.getApp().getLoginUrl());
        return send(user.getEmail(), WELCOME_SUBJECT, "email/user-created", context,
                "bienvenue/creation de compte");
    }

    /** Envoie les nouveaux identifiants après une réinitialisation par un administrateur. */
    public boolean sendAdminPasswordResetEmail(User user, String temporaryPassword) {
        Context context = new Context();
        context.setVariable("fullName", fullName(user));
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("email", user.getEmail());
        context.setVariable("role", roleLabel(user.getRole()));
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("loginUrl", properties.getApp().getLoginUrl());
        return send(user.getEmail(), ADMIN_RESET_SUBJECT, "email/admin-password-reset", context,
                "reinitialisation admin du mot de passe");
    }

    /**
     * Envoie l'e-mail de reinitialisation de mot de passe avec le lien contenant
     * le token. Ne contient jamais l'ancien ou l'actuel mot de passe.
     *
     * @return {@code true} si l'e-mail est parti, {@code false} en cas d'echec.
     */
    public boolean sendPasswordResetEmail(User user, String resetLink, long expirationMinutes) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("resetLink", resetLink);
        context.setVariable("expirationMinutes", expirationMinutes);
        return send(user.getEmail(), RESET_SUBJECT, "email/password-reset", context,
                "reinitialisation de mot de passe");
    }

    /** Rend le template et envoie l'e-mail HTML. Tolerant aux pannes SMTP. */
    private boolean send(String to, String subject, String template, Context context, String purpose) {
        if (!properties.getMail().isEnabled()) {
            // Mode desactive (dev/tests) : on ne contacte pas le serveur SMTP.
            log.info("Envoi d'e-mail desactive (campusops.mail.enabled=false) - e-mail '{}' non envoye a {}",
                    purpose, to);
            return false;
        }
        if (!settingsService.current().isEmailsActives()) {
            // Reglage administrable (Module 11, §8). Il COMPLETE la propriete
            // technique ci-dessus sans la remplacer : la configuration SMTP reste
            // intacte, il suffit qu'un des deux interrupteurs soit ferme pour
            // qu'aucun e-mail ne parte. Ce point de passage unique garantit que
            // AUCUN e-mail automatique n'est envoye quand l'admin le refuse.
            log.info("Envoi d'e-mail desactive dans les parametres (Notifications) - e-mail '{}' non envoye a {}",
                    purpose, to);
            return false;
        }
        if (smtpAuthRequired && (smtpUsername == null || smtpUsername.isBlank())) {
            // Diagnostic explicite : la cause la plus frequente d'echec en local
            // est l'absence d'identifiants SMTP.
            log.error("E-mail '{}' non envoye a {} : aucun identifiant SMTP configure alors que le "
                            + "serveur exige une authentification. Renseignez MAIL_USERNAME / MAIL_PASSWORD "
                            + "(variables d'environnement ou fichier secrets.properties).",
                    purpose, to);
            return false;
        }
        // Diagnostic utile avant tentative d'envoi (sans exposer de secret)
        try {
            String maskedUser = maskUsername(smtpUsername);
            log.info("Tentative d'envoi e-mail '{}' a {} via SMTP {}:{} (auth={}) utilisateur={}",
                    purpose, to, smtpHost, smtpPort, smtpAuthRequired, maskedUser);
        } catch (Exception ignore) {
            // ne doit pas empecher l'envoi
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, context), true);
            mailSender.send(message);
            log.info("E-mail '{}' envoye avec succes a {}", purpose, to);
            return true;
        } catch (MailException | jakarta.mail.MessagingException ex) {
            // On logge la cause racine au niveau ERROR et la pile au niveau DEBUG
            log.error("Echec d'envoi de l'e-mail '{}' a {} : {}", purpose, to, rootMessage(ex));
            log.error("Verifiez la configuration SMTP (spring.mail.host/port, MAIL_USERNAME, "
                    + "MAIL_PASSWORD). Avec Gmail, MAIL_PASSWORD doit etre un « mot de passe "
                    + "d'application » de 16 caracteres, pas le mot de passe du compte.", ex);
            return false;
        }
    }

    private String maskUsername(String username) {
        if (username == null || username.isBlank()) return "(aucun)";
        try {
            if (username.contains("@")) {
                String[] parts = username.split("@", 2);
                String local = parts[0];
                String domain = parts[1];
                if (local.length() <= 1) return "*@@@@" + domain;
                return local.charAt(0) + "****@" + domain;
            }
            if (username.length() <= 4) return "****";
            return username.substring(0, 3) + "****";
        } catch (Exception ex) {
            return "(masked)";
        }
    }

    /** Message de la cause racine, utile au diagnostic SMTP (sans secret). */
    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + " - " + current.getMessage();
    }

    private String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    /** Libelle lisible du role pour l'affichage dans les e-mails. */
    private String roleLabel(Role role) {
        if (role == null) {
            return "Utilisateur";
        }
        return switch (role) {
            case ADMIN -> "Administrateur";
            case RESPONSABLE_PEDAGOGIQUE -> "Responsable pédagogique";
            case ENSEIGNANT -> "Enseignant";
            case RESPONSABLE_CLUB -> "Responsable de club";
        };
    }
}
