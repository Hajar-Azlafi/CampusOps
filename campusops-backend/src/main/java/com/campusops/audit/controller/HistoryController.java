package com.campusops.audit.controller;

import com.campusops.audit.dto.AuditLogResponseDto;
import com.campusops.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Historique des actions. L'historique est une projection du journal d'audit :
 * "/api/history/me" renvoie l'historique de l'utilisateur courant, tandis que
 * "/api/history" (avec un userId optionnel) est une vue d'administration.
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final AuditService auditService;

    /** Historique de l'utilisateur courant, borne a l'annee universitaire. */
    @GetMapping("/me")
    public ResponseEntity<List<AuditLogResponseDto>> getMyHistory(
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(auditService.getMyHistory(academicYearId));
    }

    /** Historique d'un utilisateur donne, ou de tous a defaut (administration). */
    @GetMapping
    public ResponseEntity<List<AuditLogResponseDto>> getHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(auditService.getHistory(userId, academicYearId));
    }
}
