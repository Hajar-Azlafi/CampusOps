package com.campusops.reservation.dto;

import com.campusops.enums.ReservationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * La priorite n'est jamais saisie par le client : elle est toujours calculee
 * automatiquement par le Backend selon le type de reservation et le role du
 * demandeur (voir ReservationService#resolvePriority).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRequestDto {

    @NotNull(message = "L'espace est obligatoire")
    private Long spaceId;

    @NotNull(message = "Le type de réservation est obligatoire")
    private ReservationType type;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotNull(message = "L'heure de début est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @NotNull(message = "L'heure de fin est obligatoire")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;

    private String commentaire;

    /**
     * Utilisateur pour lequel la reservation est creee. Reserve a
     * l'administrateur (reservation pour le compte d'un tiers) ; ignore pour
     * les autres roles qui reservent toujours pour eux-memes.
     */
    private Long targetUserId;

    // ----- Contexte pédagogique (facultatif) -----
    // Utilisé par le responsable pédagogique pour rattacher sa demande à une
    // filière (obligatoire pour ce rôle), et éventuellement à un groupe, un
    // semestre et une année universitaire de SON périmètre.

    private Long programId;

    private Long groupId;

    private Long semesterId;

    private Long academicYearId;

    private String contenuPedagogique;
}