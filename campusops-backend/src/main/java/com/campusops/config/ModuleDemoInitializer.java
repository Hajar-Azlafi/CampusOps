package com.campusops.config;

import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Amorçage des <b>modules d'enseignement</b> du petit jeu de données de
 * démonstration (cahier des charges §6 &amp; §9), afin que les écrans d'emploi
 * du temps disposent d'un référentiel de modules à sélectionner et que les
 * réservations reposent sur des données réelles.
 *
 * <p><b>Ancrage (filière + semestre)</b> : pour chaque filière rattachée à un
 * cycle, on amorce les modules de chacun de ses semestres (semestres créés par
 * {@code AcademicStructureInitializer}, {@code @Order(8)}), en s'appuyant sur
 * l'<b>ordre</b> du semestre (numérotation propre au cycle : S1..S(2×années)).
 * Les filières sans cycle ne reçoivent aucun module — c'est volontaire.</p>
 *
 * <p><b>Curaté et cohérent par filière (§6)</b> : chaque filière possède ses
 * propres modules, cohérents avec sa discipline et son niveau ; ils ne sont
 * <b>pas identiques</b> d'une filière à l'autre. Les listes sont indexées par
 * <b>code de filière</b> (référentiel reconstruit par
 * {@code ReferenceDataResetInitializer}) puis par <b>ordre de semestre</b>. Une
 * filière absente du curriculum retombe sur un petit pool transversal (filet de
 * sécurité pour une base non réinitialisée).</p>
 *
 * <p><b>Idempotent &amp; non destructif</b> : un module déjà présent dans un
 * contexte (même filière + même semestre + même libellé) n'est jamais recréé ni
 * modifié. Réglable par la propriété {@code campusops.seed.modules}
 * (activée par défaut). S'exécute après le référentiel académique
 * ({@code @Order(13)}).</p>
 */
@Component
@Order(13)
@RequiredArgsConstructor
@Slf4j
public class ModuleDemoInitializer implements CommandLineRunner {

    private final ProgramRepository programRepository;
    private final SemesterRepository semesterRepository;
    private final ModuleRepository moduleRepository;

    @Value("${campusops.seed.modules:true}")
    private boolean enabled;

    /**
     * Curriculum curaté : {@code code filière → (ordre semestre → libellés)}.
     * L'ordre de semestre suit la numérotation propre au cycle (S1 = ordre 1…).
     * Les listes ING-GI (S1–S6) et MAS-RSI (M1 S1/S2) proviennent du cahier des
     * charges ; les autres semestres et filières sont construits de façon
     * réaliste et cohérente avec la discipline (§6 : jamais identiques partout).
     */
    private static final Map<String, Map<Integer, List<String>>> CURRICULUM = buildCurriculum();

    /**
     * Pool transversal de secours, utilisé uniquement pour une filière absente
     * du curriculum curaté (base non réinitialisée). Rotation déterministe.
     */
    private static final List<String> FALLBACK_POOL = List.of(
            "Langues et Communication",
            "Anglais",
            "Méthodologie du Travail Universitaire",
            "Compétences Numériques",
            "Culture Entrepreneuriale et Management",
            "Projet Personnel et Professionnel"
    );

    /** Nombre de modules de secours amorcés par contexte (filière + semestre). */
    private static final int FALLBACK_PER_CONTEXT = 4;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Amorçage des modules de démonstration désactivé "
                    + "(campusops.seed.modules=false).");
            return;
        }

        int created = 0;
        int contexts = 0;
        for (Program program : programRepository.findAll()) {
            if (program.getLevel() == null) {
                continue; // filière sans cycle : aucun contexte (filière + semestre).
            }
            List<Semester> semesters = semesterRepository
                    .findByLevelIdAndActifOrderByOrdreAsc(program.getLevel().getId(), true);
            // La position dans le cycle (1er, 2e… semestre) sert de clé au
            // curriculum, indépendamment du numéro absolu (le Master va de S7 à
            // S10 mais ses listes curatées restent indexées 1..4).
            int cyclePosition = 1;
            for (Semester semester : semesters) {
                contexts++;
                created += seedContext(program, semester, cyclePosition);
                cyclePosition++;
            }
        }

        log.info("Modules de démonstration : {} créé(s) sur {} contexte(s) "
                        + "(filière + semestre) ; {} module(s) au total.",
                created, contexts, moduleRepository.count());
    }

    /**
     * Amorce (idempotent) les modules d'un contexte (filière + semestre). Les
     * libellés déjà présents pour ce contexte sont ignorés — jamais dupliqués ni
     * écrasés (non destructif). Renvoie le nombre de modules réellement créés.
     */
    private int seedContext(Program program, Semester semester, int cyclePosition) {
        // Libellés déjà présents dans ce contexte (comparaison insensible à la casse).
        Set<String> existing = new HashSet<>();
        for (Module m : moduleRepository.findByProgramIdAndSemesterIdOrderByNomAsc(
                program.getId(), semester.getId())) {
            if (m.getNom() != null) {
                existing.add(m.getNom().toLowerCase(Locale.ROOT));
            }
        }

        int created = 0;
        int index = 1;
        for (String nom : modulesFor(program, cyclePosition)) {
            String code = buildCode(program, semester, index);
            index++;
            if (existing.contains(nom.toLowerCase(Locale.ROOT))) {
                continue; // déjà présent : idempotent.
            }
            moduleRepository.save(Module.builder()
                    .nom(nom)
                    .code(code)
                    .description("Module — " + program.getNom() + " (" + semester.getNom() + ").")
                    .program(program)
                    .semester(semester)
                    .actif(true)
                    .build());
            created++;
        }
        return created;
    }

    /**
     * Renvoie les libellés de modules pour un contexte : les modules curatés de
     * la filière à la <b>position du semestre dans son cycle</b> (1er, 2e…)
     * si disponibles, sinon une rotation déterministe du pool transversal de
     * secours. La clé est la position dans le cycle et non le numéro absolu, car
     * le curriculum est indexé 1..n (le Master S7–S10 réutilise les positions
     * 1..4).
     */
    private List<String> modulesFor(Program program, int cyclePosition) {
        Map<Integer, List<String>> perSemester = (program.getCode() != null)
                ? CURRICULUM.get(program.getCode())
                : null;
        if (perSemester != null) {
            List<String> curated = perSemester.get(cyclePosition);
            if (curated != null && !curated.isEmpty()) {
                return curated;
            }
        }
        return fallbackModules(cyclePosition);
    }

    /** Rotation déterministe du pool transversal (filet de sécurité, jamais aléatoire). */
    private List<String> fallbackModules(int ordre) {
        int poolSize = FALLBACK_POOL.size();
        int start = Math.floorMod(ordre - 1, poolSize);
        List<String> selection = new ArrayList<>(FALLBACK_PER_CONTEXT);
        for (int i = 0; i < FALLBACK_PER_CONTEXT && i < poolSize; i++) {
            selection.add(FALLBACK_POOL.get((start + i) % poolSize));
        }
        return selection;
    }

    /**
     * Code de module traçable, dérivé du code de la filière, du libellé du
     * semestre et d'un index. Tronqué à la longueur maximale de la colonne (40).
     */
    private String buildCode(Program program, Semester semester, int index) {
        String base = (program.getCode() != null && !program.getCode().isBlank())
                ? program.getCode().trim()
                : "MOD";
        String sem = (semester.getNom() != null && !semester.getNom().isBlank())
                ? semester.getNom().trim().replaceAll("\\s+", "")
                : ("S" + ((semester.getOrdre() != null) ? semester.getOrdre() : 0));
        String code = base + "-" + sem + "-" + String.format("%02d", index);
        return (code.length() > 40) ? code.substring(0, 40) : code;
    }

    // --- Curriculum curaté par filière -------------------------------------

    private static Map<String, Map<Integer, List<String>>> buildCurriculum() {
        Map<String, Map<Integer, List<String>>> c = new LinkedHashMap<>();

        // === Département Informatique ======================================

        // Cycle ingénieur Génie Informatique — S1..S6 (cahier des charges §6).
        c.put("ING-GI", sem(
                List.of("Algorithmique 1", "Programmation 1", "Mathématiques 1",
                        "Architecture des ordinateurs", "Systèmes d'exploitation",
                        "Langues et Communication"),
                List.of("Algorithmique 2", "Programmation 2", "Bases de données",
                        "Mathématiques 2", "Réseaux 1", "Langues et Communication"),
                List.of("Programmation Orientée Objet", "Structures de données",
                        "Bases de données avancées", "Réseaux 2", "Génie logiciel",
                        "Systèmes d'exploitation avancés"),
                List.of("Développement Web", "Architecture logicielle",
                        "Administration systèmes", "Réseaux avancés",
                        "Analyse et conception", "Anglais technique"),
                List.of("Java / Spring", "Développement Web avancé", "Sécurité informatique",
                        "Cloud Computing", "DevOps", "Gestion de projet"),
                List.of("Projet tutoré", "Projet de fin d'études", "Développement Full Stack",
                        "Administration réseaux et systèmes", "Sécurité avancée", "Entrepreneuriat")
        ));

        // Master Réseaux et Systèmes Informatiques — S1..S4 (M1 §9, M2 cohérent).
        c.put("MAS-RSI", sem(
                List.of("Réseaux avancés", "Administration systèmes", "Sécurité des réseaux",
                        "Développement Web", "Bases de données avancées", "Virtualisation et Cloud",
                        "Anglais scientifique"),
                List.of("DevOps", "Architectures distribuées", "Sécurité des systèmes",
                        "Développement mobile", "Intelligence artificielle", "Gestion de projets",
                        "Projet"),
                List.of("Réseaux définis par logiciel (SDN)", "Cybersécurité avancée",
                        "Cloud Computing avancé", "Big Data et analyse de données",
                        "Internet des objets (IoT)", "Anglais professionnel"),
                List.of("Projet de fin d'études", "Stage professionnel",
                        "Conduite de projet informatique", "Audit et sécurité des systèmes",
                        "Technologies émergentes (Blockchain)", "Entrepreneuriat et innovation")
        ));

        // Licence Génie Informatique — S1..S2 (année de licence, ingénierie logicielle).
        c.put("LIC-GI", sem(
                List.of("Programmation avancée", "Bases de données relationnelles",
                        "Développement Web", "Génie logiciel", "Réseaux informatiques",
                        "Anglais technique"),
                List.of("Développement d'applications mobiles",
                        "Programmation orientée objet avancée", "Systèmes d'information",
                        "Sécurité informatique", "Projet de fin de licence",
                        "Méthodologie de projet")
        ));

        // Licence Systèmes d'Information et Développement — S1..S2.
        c.put("LIC-SID", sem(
                List.of("Analyse et conception des systèmes d'information",
                        "Bases de données avancées", "Développement Web full-stack",
                        "Modélisation UML", "Gestion de projet informatique",
                        "Anglais professionnel"),
                List.of("Business Intelligence", "Développement d'applications d'entreprise",
                        "Administration de bases de données", "ERP et progiciels de gestion",
                        "Projet de fin de licence", "Entrepreneuriat numérique")
        ));

        // Tronc commun Sciences et Technologies — S1..S4.
        c.put("TC-ST", sem(
                List.of("Analyse 1", "Algèbre 1", "Mécanique du point", "Chimie générale",
                        "Informatique 1", "Langues et Communication"),
                List.of("Analyse 2", "Algèbre 2", "Électrostatique et électrocinétique",
                        "Thermodynamique", "Informatique 2", "Anglais"),
                List.of("Analyse 3", "Probabilités et statistiques", "Optique géométrique",
                        "Chimie des solutions", "Électronique de base", "Méthodologie"),
                List.of("Équations différentielles", "Mécanique des solides", "Électromagnétisme",
                        "Structure de la matière", "Programmation", "Culture entrepreneuriale")
        ));

        // Tronc commun Mathématiques-Informatique — S1..S4.
        c.put("TC-MI", sem(
                List.of("Analyse 1", "Algèbre 1", "Logique et raisonnement", "Algorithmique 1",
                        "Bureautique et outils numériques", "Langues et Communication"),
                List.of("Analyse 2", "Algèbre 2", "Structures de données", "Algorithmique 2",
                        "Architecture des ordinateurs", "Anglais"),
                List.of("Analyse 3", "Probabilités", "Théorie des graphes",
                        "Programmation orientée objet", "Bases de données", "Méthodologie"),
                List.of("Analyse numérique", "Statistiques", "Automates et langages",
                        "Programmation Web", "Systèmes d'exploitation", "Culture entrepreneuriale")
        ));

        // Tronc commun Sciences de l'Ingénieur — S1..S4.
        c.put("TC-SI", sem(
                List.of("Analyse 1", "Algèbre 1", "Mécanique du point", "Dessin technique",
                        "Informatique 1", "Langues et Communication"),
                List.of("Analyse 2", "Électricité générale", "Résistance des matériaux",
                        "Thermodynamique", "Informatique 2", "Anglais"),
                List.of("Mécanique des fluides", "Électronique analogique", "Automatique",
                        "Science des matériaux", "Fabrication mécanique", "Méthodologie"),
                List.of("Électrotechnique", "Systèmes asservis", "Conception mécanique (CAO)",
                        "Électronique numérique", "Programmation industrielle",
                        "Culture entrepreneuriale")
        ));

        // === Département Mathématiques =====================================

        // Licence Mathématiques — S1..S2 (année de licence).
        c.put("LIC-MATH", sem(
                List.of("Topologie", "Analyse réelle et complexe", "Algèbre linéaire avancée",
                        "Probabilités", "Analyse numérique", "Anglais scientifique"),
                List.of("Analyse fonctionnelle", "Théorie de la mesure",
                        "Équations différentielles", "Statistique mathématique",
                        "Optimisation", "Projet de fin de licence")
        ));

        // Master Mathématiques Appliquées — S1..S4.
        c.put("MAS-MA", sem(
                List.of("Optimisation", "Analyse numérique avancée", "Probabilités avancées",
                        "Modélisation mathématique", "Programmation scientifique",
                        "Anglais scientifique"),
                List.of("Statistique inférentielle", "Équations aux dérivées partielles",
                        "Méthodes numériques", "Recherche opérationnelle", "Analyse de données",
                        "Projet"),
                List.of("Apprentissage statistique (Machine Learning)",
                        "Calcul scientifique haute performance", "Modèles stochastiques",
                        "Séries temporelles", "Simulation numérique", "Anglais professionnel"),
                List.of("Projet de fin d'études", "Stage en entreprise",
                        "Mathématiques financières", "Science des données (Big Data)",
                        "Optimisation avancée", "Entrepreneuriat")
        ));

        // === Département Sciences Physiques ================================

        // Licence Physique — S1..S2.
        c.put("LIC-PHYS", sem(
                List.of("Mécanique quantique", "Électromagnétisme", "Thermodynamique statistique",
                        "Physique des ondes", "Mathématiques pour la physique",
                        "Anglais scientifique"),
                List.of("Physique du solide", "Optique physique", "Physique nucléaire",
                        "Instrumentation et mesures", "Travaux pratiques de physique",
                        "Projet de fin de licence")
        ));

        // Licence Chimie — S1..S2.
        c.put("LIC-CHIM", sem(
                List.of("Chimie organique", "Chimie inorganique", "Chimie analytique",
                        "Thermodynamique chimique", "Spectroscopie", "Anglais scientifique"),
                List.of("Chimie physique", "Électrochimie", "Chimie des polymères",
                        "Sécurité et environnement", "Travaux pratiques de chimie",
                        "Projet de fin de licence")
        ));

        return c;
    }

    /**
     * Construit la table {@code ordre semestre → libellés} à partir des listes
     * fournies dans l'ordre : le 1er argument devient S1 (ordre 1), le 2e S2, etc.
     */
    @SafeVarargs
    private static Map<Integer, List<String>> sem(List<String>... semesters) {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < semesters.length; i++) {
            m.put(i + 1, semesters[i]);
        }
        return m;
    }
}
