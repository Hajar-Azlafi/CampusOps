package com.campusops.auth.controller;

import com.campusops.auth.dto.ChangePasswordRequestDto;
import com.campusops.auth.dto.ForgotPasswordRequestDto;
import com.campusops.auth.dto.LoginRequestDto;
import com.campusops.auth.dto.LoginResponseDto;
import com.campusops.auth.dto.MessageResponseDto;
import com.campusops.auth.dto.ResetPasswordRequestDto;
import com.campusops.auth.service.AuthService;
import com.campusops.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        LoginResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequestDto request
    ) {
        authService.changePassword(authentication, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout(authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * Demande de reinitialisation (« mot de passe oublie »). Renvoie toujours le
     * meme message generique, que l'adresse existe ou non.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request) {
        String message = passwordResetService.requestReset(request);
        return ResponseEntity.ok(MessageResponseDto.builder().message(message).build());
    }

    /** Definition d'un nouveau mot de passe a partir du jeton recu par e-mail. */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDto> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(MessageResponseDto.builder()
                .message("Votre mot de passe a été réinitialisé. Vous pouvez maintenant vous connecter.")
                .build());
    }
}