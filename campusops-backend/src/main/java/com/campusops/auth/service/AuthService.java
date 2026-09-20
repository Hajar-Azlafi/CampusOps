package com.campusops.auth.service;

import com.campusops.auth.dto.ChangePasswordRequestDto;
import com.campusops.auth.dto.LoginRequestDto;
import com.campusops.auth.dto.LoginResponseDto;
import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.security.JwtService;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import com.campusops.user.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Authentification";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final LoginSecurityService loginSecurityService;
    private final AuditService auditService;

    public LoginResponseDto login(LoginRequestDto request) {

        // Reglages de securite (Module 11, §7) : un compte verrouille est refuse
        // avant toute verification de mot de passe, avec un message explicite.
        loginSecurityService.verifierNonVerrouille(request.getEmail());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            // Compte le mot de passe errone et verrouille au besoin. Le message
            // renvoye a l'appelant reste generique (aucune enumeration possible).
            loginSecurityService.enregistrerEchec(request.getEmail());
            throw ex;
        }

        User user = (User) authentication.getPrincipal();

        // Remise a zero du compteur d'echecs + expiration eventuelle du mot de
        // passe (reutilise le mecanisme existant « changement obligatoire »).
        loginSecurityService.enregistrerSucces(user);

        String token = jwtService.generateToken(user);

        auditService.record(user, AuditAction.LOGIN, MODULE, "Connexion a l'application");

        return LoginResponseDto.builder()
                .token(token)
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    /** Trace la deconnexion de l'utilisateur courant (session JWT sans etat serveur). */
    public void logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            auditService.record(user, AuditAction.LOGOUT, MODULE, "Deconnexion de l'application");
        }
    }

    public void changePassword(Authentication authentication, ChangePasswordRequestDto request) {
        User user = (User) authentication.getPrincipal();

        if (!passwordService.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadCredentialsException("L'ancien mot de passe est incorrect");
        }

        // Verifie que les deux nouveaux mots de passe correspondent et respectent
        // la politique de securite avant tout encodage.
        passwordService.validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        user.setPassword(passwordService.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        // Nouveau point de depart de l'expiration + levee d'un eventuel verrou (§7).
        loginSecurityService.marquerMotDePasseChange(user);
        userRepository.save(user);

        auditService.record(user, AuditAction.UPDATE, MODULE, "Changement de mot de passe");
    }
}