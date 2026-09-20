package com.campusops.auth.service;

import com.campusops.audit.service.AuditService;
import com.campusops.auth.dto.ForgotPasswordRequestDto;
import com.campusops.auth.dto.ResetPasswordRequestDto;
import com.campusops.auth.entity.PasswordResetToken;
import com.campusops.auth.repository.PasswordResetTokenRepository;
import com.campusops.enums.AuditAction;
import com.campusops.exception.BadRequestException;
import com.campusops.mail.EmailProperties;
import com.campusops.mail.EmailService;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.user.service.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Gere le parcours « mot de passe oublie » et la reinitialisation par jeton.
 *
 * <p>Regles de securite appliquees :</p>
 * <ul>
 *   <li>la demande ne revele jamais si un e-mail existe (message generique) ;</li>
 *   <li>le jeton est aleatoire (256 bits, {@link SecureRandom}) et n'est jamais
 *       stocke en clair : seule son empreinte SHA-256 est persistee ;</li>
 *   <li>le jeton expire et devient invalide apres usage (usage unique) ;</li>
 *   <li>l'ancien mot de passe n'est jamais renvoye ni envoye par e-mail.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String MODULE = "Authentification";
        private static final String NOT_FOUND_MESSAGE =
            "Aucun compte n'est associé à cette adresse e-mail.";
        private static final String SUCCESS_MESSAGE =
            "Un e-mail de réinitialisation a été envoyé à l'adresse fournie.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordService passwordService;
    private final LoginSecurityService loginSecurityService;
    private final EmailService emailService;
    private final EmailProperties properties;
    private final AuditService auditService;

    /**
     * Traite une demande « mot de passe oublie ». Emet un jeton et envoie l'e-mail
     * uniquement si le compte existe et est actif, mais renvoie TOUJOURS le meme
     * message generique afin de ne pas divulguer l'existence de l'adresse.
     */
    @Transactional
    public String requestReset(ForgotPasswordRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (maybeUser.isPresent() && maybeUser.get().isActive()) {
            User user = maybeUser.get();

            // Un seul jeton valable a la fois : on invalide les precedents.
            tokenRepository.invalidateActiveTokens(user);

            String rawToken = generateRawToken();
            long expirationMinutes = properties.getMail().getResetTokenExpirationMinutes();

            PasswordResetToken token = PasswordResetToken.builder()
                    .tokenHash(hash(rawToken))
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                    .used(false)
                    .build();
            tokenRepository.save(token);

            String resetLink = buildResetLink(rawToken);
            log.info("[EMAIL] Demande d'envoi d'e-mail de reinitialisation pour {} (expiration {} minutes)",
                    user.getEmail(), expirationMinutes);
            emailService.sendPasswordResetEmail(user, resetLink, expirationMinutes);
            return SUCCESS_MESSAGE;
        } else {
            // L'adresse n'existe pas ou le compte n'est pas actif : on renvoie
            // explicitement le message demandé par l'utilisateur.
            log.info("Demande de reinitialisation pour une adresse sans compte actif - aucun e-mail envoye");
            return NOT_FOUND_MESSAGE;
        }

    }

    /**
     * Definit un nouveau mot de passe a partir d'un jeton. Le jeton doit exister,
     * ne pas etre expire et ne pas avoir deja servi. Il est marque comme utilise
     * apres reussite (usage unique).
     */
    @Transactional
    public void resetPassword(ResetPasswordRequestDto request) {
        passwordService.validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(request.getToken()))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new BadRequestException(
                        "Le lien de réinitialisation est invalide ou a expiré."));

        User user = token.getUser();
        user.setPassword(passwordService.encode(request.getNewPassword()));
        // Le compte a de nouveau un mot de passe choisi par l'utilisateur : plus
        // de changement force au prochain login.
        user.setMustChangePassword(false);
        // Nouveau point de depart de l'expiration et levee d'un eventuel
        // verrouillage : la reinitialisation est la porte de sortie d'un compte
        // bloque par trop de tentatives (Module 11, §7).
        loginSecurityService.marquerMotDePasseChange(user);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        auditService.record(user, AuditAction.UPDATE, MODULE,
                "Reinitialisation du mot de passe via lien e-mail");
    }

    /** Jeton aleatoire de 256 bits encode en Base64 URL-safe (transmis dans le lien). */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Empreinte SHA-256 (hex) du jeton : c'est cette valeur qui est persistee. */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est toujours disponible sur une JVM standard.
            throw new IllegalStateException("Algorithme SHA-256 indisponible", e);
        }
    }

    private String buildResetLink(String rawToken) {
        String base = properties.getApp().getResetPasswordUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "token=" + rawToken;
    }
}
