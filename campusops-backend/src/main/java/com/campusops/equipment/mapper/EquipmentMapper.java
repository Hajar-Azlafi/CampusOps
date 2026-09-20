package com.campusops.equipment.mapper;

import com.campusops.equipment.dto.EquipmentRequestDto;
import com.campusops.equipment.dto.EquipmentResponseDto;
import com.campusops.equipment.entity.Equipment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EquipmentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Equipment toEntity(EquipmentRequestDto dto);

    EquipmentResponseDto toResponseDto(Equipment equipment);
}
