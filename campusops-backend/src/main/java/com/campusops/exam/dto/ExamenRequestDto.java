package com.campusops.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Création / modification d'un examen daté (Lot 2 F2).
 *
 * <p>Seul le strict nécessaire est saisi : la <strong>filière</strong>, le
 * <strong>semestre</strong> et l'<strong>année universitaire</strong> sont
 * <em>dérivés</em> côté backend (la matière porte filière + semestre ; la
 * promotion porte filière + année) puis contrôlés pour cohérence. Le
 * {@code groupId} est facultatif : à défaut, l'examen concerne toute la
 * promotion.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamenRequestDto {

    @NotNull(message = "La date de l'examen est obligatoire")
    private LocalDate date;

    @NotNull(message = "Le créneau horaire est obligatoire")
    private Long timeSlotId;

    @NotNull(message = "La salle est obligatoire")
    private Long spaceId;

    @NotNull(message = "La matière (module) est obligatoire")
    private Long moduleId;

    @NotNull(message = "La promotion est obligatoire")
    private Long promotionId;

    /**
     * Groupe concerné (facultatif). À défaut ({@code null}), l'examen s'adresse à
     * toute la promotion.
     */
    private Long groupId;

    @NotNull(message = "La session universitaire est obligatoire")
    private Long sessionId;

    /** Note libre optionnelle (consignes, surveillant, etc.). */
    private String commentaire;
}
