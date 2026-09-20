package com.campusops.audit.mapper;

import com.campusops.audit.dto.AuditLogResponseDto;
import com.campusops.audit.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "userNomComplet", expression = "java(fullName(auditLog))")
    AuditLogResponseDto toResponseDto(AuditLog auditLog);

    /** Nom complet du destinataire, en tolerant l'absence d'utilisateur. */
    default String fullName(AuditLog auditLog) {
        if (auditLog == null || auditLog.getUser() == null) {
            return null;
        }
        return auditLog.getUser().getFirstName() + " " + auditLog.getUser().getLastName();
    }
}
