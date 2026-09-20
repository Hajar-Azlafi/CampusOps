package com.campusops.schedule.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.audit.service.AuditService;
import com.campusops.department.entity.Department;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.SessionType;
import com.campusops.enums.WeekDay;
import com.campusops.exception.BadRequestException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.notification.service.NotificationService;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.schedule.dto.ScheduleImportHistoryDto;
import com.campusops.schedule.dto.ScheduleImportResultDto;
import com.campusops.schedule.dto.ScheduleImportRowErrorDto;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.entity.ScheduleImport;
import com.campusops.schedule.repository.ScheduleImportRepository;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.settings.service.ImportPolicyService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScheduleExcelImportService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleImportRepository scheduleImportRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SpaceRepository spaceRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicYearRepository academicYearRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AccessScopeService accessScope;
    private final ImportPolicyService importPolicy;

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Emplois du temps";

    private static final String[] HEADERS = {
            "Jour", "Heure début", "Heure fin", "Espace (code)", "Département",
            "Filière", "Niveau", "Ordre niveau", "Promotion", "Groupe",
            "Semestre", "Ordre semestre", "Année universitaire",
            "Enseignant", "Matiere", "Type", "Commentaire"
    };

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    @Transactional
    public ScheduleImportResultDto importSchedules(MultipartFile file) {
        // Reserve a l'ADMIN : l'import en masse resout les lignes par nom et peut
        // creer des departements, filieres et niveaux globaux. Un responsable
        // pedagogique n'a pas ce droit (il gere l'emploi du temps de SES filieres
        // via les endpoints unitaires, deja restreints a son perimetre).
        accessScope.requireAdmin();

        // Taille, extension ET signature binaire du fichier, selon les parametres
        // d'import (§9). Seul garde-fou applique a ce chemin historique : cet
        // import cree les referentiels manquants ligne par ligne, une passe de
        // validation prealable y serait impossible sans le reecrire (§24), aussi
        // le reglage « validation automatique » ne s'y applique pas — il vaut pour
        // les imports d'utilisateurs, d'emploi du temps, d'examens et
        // d'occupations, tous structures en deux temps.
        importPolicy.verifierFichier(file, "des séances");

        List<ScheduleImportRowErrorDto> errors = new ArrayList<>();
        Set<String> createdEntities = new LinkedHashSet<>();
        int totalRows = 0;
        int successCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();

                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || isRowEmpty(row)) {
                        continue;
                    }

                    totalRows++;
                    int displayRowNumber = rowIndex + 1;

                    try {
                        importRow(row, createdEntities);
                        successCount++;
                    } catch (IllegalArgumentException ex) {
                        errors.add(ScheduleImportRowErrorDto.builder()
                                .sheet(sheetName)
                                .row(displayRowNumber)
                                .message(ex.getMessage())
                                .build());
                    }
                }
            }

        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + e.getMessage());
        }

        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "import.xlsx";

        scheduleImportRepository.save(ScheduleImport.builder()
                .fileName(fileName)
                .totalRows(totalRows)
                .successCount(successCount)
                .errorCount(errors.size())
                .importedBy(currentUsername())
                .build());

        String description = String.format(
                "Import de l'emploi du temps depuis '%s' : %d ligne(s), %d succes, %d erreur(s)",
                fileName, totalRows, successCount, errors.size());
        auditService.record(AuditAction.EXCEL_IMPORT, MODULE, description);
        notificationService.notifyUser(currentUserOrNull(), NotificationType.SCHEDULE_IMPORTED,
                "Import d'emploi du temps termine", description, "/schedules/import");

        return ScheduleImportResultDto.builder()
                .fileName(fileName)
                .totalRows(totalRows)
                .successCount(successCount)
                .errorCount(errors.size())
                .createdEntities(new ArrayList<>(createdEntities))
                .errors(errors)
                .build();
    }

    private void importRow(Row row, Set<String> createdEntities) {
        String jourRaw = getCellValue(row, 0);
        String heureDebutRaw = getCellValue(row, 1);
        String heureFinRaw = getCellValue(row, 2);
        String spaceCode = getCellValue(row, 3);
        String departementNom = getCellValue(row, 4);
        String filiereNom = getCellValue(row, 5);
        String niveauNom = getCellValue(row, 6);
        String ordreNiveauRaw = getCellValue(row, 7);
        String promotionNom = getCellValue(row, 8);
        String groupeNom = getCellValue(row, 9);
        String semestreNom = getCellValue(row, 10);
        String ordreSemestreRaw = getCellValue(row, 11);
        String anneeLibelle = getCellValue(row, 12);
        String enseignant = getCellValue(row, 13);
        String matiere = getCellValue(row, 14);
        String typeRaw = getCellValue(row, 15);
        String commentaire = getCellValue(row, 16);

        WeekDay jour = parseJour(jourRaw);
        SessionType type = parseType(typeRaw);
        LocalTime heureDebut = parseTime(heureDebutRaw, "heure de debut");
        LocalTime heureFin = parseTime(heureFinRaw, "heure de fin");
        if (!heureFin.isAfter(heureDebut)) {
            throw new IllegalArgumentException(
                    "L'heure de fin doit etre posterieure a l'heure de debut");
        }

        requireNotBlank(spaceCode, "Le code de l'espace est obligatoire");
        requireNotBlank(departementNom, "Le departement est obligatoire");
        requireNotBlank(filiereNom, "La filiere est obligatoire");
        requireNotBlank(niveauNom, "Le niveau est obligatoire");
        requireNotBlank(promotionNom, "La promotion est obligatoire");
        requireNotBlank(groupeNom, "Le groupe est obligatoire");
        requireNotBlank(semestreNom, "Le semestre est obligatoire");
        requireNotBlank(anneeLibelle, "L'annee universitaire est obligatoire");
        requireNotBlank(enseignant, "L'enseignant est obligatoire");
        requireNotBlank(matiere, "La matiere est obligatoire");

        // L'espace doit exister (jamais cree automatiquement) ET etre utilisable :
        // une salle inactive, ou rattachee a un etage/bloc desactive, ne peut pas
        // recevoir de nouvelle seance (§5/§21). L'erreur nomme la cause exacte.
        Space space = spaceRepository.findAll().stream()
                .filter(s -> s.getCode().equalsIgnoreCase(spaceCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun espace trouvé avec le code '" + spaceCode + "'"));
        String motifSalle = ReferentialStatus.reason(space);
        if (motifSalle != null) {
            throw new IllegalArgumentException(motifSalle);
        }

        TimeSlot timeSlot = resolveTimeSlot(heureDebut, heureFin, createdEntities);
        AcademicYear academicYear = resolveAcademicYear(anneeLibelle, createdEntities);
        Department department = resolveDepartment(departementNom, createdEntities);
        Program program = resolveProgram(filiereNom, department, createdEntities);
        Level level = resolveLevel(niveauNom, ordreNiveauRaw, createdEntities);
        Semester semester = resolveSemester(semestreNom, ordreSemestreRaw, createdEntities);
        Promotion promotion = resolvePromotion(promotionNom, program, level, academicYear, createdEntities);
        Group group = resolveGroup(groupeNom, promotion, createdEntities);

        // Regles de conflit (salle, groupe, enseignant) sur le meme moment.
        if (scheduleRepository.existsSpaceConflict(academicYear.getId(), semester.getId(),
                jour, timeSlot.getHeureDebut(), timeSlot.getHeureFin(), space.getId())) {
            throw new IllegalArgumentException(
                    "Conflit de salle : l'espace '" + space.getCode() + "' est déjà occupé sur ce créneau");
        }
        if (scheduleRepository.existsGroupConflict(academicYear.getId(), semester.getId(),
                jour, timeSlot.getHeureDebut(), timeSlot.getHeureFin(), group.getId())) {
            throw new IllegalArgumentException(
                    "Conflit de groupe : le groupe '" + group.getNom() + "' a déjà une séance sur ce créneau");
        }
        if (scheduleRepository.existsTeacherConflict(academicYear.getId(), semester.getId(),
                jour, timeSlot.getHeureDebut(), timeSlot.getHeureFin(), enseignant)) {
            throw new IllegalArgumentException(
                    "Conflit d'enseignant : '" + enseignant + "' a déjà une séance sur ce créneau");
        }

        Schedule schedule = Schedule.builder()
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
                .commentaire(commentaire.isBlank() ? null : commentaire)
                .actif(true)
                .build();

        scheduleRepository.save(schedule);
    }

    // ----- Resolution / auto-creation des entites -----

    private TimeSlot resolveTimeSlot(LocalTime debut, LocalTime fin, Set<String> created) {
        Optional<TimeSlot> existing = timeSlotRepository.findByHeureDebutAndHeureFin(debut, fin);
        if (existing.isPresent()) {
            TimeSlot creneau = existing.get();
            // Le creneau existe deja dans la grille mais a pu etre desactive : on ne
            // rattache jamais une nouvelle seance a un creneau inactif, et on n'en
            // recree pas de doublon actif (les horaires sont uniques). L'import
            // signale explicitement la cause exacte, comme pour la salle (§20/§21/§26).
            String motif = ReferentialStatus.reason(creneau);
            if (motif != null) {
                throw new IllegalArgumentException(motif);
            }
            return creneau;
        }
        TimeSlot timeSlot = timeSlotRepository.save(TimeSlot.builder()
                .heureDebut(debut).heureFin(fin).actif(true).build());
        created.add("Creneau horaire cree : " + debut + " - " + fin);
        return timeSlot;
    }

    private AcademicYear resolveAcademicYear(String libelle, Set<String> created) {
        Optional<AcademicYear> existing = academicYearRepository.findByLibelle(libelle.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        AcademicYear year = academicYearRepository.save(AcademicYear.builder()
                .libelle(libelle.trim()).actif(true).build());
        created.add("Année universitaire créée : " + libelle.trim());
        return year;
    }

    private Department resolveDepartment(String nom, Set<String> created) {
        Optional<Department> existing = departmentRepository.findAll().stream()
                .filter(d -> d.getNom().equalsIgnoreCase(nom.trim()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Department department = departmentRepository.save(Department.builder()
                .nom(nom.trim())
                .code(uniqueCode(nom, code -> departmentRepository.existsByCode(code)))
                .actif(true).build());
        created.add("Département créé : " + nom.trim());
        return department;
    }

    private Program resolveProgram(String nom, Department department, Set<String> created) {
        Optional<Program> existing = programRepository.findByNom(nom.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        Program program = programRepository.save(Program.builder()
                .nom(nom.trim())
                .code(uniqueCode(nom, code -> programRepository.existsByCode(code)))
                .department(department)
                .build());
        created.add("Filière créée : " + nom.trim());
        return program;
    }

    private Level resolveLevel(String nom, String ordreRaw, Set<String> created) {
        Optional<Level> existing = levelRepository.findAll().stream()
                .filter(l -> l.getNom().equalsIgnoreCase(nom.trim()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        int ordre = parseOptionalInt(ordreRaw,
                (int) (levelRepository.count() + 1));
        Level level = levelRepository.save(Level.builder()
                .nom(nom.trim()).ordre(ordre).actif(true).build());
        created.add("Niveau cree : " + nom.trim());
        return level;
    }

    private Semester resolveSemester(String nom, String ordreRaw, Set<String> created) {
        Optional<Semester> existing = semesterRepository.findAll().stream()
                .filter(s -> s.getNom().equalsIgnoreCase(nom.trim()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        int ordre = parseOptionalInt(ordreRaw,
                (int) (semesterRepository.count() + 1));
        Semester semester = semesterRepository.save(Semester.builder()
                .nom(nom.trim()).ordre(ordre).actif(true).build());
        created.add("Semestre cree : " + nom.trim());
        return semester;
    }

    private Promotion resolvePromotion(String nom, Program program, Level level,
                                       AcademicYear academicYear, Set<String> created) {
        Optional<Promotion> existing = promotionRepository.findAll().stream()
                .filter(p -> p.getProgram().getId().equals(program.getId())
                        && p.getLevel().getId().equals(level.getId())
                        && p.getAcademicYear().getId().equals(academicYear.getId()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Promotion promotion = promotionRepository.save(Promotion.builder()
                .nom(nom.trim())
                .program(program).level(level).academicYear(academicYear)
                .actif(true).build());
        created.add("Promotion créée : " + nom.trim());
        return promotion;
    }

    private Group resolveGroup(String nom, Promotion promotion, Set<String> created) {
        Optional<Group> existing = groupRepository.findByPromotionIdAndNom(promotion.getId(), nom.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        Group group = groupRepository.save(Group.builder()
                .nom(nom.trim()).promotion(promotion).actif(true).build());
        created.add("Groupe cree : " + nom.trim());
        return group;
    }

    // ----- Historique -----

    @Transactional(readOnly = true)
    public List<ScheduleImportHistoryDto> getImportHistory() {
        // Historique global des imports : reserve a l'ADMIN.
        accessScope.requireAdmin();
        return scheduleImportRepository.findAllByOrderByImportedAtDesc().stream()
                .map(this::toHistoryDto)
                .toList();
    }

    private ScheduleImportHistoryDto toHistoryDto(ScheduleImport entity) {
        return ScheduleImportHistoryDto.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .totalRows(entity.getTotalRows())
                .successCount(entity.getSuccessCount())
                .errorCount(entity.getErrorCount())
                .importedBy(entity.getImportedBy())
                .importedAt(entity.getImportedAt())
                .build();
    }

    // ----- Modele Excel -----

    public byte[] generateTemplate() {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Emploi du temps");
            Row headerRow = sheet.createRow(0);

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            Row example = sheet.createRow(1);
            String[] values = {
                    "Lundi", "08:30", "10:30", "A-101", "Informatique",
                    "Genie Informatique", "1ere annee", "1", "GI-1", "Groupe A",
                    "Semestre 1", "1", "2025-2026", "Prof. Alaoui",
                    "Algorithmique", "COURS", "Salle principale"
            };
            for (int i = 0; i < values.length; i++) {
                example.createCell(i).setCellValue(values[i]);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la generation du modele Excel");
        }
    }

    // ----- Utilitaires -----

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private WeekDay parseJour(String raw) {
        requireNotBlank(raw, "Le jour est obligatoire");
        String normalized = stripAccents(raw.trim()).toUpperCase();
        return switch (normalized) {
            case "LUNDI", "MONDAY" -> WeekDay.LUNDI;
            case "MARDI", "TUESDAY" -> WeekDay.MARDI;
            case "MERCREDI", "WEDNESDAY" -> WeekDay.MERCREDI;
            case "JEUDI", "THURSDAY" -> WeekDay.JEUDI;
            case "VENDREDI", "FRIDAY" -> WeekDay.VENDREDI;
            case "SAMEDI", "SATURDAY" -> WeekDay.SAMEDI;
            case "DIMANCHE", "SUNDAY" -> WeekDay.DIMANCHE;
            default -> throw new IllegalArgumentException("Le jour '" + raw + "' n'est pas valide");
        };
    }

    private SessionType parseType(String raw) {
        requireNotBlank(raw, "Le type de seance est obligatoire");
        String normalized = stripAccents(raw.trim()).toUpperCase();
        try {
            return SessionType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Le type de seance '" + raw + "' n'est pas valide");
        }
    }

    private LocalTime parseTime(String raw, String label) {
        requireNotBlank(raw, "L'" + label + " est obligatoire");
        String value = raw.trim().replace('h', ':').replace('H', ':');
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e1) {
            try {
                return LocalTime.parse(value);
            } catch (Exception e2) {
                throw new IllegalArgumentException(
                        "L'" + label + " '" + raw + "' n'est pas valide (format attendu HH:mm)");
            }
        }
    }

    private int parseOptionalInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String uniqueCode(String source, java.util.function.Predicate<String> exists) {
        String base = stripAccents(source.trim()).toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (base.isBlank()) {
            base = "COD";
        }
        if (base.length() > 12) {
            base = base.substring(0, 12);
        }
        String candidate = base;
        int counter = 1;
        while (exists.test(candidate)) {
            candidate = base + counter;
            counter++;
        }
        return candidate;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }

    /** Utilisateur courant, ou null hors contexte authentifie (pour notification). */
    private User currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByEmail(authentication.getName()).orElse(null);
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (!getCellValue(row, i).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numeric = cell.getNumericCellValue();
                if (numeric == Math.floor(numeric)) {
                    yield String.valueOf((long) numeric);
                }
                yield String.valueOf(numeric);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
}
