package com.campusops.me.controller;

import com.campusops.me.dto.MeScopeResponseDto;
import com.campusops.program.dto.ProgramResponseDto;
import com.campusops.program.mapper.ProgramMapper;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expose le perimetre de l'utilisateur authentifie. Sert au frontend pour
 * adapter la navigation et les ecrans au role, sans jamais se substituer aux
 * controles de perimetre appliques cote services.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final AccessScopeService accessScope;
    private final ProgramRepository programRepository;
    private final ProgramMapper programMapper;

    /** Renvoie l'identite, le role et (pour un RP) ses filieres. */
    @GetMapping("/scope")
    public ResponseEntity<MeScopeResponseDto> getMyScope() {
        User me = accessScope.getCurrentUser();
        List<ProgramResponseDto> programs = accessScope.isResponsablePedagogique()
                ? programRepository.findByResponsableId(me.getId()).stream()
                        .map(programMapper::toResponseDto)
                        .toList()
                : List.of();
        return ResponseEntity.ok(MeScopeResponseDto.builder()
                .id(me.getId())
                .firstName(me.getFirstName())
                .lastName(me.getLastName())
                .email(me.getEmail())
                .role(me.getRole())
                .admin(accessScope.isAdmin())
                .programs(programs)
                .build());
    }
}
