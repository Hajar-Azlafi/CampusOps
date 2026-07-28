package com.campusops.auth.controller;

import com.campusops.auth.dto.ChangePasswordRequestDto;
import com.campusops.auth.dto.LoginRequestDto;
import com.campusops.auth.dto.LoginResponseDto;
import com.campusops.auth.service.AuthService;
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
}