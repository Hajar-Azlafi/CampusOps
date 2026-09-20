package com.campusops.mail;

import com.campusops.security.AccessScopeService;
import com.campusops.user.entity.User;
import com.campusops.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint d'administration pour tester la configuration SMTP.
 * Accessible seulement aux administrateurs (vérification via AccessScopeService).
 */
@RestController
@RequestMapping("/api/debug/email")
@RequiredArgsConstructor
public class MailTestController {

    private final EmailService emailService;
    private final AccessScopeService accessScope;

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestBody(required = false) Map<String, String> body) {
        // Reserve aux admins pour éviter toute exposition en prod
        accessScope.requireAdmin();

        String to = body != null && body.get("email") != null ? body.get("email") : null;
        if (to == null || to.isBlank()) {
            // Par defaut, envoie a l'admin configure si present
            to = "admin@campusops.local";
        }

        User u = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(to)
                .role(Role.ADMIN)
                .build();

        boolean sent = emailService.sendUserCreatedEmail(u, "TestTemp123!");
        return ResponseEntity.ok(Map.of("email", to, "sent", sent));
    }
}
