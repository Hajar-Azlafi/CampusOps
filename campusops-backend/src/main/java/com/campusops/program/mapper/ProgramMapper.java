package com.campusops.program.mapper;

import com.campusops.program.dto.ProgramRequestDto;
import com.campusops.program.dto.ProgramResponseDto;
import com.campusops.program.entity.Program;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProgramMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "responsable", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Program toEntity(ProgramRequestDto dto);

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentNom", source = "department.nom")
    @Mapping(target = "departmentCode", source = "department.code")
    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelNom", source = "level.nom")
    @Mapping(target = "levelTypeFormation", source = "level.typeFormation")
    @Mapping(target = "responsableId", source = "responsable.id")
    @Mapping(target = "responsableFirstName", source = "responsable.firstName")
    @Mapping(target = "responsableLastName", source = "responsable.lastName")
    @Mapping(target = "responsableEmail", source = "responsable.email")
    @Mapping(target = "utilisable",
            expression = "java(com.campusops.validation.ReferentialStatus.usable(program))")
    ProgramResponseDto toResponseDto(Program program);
}
