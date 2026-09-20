package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Utilisateur accompagne de son activite de reservation sur l'annee consultee,
 * pour le classement des utilisateurs les plus actifs.
 * <p>
 * Le total compte <b>toutes</b> les demandes de l'utilisateur : la decomposition
 * par statut est fournie a cote afin que le classement reste verifiable (une
 * demande refusee ou annulee ne represente pas la meme activite qu'une
 * reservation reellement accordee).
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
public class UserActivityDto {

    private Long userId;
    private String nom;
    private String email;

    /** Nom technique du role (ADMIN, ENSEIGNANT, ...), libelle cote frontend. */
    private String role;

    /** Nombre total de demandes de reservation, tous statuts confondus. */
    private long total;

    /** Reservations accordees : statuts APPROVED et COMPLETED. */
    private long validees;

    /** Demandes encore en attente de validation. */
    private long enAttente;

    /** Demandes non abouties : statuts REJECTED et CANCELLED. */
    private long nonAbouties;
}
