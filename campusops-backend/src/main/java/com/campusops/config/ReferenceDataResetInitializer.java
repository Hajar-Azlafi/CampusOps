package com.campusops.config;

import com.campusops.department.entity.Department;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.enums.TypeFormation;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Réinitialise et reconstruit un <b>petit</b> référentiel de démonstration
 * (3 départements, 4 niveaux, 11 filières) conçu pour tester le module de
 * réservation des espaces : peu de données, mais correctement reliées et assez
 * variées pour couvrir les scénarios (salles libres/occupées, priorités, EDT,
 * réservations en attente/acceptées/refusées).
 *
 * <p>La reconstruction s'exécute <b>automatiquement une seule fois</b> au
 * démarrage tant que la filière témoin {@code code=MAS-RSI} (jeu de données
 * cible) est absente, ou peut être <b>forcée</b> via
 * {@code campusops.seed.reset-reference-data=true}.
 *
 * <p><b>Ce qui est supprimé</b> (dans l'ordre des clés étrangères, enfants →
 * parents) : réservations → occupations supplémentaires (examens, soutenances,
 * autres) → séances → emplois du temps → modules → groupes → promotions →
 * filières → semestres → niveaux ; puis les
 * <b>comptes responsables pédagogiques</b> existants (leurs notifications sont
 * supprimées et leurs entrées d'audit détachées) — ils seront recréés, adaptés
 * aux nouvelles filières, par les seeders RP (@Order 10/11). Les départements
 * hors cible sont retirés (best-effort).
 *
 * <p><b>Ce qui est CONSERVÉ</b> : espaces / bâtiments / étages / équipements,
 * créneaux horaires, types de séance, sessions universitaires, années
 * universitaires, et tous les comptes non-RP (notamment {@code ADMIN},
 * enseignants et responsables de club).
 *
 * <p>Structure cible : Département → Filière → Niveau/Cycle. Une même filière
 * (nom) peut exister dans plusieurs cycles ; l'unicité est composite
 * (nom + niveau). Le {@code code} reste globalement unique. Le nombre d'années
 * de chaque niveau ({@code Level.nombreAnnees}) pilote ensuite le nombre de
 * semestres et de groupes (voir {@code AcademicStructureService}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(7)
public class ReferenceDataResetInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final LevelRepository levelRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final ModuleRepository moduleRepository;
    private final SemesterRepository semesterRepository;
    private final ScheduleRepository scheduleRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final OccupationSupplementaireRepository occupationRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${campusops.seed.reset-reference-data:false}")
    private boolean resetEnabled;

    /**
     * Filière témoin du jeu de données cible : sa présence indique que le petit
     * référentiel de démonstration est déjà en place (on ne réinitialise pas).
     */
    private static final String SIGNATURE_CODE = "MAS-RSI";

    // --- Départements cibles (clé stable = code) ---------------------------
    private static final String DEP_INFO = "INFO";
    private static final String DEP_MATH = "MATH";
    private static final String DEP_PHYS = "PHYS";

    private static final List<String> TARGET_DEPARTMENT_CODES = List.of(DEP_INFO, DEP_MATH, DEP_PHYS);

    private Map<String, Department> departmentsByCode;

    @Override
    public void run(String... args) {
        boolean alreadySeeded = programRepository.existsByCode(SIGNATURE_CODE);
                if (!resetEnabled) {
                        // La reconstruction est strictement opt-in : un référentiel partiel
                        // ou ancien ne doit jamais entraîner la suppression de données.
            return;
        }

        log.warn("[reset-reference-data] Reconstruction du petit référentiel de démonstration "
                + "(3 départements / 4 niveaux / 11 filières).");

        dropLegacyNomUniqueConstraint();

        // 1) Suppression des données académiques dans l'ordre des clés étrangères
        //    (enfants → parents). Les espaces, comptes et référentiels d'infra
        //    ne sont JAMAIS touchés.
        deleteAcademicDataInFkSafeOrder();

        // 2) Suppression des comptes responsables pédagogiques existants
        //    (recréés ensuite, adaptés aux nouvelles filières).
        deleteLegacyResponsables();

        // 3) Départements : on retire ceux hors cible (les filières ayant été
        //    supprimées, plus aucune dépendance ne les référence), puis on
        //    garantit la présence des 3 départements cibles (clé = code).
        removeNonTargetDepartments();
        ensureTargetDepartments();
        departmentsByCode = departmentRepository.findAll().stream()
                .filter(d -> TARGET_DEPARTMENT_CODES.contains(d.getCode()))
                .collect(java.util.stream.Collectors.toMap(Department::getCode, d -> d));

        // 4) Niveaux/cycles avec leur nombre d'années (source de vérité pour la
        //    génération des semestres et des groupes).
        Level troncCommun = saveLevel("Tronc commun", 1, 2, TypeFormation.INITIALE);
        Level licence = saveLevel("Licence", 2, 1, TypeFormation.INITIALE);
        Level master = saveLevel("Master", 3, 2, TypeFormation.INITIALE);
        Level cycleIngenieur = saveLevel("Cycle ingénieur", 4, 3, TypeFormation.INITIALE);

        // 5) Filières (3 départements). Une filière = 1 département + 1 niveau.
        seedInformatique(troncCommun, licence, master, cycleIngenieur);
        seedMathematiques(licence, master);
        seedSciencesPhysiques(licence);

        log.warn("[reset-reference-data] Terminé : {} départements, {} niveaux, {} filières.",
                departmentRepository.count(), levelRepository.count(), programRepository.count());
    }

    // --- Suppression FK-safe ------------------------------------------------

    /**
     * Supprime toutes les données académiques dans l'ordre des clés étrangères.
     * {@code deleteAllInBatch} émet un unique DELETE par table (pas de cascade),
     * d'où l'importance stricte de l'ordre enfants → parents.
     *
     * <p>Les <b>occupations supplémentaires</b> (examens, soutenances et autres)
     * partagent une seule table depuis la restructuration : une suppression
     * polymorphe suffit à couvrir les trois catégories. L'ancienne table
     * {@code examens}, conservée par la migration non destructive, est purgée
     * séparément car ses clés étrangères pointent aussi vers les référentiels
     * détruits ici.</p>
     */
    private void deleteAcademicDataInFkSafeOrder() {
        long reservations = reservationRepository.count();
        long occupations = occupationRepository.count();
        long schedules = scheduleRepository.count();
        long timetables = emploiDuTempsRepository.count();
        long modules = moduleRepository.count();
        long groups = groupRepository.count();
        long promotions = promotionRepository.count();
        long programs = programRepository.count();
        long semesters = semesterRepository.count();
        long levels = levelRepository.count();

        reservationRepository.deleteAllInBatch();      // → space,user (conservés) + program/group/semester/year
        deleteLegacyExamens();                          // ancienne table (conservée par la migration)
        occupationRepository.deleteAllInBatch();       // examens + soutenances + autres (table unique)
        scheduleRepository.deleteAllInBatch();         // → program,level,promotion,group,semester,year,slot,space,module,typeSeance,edt
        emploiDuTempsRepository.deleteAllInBatch();    // → year,program,level,promotion,group,semester,session,importePar
        moduleRepository.deleteAllInBatch();           // → program,semester
        groupRepository.deleteAllInBatch();            // → promotion
        promotionRepository.deleteAllInBatch();        // → program,level,year
        programRepository.deleteAllInBatch();          // → department,level,responsable(user)
        semesterRepository.deleteAllInBatch();         // → level
        levelRepository.deleteAllInBatch();            // (aucune FK sortante)

        log.warn("[reset-reference-data] Supprimés — réservations:{}, occupations supplémentaires:{}, "
                        + "séances:{}, emplois du temps:{}, modules:{}, groupes:{}, promotions:{}, "
                        + "filières:{}, semestres:{}, niveaux:{}.",
                reservations, occupations, schedules, timetables, modules, groups, promotions, programs,
                semesters, levels);
    }

    /**
     * Purge l'ancienne table {@code examens} si elle existe encore. Elle n'est
     * plus mappée par aucune entité (les examens vivent dans
     * {@code occupations_supplementaires}) mais ses clés étrangères vers les
     * modules, promotions et semestres empêcheraient la reconstruction.
     * Best-effort : une erreur est journalisée sans interrompre le démarrage.
     */
    private void deleteLegacyExamens() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_name = 'examens'",
                    Integer.class);
            if (exists == null || exists == 0) {
                return;
            }
            int purged = jdbcTemplate.update("DELETE FROM examens");
            if (purged > 0) {
                log.warn("[reset-reference-data] Ancienne table « examens » purgée : {} ligne(s).", purged);
            }
        } catch (Exception e) {
            log.warn("[reset-reference-data] Purge de l'ancienne table « examens » ignorée : {}",
                    e.getMessage());
        }
    }

    /**
     * Supprime les comptes {@code RESPONSABLE_PEDAGOGIQUE} existants afin d'en
     * recréer de nouveaux, adaptés aux nouvelles filières (seeders RP). Les
     * dépendances vers {@code users} sont traitées d'abord :
     * <ul>
     *   <li>les <b>notifications</b> (FK {@code user_id} NOT NULL) des RP sont supprimées ;</li>
     *   <li>les <b>journaux d'audit</b> (FK {@code user_id} nullable) des RP sont détachés
     *       ({@code user_id = NULL}) pour préserver l'historique ;</li>
     *   <li>les réservations, emplois du temps et filières (qui référencent aussi
     *       {@code users}) ont déjà été supprimés à l'étape précédente.</li>
     * </ul>
     * Best-effort : toute erreur est journalisée sans interrompre le démarrage.
     */
    private void deleteLegacyResponsables() {
        try {
            String rpSelect = "SELECT id FROM users WHERE role = 'RESPONSABLE_PEDAGOGIQUE'";
            int notifs = jdbcTemplate.update(
                    "DELETE FROM notifications WHERE user_id IN (" + rpSelect + ")");
            int audits = jdbcTemplate.update(
                    "UPDATE audit_logs SET user_id = NULL WHERE user_id IN (" + rpSelect + ")");
            int users = jdbcTemplate.update(
                    "DELETE FROM users WHERE role = 'RESPONSABLE_PEDAGOGIQUE'");
            log.warn("[reset-reference-data] Responsables pédagogiques supprimés : {} compte(s) "
                    + "(+{} notification(s) supprimée(s), {} entrée(s) d'audit détachée(s)). "
                    + "De nouveaux comptes RP seront créés pour les filières cibles.", users, notifs, audits);
        } catch (Exception e) {
            log.warn("[reset-reference-data] Suppression des responsables pédagogiques ignorée "
                    + "(dépendances inattendues) : {}", e.getMessage());
        }
    }

    // --- Départements -------------------------------------------------------

    /**
     * Retire les départements dont le code n'est pas dans la cible
     * {INFO, MATH, PHYS}. Best-effort : un département encore référencé est
     * conservé sans interrompre le démarrage (ne devrait plus l'être une fois
     * les filières supprimées).
     */
    private void removeNonTargetDepartments() {
        for (Department d : departmentRepository.findAll()) {
            if (TARGET_DEPARTMENT_CODES.contains(d.getCode())) {
                continue;
            }
            try {
                departmentRepository.delete(d);
                log.warn("[reset-reference-data] Département hors cible supprimé : {} ({}).",
                        d.getNom(), d.getCode());
            } catch (Exception e) {
                log.warn("[reset-reference-data] Département « {} » conservé (dépendances) : {}",
                        d.getNom(), e.getMessage());
            }
        }
    }

    /** Garantit la présence des 3 départements cibles (créés uniquement si absents, clé = code). */
    private void ensureTargetDepartments() {
        ensureDepartment("Informatique", DEP_INFO,
                "Département d'informatique : programmation, réseaux, systèmes et intelligence artificielle.");
        ensureDepartment("Mathématiques", DEP_MATH,
                "Département de mathématiques : analyse, algèbre, probabilités et mathématiques appliquées.");
        ensureDepartment("Sciences Physiques", DEP_PHYS,
                "Département de sciences physiques : physique, chimie et sciences expérimentales.");
    }

    private void ensureDepartment(String nom, String code, String description) {
        if (departmentRepository.existsByCode(code)) {
            return;
        }
        departmentRepository.save(Department.builder()
                .nom(nom).code(code).description(description).actif(true).build());
        log.warn("[reset-reference-data] Département créé : {} ({}).", nom, code);
    }

    // --- Niveaux ------------------------------------------------------------

    private Level saveLevel(String nom, int ordre, int nombreAnnees, TypeFormation type) {
        return levelRepository.save(Level.builder()
                .nom(nom).ordre(ordre).nombreAnnees(nombreAnnees)
                .typeFormation(type).actif(true).build());
    }

    // --- Filières par département ------------------------------------------

    /** Département 1 — Informatique : Master RSI, Licences GI/SID, 3 troncs communs, Cycle ingénieur GI. */
    private void seedInformatique(Level troncCommun, Level licence, Level master, Level cycleIngenieur) {
        // Master
        createProgram("Réseaux et Systèmes Informatiques", "MAS-RSI", DEP_INFO, master,
                "Master RSI : réseaux avancés, administration systèmes, sécurité, virtualisation et cloud.");
        // Licences
        createProgram("Génie Informatique", "LIC-GI", DEP_INFO, licence,
                "Licence en développement logiciel, programmation orientée objet, bases de données et réseaux.");
        createProgram("Systèmes d'Information et Développement", "LIC-SID", DEP_INFO, licence,
                "Licence en systèmes d'information, développement d'applications et modélisation.");
        // Troncs communs
        createProgram("Sciences et Technologies", "TC-ST", DEP_INFO, troncCommun,
                "Tronc commun scientifique (maths, physique, chimie, informatique) préparant aux licences.");
        createProgram("Mathématiques-Informatique", "TC-MI", DEP_INFO, troncCommun,
                "Tronc commun mathématiques-informatique : analyse, algèbre, algorithmique et programmation.");
        createProgram("Sciences de l'Ingénieur", "TC-SI", DEP_INFO, troncCommun,
                "Tronc commun sciences de l'ingénieur : mécanique, électricité, électronique et informatique.");
        // Cycle ingénieur
        createProgram("Génie Informatique", "ING-GI", DEP_INFO, cycleIngenieur,
                "Cycle ingénieur en génie informatique : logiciel, réseaux, systèmes, cloud et sécurité.");
    }

    /** Département 2 — Mathématiques : Licence Mathématiques, Master Mathématiques Appliquées. */
    private void seedMathematiques(Level licence, Level master) {
        createProgram("Mathématiques", "LIC-MATH", DEP_MATH, licence,
                "Licence de mathématiques : analyse, algèbre, topologie, probabilités et analyse numérique.");
        createProgram("Mathématiques Appliquées", "MAS-MA", DEP_MATH, master,
                "Master de mathématiques appliquées : optimisation, statistique, modélisation et calcul scientifique.");
    }

    /** Département 3 — Sciences Physiques : Licence Physique, Licence Chimie. */
    private void seedSciencesPhysiques(Level licence) {
        createProgram("Physique", "LIC-PHYS", DEP_PHYS, licence,
                "Licence de physique : mécanique, électromagnétisme, thermodynamique, optique et physique quantique.");
        createProgram("Chimie", "LIC-CHIM", DEP_PHYS, licence,
                "Licence de chimie : chimie générale, organique, minérale, analytique et physique.");
    }

    /** Crée une filière rattachée au département identifié par son code cible et au niveau fourni. */
    private void createProgram(String nom, String code, String departmentCode, Level level, String description) {
        Department dept = departmentsByCode.get(departmentCode);
        if (dept == null) {
            log.error("[reset-reference-data] Département « {} » introuvable : filière « {} » ({}) ignorée.",
                    departmentCode, nom, code);
            return;
        }
        programRepository.save(Program.builder()
                .nom(nom).code(code).description(description)
                .department(dept).level(level)
                .build());
    }

    /**
     * Supprime l'ancienne contrainte d'unicité mono-colonne sur {@code programs.nom}.
     * Hibernate en mode {@code update} ne la retire pas et elle ferait échouer la
     * reconstruction (une même filière — nom — peut exister dans plusieurs cycles).
     */
    private void dropLegacyNomUniqueConstraint() {
        try {
            List<String> nomConstraints = jdbcTemplate.queryForList(
                    "SELECT tc.constraint_name "
                            + "FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.constraint_column_usage ccu "
                            + "  ON tc.constraint_name = ccu.constraint_name "
                            + " AND tc.table_schema = ccu.table_schema "
                            + "WHERE tc.table_name = 'programs' "
                            + "  AND tc.constraint_type = 'UNIQUE' "
                            + "GROUP BY tc.constraint_name "
                            + "HAVING COUNT(*) = 1 AND MAX(ccu.column_name) = 'nom'",
                    String.class);
            for (String constraint : nomConstraints) {
                jdbcTemplate.execute("ALTER TABLE programs DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");
                log.warn("[reset-reference-data] Contrainte d'unicité mono-colonne sur nom supprimée : {}", constraint);
            }
        } catch (Exception e) {
            log.warn("[reset-reference-data] Échec de la détection/suppression des contraintes uniques sur nom : {}",
                    e.getMessage());
        }
        for (String constraint : List.of("programs_nom_key", "uk_programs_nom", "programs_nom_unique")) {
            try {
                jdbcTemplate.execute("ALTER TABLE programs DROP CONSTRAINT IF EXISTS " + constraint);
            } catch (Exception e) {
                log.debug("[reset-reference-data] Contrainte {} non supprimée : {}", constraint, e.getMessage());
            }
        }
    }
}
