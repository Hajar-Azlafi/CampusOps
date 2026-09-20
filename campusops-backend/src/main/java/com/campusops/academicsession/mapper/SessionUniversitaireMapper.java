package com.campusops.academicsession.mapper;

import com.campusops.academicsession.dto.SessionUniversitaireRequestDto;
import com.campusops.academicsession.dto.SessionUniversitaireResponseDto;
import com.campusops.academicsession.entity.SessionUniversitaire;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SessionUniversitaireMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SessionUniversitaire toEntity(SessionUniversitaireRequestDto dto);

    SessionUniversitaireResponseDto toResponseDto(SessionUniversitaire entity);
}
