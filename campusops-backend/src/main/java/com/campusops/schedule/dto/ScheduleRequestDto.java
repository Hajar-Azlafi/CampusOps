package com.campusops.schedule.dto;

import com.campusops.enums.PresenceType;
import com.campusops.enums.SessionType;
import com.campusops.enums.WeekDay;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequestDto {

    @NotNull(message = "Le jour est obligatoire")
    private WeekDay jour;

    @NotNull(message = "Le créneau horaire est obligatoire")
    private Long timeSlotId;

    // Salle facultative : obligatoire uniquement en présentiel (§6).
    // La règle « présentiel ⇒ salle » est appliquée côté service.
    private Long spaceId;

    @NotNull(message = "La filière est obligatoire")
    private Long programId;

    @NotNull(message = "Le niveau est obligatoire")
    private Long levelId;

    @NotNull(message = "La promotion est obligatoire")
    private Long promotionId;

    @NotNull(message = "Le groupe est obligatoire")
    private Long groupId;

    @NotNull(message = "Le semestre est obligatoire")
    private Long semesterId;

    @NotNull(message = "L'année universitaire est obligatoire")
    private Long academicYearId;

    @NotBlank(message = "L'enseignant est obligatoire")
    private String enseignant;

    /**
     * Module d'enseignement sélectionné dans le référentiel (§8). Prioritaire :
     * lorsqu'il est fourni, la matière est renseignée à partir du nom du module
     * et le module est validé contre le contexte (filière + semestre) de la
     * séance. Facultatif pour rester compatible avec l'import Excel et les
     * anciens appels qui fournissent une matière en texte libre.
     */
    private Long moduleId;

    /**
     * Matière (libellé). Conservée pour la rétro-compatibilité (import Excel,
     * anciens appels). Ignorée lorsqu'un {@link #moduleId} est fourni : le nom du
     * module fait alors foi. Obligatoire uniquement si aucun module n'est choisi.
     */
    private String matiere;

    /**
     * Type de séance <b>configurable</b> (§7), choisi dans le référentiel.
     * Prioritaire sur l'enum {@link #type}. Facultatif : à défaut, l'enum
     * historique est utilisée.
     */
    private Long typeSeanceId;

    /**
     * Type de séance historique (enum). Conservé pour la rétro-compatibilité et
     * toujours renseigné en base (dérivé du type configurable si besoin).
     * Facultatif si un {@link #typeSeanceId} est fourni.
     */
    private SessionType type;

    // Type de présence (§6). Par défaut PRESENTIEL si absent.
    private PresenceType typePresence;

    // Rattachement facultatif à un en-tête d'emploi du temps (§1/§17).
    private Long emploiDuTempsId;

    private String commentaire;
}
