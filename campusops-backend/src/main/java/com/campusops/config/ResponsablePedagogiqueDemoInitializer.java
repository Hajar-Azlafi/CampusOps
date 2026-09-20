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

import java.util.List;

/**
 * Comptes de démonstration « Responsable pédagogique » (Section 18 du cahier
 * des charges RBAC) — <b>hérité, désactivé par défaut</b>.
 *
 * <p><b>Obsolète dans le petit jeu de données actuel.</b> Ce seeder attribuait
 * deux responsables nommés (ISI, Génie civil) rattachés à des filières de
 * départements différents pour tester l'isolation. Il visait des codes de
 * filières d'anciens référentiels ({@code MAS-ISI}, {@code MAS-GCMI},
 * {@code LIC-GC}…) qui n'existent plus. Il est donc <b>désactivé par défaut</b>
 * ({@code campusops.seed.responsables=false}) car il est entièrement remplacé
 * par {@link ResponsablePedagogiquePerFiliereInitializer} (@Order 11), qui
 * dote <b>chaque</b> filière d'un responsable dédié {@code rp.<code>@campusops.ma}.
 * L'isolation inter-départements reste testable avec ces comptes par filière
 * (ex. {@code rp.mas-rsi} vs {@code rp.lic-math} vs {@code rp.lic-phys}).
 *
 * <p>Le laisser actif provoquerait en outre une capture prématurée : s'exécutant
 * avant le seeder par filière (@Order 10 &lt; 11), il affecterait ses filières de
 * repli désormais présentes ({@code ING-GI}, {@code LIC-GI}) à un compte au nom
 * trompeur, cassant le schéma « un {@code rp.<code>} par filière ».
 *
 * <p><b>Idempotent</b> et <b>non destructif</b> : ne recrée jamais un compte
 * existant (contrôle par e-mail) et ne réattribue jamais une filière déjà
 * pilotée. Réactivable ({@code campusops.seed.responsables=true}) pour un
 * référentiel hérité contenant encore les anciens codes.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class ResponsablePedagogiqueDemoInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${campusops.seed.responsables:false}")
    private boolean enabled;

    @Value("${campusops.seed.responsables-password:Responsable@123}")
    private String demoPassword;

    /**
     * Définition d'un responsable de démonstration et de ses filières candidates,
     * par ordre de préférence. La première filière existante et non encore
     * attribuée sera affectée ; les codes de repli couvrent les référentiels
     * antérieurs afin de rester robuste selon l'environnement.
     */
    private record DemoResponsable(
            String firstName,
            String lastName,
            String email,
            String department,
            List<String> programCodes) {
    }

    private static final List<DemoResponsable> DEMO_RESPONSABLES = List.of(
            new DemoResponsable(
                    "Responsable", "ISI",
                    "responsable.isi@campusops.ma",
                    "Mathématiques et Informatique",
                    List.of("MAS-ISI", "ING-GI", "LIC-GI", "LIC-SITD", "ISI", "GI")),
            new DemoResponsable(
                    "Responsable", "Génie Civil",
                    "responsable.gc@campusops.ma",
                    "Génie de l'Aménagement et Génie Civil",
                    List.of("MAS-GCMI", "LIC-GC", "GCB", "GC"))
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Seed des responsables pédagogiques de démonstration désactivé "
                    + "(campusops.seed.responsables=false).");
            return;
        }
        for (DemoResponsable demo : DEMO_RESPONSABLES) {
            seedResponsable(demo);
        }
    }

    private void seedResponsable(DemoResponsable demo) {
        // 1) Créer le compte s'il n'existe pas encore (idempotent, par e-mail).
        User responsable = userRepository.findByEmail(demo.email()).orElse(null);
        if (responsable == null) {
            responsable = userRepository.save(User.builder()
                    .firstName(demo.firstName())
                    .lastName(demo.lastName())
                    .email(demo.email())
                    .password(passwordEncoder.encode(demoPassword))
                    .role(Role.RESPONSABLE_PEDAGOGIQUE)
                    .department(demo.department())
                    .isActive(true)
                    .mustChangePassword(false)
                    .build());
            log.info("Responsable pédagogique de démonstration créé : {} (mot de passe initial : {}).",
                    demo.email(), demoPassword);
        }

        // 2) Non destructif : si le responsable pilote déjà au moins une filière,
        //    on ne touche à rien (une réexécution ne modifie pas les affectations).
        if (!programRepository.findByResponsableId(responsable.getId()).isEmpty()) {
            return;
        }

        // 3) Affecter la première filière candidate existante et encore libre.
        for (String code : demo.programCodes()) {
            Program program = programRepository.findByCode(code).orElse(null);
            if (program == null) {
                continue; // code absent dans ce référentiel : on tente le suivant.
            }
            if (program.getResponsable() != null) {
                continue; // filière déjà pilotée : on ne la réattribue pas.
            }
            program.setResponsable(responsable);
            programRepository.save(program);
            log.info("Filière « {} » ({}) affectée au responsable pédagogique {}.",
                    program.getNom(), program.getCode(), demo.email());
            return;
        }

        log.warn("Aucune filière candidate disponible pour le responsable {} : "
                + "affectation ignorée (aucun code parmi {} n'est libre).",
                demo.email(), demo.programCodes());
    }
}
