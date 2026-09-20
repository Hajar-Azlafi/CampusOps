package com.campusops.validation;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.building.entity.Building;
import com.campusops.department.entity.Department;
import com.campusops.equipment.entity.Equipment;
import com.campusops.exception.BadRequestException;
import com.campusops.floor.entity.Floor;
import com.campusops.group.entity.Group;
import com.campusops.module.entity.Module;
import com.campusops.program.entity.Program;
import com.campusops.promotion.entity.Promotion;
import com.campusops.semester.entity.Semester;
import com.campusops.space.entity.Space;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.user.entity.User;

/**
 * Socle central de la regle metier <b>ACTIF / INACTIF</b> (desactivation
 * logique, ou <i>soft disable</i>).
 *
 * <p><b>Contrat.</b> Un element <b>ACTIF</b> peut etre utilise dans les
 * nouvelles operations. Un element <b>INACTIF</b> reste en base et reste
 * consultable dans l'historique, mais ne doit plus etre propose ni accepte
 * dans une nouvelle operation. Desactiver ne supprime jamais les donnees
 * anciennes.
 *
 * <p><b>Hierarchie.</b> Un element est <i>utilisable</i> seulement si lui-meme
 * et tous ses parents structurants le sont :
 * <pre>
 *   Batiment  -> Etage    -> Salle
 *   Departement -> Filiere -> Promotion -> Groupe
 *   Filiere + Semestre     -> Module
 *   Annee universitaire    -> Promotion -> Groupe
 * </pre>
 * La desactivation propage le drapeau vers le bas (voir les services
 * {@code deactivateXxx}). Ces predicats constituent la seconde barriere :
 * ils protegent contre toute derive de donnees et contre les appels d'API
 * directs qui contourneraient le frontend.
 *
 * <p><b>Hors perimetre volontairement.</b> Le niveau/cycle ({@code Level}) est
 * un referentiel taxonomique stable : son drapeau n'est pas une regle metier
 * operationnelle et n'entre donc dans aucun predicat. Les drapeaux
 * {@code actif} de {@code Reservation}, {@code Schedule}, {@code Examen} et
 * {@code OccupationSupplementaire} ont une autre semantique (suppression
 * logique d'un enregistrement transactionnel) et ne sont pas traites ici.
 *
 * <p><b>Utilisation.</b> Classe utilitaire sans etat, a appeler <b>dans une
 * transaction</b> : elle traverse des relations {@code LAZY}.
 */
public final class ReferentialStatus {

    private ReferentialStatus() {
        // Classe utilitaire.
    }

    /* ==================================================================
     *  ESPACES PHYSIQUES : Batiment -> Etage -> Salle
     * ================================================================== */

    public static boolean usable(Building building) {
        return building != null && building.isActif();
    }

    public static boolean usable(Floor floor) {
        return floor != null && floor.isActif() && usable(floor.getBuilding());
    }

    public static boolean usable(Space space) {
        return space != null && space.isActif() && usable(space.getFloor());
    }

    public static boolean usable(Equipment equipment) {
        return equipment != null && equipment.isActif();
    }

    /* ==================================================================
     *  STRUCTURE ACADEMIQUE : Departement -> Filiere -> Promotion -> Groupe
     * ================================================================== */

    public static boolean usable(Department department) {
        return department != null && department.isActif();
    }

    public static boolean usable(Program program) {
        return program != null && program.isActif() && usable(program.getDepartment());
    }

    public static boolean usable(AcademicYear academicYear) {
        return academicYear != null && academicYear.isActif();
    }

    public static boolean usable(Promotion promotion) {
        return promotion != null
                && promotion.isActif()
                && usable(promotion.getProgram())
                && usable(promotion.getAcademicYear());
    }

    public static boolean usable(Group group) {
        return group != null && group.isActif() && usable(group.getPromotion());
    }

    public static boolean usable(Semester semester) {
        return semester != null && semester.isActif();
    }

    public static boolean usable(SessionUniversitaire session) {
        return session != null && session.isActif();
    }

    public static boolean usable(Module module) {
        return module != null
                && module.isActif()
                && usable(module.getProgram())
                && usable(module.getSemester());
    }

    /* ==================================================================
     *  REFERENTIELS D'ORGANISATION
     * ================================================================== */

    public static boolean usable(TypeSeance typeSeance) {
        return typeSeance != null && typeSeance.isActif();
    }

    public static boolean usable(TimeSlot timeSlot) {
        return timeSlot != null && timeSlot.isActif();
    }

    public static boolean usable(User user) {
        return user != null && user.isActive();
    }

    /* ==================================================================
     *  MOTIFS : message metier francais nommant la cause EXACTE
     *  (l'element lui-meme, ou celui de ses parents qui est desactive).
     *  Retourne {@code null} lorsque l'element est utilisable.
     * ================================================================== */

    public static String reason(Building building) {
        if (building == null) {
            return null;
        }
        return building.isActif() ? null
                : "Le bâtiment « " + label(building.getNom()) + " » est désactivé.";
    }

    public static String reason(Floor floor) {
        if (floor == null) {
            return null;
        }
        Building building = floor.getBuilding();
        if (!usable(building)) {
            return "L'étage « " + label(floor.getNom()) + " » appartient au bâtiment « "
                    + label(building == null ? null : building.getNom()) + " » qui est désactivé.";
        }
        return floor.isActif() ? null
                : "L'étage « " + label(floor.getNom()) + " » est désactivé.";
    }

    public static String reason(Space space) {
        if (space == null) {
            return null;
        }
        Floor floor = space.getFloor();
        Building building = floor == null ? null : floor.getBuilding();
        if (!usable(building)) {
            return "La salle « " + label(space.getNom()) + " » appartient au bâtiment « "
                    + label(building == null ? null : building.getNom()) + " » qui est désactivé.";
        }
        if (!floor.isActif()) {
            return "La salle « " + label(space.getNom()) + " » appartient à l'étage « "
                    + label(floor.getNom()) + " » qui est désactivé.";
        }
        return space.isActif() ? null
                : "La salle « " + label(space.getNom()) + " » est désactivée.";
    }

    public static String reason(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return equipment.isActif() ? null
                : "L'équipement « " + label(equipment.getNom()) + " » est désactivé.";
    }

    public static String reason(Department department) {
        if (department == null) {
            return null;
        }
        return department.isActif() ? null
                : "Le département « " + label(department.getNom()) + " » est désactivé.";
    }

    public static String reason(Program program) {
        if (program == null) {
            return null;
        }
        Department department = program.getDepartment();
        if (!usable(department)) {
            return "La filière « " + label(program.getNom()) + " » appartient au département « "
                    + label(department == null ? null : department.getNom())
                    + " » qui est désactivé.";
        }
        return program.isActif() ? null
                : "La filière « " + label(program.getNom()) + " » est désactivée.";
    }

    public static String reason(AcademicYear academicYear) {
        if (academicYear == null) {
            return null;
        }
        return academicYear.isActif() ? null
                : "L'année universitaire « " + label(academicYear.getLibelle()) + " » est inactive.";
    }

    public static String reason(Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        String own = "La promotion « " + label(promotion.getNom()) + " »";
        String parent = reason(promotion.getProgram());
        if (parent != null) {
            return own + " n'est pas utilisable : " + lowerFirst(parent);
        }
        String year = reason(promotion.getAcademicYear());
        if (year != null) {
            return own + " n'est pas utilisable : " + lowerFirst(year);
        }
        return promotion.isActif() ? null : own + " est désactivée.";
    }

    public static String reason(Group group) {
        if (group == null) {
            return null;
        }
        String own = "Le groupe « " + label(group.getNom()) + " »";
        String parent = reason(group.getPromotion());
        if (parent != null) {
            return own + " n'est pas utilisable : " + lowerFirst(parent);
        }
        return group.isActif() ? null : own + " est désactivé.";
    }

    public static String reason(Semester semester) {
        if (semester == null) {
            return null;
        }
        return semester.isActif() ? null
                : "Le semestre « " + label(semester.getNom()) + " » est désactivé.";
    }

    public static String reason(SessionUniversitaire session) {
        if (session == null) {
            return null;
        }
        return session.isActif() ? null
                : "La session « " + label(session.getNom()) + " » est désactivée.";
    }

    public static String reason(Module module) {
        if (module == null) {
            return null;
        }
        String own = "Le module « " + label(module.getNom()) + " »";
        String parent = reason(module.getProgram());
        if (parent != null) {
            return own + " n'est pas utilisable : " + lowerFirst(parent);
        }
        String semester = reason(module.getSemester());
        if (semester != null) {
            return own + " n'est pas utilisable : " + lowerFirst(semester);
        }
        return module.isActif() ? null : own + " est désactivé.";
    }

    public static String reason(TypeSeance typeSeance) {
        if (typeSeance == null) {
            return null;
        }
        return typeSeance.isActif() ? null
                : "Le type de séance « " + label(typeSeance.getNom()) + " » est désactivé.";
    }

    public static String reason(TimeSlot timeSlot) {
        if (timeSlot == null) {
            return null;
        }
        return timeSlot.isActif() ? null
                : "Le créneau « " + label(timeSlot.getNom()) + " » est désactivé.";
    }

    public static String reason(User user) {
        if (user == null) {
            return null;
        }
        return user.isActive() ? null
                : "Le compte « " + label(fullName(user)) + " » est désactivé.";
    }

    /* ==================================================================
     *  GARDES : levent BadRequestException (-> HTTP 400) si inutilisable.
     *  A appeler dans tous les chemins d'ECRITURE (creation, modification,
     *  validation, import) apres avoir resolu l'entite par son id.
     * ================================================================== */

    public static void requireUsable(Building building) {
        require(reason(building));
    }

    public static void requireUsable(Floor floor) {
        require(reason(floor));
    }

    public static void requireUsable(Space space) {
        require(reason(space));
    }

    public static void requireUsable(Equipment equipment) {
        require(reason(equipment));
    }

    public static void requireUsable(Department department) {
        require(reason(department));
    }

    public static void requireUsable(Program program) {
        require(reason(program));
    }

    public static void requireUsable(AcademicYear academicYear) {
        require(reason(academicYear));
    }

    public static void requireUsable(Promotion promotion) {
        require(reason(promotion));
    }

    public static void requireUsable(Group group) {
        require(reason(group));
    }

    public static void requireUsable(Semester semester) {
        require(reason(semester));
    }

    public static void requireUsable(SessionUniversitaire session) {
        require(reason(session));
    }

    public static void requireUsable(Module module) {
        require(reason(module));
    }

    public static void requireUsable(TypeSeance typeSeance) {
        require(reason(typeSeance));
    }

    public static void requireUsable(TimeSlot timeSlot) {
        require(reason(timeSlot));
    }

    public static void requireUsable(User user) {
        require(reason(user));
    }

    /* ==================================================================
     *  OUTILS INTERNES
     * ================================================================== */

    private static void require(String reason) {
        if (reason != null) {
            throw new BadRequestException(reason);
        }
    }

    /** Libelle sur : evite « null » dans les messages destines a l'utilisateur. */
    private static String label(String value) {
        return (value == null || value.isBlank()) ? "sans nom" : value.trim();
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? user.getEmail() : full;
    }

    /**
     * Met la premiere lettre en minuscule pour enchainer une cause apres deux
     * points (« ... n'est pas utilisable : la filiere ... est desactivee »).
     */
    private static String lowerFirst(String phrase) {
        if (phrase == null || phrase.isEmpty()) {
            return phrase;
        }
        return Character.toLowerCase(phrase.charAt(0)) + phrase.substring(1);
    }
}
