package com.campusops.semester.mapper;

import com.campusops.semester.dto.SemesterRequestDto;
import com.campusops.semester.dto.SemesterResponseDto;
import com.campusops.semester.entity.Semester;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SemesterMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "courant", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Semester toEntity(SemesterRequestDto dto);

    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelNom", source = "level.nom")
    SemesterResponseDto toResponseDto(Semester semester);
}
