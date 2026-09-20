package com.campusops.config;

import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cree la configuration globale par defaut a la premiere installation (§19).
 *
 * <p>Valeurs initiales : universite « CampusOps University », fuseau
 * {@code Africa/Casablanca}, devise MAD, ouverture 08:30 -> 18:00, jours
 * ouvrables du lundi au samedi, dimanche desactive. Toutes restent modifiables
 * depuis la page Parametres.</p>
 *
 * <p><b>Idempotent et non destructif</b> : si la ligne unique existe deja, elle
 * n'est jamais reecrite — une universite qui a personnalise sa configuration ne
 * la voit pas revenir aux valeurs d'usine a chaque redemarrage.</p>
 *
 * <p>S'execute <b>en premier</b> ({@code @Order(0)}) afin que tout initialiseur
 * ou service demarrant ensuite lise une configuration deja presente plutot que
 * les valeurs de repli.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class SettingsInitializer implements CommandLineRunner {

    private final SettingsService settingsService;

    @Value("${campusops.seed.settings:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Initialisation de la configuration globale desactivee"
                    + " (campusops.seed.settings=false).");
            return;
        }

        AppSettings settings = settingsService.loadOrCreateDefaults();
        log.info("Configuration globale disponible : « {} », {} -> {}, {} jour(s) ouvrable(s),"
                        + " fuseau {}.",
                settings.getNom(), settings.getHeureOuverture(), settings.getHeureFermeture(),
                settings.getJoursOuvrables() == null ? 0 : settings.getJoursOuvrables().size(),
                settings.getFuseauHoraire());
    }
}
