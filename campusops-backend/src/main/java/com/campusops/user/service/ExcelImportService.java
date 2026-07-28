package com.campusops.user.service;

import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.user.dto.CreatedAccountDto;
import com.campusops.user.dto.ImportResultDto;
import com.campusops.user.dto.ImportRowErrorDto;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.user.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    private static final String[] HEADERS = {
            "Prenom", "Nom", "Email", "Role", "Departement", "Telephone"
    };

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Transactional
    public ImportResultDto importUsers(MultipartFile file) {

        if (file.isEmpty()) {
            throw new BadRequestException("Le fichier envoye est vide");
        }

        List<CreatedAccountDto> createdAccounts = new ArrayList<>();
        List<ImportRowErrorDto> errors = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        int totalRows = 0;

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
                    String firstName = getCellValue(row, 0);
                    String lastName = getCellValue(row, 1);
                    String email = getCellValue(row, 2);
                    String roleRaw = getCellValue(row, 3);
                    String department = getCellValue(row, 4);
                    String phoneNumber = getCellValue(row, 5);

                    validateRow(firstName, lastName, email, roleRaw, emailsInFile, displayRowNumber);

                    Role role = Role.valueOf(roleRaw.trim().toUpperCase());
                    String temporaryPassword = passwordGenerator.generate();

                    User user = User.builder()
                            .firstName(firstName.trim())
                            .lastName(lastName.trim())
                            .email(email.trim().toLowerCase())
                            .password(passwordEncoder.encode(temporaryPassword))
                            .role(role)
                            .department(department.isBlank() ? null : department.trim())
                            .phoneNumber(phoneNumber.isBlank() ? null : phoneNumber.trim())
                            .isActive(true)
                            .build();

                    userRepository.save(user);
                    emailsInFile.add(email.trim().toLowerCase());

                    createdAccounts.add(CreatedAccountDto.builder()
                            .email(user.getEmail())
                            .temporaryPassword(temporaryPassword)
                            .build());

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

        return ImportResultDto.builder()
                .totalRows(totalRows)
                .successCount(createdAccounts.size())
                .errorCount(errors.size())
                .createdAccounts(createdAccounts)
                .errors(errors)
                .build();
    }

    private void validateRow(String firstName, String lastName, String email,
                             String roleRaw, Set<String> emailsInFile, int rowNumber) {

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

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("L'email '" + email + "' existe deja en base");
        }
        if (emailsInFile.contains(normalizedEmail)) {
            throw new IllegalArgumentException("L'email '" + email + "' est duplique dans le fichier");
        }

        if (roleRaw.isBlank()) {
            throw new IllegalArgumentException("Le role est obligatoire");
        }
        try {
            Role.valueOf(roleRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Le role '" + roleRaw + "' n'est pas valide");
        }
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
}