package com.campusops.occupation.dto;

import com.campusops.enums.OccupationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Requête de création / modification d'une <b>occupation supplémentaire</b>
 * générique : soutenance ou « autre » occupation ponctuelle (événement, réunion,
 * activité de club, conférence...). Les examens ont leur propre DTO
 * ({@code ExamenRequestDto}) car ils portent un contexte pédagogique complet ;
 * ici le formulaire est volontairement plus souple (cahier des charges, onglets
 * « Planning soutenances » et « Autre »).
 *
 * <p>Contrainte de périmètre (§12), portée par le service :</p>
 * <ul>
 *   <li>une <b>soutenance</b> doit être rattachée à une filière
 *       ({@code programId} obligatoire) — un responsable pédagogique ne gère que
 *       ses filières ;</li>
 *   <li>une occupation « autre » peut n'avoir aucune filière : elle relève alors
 *       de l'administrateur uniquement.</li>
 * </ul>
 *
 * <p>Heures libres au format {@code HH:mm} (pas de créneau officiel imposé),
 * bornées par les horaires d'ouverture et la durée minimale exploitable — ces
 * règles sont appliquées par le moteur central, pas ici.</p>
 */
@Getter
@Setter
public class OccupationRequestDto {

    /**
     * Nature précise de l'occupation. Doit appartenir à la catégorie
     * {@code SOUTENANCE} ou {@code AUTRE} : le type {@code EXAMEN} est refusé par
     * le service (les examens passent par leur propre module).
     */
    @NotNull(message = "Le type d'occupation est obligatoire.")
    private OccupationType type;

    /** Intitulé affiché (sujet de soutenance, nom de l'événement...). */
    @Size(max = 180, message = "L'intitulé ne peut dépasser 180 caractères.")
    private String intitule;

    @NotNull(message = "L'espace est obligatoire.")
    private Long spaceId;

    @NotNull(message = "La date est obligatoire.")
    private LocalDate date;

    @NotNull(message = "L'heure de début est obligatoire.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @NotNull(message = "L'heure de fin est obligatoire.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    /**
     * Filière de rattachement. Obligatoire pour une soutenance, facultative pour
     * une occupation « autre » (contrôle porté par le service selon la catégorie).
     */
    private Long programId;

    /** Promotion concernée, facultative. */
    private Long promotionId;

    /** Groupe concerné, ou {@code null} pour toute la promotion. */
    private Long groupId;

    /** Responsable de l'activité, texte libre (intervenant externe, club...). */
    @Size(max = 150, message = "Le responsable ne peut dépasser 150 caractères.")
    private String responsable;

    /** Description / motif libre. */
    private String commentaire;
}
