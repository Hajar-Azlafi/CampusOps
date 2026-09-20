package com.campusops.user.service;

import com.campusops.enums.Role;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.exception.BadRequestException;
import com.campusops.mail.EmailService;
import com.campusops.security.AccessScopeService;
import com.campusops.settings.service.ImportPolicyService;
import com.campusops.user.dto.CreatedAccountDto;
import com.campusops.user.dto.ImportResultDto;
import com.campusops.user.dto.ImportRowErrorDto;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordService passwordService;
    private final EmailService emailService;
    private final AccessScopeService accessScope;
    private final ImportPolicyService importPolicy;

    private static final String[] HEADERS = {
            "Prénom", "Nom", "Email", "Role", "Département", "Téléphone"
    };

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Import des utilisateurs en <b>deux passes</b> (§9).
     *
     * <p>La premiere passe lit et valide toutes les lignes sans rien enregistrer,
     * la seconde enregistre. Ce decoupage est ce qui rend le parametre « validation
     * automatique » applicable : quand il est actif, une seule ligne invalide
     * suffit a refuser le fichier <b>sans avoir cree le moindre compte</b> ni
     * envoye le moindre e-mail, tout en renvoyant le rapport ligne par ligne pour
     * que l'administrateur corrige d'un coup. Desactive, l'import reste tolerant :
     * les lignes valides sont creees, les autres rapportees.</p>
     */
    @Transactional
    public ImportResultDto importUsers(MultipartFile file) {

        accessScope.requireAdmin();
        // Taille, extension ET signature binaire, selon les parametres (§9).
        importPolicy.verifierFichier(file, "des utilisateurs");

        boolean ecrasementAutorise = importPolicy.isEcrasementAutorise();
        List<PreparedRow> preparees = new ArrayList<>();
        List<ImportRowErrorDto> errors = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        Set<String> activeDepartments = departmentRepository.findByActif(true).stream()
            .map(department -> department.getNom().trim().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        int totalRows = 0;

        // ----- Passe 1 : lecture et validation, aucun enregistrement -----
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRowNumber = rowIndex + 1;

                try {
                    PreparedRow prepared = new PreparedRow();
                    prepared.firstName = getCellValue(row, 0);
                    prepared.lastName = getCellValue(row, 1);
                    prepared.email = getCellValue(row, 2);
                    String roleRaw = getCellValue(row, 3);
                    prepared.department = getCellValue(row, 4);
                    prepared.phoneNumber = getCellValue(row, 5);

                    prepared.existant = validateRow(prepared.firstName, prepared.lastName,
                            prepared.email, roleRaw, prepared.department, activeDepartments,
                            emailsInFile, ecrasementAutorise);
                    prepared.role = Role.valueOf(roleRaw.trim().toUpperCase());
                    prepared.email = prepared.email.trim().toLowerCase();

                    emailsInFile.add(prepared.email);
                    preparees.add(prepared);

                } catch (IllegalArgumentException ex) {
                    errors.add(ImportRowErrorDto.builder()
                            .row(displayRowNumber)
                            .message(ex.getMessage())
                            .build());
                }
            }

        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + e.getMessage());
        }

        // ----- Validation automatique : refus en bloc, rapport conserve -----
        if (!errors.isEmpty() && importPolicy.isValidationStricte()) {
            return ImportResultDto.builder()
                    .totalRows(totalRows)
                    .successCount(0)
                    .updatedCount(0)
                    .errorCount(errors.size())
                    .createdAccounts(List.of())
                    .errors(errors)
                    .message(importPolicy.motifValidationStricte(errors.size(), "compte"))
                    .build();
        }

        // ----- Passe 2 : enregistrement des lignes retenues -----
        List<CreatedAccountDto> createdAccounts = new ArrayList<>();
        int updated = 0;
        for (PreparedRow prepared : preparees) {
            if (prepared.existant != null) {
                mettreAJour(prepared);
                updated++;
            } else {
                createdAccounts.add(creer(prepared));
            }
        }

        return ImportResultDto.builder()
                .totalRows(totalRows)
                .successCount(createdAccounts.size())
                .updatedCount(updated)
                .errorCount(errors.size())
                .createdAccounts(createdAccounts)
                .errors(errors)
                .message(messageSynthese(createdAccounts.size(), updated, errors.size()))
                .build();
    }

    /** Ligne validee en passe 1, prete a etre enregistree en passe 2. */
    private static final class PreparedRow {
        private String firstName;
        private String lastName;
        private String email;
        private Role role;
        private String department;
        private String phoneNumber;
        /** Compte deja existant a mettre a jour, {@code null} pour une creation. */
        private User existant;
    }

    /**
     * Creation d'un compte : mot de passe temporaire genere, jamais stocke en
     * clair, communique uniquement par e-mail. Un echec d'envoi n'invalide pas la
     * creation.
     */
    private CreatedAccountDto creer(PreparedRow prepared) {
        String temporaryPassword = passwordService.generateTemporaryPassword();

        User user = User.builder()
                .firstName(prepared.firstName.trim())
                .lastName(prepared.lastName.trim())
                .email(prepared.email)
                .password(passwordService.encode(temporaryPassword))
                .role(prepared.role)
                .department(prepared.department.isBlank() ? null : prepared.department.trim())
                .phoneNumber(prepared.phoneNumber.isBlank() ? null : prepared.phoneNumber.trim())
                .isActive(true)
                .mustChangePassword(true)
                .build();

        User savedUser = userRepository.save(user);
        boolean emailSent = emailService.sendUserCreatedEmail(savedUser, temporaryPassword);

        return CreatedAccountDto.builder()
                .email(savedUser.getEmail())
            .temporaryPassword(temporaryPassword)
                .emailSent(emailSent)
                .build();
    }

    /**
     * Mise a jour d'un compte existant, uniquement si le parametre « ecrasement des
     * donnees existantes » est actif. Le mot de passe, l'etat d'activation et
     * l'obligation de changement de mot de passe ne sont jamais touches : un import
     * ne doit pas pouvoir reinitialiser l'acces d'un utilisateur.
     */
    private void mettreAJour(PreparedRow prepared) {
        User existant = prepared.existant;
        existant.setFirstName(prepared.firstName.trim());
        existant.setLastName(prepared.lastName.trim());
        existant.setRole(prepared.role);
        existant.setDepartment(prepared.department.isBlank() ? null : prepared.department.trim());
        existant.setPhoneNumber(prepared.phoneNumber.isBlank() ? null : prepared.phoneNumber.trim());
        userRepository.save(existant);
    }

    /** Synthese renvoyee au frontend, {@code null} quand il n'y a rien a expliquer. */
    private String messageSynthese(int crees, int misAJour, int enErreur) {
        if (misAJour == 0 && enErreur == 0) {
            return null;
        }
        StringBuilder message = new StringBuilder();
        message.append(crees).append(" compte(s) créé(s)");
        if (misAJour > 0) {
            message.append(", ").append(misAJour).append(" compte(s) existant(s) mis à jour");
        }
        if (enErreur > 0) {
            message.append(", ").append(enErreur)
                    .append(" ligne(s) ignorée(s) car en erreur (validation automatique désactivée)");
        }
        return message.append('.').toString();
    }

    /**
     * Valide une ligne et renvoie le compte deja existant a mettre a jour, ou
     * {@code null} s'il s'agit d'une creation. Leve une {@link IllegalArgumentException}
     * dont le message est rapporte tel quel a l'administrateur.
     */
    private User validateRow(String firstName, String lastName, String email,
                             String roleRaw, String department, Set<String> activeDepartments,
                             Set<String> emailsInFile,
                             boolean ecrasementAutorise) {

        if (firstName.isBlank()) {
            throw new IllegalArgumentException("Le prenom est obligatoire");
        }
        if (lastName.isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("L'email '" + email + "' n'est pas valide");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (emailsInFile.contains(normalizedEmail)) {
            throw new IllegalArgumentException("L'email '" + email + "' est dupliqué dans le fichier");
        }

        User existant = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existant != null && !ecrasementAutorise) {
            throw new IllegalArgumentException("L'email '" + email + "' existe déjà en base");
        }
        if (existant != null
                && Objects.equals(existant.getId(), accessScope.getCurrentUser().getId())) {
            // Garde-fou : un administrateur ne peut pas modifier son propre compte
            // (et donc son propre role) par import.
            throw new IllegalArgumentException(
                    "L'email '" + email + "' est celui de votre propre compte :"
                            + " modifiez-le depuis la fiche utilisateur");
        }

        if (roleRaw.isBlank()) {
            throw new IllegalArgumentException("Le role est obligatoire");
        }
        Role role;
        try {
            role = Role.valueOf(roleRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Le role '" + roleRaw + "' n'est pas valide");
        }

        String normalizedDepartment = department == null ? "" : department.trim();
        if ((role == Role.ENSEIGNANT || role == Role.RESPONSABLE_PEDAGOGIQUE)
            && normalizedDepartment.isEmpty()) {
            throw new IllegalArgumentException(
                "Le département est obligatoire pour le rôle Enseignant ou Responsable pédagogique");
        }
        if (!normalizedDepartment.isEmpty()
                && !activeDepartments.contains(normalizedDepartment.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Le département '" + normalizedDepartment + "' est introuvable ou inactif");
        }

        return existant;
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
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    public byte[] generateTemplate() {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Utilisateurs");
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

            Row exampleRow = sheet.createRow(1);
            String[] example = {"Fatima", "Zahra", "fatima.zahra@example.com", "ENSEIGNANT", "Informatique", "0600000000"};
            for (int i = 0; i < example.length; i++) {
                exampleRow.createCell(i).setCellValue(example[i]);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la generation du modele Excel");
        }
    }

    public byte[] generateCsvReport(ImportResultDto result) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Email;E-mail envoye\n");

        for (CreatedAccountDto account : result.getCreatedAccounts()) {
            csv.append(account.getEmail())
                    .append(';')
                    .append(account.isEmailSent() ? "Oui" : "Non")
                    .append('\n');
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}