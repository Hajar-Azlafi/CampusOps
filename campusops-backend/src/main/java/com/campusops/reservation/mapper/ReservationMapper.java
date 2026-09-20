package com.campusops.reservation.mapper;

import com.campusops.reservation.dto.ReservationResponseDto;
import com.campusops.reservation.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "spaceId", source = "space.id")
    @Mapping(target = "spaceNom", source = "space.nom")
    @Mapping(target = "spaceCode", source = "space.code")
    @Mapping(target = "spaceType", source = "space.type")
    @Mapping(target = "spaceCapacite", source = "space.capacite")
    @Mapping(target = "buildingNom", source = "space.floor.building.nom")
    @Mapping(target = "floorNom", source = "space.floor.nom")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userNomComplet",
            expression = "java(reservation.getUser().getFirstName() + \" \" + reservation.getUser().getLastName())")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "userRole", source = "user.role")
    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "programCode", source = "program.code")
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupNom", source = "group.nom")
    @Mapping(target = "semesterId", source = "semester.id")
    @Mapping(target = "semesterNom", source = "semester.nom")
    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
    ReservationResponseDto toResponseDto(Reservation reservation);
}
