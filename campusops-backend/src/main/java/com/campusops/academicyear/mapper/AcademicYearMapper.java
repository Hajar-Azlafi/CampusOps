package com.campusops.academicyear.mapper;

import com.campusops.academicyear.dto.AcademicYearRequestDto;
import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.academicyear.entity.AcademicYear;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AcademicYearMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AcademicYear toEntity(AcademicYearRequestDto dto);

    AcademicYearResponseDto toResponseDto(AcademicYear academicYear);
}
