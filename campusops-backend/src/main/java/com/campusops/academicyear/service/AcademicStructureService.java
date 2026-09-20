package com.campusops.academicyear.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Logique métier réutilisable de mise en place de la structure académique :
 * semestres rattachés au niveau, promotions et groupes d'une année donnée.
 *
 * <p>Centralisée ici pour être partagée entre le seed de démarrage
 * ({@code AcademicStructureInitializer}) et le passage à l'année suivante
 * ({@code AcademicYearRolloverService}), sans duplication.
 *
 * <p>Toutes les opérations sont <b>idempotentes</b> (contrôles {@code existsBy...}
 * avant insertion) et <b>non destructives</b> pour les données réelles et
 * historiques.
 *
 * <p><b>Semestres par niveau :</b> le nombre de semestres est <b>dérivé de
 * {@code Level.nombreAnnees}</b> (2 semestres par année) et numéroté selon la
 * place du cycle dans le cursus LMD. Tronc commun (2 ans) → S1–S4 ; Licence
 * (1 an) → S1–S2 ; <b>Master (2 ans) → S7–S10</b> (il fait suite à une licence
 * bac+3) ; Cycle ingénieur (3 ans) → S1–S6. Un niveau sans {@code nombreAnnees}
 * (base non réinitialisée) retombe sur l'ancien barème par nom (filet de
 * sécurité). Le semestre est rattaché à son niveau : « S1 » du Tronc commun ≠
 * « S1 » d'un autre cycle (unicité composite nom + niveau).
 *
 * <p><b>Groupes par année d'étude (configurables) :</b> le nombre de groupes
 * parallèles par niveau (Tronc commun = 2, Licence = 2, Master = 1, Cycle
 * ingénieur = 2 par défaut) est appliqué à <b>chaque année d'étude</b> du cycle
 * (nombre d'années = {@code Level.nombreAnnees}). Les groupes sont nommés
 * {@code <filière>-<k>A-G<n>} (année d'étude k, groupe parallèle n), ex.
 * {@code RSI-1A-G1}, {@code RSI-2A-G1}, et portent leur année d'étude dans
 * {@code Group.anneeNiveau} (essentiel à la gestion et à l'import des emplois
 * du temps). <b>Migration non destructive</b> : un groupe historique
 * {@code <filière>-G<n>} (sans année) est renommé en {@code <filière>-1A-G<n>}
 * et son {@code anneeNiveau} renseigné (même entité, aucune suppression) ; les
 * groupes déjà datés dont l'année manque sont rétro-remplis. Doctorat et
 * Formation continue : aucun groupe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicStructureService {

    private final LevelRepository levelRepository;
    private final ProgramRepository programRepository;
    private final SemesterRepository semesterRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ScheduleRepository scheduleRepository;
    private final ModuleRepository moduleRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;

    @Value("${campusops.seed.cleanup-legacy-semesters:true}")
    private boolean cleanupLegacyEnabled;

    @Value("${campusops.seed.groups.tronc-commun:2}")
    private int groupsTroncCommun;

    @Value("${campusops.seed.groups.licence:2}")
    private int groupsLicence;

    @Value("${campusops.seed.groups.master:1}")
    private int groupsMaster;

    @Value("${campusops.seed.groups.cycle-ingenieur:2}")
    private int groupsCycleIngenieur;

    /** Compteurs d'un passage de seed (pour journalisation). */
    public record SeedCounts(int promotions, int groups) {}

    /**
     * Motif d'un nom de groupe « historique » (sans année d'étude) :
     * {@code <filière>-G<n>}. Sert à renommer ces groupes en année 1 lors de la
     * migration non destructive vers les groupes par année d'étude. Un nom déjà
     * daté ({@code ISI-1A-G1}) matche aussi ce motif, mais son préfixe capturé
     * ({@code ISI-1A}) ne correspondra pas au suffixe court de la filière, ce qui
     * l'exclut naturellement du renommage.
     */
    private static final Pattern LEGACY_GROUP_NAME = Pattern.compile("^(.+)-G(\\d+)$");

    /**
     * Motif d'un nom de groupe <b>déjà daté</b> {@code <filière>-<k>A-G<n>} :
     * la première capture est l'année d'étude {@code k}. Sert à rétro-remplir
     * {@code Group.anneeNiveau} des groupes datés créés avant l'ajout de la
     * colonne (valeur nulle), sans les renommer ni les recréer.
     */
    private static final Pattern DATED_GROUP_NAME = Pattern.compile("^.+-(\\d+)A-G\\d+$");

    // --- Semestres rattachés au niveau -------------------------------------

    @Transactional
    public int seedSemestersPerLevel() {
        int created = 0;
        for (Level level : levelRepository.findAll()) {
            for (int numero : semesterNumbersFor(level)) {
                String nom = "S" + numero;
                if (!semesterRepository.existsByNomAndLevelId(nom, level.getId())) {
                    semesterRepository.save(Semester.builder()
                            .nom(nom)
                            .ordre(numero)
                            .level(level)
                            .actif(true)
                            .build());
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Supprime uniquement les semestres de démonstration « Semestre N » sans
     * niveau et non référencés par un emploi du temps. Ne touche jamais à un
     * semestre libre créé volontairement (nom différent) ni à un semestre utilisé.
     * No-op si {@code campusops.seed.cleanup-legacy-semesters=false}.
     */
    @Transactional
    public int cleanupLegacyDemoSemesters() {
        if (!cleanupLegacyEnabled) {
            return 0;
        }
        int removed = 0;
        for (Semester legacy : semesterRepository.findByLevelIsNullOrderByOrdreAsc()) {
            String nom = legacy.getNom() == null ? "" : legacy.getNom().trim();
            if (!nom.matches("(?i)semestre\\s+\\d+")) {
                continue; // semestre libre volontaire : on n'y touche pas
            }
            if (scheduleRepository.existsBySemesterId(legacy.getId())) {
                log.info("Semestre générique « {} » conservé : encore référencé par un emploi du temps.", nom);
                continue;
            }
            semesterRepository.delete(legacy);
            removed++;
        }
        return removed;
    }

    /**
     * Supprime les semestres rattachés à un niveau dont le numéro ne fait plus
     * partie du barème attendu ({@link #semesterNumbersFor}) — typiquement des
     * semestres mal numérotés hérités d'un ancien barème (ex. un Master qui
     * portait S1–S4 alors qu'il doit désormais aller de S7 à S10). Purement
     * correctif et <b>non destructif</b> : un semestre inattendu n'est retiré
     * que s'il n'est référencé par <i>aucun</i> module, emploi du temps ou
     * séance. No-op si {@code campusops.seed.cleanup-legacy-semesters=false}.
     */
    @Transactional
    public int cleanupUnexpectedLevelSemesters() {
        if (!cleanupLegacyEnabled) {
            return 0;
        }
        int removed = 0;
        for (Level level : levelRepository.findAll()) {
            Set<Integer> expected = Arrays.stream(semesterNumbersFor(level))
                    .boxed().collect(Collectors.toSet());
            for (Semester s : semesterRepository.findByLevelIdOrderByOrdreAsc(level.getId())) {
                Integer ordre = s.getOrdre();
                if (ordre != null && expected.contains(ordre)) {
                    continue; // semestre attendu : conservé.
                }
                if (moduleRepository.existsBySemesterId(s.getId())
                        || scheduleRepository.existsBySemesterId(s.getId())
                        || emploiDuTempsRepository.existsBySemesterId(s.getId())) {
                    log.info("Semestre « {} » du niveau « {} » inattendu mais conservé : "
                            + "encore référencé (module / emploi du temps / séance).",
                            s.getNom(), level.getNom());
                    continue;
                }
                semesterRepository.delete(s);
                removed++;
            }
        }
        return removed;
    }

    // --- Promotions + groupes d'une année ----------------------------------

    /**
     * Crée (si absents) les promotions et groupes initiaux d'une année
     * universitaire, pour chaque filière rattachée à un niveau générant des
     * groupes (Doctorat et Formation continue exclus). Idempotent : une
     * promotion existante est réutilisée, et les groupes ne sont créés que si la
     * promotion n'en possède aucun (ne ressuscite pas des groupes désactivés).
     */
    @Transactional
    public SeedCounts seedPromotionsAndGroupsForYear(AcademicYear year) {
        if (year == null) {
            log.warn("Année nulle : promotions/groupes non initialisés.");
            return new SeedCounts(0, 0);
        }

        int promotionsCreated = 0;
        int groupsCreated = 0;
        int groupsRenamed = 0;

        for (Program program : programRepository.findAll()) {
            Level level = program.getLevel();
            if (level == null) {
                log.debug("Filière « {} » sans niveau : ignorée pour le seed de promotions.", program.getNom());
                continue;
            }

            int groupCount = groupCountFor(level);
            if (groupCount <= 0) {
                // Doctorat / Formation continue : pas de promotion/groupe automatique.
                continue;
            }

            Promotion promotion = promotionRepository
                    .findByProgramIdAndLevelIdAndAcademicYearId(program.getId(), level.getId(), year.getId())
                    .orElse(null);
            if (promotion == null) {
                promotion = promotionRepository.save(Promotion.builder()
                        .nom(level.getNom() + " — " + program.getNom())
                        .program(program)
                        .level(level)
                        .academicYear(year)
                        .actif(true)
                        .build());
                promotionsCreated++;
            }

            // Groupes par année d'étude : le nombre de groupes parallèles du
            // niveau (groupCount) est appliqué à CHACUNE des années d'étude du
            // cycle (years = semestres / 2).
            String suffix = shortCode(program.getCode());
            int years = studyYearsFor(level);
            if (years <= 0) {
                continue; // cycle sans semestres : aucune année d'étude à peupler
            }

            // Migration non destructive des groupes existants :
            //  - un groupe historique « <suffix>-G<n> » (sans année) devient
            //    « <suffix>-1A-G<n> » avec anneeNiveau=1 ;
            //  - un groupe déjà daté « <suffix>-<k>A-G<n> » dont anneeNiveau est
            //    nul est rétro-rempli à partir de son nom.
            // On ne renomme jamais vers un nom déjà pris (idempotent, zéro
            // doublon) et on ne supprime rien (entité, EDT et historique restent).
            for (Group existing : groupRepository.findByPromotionId(promotion.getId())) {
                String currentNom = existing.getNom() == null ? "" : existing.getNom();
                Matcher legacy = LEGACY_GROUP_NAME.matcher(currentNom);
                if (legacy.matches() && suffix.equals(legacy.group(1))) {
                    // Cas 1 : groupe historique sans année.
                    boolean changed = false;
                    String renamed = suffix + "-1A-G" + legacy.group(2);
                    if (!renamed.equals(currentNom)
                            && !groupRepository.existsByPromotionIdAndNomAndIdNot(
                                    promotion.getId(), renamed, existing.getId())) {
                        existing.setNom(renamed);
                        changed = true;
                        groupsRenamed++;
                    }
                    if (existing.getAnneeNiveau() == null) {
                        existing.setAnneeNiveau(1);
                        changed = true;
                    }
                    if (changed) {
                        groupRepository.save(existing);
                    }
                } else if (existing.getAnneeNiveau() == null) {
                    // Cas 2 : groupe déjà daté, année manquante → backfill depuis le nom.
                    Integer annee = parseAnneeNiveau(currentNom);
                    if (annee != null) {
                        existing.setAnneeNiveau(annee);
                        groupRepository.save(existing);
                    }
                }
                // Rétro-remplissage non destructif de l'effectif (colonne ajoutée
                // après coup) : on ne renseigne que les groupes dont l'effectif
                // est nul, sans écraser une valeur saisie par l'administrateur.
                if (existing.getEffectif() == null) {
                    existing.setEffectif(defaultEffectifFor(level));
                    groupRepository.save(existing);
                }
            }

            // Création idempotente des groupes attendus, par année d'étude puis
            // par groupe parallèle. Seuls les groupes réellement manquants sont
            // créés ; un groupe désactivé n'est jamais ressuscité (test par nom).
            // Chaque groupe porte son année d'étude dans anneeNiveau.
            for (int annee = 1; annee <= years; annee++) {
                for (int n = 1; n <= groupCount; n++) {
                    String nom = suffix + "-" + annee + "A-G" + n;
                    if (!groupRepository.existsByPromotionIdAndNom(promotion.getId(), nom)) {
                        groupRepository.save(Group.builder()
                                .nom(nom)
                                .promotion(promotion)
                                .anneeNiveau(annee)
                                .effectif(defaultEffectifFor(level))
                                .actif(true)
                                .build());
                        groupsCreated++;
                    }
                }
            }
        }
        if (groupsRenamed > 0) {
            log.info("Migration groupes par année d'étude : {} groupe(s) historique(s) "
                    + "« <filière>-G<n> » renommé(s) en « <filière>-1A-G<n> » (aucune suppression).",
                    groupsRenamed);
        }
        return new SeedCounts(promotionsCreated, groupsCreated);
    }

    /** Seed des promotions/groupes de l'année universitaire active. */
    @Transactional
    public SeedCounts seedPromotionsAndGroupsForActiveYear() {
        AcademicYear activeYear = academicYearRepository.findFirstByActifTrue().orElse(null);
        if (activeYear == null) {
            log.warn("Aucune année universitaire active : promotions/groupes non initialisés.");
            return new SeedCounts(0, 0);
        }
        return seedPromotionsAndGroupsForYear(activeYear);
    }

    // --- Helpers -----------------------------------------------------------

    /**
     * Numéros de semestres attendus pour un niveau, <b>dérivés de
     * {@code Level.nombreAnnees}</b> (2 semestres par année) à partir du premier
     * semestre du cycle ({@link #semesterStartFor}) : {@code start .. start +
     * 2×années − 1}. Master → S7–S10, les autres cycles → S1.. Si
     * {@code nombreAnnees} est absent (base non réinitialisée), on retombe sur
     * l'ancien barème par nom de niveau.
     */
    private int[] semesterNumbersFor(Level level) {
        Integer years = level.getNombreAnnees();
        if (years != null && years > 0) {
            int start = semesterStartFor(level);
            int[] nums = new int[years * 2];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = start + i; // start .. start + 2×années − 1
            }
            return nums;
        }
        return legacySemesterNumbersByName(level);
    }

    /**
     * Premier numéro de semestre d'un cycle, selon sa place dans le cursus LMD.
     * Le Master fait suite à une licence (bac+3) : sa numérotation commence donc
     * à <b>S7</b> (bac+4 → S7–S8, bac+5 → S9–S10). Les autres cycles amorcent
     * leur propre numérotation à S1.
     */
    private int semesterStartFor(Level level) {
        String n = normalize(level.getNom());
        if (n.contains("master")) {
            return 7; // Master : 2 ans → S7, S8, S9, S10.
        }
        return 1;
    }

    /**
     * Filet de sécurité (base non réinitialisée, {@code nombreAnnees} nul) :
     * ancien barème de semestres basé sur le nom normalisé du niveau.
     */
    private int[] legacySemesterNumbersByName(Level level) {
        String n = normalize(level.getNom());
        if (n.contains("tronc commun")) return new int[]{1, 2};
        if (n.contains("licence")) return new int[]{3, 4, 5, 6};
        if (n.contains("master")) return new int[]{7, 8, 9, 10};
        if (n.contains("ingenieur")) return new int[]{1, 2, 3, 4, 5, 6};
        return new int[0]; // Doctorat / Formation continue
    }

    private int groupCountFor(Level level) {
        String n = normalize(level.getNom());
        if (n.contains("tronc commun")) return groupsTroncCommun;
        if (n.contains("licence")) return groupsLicence;
        if (n.contains("master")) return groupsMaster;
        if (n.contains("ingenieur")) return groupsCycleIngenieur;
        return 0; // Doctorat / Formation continue
    }

    /**
     * Effectif par défaut d'un groupe selon son cycle, utilisé pour amorcer la
     * colonne {@code effectif} (l'administrateur peut ensuite l'ajuster). Les
     * valeurs sont volontairement <b>≤ 25</b> pour que chaque groupe tienne dans
     * un laboratoire (capacité 25) ou une salle informatique (30) lors d'un TP,
     * et dans une salle de cours (40) pour un TD ; un cours réunissant toute la
     * promotion passe alors en amphithéâtre. Masters plus réduits que les
     * cycles à fort effectif.
     */
    private int defaultEffectifFor(Level level) {
        String n = normalize(level.getNom());
        if (n.contains("master")) return 18;
        if (n.contains("ingenieur")) return 24;
        if (n.contains("licence")) return 25;
        if (n.contains("tronc commun")) return 25;
        return 20;
    }

    /**
     * Nombre d'<b>années d'étude</b> d'un cycle = nombre de semestres / 2
     * (2 semestres par année), donc égal à {@code Level.nombreAnnees}. Tronc
     * commun → 2, Licence → 1, Master → 2, Cycle ingénieur → 3. Doctorat /
     * Formation continue → 0 (aucun groupe). Dérivé du même référentiel de
     * semestres que {@link #semesterNumbersFor} pour rester cohérent, sans
     * aucune valeur codée en dur ailleurs.
     */
    private int studyYearsFor(Level level) {
        return semesterNumbersFor(level).length / 2;
    }

    /** Abréviation de filière = partie du code après le préfixe de cycle. */
    private String shortCode(String code) {
        if (code == null || code.isBlank()) return "G";
        int dash = code.indexOf('-');
        if (dash >= 0 && dash < code.length() - 1) {
            return code.substring(dash + 1);
        }
        return code;
    }

    /**
     * Extrait l'année d'étude {@code k} d'un nom de groupe daté
     * {@code <filière>-<k>A-G<n>}, ou {@code null} si le nom ne suit pas ce motif.
     */
    private Integer parseAnneeNiveau(String nom) {
        if (nom == null) return null;
        Matcher m = DATED_GROUP_NAME.matcher(nom);
        if (m.matches()) {
            try {
                return Integer.valueOf(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Minuscule + suppression des accents pour une comparaison robuste. */
    private String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .toLowerCase();
    }
}
