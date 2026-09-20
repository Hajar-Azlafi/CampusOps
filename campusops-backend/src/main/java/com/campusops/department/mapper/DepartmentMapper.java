package com.campusops.department.mapper;

import com.campusops.department.dto.DepartmentRequestDto;
import com.campusops.department.dto.DepartmentResponseDto;
import com.campusops.department.entity.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Department toEntity(DepartmentRequestDto dto);

    DepartmentResponseDto toResponseDto(Department department);
}
