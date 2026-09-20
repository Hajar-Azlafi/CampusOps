package com.campusops.timeslot.mapper;

import com.campusops.timeslot.dto.TimeSlotRequestDto;
import com.campusops.timeslot.dto.TimeSlotResponseDto;
import com.campusops.timeslot.entity.TimeSlot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimeSlotMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TimeSlot toEntity(TimeSlotRequestDto dto);

    TimeSlotResponseDto toResponseDto(TimeSlot timeSlot);
}
