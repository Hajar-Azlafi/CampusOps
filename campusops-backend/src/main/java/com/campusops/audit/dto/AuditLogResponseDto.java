package com.campusops.audit.dto;

import com.campusops.enums.AuditAction;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class AuditLogResponseDto {

    private Long id;

    private Long userId;
    private String userNomComplet;
    private String userEmail;

    private AuditAction action;
    private String module;
    private String description;
    private String adresseIp;

    private LocalDateTime createdAt;
}
