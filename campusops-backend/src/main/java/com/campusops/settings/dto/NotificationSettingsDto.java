package com.campusops.settings.dto;

import com.campusops.enums.ReminderFrequency;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interrupteurs du systeme de notification et d'e-mail existant (§8).
 *
 * <p>Ces drapeaux s'ajoutent a la configuration SMTP en place
 * ({@code campusops.mail.*}) sans la remplacer : ils sont evalues par
 * {@code NotificationService} et {@code EmailService} avant tout envoi.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class NotificationSettingsDto {

    @NotNull(message = "L'activation des notifications est obligatoire")
    private Boolean notificationsActivees;

    @NotNull(message = "L'activation des e-mails est obligatoire")
    private Boolean emailsActives;

    @NotNull(message = "L'activation des notifications automatiques est obligatoire")
    private Boolean notificationsAutomatiquesActivees;

    @NotNull(message = "La fréquence des rappels est obligatoire")
    private ReminderFrequency frequenceRappels;

    /**
     * Lecture seule : indique si un serveur SMTP est reellement configure. Un
     * envoi d'e-mail reste impossible sans identifiants, meme si
     * {@code emailsActives} est vrai.
     */
    private Boolean smtpConfigure;

    /** Lecture seule : adresse expeditrice utilisee par les e-mails. */
    private String expediteur;
}
