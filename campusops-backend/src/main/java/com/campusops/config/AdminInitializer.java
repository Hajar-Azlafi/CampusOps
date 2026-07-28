package com.campusops.config;

import com.campusops.enums.Role;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.security.admin.email}")
    private String adminEmail;

    @Value("${application.security.admin.default-password}")
    private String adminDefaultPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("L'administrateur par defaut existe deja, aucune action necessaire.");
            return;
        }

        User admin = User.builder()
                .firstName("Admin")
                .lastName("CampusOps")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminDefaultPassword))
                .role(Role.ADMIN)
                .isActive(true)
                .mustChangePassword(false)
                .build();

        userRepository.save(admin);

        log.info("========================================");
        log.info("Administrateur par defaut cree avec succes");
        log.info("Email : {}", adminEmail);
        log.info("Pensez a changer ce mot de passe apres la premiere connexion");
        log.info("========================================");
    }
}