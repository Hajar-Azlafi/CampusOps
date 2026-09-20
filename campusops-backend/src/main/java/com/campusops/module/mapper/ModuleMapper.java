package com.campusops.module.mapper;

import com.campusops.module.dto.ModuleResponseDto;
import com.campusops.module.entity.Module;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapping du module vers son DTO de réponse. Le sens entrant (DTO → entité) est
 * traité manuellement dans le service : les champs {@code programId}/{@code
 * semesterId} exigent une résolution par référentiel (chargement de la filière
 * et du semestre), ce que MapStruct ne peut pas faire seul.
 */
@Mapper(componentModel = "spring")
public interface ModuleMapper {

    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "programCode", source = "program.code")
    @Mapping(target = "semesterId", source = "semester.id")
    @Mapping(target = "semesterNom", source = "semester.nom")
    ModuleResponseDto toResponseDto(Module entity);
}
