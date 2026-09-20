package com.campusops.me.dto;

import com.campusops.enums.Role;
import com.campusops.program.dto.ProgramResponseDto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Perimetre de l'utilisateur authentifie, expose au frontend pour adapter
 * l'interface (sidebar, routes, tableaux de bord) selon le role.
 *
 * <p>{@code admin=true} signifie un perimetre non borne (acces global).
 * Pour un responsable pedagogique, {@code programs} contient exactement ses
 * filieres ; le frontend ne doit afficher/charger que celles-ci. La securite
 * reelle reste appliquee cote backend : ce DTO n'est qu'une aide d'affichage.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class MeScopeResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;

    /** Vrai si l'utilisateur a un acces global (ADMIN) et n'est donc pas borne. */
    private boolean admin;

    /** Filieres du responsable pedagogique (vide pour ADMIN et autres roles). */
    private List<ProgramResponseDto> programs;
}
