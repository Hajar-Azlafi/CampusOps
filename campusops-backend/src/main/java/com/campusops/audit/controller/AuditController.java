package com.campusops.audit.controller;

import com.campusops.audit.dto.AuditLogResponseDto;
import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Exposition du journal d'audit. Reserve aux administrateurs (le controle
 * d'acces est assure par la configuration de securite basee sur l'URL).
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** Journal complet, du plus recent au plus ancien, borne a l'annee universitaire. */
    @GetMapping
    public ResponseEntity<List<AuditLogResponseDto>> getAll(
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(auditService.getAllAudits(academicYearId));
    }

    /** Recherche multi-criteres : chaque parametre est facultatif. */
    @GetMapping("/search")
    public ResponseEntity<List<AuditLogResponseDto>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(auditService.search(userId, action, module, start, end));
    }
}
