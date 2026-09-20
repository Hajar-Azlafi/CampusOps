package com.campusops.program.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corps de la requete d'affectation du responsable pedagogique d'une filiere.
 * {@code userId} null retire le responsable actuel de la filiere.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignResponsableRequestDto {

    private Long userId;
}
