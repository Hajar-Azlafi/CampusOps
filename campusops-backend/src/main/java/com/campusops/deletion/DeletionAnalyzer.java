package com.campusops.deletion;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.building.entity.Building;
import com.campusops.department.entity.Department;
import com.campusops.equipment.entity.Equipment;
import com.campusops.exam.repository.ExamenRepository;
import com.campusops.floor.entity.Floor;
import com.campusops.floor.repository.FloorRepository;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.module.entity.Module;
import com.campusops.module.repository.ModuleRepository;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analyse <b>metier</b> d'une suppression : dit, pour une entite donnee, si elle
 * peut etre supprimee, ce qui serait supprime en cascade et ce qui l'en empeche.
 * C'est le cerveau de la refonte des suppressions — il ne modifie <b>rien</b>
 * (transactions en lecture seule) ; l'execution est confiee au
 * {@link DeletionExecutor} et aux services de domaine.
 *
 * <p><b>Trois familles de relations</b> (cahier de la refonte) :</p>
 * <ul>
 *   <li><b>Hierarchique supprimable</b> (departement, filiere, promotion,
 *       batiment, etage) : les enfants structurels sont ajoutes en
 *       {@code cascade}. La suppression reste bloquee si un usage metier existe
 *       quelque part dans le sous-arbre.</li>
 *   <li><b>Usage metier / historique</b> (seances, examens, occupations,
 *       reservations, emplois du temps) : ajoute en {@code blocking} — la
 *       suppression est refusee tant que ces elements existent.</li>
 *   <li><b>Association technique</b> (equipement <-> salle) : jamais bloquante,
 *       l'equipement est simplement detache des salles.</li>
 * </ul>
 *
 * <p><b>Exactitude.</b> Les relations {@code program}, {@code promotion},
 * {@code group} d'une {@code Schedule} et d'un {@code EmploiDuTemps} sont
 * NOT NULL : le comptage d'usage dans un sous-arbre est donc exact. Pour les
 * {@code Occupation} / {@code Reservation} (relations academiques nullable), le
 * comptage est au mieux ; le cas rare non detecte est rattrape par le verrou
 * transactionnel + le handler {@code DataIntegrityViolationException} (aucune
 * suppression partielle, aucune erreur SQL brute exposee).</p>
 *
 * <p>A appeler <b>dans une transaction</b> : traverse des relations LAZY.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeletionAnalyzer {

    private final ScheduleRepository scheduleRepository;
    private final ExamenRepository examenRepository;
    private final OccupationSupplementaireRepository occupationRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final ReservationRepository reservationRepository;
    private final ModuleRepository moduleRepository;
    private final SemesterRepository semesterRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SpaceRepository spaceRepository;
    private final FloorRepository floorRepository;

    /* ==================================================================
     *  STRUCTURE ACADEMIQUE (hierarchique + bloquant sur usage)
     * ================================================================== */

    public DeletionImpact analyze(Department department) {
        Long id = department.getId();
        DeletionImpact impact = new DeletionImpact("le département " + q(department.getNom()));
        impact.addCascade(programRepository.countByDepartmentId(id), "filière", "filières");
        impact.addCascade(promotionRepository.countByProgram_Department_Id(id), "promotion", "promotions");
        impact.addCascade(groupRepository.countByPromotion_Program_Department_Id(id), "groupe", "groupes");
        impact.addCascade(moduleRepository.countByProgram_Department_Id(id), "module", "modules");
        impact.addBlocking(scheduleRepository.countByProgram_Department_Id(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByProgram_Department_Id(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(occupationRepository.countByProgram_Department_Id(id), "occupation ou examen", "occupations ou examens");
        impact.addBlocking(reservationRepository.countByProgram_Department_Id(id), "réservation", "réservations");
        return impact;
    }

    public DeletionImpact analyze(Program program) {
        Long id = program.getId();
        DeletionImpact impact = new DeletionImpact("la filière " + q(program.getNom()));
        impact.addCascade(promotionRepository.countByProgramId(id), "promotion", "promotions");
        impact.addCascade(groupRepository.countByPromotion_Program_Id(id), "groupe", "groupes");
        impact.addCascade(moduleRepository.countByProgramId(id), "module", "modules");
        impact.addBlocking(scheduleRepository.countByProgramId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByProgramId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(occupationRepository.countByProgramId(id), "occupation ou examen", "occupations ou examens");
        impact.addBlocking(reservationRepository.countByProgramId(id), "réservation", "réservations");
        return impact;
    }

    public DeletionImpact analyze(Promotion promotion) {
        Long id = promotion.getId();
        DeletionImpact impact = new DeletionImpact("la promotion " + q(promotion.getNom()));
        impact.addCascade(groupRepository.countByPromotionId(id), "groupe", "groupes");
        impact.addBlocking(scheduleRepository.countByPromotionId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByPromotionId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(occupationRepository.countByPromotionId(id), "occupation ou examen", "occupations ou examens");
        return impact;
    }

    public DeletionImpact analyze(Group group) {
        Long id = group.getId();
        DeletionImpact impact = new DeletionImpact("le groupe " + q(group.getNom()));
        impact.addBlocking(scheduleRepository.countByGroupId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByGroupId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(occupationRepository.countByGroupId(id), "occupation ou examen", "occupations ou examens");
        impact.addBlocking(reservationRepository.countByGroupId(id), "réservation", "réservations");
        return impact;
    }

    public DeletionImpact analyze(Module module) {
        Long id = module.getId();
        DeletionImpact impact = new DeletionImpact("le module " + q(module.getNom()));
        impact.addBlocking(scheduleRepository.countByModuleId(id), "séance", "séances");
        impact.addBlocking(examenRepository.countByModuleId(id), "examen", "examens");
        return impact;
    }

    /* ==================================================================
     *  ESPACES PHYSIQUES (hierarchique + bloquant sur usage des salles)
     * ================================================================== */

    public DeletionImpact analyze(Building building) {
        Long id = building.getId();
        DeletionImpact impact = new DeletionImpact("le bâtiment " + q(building.getNom()));
        impact.addCascade(floorRepository.countByBuildingId(id), "étage", "étages");
        impact.addCascade(spaceRepository.countByFloorBuildingId(id), "salle", "salles");
        addSpaceUsage(impact, spaceRepository.findByFloorBuildingId(id));
        return impact;
    }

    public DeletionImpact analyze(Floor floor) {
        Long id = floor.getId();
        DeletionImpact impact = new DeletionImpact("l'étage " + q(floor.getNom()));
        impact.addCascade(spaceRepository.countByFloorId(id), "salle", "salles");
        addSpaceUsage(impact, spaceRepository.findByFloorId(id));
        return impact;
    }

    public DeletionImpact analyze(Space space) {
        Long id = space.getId();
        DeletionImpact impact = new DeletionImpact("la salle " + q(space.getNom()));
        impact.addBlocking(scheduleRepository.countBySpaceId(id), "séance", "séances");
        impact.addBlocking(reservationRepository.countBySpaceId(id), "réservation", "réservations");
        impact.addBlocking(occupationRepository.countBySpaceId(id), "occupation ou examen", "occupations ou examens");
        return impact;
    }

    public DeletionImpact analyze(Equipment equipment) {
        // Association technique : jamais bloquante. L'equipement est detache des
        // salles qui le referencent, puis supprime (cf. DeletionExecutor / service).
        DeletionImpact impact = new DeletionImpact("l'équipement " + q(equipment.getNom()));
        long salles = spaceRepository.findByEquipments_Id(equipment.getId()).size();
        impact.addCascade(salles, "salle (l'équipement en sera retiré)",
                                  "salles (l'équipement en sera retiré)");
        return impact;
    }

    /* ==================================================================
     *  REFERENTIELS (bloquant sur usage)
     * ================================================================== */

    public DeletionImpact analyze(TimeSlot timeSlot) {
        Long id = timeSlot.getId();
        DeletionImpact impact = new DeletionImpact("le créneau " + q(timeSlot.getNom()));
        impact.addBlocking(scheduleRepository.countByTimeSlotId(id), "séance", "séances");
        impact.addBlocking(examenRepository.countByTimeSlotId(id), "examen", "examens");
        return impact;
    }

    public DeletionImpact analyze(TypeSeance typeSeance) {
        DeletionImpact impact = new DeletionImpact("le type de séance " + q(typeSeance.getNom()));
        impact.addBlocking(scheduleRepository.countByTypeSeanceId(typeSeance.getId()), "séance", "séances");
        return impact;
    }

    public DeletionImpact analyze(Semester semester) {
        Long id = semester.getId();
        DeletionImpact impact = new DeletionImpact("le semestre " + q(semester.getNom()));
        impact.addBlocking(moduleRepository.countBySemesterId(id), "module", "modules");
        impact.addBlocking(scheduleRepository.countBySemesterId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countBySemesterId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(examenRepository.countBySemesterId(id), "examen", "examens");
        return impact;
    }

    public DeletionImpact analyze(SessionUniversitaire session) {
        Long id = session.getId();
        DeletionImpact impact = new DeletionImpact("la session " + q(session.getNom()));
        impact.addBlocking(emploiDuTempsRepository.countBySessionId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(examenRepository.countBySessionId(id), "examen", "examens");
        return impact;
    }

    public DeletionImpact analyze(AcademicYear year) {
        Long id = year.getId();
        DeletionImpact impact = new DeletionImpact("l'année universitaire " + q(year.getLibelle()));
        impact.addBlocking(promotionRepository.countByAcademicYearId(id), "promotion", "promotions");
        impact.addBlocking(scheduleRepository.countByAcademicYearId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByAcademicYearId(id), "emploi du temps", "emplois du temps");
        impact.addBlocking(occupationRepository.countByAcademicYearId(id), "occupation ou examen", "occupations ou examens");
        impact.addBlocking(reservationRepository.countByAcademicYearId(id), "réservation", "réservations");
        return impact;
    }

    public DeletionImpact analyze(Level level) {
        Long id = level.getId();
        DeletionImpact impact = new DeletionImpact("le niveau " + q(level.getNom()));
        impact.addBlocking(programRepository.countByLevelId(id), "filière", "filières");
        impact.addBlocking(promotionRepository.countByLevelId(id), "promotion", "promotions");
        impact.addBlocking(semesterRepository.countByLevelId(id), "semestre", "semestres");
        impact.addBlocking(scheduleRepository.countByLevelId(id), "séance", "séances");
        impact.addBlocking(emploiDuTempsRepository.countByLevelId(id), "emploi du temps", "emplois du temps");
        return impact;
    }

    public DeletionImpact analyze(User user) {
        Long id = user.getId();
        DeletionImpact impact = new DeletionImpact("le compte " + q(fullName(user)));
        impact.addBlocking(reservationRepository.countByUserId(id), "réservation", "réservations");
        impact.addBlocking(programRepository.findByResponsableId(id).size(),
                "filière dont ce compte est responsable", "filières dont ce compte est responsable");
        return impact;
    }

    /* ==================================================================
     *  OUTILS INTERNES
     * ================================================================== */

    /** Agrege l'usage metier des salles fournies (bloquant d'un batiment / etage). */
    private void addSpaceUsage(DeletionImpact impact, java.util.List<Space> spaces) {
        long seances = 0, reservations = 0, occupations = 0;
        for (Space s : spaces) {
            seances += scheduleRepository.countBySpaceId(s.getId());
            reservations += reservationRepository.countBySpaceId(s.getId());
            occupations += occupationRepository.countBySpaceId(s.getId());
        }
        impact.addBlocking(seances, "séance", "séances");
        impact.addBlocking(reservations, "réservation", "réservations");
        impact.addBlocking(occupations, "occupation ou examen", "occupations ou examens");
    }

    /** Libelle entre guillemets francais, « sans nom » si vide. */
    private static String q(String value) {
        return "« " + ((value == null || value.isBlank()) ? "sans nom" : value.trim()) + " »";
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? user.getEmail() : full;
    }
}
