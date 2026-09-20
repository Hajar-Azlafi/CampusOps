package com.campusops.notification.controller;

import com.campusops.notification.dto.NotificationResponseDto;
import com.campusops.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Points d'entree REST du centre de notifications de l'utilisateur courant.
 * Aucune logique metier n'est realisee ici : le controleur delegue au service.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getMyNotifications(
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(academicYearId));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDto>> getMyUnreadNotifications(
            @RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications(academicYearId));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> countMyUnreadNotifications() {
        return ResponseEntity.ok(Map.of("count", notificationService.countMyUnreadNotifications()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDto> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }
}
