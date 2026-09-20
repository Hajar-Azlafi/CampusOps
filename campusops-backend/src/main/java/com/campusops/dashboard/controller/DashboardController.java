package com.campusops.dashboard.controller;

import com.campusops.dashboard.dto.DashboardStatsDto;
import com.campusops.dashboard.dto.OverviewStatsDto;
import com.campusops.dashboard.dto.ReservationStatsDto;
import com.campusops.dashboard.dto.SpaceStatsDto;
import com.campusops.dashboard.dto.TemporalStatsDto;
import com.campusops.dashboard.service.DashboardService;
import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Points d'entree REST du tableau de bord. Reserve a l'administrateur : les
 * statistiques agregees ne concernent pas les autres roles.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/statistics")
    public ResponseEntity<DashboardStatsDto> getStatistics(
            @RequestParam(required = false) Long academicYearId) {
        ensureAdmin();
        return ResponseEntity.ok(dashboardService.getStatistics(academicYearId));
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewStatsDto> getOverview(
            @RequestParam(required = false) Long academicYearId) {
        ensureAdmin();
        return ResponseEntity.ok(dashboardService.getOverview(academicYearId));
    }

    @GetMapping("/spaces")
    public ResponseEntity<SpaceStatsDto> getSpaceStats(
            @RequestParam(required = false) Long academicYearId) {
        ensureAdmin();
        return ResponseEntity.ok(dashboardService.getSpaceStats(academicYearId));
    }

    @GetMapping("/reservations")
    public ResponseEntity<ReservationStatsDto> getReservationStats(
            @RequestParam(required = false) Long academicYearId) {
        ensureAdmin();
        return ResponseEntity.ok(dashboardService.getReservationStats(academicYearId));
    }

    @GetMapping("/temporal")
    public ResponseEntity<TemporalStatsDto> getTemporalStats(
            @RequestParam(required = false) Long academicYearId) {
        ensureAdmin();
        return ResponseEntity.ok(dashboardService.getTemporalStats(academicYearId));
    }

    /** Le tableau de bord est reserve a l'administrateur. */
    private void ensureAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user
                && user.getRole() == Role.ADMIN) {
            return;
        }
        throw new BadRequestException("Le tableau de bord est reserve a l'administrateur");
    }
}