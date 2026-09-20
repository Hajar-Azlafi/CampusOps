package com.campusops.occupation.mapper;

import com.campusops.enums.OccupationType;
import com.campusops.occupation.dto.OccupationResponseDto;
import com.campusops.occupation.entity.OccupationSupplementaire;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct : entité {@link OccupationSupplementaire} → {@link OccupationResponseDto}.
 *
 * <p>Le libellé et la catégorie sont dérivés du {@code type} (jamais stockés,
 * donc jamais désynchronisés). La durée provient du helper transient de
 * l'entité.</p>
 */
@Mapper(componentModel = "spring")
public interface OccupationMapper {

    @Mapping(target = "typeLibelle", source = "type", qualifiedByName = "typeLibelle")
    @Mapping(target = "categorie", expression = "java(entity.getCategorie())")
    @Mapping(target = "dureeMinutes", expression = "java(entity.getDureeMinutes())")
    @Mapping(target = "spaceId", source = "space.id")
    @Mapping(target = "spaceNom", source = "space.nom")
    @Mapping(target = "spaceCode", source = "space.code")
    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionNom", source = "promotion.nom")
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupNom", source = "group.nom")
    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
    OccupationResponseDto toResponseDto(OccupationSupplementaire entity);

    @Named("typeLibelle")
    default String typeLibelle(OccupationType type) {
        return type != null ? type.getLibelle() : null;
    }
}
