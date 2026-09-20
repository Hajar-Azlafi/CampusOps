package com.campusops.user.service;

import com.campusops.exception.BadRequestException;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import com.campusops.user.entity.User;
import com.campusops.user.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service centralise de gestion des mots de passe. Il concentre les seules
 * responsabilites « mot de passe » du systeme :
 * <ul>
 *   <li>generer un mot de passe temporaire aleatoire et robuste ;</li>
 *   <li>valider qu'un nouveau mot de passe respecte la politique de securite ;</li>
 *   <li>encoder un mot de passe (meme mecanisme BCrypt que le reste de l'app) ;</li>
 *   <li>dire si un mot de passe a depasse sa duree de validite.</li>
 * </ul>
 *
 * <p>Le mot de passe en clair ne transite jamais par les logs ni par la base :
 * il n'est retourne (par le generateur) que pour etre immediatement encode et
 * transmis a l'utilisateur par e-mail.</p>
 *
 * <p><b>Module 11 (§7)</b> : la politique n'est plus codee en dur, elle vient de
 * la configuration centrale ({@link SettingsService}) : longueur minimale et
 * classes de caracteres obligatoires (majuscule, minuscule, chiffre, caractere
 * special). Ce service est le <b>seul</b> endroit qui interprete ces reglages,
 * pour que tous les chemins (changement, reinitialisation, import) appliquent
 * exactement la meme regle (§20).</p>
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    /**
     * Longueur minimale retenue si le reglage est absent (base vierge, valeur
     * NULL heritee d'une ancienne ligne). Jamais utilisee comme regle en dur :
     * c'est uniquement un filet de securite.
     */
    private static final int LONGUEUR_MIN_PAR_DEFAUT = 8;

    /** Nombre d'essais de generation avant completion deterministe. */
    private static final int MAX_ESSAIS_GENERATION = 12;

    private static final Pattern HAS_MAJUSCULE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_MINUSCULE = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_CHIFFRE = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;

    /**
     * Genere un mot de passe temporaire aleatoire securise (en clair, ephemere).
     * Le resultat respecte la politique configuree : inutile d'envoyer a un
     * utilisateur un mot de passe que l'application refuserait ensuite.
     */
    public String generateTemporaryPassword() {
        for (int essai = 0; essai < MAX_ESSAIS_GENERATION; essai++) {
            String candidat = temporaryPasswordGenerator.generate();
            if (motifNonConforme(candidat) == null) {
                return candidat;
            }
        }
        // Tirage malchanceux (politique tres exigeante) : on complete le tirage
        // avec les classes manquantes plutot que de boucler indefiniment.
        return completer(temporaryPasswordGenerator.generate());
    }

    /** Encode un mot de passe en clair avec le mecanisme commun (BCrypt). */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /** Verifie qu'un mot de passe en clair correspond a l'empreinte stockee. */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Valide la politique de securite d'un nouveau mot de passe. En cas de
     * non-respect, leve une {@link BadRequestException} au message explicite
     * (aucune valeur sensible n'est incluse : seules les regles sont citees).
     */
    public void validatePolicy(String rawPassword) {
        String motif = motifNonConforme(rawPassword);
        if (motif != null) {
            throw new BadRequestException(motif);
        }
    }

    /**
     * Valide qu'un nouveau mot de passe et sa confirmation correspondent, puis
     * applique la politique de securite.
     */
    public void validateNewPassword(String newPassword, String confirmPassword) {
        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            throw new BadRequestException(
                    "Le nouveau mot de passe et sa confirmation ne correspondent pas.");
        }
        validatePolicy(newPassword);
    }

    /**
     * Renvoie le motif de non-conformite d'un mot de passe, ou {@code null}
     * s'il respecte la politique. Renvoyer le message plutot que lever permet de
     * reutiliser la meme regle pour valider une saisie <b>et</b> pour verifier un
     * mot de passe genere (§20 : une seule source de verite).
     */
    public String motifNonConforme(String rawPassword) {
        AppSettings reglages = settingsService.current();
        int longueurMin = reglages.getLongueurMinMotDePasse() == null
                ? LONGUEUR_MIN_PAR_DEFAUT
                : Math.max(1, reglages.getLongueurMinMotDePasse());

        if (rawPassword == null || rawPassword.length() < longueurMin) {
            return "Le mot de passe doit contenir au moins " + longueurMin + " caractères.";
        }

        List<String> manquants = new ArrayList<>();
        if (reglages.isMajusculeObligatoire() && !HAS_MAJUSCULE.matcher(rawPassword).matches()) {
            manquants.add("une majuscule");
        }
        if (reglages.isMinusculeObligatoire() && !HAS_MINUSCULE.matcher(rawPassword).matches()) {
            manquants.add("une minuscule");
        }
        if (reglages.isChiffreObligatoire() && !HAS_CHIFFRE.matcher(rawPassword).matches()) {
            manquants.add("un chiffre");
        }
        if (reglages.isCaractereSpecialObligatoire() && !HAS_SPECIAL.matcher(rawPassword).matches()) {
            manquants.add("un caractère spécial (ex. ! @ # $ %)");
        }
        if (manquants.isEmpty()) {
            return null;
        }
        return "Le mot de passe doit contenir au moins " + String.join(", ", manquants) + ".";
    }

    /**
     * Indique si le mot de passe de l'utilisateur a depasse sa duree de validite
     * (§7). Une duree a {@code 0} (ou absente) desactive l'expiration.
     *
     * <p>La reference est la date du dernier changement ; pour les comptes
     * anterieurs a l'introduction de cette colonne, on retombe sur la date de
     * creation. Un compte deja en « changement obligatoire » n'a pas besoin de
     * cette verification.</p>
     */
    public boolean estExpire(User user) {
        Integer jours = settingsService.current().getDureeValiditeMotDePasseJours();
        if (user == null || jours == null || jours <= 0) {
            return false;
        }
        LocalDateTime reference = user.getPasswordChangedAt() != null
                ? user.getPasswordChangedAt()
                : user.getCreatedAt();
        return reference != null && reference.plusDays(jours).isBefore(LocalDateTime.now());
    }

    /**
     * Complete un tirage aleatoire avec les classes de caracteres manquantes,
     * puis l'allonge si la longueur minimale configuree l'exige.
     */
    private String completer(String base) {
        StringBuilder resultat = new StringBuilder(base);
        AppSettings reglages = settingsService.current();
        if (reglages.isMajusculeObligatoire() && !HAS_MAJUSCULE.matcher(resultat).matches()) {
            resultat.append('K');
        }
        if (reglages.isMinusculeObligatoire() && !HAS_MINUSCULE.matcher(resultat).matches()) {
            resultat.append('a');
        }
        if (reglages.isChiffreObligatoire() && !HAS_CHIFFRE.matcher(resultat).matches()) {
            resultat.append('7');
        }
        if (reglages.isCaractereSpecialObligatoire() && !HAS_SPECIAL.matcher(resultat).matches()) {
            resultat.append('!');
        }
        int longueurMin = reglages.getLongueurMinMotDePasse() == null
                ? LONGUEUR_MIN_PAR_DEFAUT
                : reglages.getLongueurMinMotDePasse();
        while (resultat.length() < longueurMin) {
            resultat.append(temporaryPasswordGenerator.generate());
        }
        return resultat.toString();
    }
}
