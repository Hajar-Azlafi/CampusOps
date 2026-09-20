package com.campusops.auth.service;

import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.user.service.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Applique les reglages de securite du Module 11 (§7) au parcours de connexion :
 * <ul>
 *   <li><b>nombre maximum de tentatives</b> : les echecs consecutifs sont comptes
 *       sur le compte cible ;</li>
 *   <li><b>duree de verrouillage</b> : au-dela du plafond, le compte est
 *       verrouille pour la duree configuree ;</li>
 *   <li><b>duree de validite du mot de passe</b> : un mot de passe expire force
 *       le changement au prochain login, en reutilisant le mecanisme existant
 *       {@code mustChangePassword} (aucun nouveau parcours, aucune rupture du
 *       systeme JWT).</li>
 * </ul>
 *
 * <p>Aucune de ces regles n'est codee en dur : tout vient de
 * {@link SettingsService}. Un plafond a {@code 0} (ou absent) desactive le
 * verrouillage, une duree de validite a {@code 0} desactive l'expiration —
 * comportement identique a celui d'avant le Module 11.</p>
 *
 * <p>Les messages d'erreur ne revelent jamais si une adresse existe : le
 * verrouillage n'est annonce qu'a un compte reellement verrouille, et un mot de
 * passe errone renvoie toujours le message generique du
 * {@code GlobalExceptionHandler}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSecurityService {

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Authentification";

    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final PasswordService passwordService;
    private final AuditService auditService;

    /**
     * Refuse immediatement la connexion d'un compte encore verrouille, avec un
     * message explicite indiquant le temps restant. Sans ce pre-controle, Spring
     * Security leverait un {@link LockedException} au message anglais generique.
     *
     * <p>Si l'adresse est inconnue, la methode ne fait rien : c'est la
     * verification du mot de passe qui repondra (message generique).</p>
     */
    @Transactional(readOnly = true)
    public void verifierNonVerrouille(String email) {
        rechercher(email).ifPresent(user -> {
            if (!user.isAccountNonLocked()) {
                throw new LockedException(messageVerrouillage(user.getLockedUntil()));
            }
        });
    }

    /**
     * Enregistre un echec de connexion et verrouille le compte si le plafond
     * configure est atteint. Executee hors transaction du login (le login
     * echoue), elle sauvegarde immediatement le compteur.
     */
    @Transactional
    public void enregistrerEchec(String email) {
        Integer plafond = settingsService.current().getMaxTentativesConnexion();
        if (plafond == null || plafond <= 0) {
            return; // Verrouillage desactive : on ne compte meme pas.
        }
        rechercher(email).ifPresent(user -> {
            int tentatives = tentatives(user) + 1;
            if (tentatives >= plafond) {
                LocalDateTime echeance = LocalDateTime.now()
                        .plusMinutes(dureeVerrouillageMinutes());
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(echeance);
                userRepository.save(user);
                log.warn("Compte {} verrouille jusqu'a {} apres {} tentatives echouees",
                        user.getEmail(), echeance, plafond);
                auditService.record(user, AuditAction.SYSTEM, MODULE, String.format(
                        "Verrouillage du compte apres %d tentatives de connexion echouees"
                                + " (deverrouillage a %s)", plafond, echeance));
            } else {
                user.setFailedLoginAttempts(tentatives);
                userRepository.save(user);
            }
        });
    }

    /**
     * Enregistre une connexion reussie : compteur et verrou remis a zero, puis
     * evaluation de l'expiration du mot de passe.
     *
     * @return {@code true} si le mot de passe a expire et que l'utilisateur doit
     *         en choisir un nouveau avant de continuer.
     */
    @Transactional
    public boolean enregistrerSucces(User user) {
        boolean modifie = false;

        if (tentatives(user) != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            modifie = true;
        }

        boolean expire = !user.isMustChangePassword() && passwordService.estExpire(user);
        if (expire) {
            user.setMustChangePassword(true);
            modifie = true;
            log.info("Mot de passe expire pour {} : changement obligatoire", user.getEmail());
            auditService.record(user, AuditAction.SYSTEM, MODULE, String.format(
                    "Mot de passe expire (validite de %d jours) : changement obligatoire",
                    settingsService.current().getDureeValiditeMotDePasseJours()));
        }

        if (modifie) {
            userRepository.save(user);
        }
        return expire;
    }

    /**
     * Marque un mot de passe comme fraichement choisi par l'utilisateur : point
     * de depart de l'expiration, et levee d'un eventuel verrouillage (le
     * parcours « mot de passe oublie » est la porte de sortie documentee d'un
     * compte verrouille). L'appelant reste responsable de la sauvegarde.
     */
    public void marquerMotDePasseChange(User user) {
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    /** Tentatives echouees connues, en traitant NULL (comptes anterieurs) comme zero. */
    private int tentatives(User user) {
        return user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
    }

    /** Duree de verrouillage configuree, avec un minimum de 1 minute. */
    private long dureeVerrouillageMinutes() {
        AppSettings reglages = settingsService.current();
        Integer minutes = reglages.getDureeVerrouillageMinutes();
        return minutes == null || minutes <= 0 ? 1L : minutes;
    }

    private Optional<User> rechercher(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    /** Message utilisateur indiquant le temps restant avant deverrouillage. */
    private String messageVerrouillage(LocalDateTime echeance) {
        long minutes = Math.max(1, Duration.between(LocalDateTime.now(), echeance).toMinutes() + 1);
        return String.format("Compte temporairement verrouillé après plusieurs tentatives de"
                + " connexion échouées. Réessayez dans %d minute%s ou utilisez « Mot de passe"
                + " oublié » pour le réinitialiser.", minutes, minutes > 1 ? "s" : "");
    }
}
