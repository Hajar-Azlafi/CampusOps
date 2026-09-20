package com.campusops.reservation.controller;

import com.campusops.enums.ReservationStatus;
import com.campusops.reservation.dto.ReservationRequestDto;
import com.campusops.reservation.dto.ReservationResponseDto;
import com.campusops.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponseDto> createReservation(
            @Valid @RequestBody ReservationRequestDto request) {
        ReservationResponseDto created = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponseDto>> getReservations(
            @RequestParam(required = false) ReservationStatus statut,
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(reservationService.getReservations(statut, academicYearId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponseDto>> getMyReservations() {
        return ResponseEntity.ok(reservationService.getMyReservations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponseDto> getReservationById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReservationResponseDto>> getReservationsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(reservationService.getReservationsByUser(userId));
    }

    @GetMapping("/space/{spaceId}")
    public ResponseEntity<List<ReservationResponseDto>> getReservationsBySpace(@PathVariable Long spaceId) {
        return ResponseEntity.ok(reservationService.getReservationsBySpace(spaceId));
    }

    @GetMapping("/date")
    public ResponseEntity<List<ReservationResponseDto>> getReservationsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.getReservationsByDate(date));
    }

    @GetMapping("/period")
    public ResponseEntity<List<ReservationResponseDto>> getReservationsByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(reservationService.getReservationsByPeriod(start, end));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ReservationResponseDto>> searchReservations(
            @RequestParam String keyword,
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(reservationService.searchReservations(keyword, academicYearId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponseDto> updateReservation(
            @PathVariable Long id, @Valid @RequestBody ReservationRequestDto request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ReservationResponseDto> approveReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.approveReservation(id));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ReservationResponseDto> rejectReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.rejectReservation(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponseDto> cancelReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancelReservation(id));
    }
}
