package com.campusops.config;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.department.entity.Department;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.enums.SessionType;
import com.campusops.enums.TypeFormation;
import com.campusops.enums.WeekDay;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Initialise les donnees academiques de demonstration au premier demarrage :
 * departements, filieres, niveaux, semestres, annees universitaires, creneaux
 * horaires, promotions, groupes et quelques seances d'emploi du temps.
 * L'insertion n'est effectuee que si aucune donnee academique n'existe encore
 * (idempotent). Aucune donnee sensible n'est codee en dur : ces jeux de donnees
 * fictifs restent entierement administrables par l'administrateur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(6)
public class AcademicInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicYearRepository academicYearRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final ScheduleRepository scheduleRepository;
    private final SpaceRepository spaceRepository;

    @Override
    public void run(String... args) {
        if (departmentRepository.count() > 0
                || programRepository.count() > 0
                || levelRepository.count() > 0) {
            log.info("Des donnees academiques existent deja, initialisation ignoree.");
            return;
        }

        // --- Departements ---
        Department info = Department.builder().nom("Informatique").code("INFO")
                .description("Departement d'informatique et genie logiciel").actif(true).build();
        Department math = Department.builder().nom("Mathematiques").code("MATH")
                .description("Departement de mathematiques et statistiques").actif(true).build();
        Department phys = Department.builder().nom("Physique").code("PHYS")
                .description("Departement de physique et sciences des materiaux").actif(true).build();
        Department gc = Department.builder().nom("Genie Civil").code("GC")
                .description("Departement de genie civil et construction").actif(true).build();
        departmentRepository.saveAll(List.of(info, math, phys, gc));

        // --- Niveaux / Cycles ---
        // Structure : Type de formation -> Niveau/Cycle -> Filiere.
        Level troncCommun = Level.builder().nom("Tronc commun").ordre(1)
                .typeFormation(TypeFormation.INITIALE).actif(true).build();
        Level licence = Level.builder().nom("Licence").ordre(2)
                .typeFormation(TypeFormation.INITIALE).actif(true).build();
        Level master = Level.builder().nom("Master").ordre(3)
                .typeFormation(TypeFormation.INITIALE).actif(true).build();
        Level cycleIngenieur = Level.builder().nom("Cycle ingénieur").ordre(4)
                .typeFormation(TypeFormation.INITIALE).actif(true).build();
        Level doctorat = Level.builder().nom("Doctorat").ordre(5)
                .typeFormation(TypeFormation.INITIALE).actif(true).build();
        Level formationContinue = Level.builder().nom("Formation continue").ordre(6)
                .typeFormation(TypeFormation.CONTINUE).actif(true).build();
        levelRepository.saveAll(List.of(
                troncCommun, licence, master, cycleIngenieur, doctorat, formationContinue));

        // --- Filieres (rattachees a un departement et a un niveau/cycle) ---
        Program gi = Program.builder().nom("Génie Informatique").code("GI")
                .description("Formation en génie logiciel et systèmes").department(info).level(cycleIngenieur).build();
        Program isi = Program.builder().nom("Ingénierie des Systèmes d'Information").code("ISI")
                .description("Formation en systèmes d'information et données").department(info).level(master).build();
        Program ma = Program.builder().nom("Mathématiques Appliquées").code("MA")
                .description("Formation en mathématiques appliquées").department(math).level(licence).build();
        Program pf = Program.builder().nom("Physique Fondamentale").code("PF")
                .description("Formation en physique fondamentale").department(phys).level(licence).build();
        Program gcv = Program.builder().nom("Génie Civil et Bâtiment").code("GCB")
                .description("Formation en génie civil et bâtiment").department(gc).level(cycleIngenieur).build();
        programRepository.saveAll(List.of(gi, isi, ma, pf, gcv));

        // --- Semestres ---
        List<Semester> semesters = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            semesters.add(Semester.builder().nom("Semestre " + i).ordre(i).actif(true).build());
        }
        semesterRepository.saveAll(semesters);
        Semester s1 = semesters.get(0);
        Semester s2 = semesters.get(1);

        // --- Annees universitaires ---
        AcademicYear year2526 = AcademicYear.builder().libelle("2025-2026")
                .dateDebut(LocalDate.of(2025, 9, 1)).dateFin(LocalDate.of(2026, 6, 30)).actif(false).build();
        AcademicYear year2627 = AcademicYear.builder().libelle("2026-2027")
                .dateDebut(LocalDate.of(2026, 9, 1)).dateFin(LocalDate.of(2027, 6, 30)).actif(true).build();
        academicYearRepository.saveAll(List.of(year2526, year2627));

        // --- Creneaux horaires ---
        // Les creneaux de reference (§1.1) sont amorces par TimeSlotInitializer
        // (@Order 5), execute AVANT cet initialiseur. On les reutilise ici pour
        // les seances de demonstration, sans recreer de creneaux « hors grille ».
        List<TimeSlot> refSlots = timeSlotRepository.findAllByOrderByOrdreAscHeureDebutAsc();
        TimeSlot t1 = refSlots.size() > 0 ? refSlots.get(0) : null;
        TimeSlot t2 = refSlots.size() > 1 ? refSlots.get(1) : null;
        TimeSlot t3 = refSlots.size() > 2 ? refSlots.get(2) : null;
        TimeSlot t4 = refSlots.size() > 3 ? refSlots.get(3) : null;

        // --- Promotions (filiere + niveau/cycle + annee) ---
        Promotion promoGiIng = Promotion.builder().nom("Cycle ingénieur GI").program(gi).level(cycleIngenieur).academicYear(year2627).actif(true).build();
        Promotion promoIsiMas = Promotion.builder().nom("Master ISI").program(isi).level(master).academicYear(year2627).actif(true).build();
        Promotion promoMaLic = Promotion.builder().nom("Licence MA").program(ma).level(licence).academicYear(year2627).actif(true).build();
        Promotion promoPfLic = Promotion.builder().nom("Licence PF").program(pf).level(licence).academicYear(year2627).actif(true).build();
        Promotion promoGcbIng = Promotion.builder().nom("Cycle ingénieur GCB").program(gcv).level(cycleIngenieur).academicYear(year2627).actif(true).build();
        List<Promotion> promotions = List.of(
                promoGiIng, promoIsiMas, promoMaLic, promoPfLic, promoGcbIng);
        promotionRepository.saveAll(promotions);

        // --- Groupes (2 par promotion) ---
        List<Group> groups = new ArrayList<>();
        for (Promotion promotion : promotions) {
            groups.add(Group.builder().nom("Groupe A").promotion(promotion).actif(true).build());
            groups.add(Group.builder().nom("Groupe B").promotion(promotion).actif(true).build());
        }
        groupRepository.saveAll(groups);
        Group giGroupeA = groups.get(0);
        Group giGroupeB = groups.get(1);

        // --- Emplois du temps de demonstration ---
        List<Space> spaces = spaceRepository.findAll();
        if (spaces.isEmpty() || t1 == null || t2 == null || t3 == null || t4 == null) {
            log.info("Espaces ou creneaux de reference insuffisants : "
                    + "les seances de demonstration ne sont pas creees.");
        } else {
            List<Schedule> schedules = new ArrayList<>();
            Space sp1 = spaces.get(0);
            Space sp2 = spaces.get(Math.min(1, spaces.size() - 1));
            Space sp3 = spaces.get(Math.min(2, spaces.size() - 1));

            schedules.add(buildSchedule(WeekDay.LUNDI, t1, sp1, gi, cycleIngenieur, promoGiIng, giGroupeA, s1, year2627,
                    "Prof. Alami", "Algorithmique", SessionType.COURS));
            schedules.add(buildSchedule(WeekDay.LUNDI, t2, sp1, gi, cycleIngenieur, promoGiIng, giGroupeA, s1, year2627,
                    "Prof. Bennani", "Programmation Java", SessionType.TD));
            schedules.add(buildSchedule(WeekDay.MARDI, t1, sp2, gi, cycleIngenieur, promoGiIng, giGroupeB, s1, year2627,
                    "Prof. Chraibi", "Bases de données", SessionType.COURS));
            schedules.add(buildSchedule(WeekDay.MERCREDI, t3, sp3, gi, cycleIngenieur, promoGiIng, giGroupeA, s1, year2627,
                    "Prof. Alami", "Systèmes d'exploitation", SessionType.TP));
            schedules.add(buildSchedule(WeekDay.JEUDI, t2, sp2, gi, cycleIngenieur, promoGiIng, giGroupeB, s2, year2627,
                    "Prof. Daoudi", "Réseaux", SessionType.COURS));
            schedules.add(buildSchedule(WeekDay.VENDREDI, t4, sp3, gi, cycleIngenieur, promoGiIng, giGroupeA, s2, year2627,
                    "Prof. Bennani", "Génie logiciel", SessionType.TD));

            scheduleRepository.saveAll(schedules);
            log.info("{} seances d'emploi du temps de demonstration creees", schedules.size());
        }

        log.info("========================================");
        log.info("Donnees academiques de demonstration creees avec succes :");
        log.info("  - {} departements", departmentRepository.count());
        log.info("  - {} filieres", programRepository.count());
        log.info("  - {} niveaux", levelRepository.count());
        log.info("  - {} semestres", semesterRepository.count());
        log.info("  - {} annees universitaires", academicYearRepository.count());
        log.info("  - {} creneaux horaires", timeSlotRepository.count());
        log.info("  - {} promotions", promotionRepository.count());
        log.info("  - {} groupes", groupRepository.count());
        log.info("========================================");
    }

    private Schedule buildSchedule(WeekDay jour, TimeSlot timeSlot, Space space, Program program,
                                   Level level, Promotion promotion, Group group, Semester semester,
                                   AcademicYear academicYear, String enseignant, String matiere,
                                   SessionType type) {
        return Schedule.builder()
                .jour(jour)
                .timeSlot(timeSlot)
                .space(space)
                .program(program)
                .level(level)
                .promotion(promotion)
                .group(group)
                .semester(semester)
                .academicYear(academicYear)
                .enseignant(enseignant)
                .matiere(matiere)
                .type(type)
                .actif(true)
                .build();
    }
}
