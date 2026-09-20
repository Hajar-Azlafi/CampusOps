package com.campusops.availability.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * <b>Creneau officiel</b> de l'etablissement (entite {@code TimeSlot} active),
 * propose tel quel a la reservation lorsqu'il tient entierement dans une periode
 * libre de l'espace (§7, § reservation depuis un espace libre).
 *
 * <p>Permet a l'utilisateur de <b>choisir un creneau</b> plutot que de saisir
 * deux heures a la main, en reutilisant la meme logique de creneaux que les
 * emplois du temps et les examens.</p>
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
public class OfficialSlotDto {

    private Long id;

    /** Nom administrable du creneau, ex. « Créneau 1 ». */
    private String nom;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureDebut;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime heureFin;

    /** Libelle pret a afficher, ex. « 08:30 → 10:25 ». */
    private String label;

    private long dureeMinutes;
}
