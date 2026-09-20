package com.campusops.config;

import com.campusops.enums.Role;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Attribue automatiquement <b>un responsable pédagogique dédié à chaque filière</b>
 * (un compte RP par filière), afin de pouvoir se connecter au périmètre de
 * n'importe quelle filière et y importer les emplois du temps.
 *
 * <p><b>Source principale des comptes RP.</b> Depuis la mise en place du petit
 * jeu de données, {@link ResponsablePedagogiqueDemoInitializer} (@Order 10) est
 * désactivé par défaut ; ce seeder-ci (@Order 11) dote donc <b>chaque</b>
 * filière de son responsable dédié. Si le seeder de démonstration hérité est
 * réactivé, il s'exécute avant et ce seeder ne complète alors que les filières
 * restées <b>sans responsable</b> — les filières déjà pilotées (affectation de
 * démo ou manuelle) ne sont jamais réattribuées.
 *
 * <p><b>Idempotent</b> : le compte de chaque filière est identifié par un e-mail
 * déterministe {@code rp.<code-filière>@campusops.ma} ; une réexécution ne recrée
 * ni ne duplique rien. <b>Non destructif</b> : une filière déjà pilotée conserve son
 * responsable (on ne « vole » aucune affectation) et aucune donnée n'est supprimée.
 *
 * <p>La sécurité effective reste imposée par {@code AccessScopeService} : chaque RP
 * ne voit et n'importe que les données de la (des) filière(s) qu'il pilote.
 *
 * <p>Désactivable via {@code campusops.seed.responsables-per-filiere=false}. Mot de
 * passe initial partagé via {@code campusops.seed.responsables-password}
 * (défaut {@code Responsable@123}), commun avec les responsables de démonstration.
 */
@Component
@Order(11)
@RequiredArgsConstructor
@Slf4j
public class ResponsablePedagogiquePerFiliereInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final PasswordEncoder passwordEncoder;

    /** Interrupteur : permet de désactiver entièrement ce seed. */
    @Value("${campusops.seed.responsables-per-filiere:true}")
    private boolean enabled;

    /** Mot de passe initial commun (partagé avec les responsables de démonstration). */
    @Value("${campusops.seed.responsables-password:Responsable@123}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Seed d'un responsable pédagogique par filière désactivé "
                    + "(campusops.seed.responsables-per-filiere=false).");
            return;
        }

        int created = 0;
        int assigned = 0;
        int alreadyPiloted = 0;

        for (Program program : programRepository.findAll()) {
            // Non destructif : une filière déjà pilotée (RP de démo ou affectation
            // manuelle) conserve son responsable actuel.
            if (program.getResponsable() != null) {
                alreadyPiloted++;
                continue;
            }

            String email = emailFor(program.getCode());

            // Idempotent : réutilise le compte s'il existe déjà (par e-mail),
            // sinon le crée pour cette filière.
            User responsable = userRepository.findByEmail(email).orElse(null);
            if (responsable == null) {
                responsable = userRepository.save(User.builder()
                        .firstName("Responsable")
                        .lastName(program.getNom())
                        .email(email)
                        .password(passwordEncoder.encode(demoPassword))
                        .role(Role.RESPONSABLE_PEDAGOGIQUE)
                        .department(program.getDepartment().getNom())
                        .isActive(true)
                        .mustChangePassword(false)
                        .build());
                created++;
            }

            program.setResponsable(responsable);
            programRepository.save(program);
            assigned++;
            log.info("Filière « {} » ({}) affectée au responsable pédagogique {}.",
                    program.getNom(), program.getCode(), email);
        }

        log.info("========================================");
        log.info("Responsables pédagogiques par filière (idempotent) :");
        log.info("  - {} compte(s) RP créé(s)", created);
        log.info("  - {} filière(s) nouvellement affectée(s)", assigned);
        log.info("  - {} filière(s) déjà pilotée(s) (inchangées)", alreadyPiloted);
        log.info("  - Mot de passe initial commun : {}", demoPassword);
        log.info("========================================");
    }

    /**
     * E-mail déterministe d'un responsable de filière, dérivé du code de la
     * filière (unique) : {@code rp.<code-en-minuscules>@campusops.ma}.
     */
    private String emailFor(String code) {
        String slug = (code == null ? "" : code.trim().toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "rp." + slug + "@campusops.ma";
    }
}
