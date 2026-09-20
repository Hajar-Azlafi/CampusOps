package com.campusops.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Mise a jour de la seule periode de validite d'un emploi du temps existant
 * (§20). Le contexte pedagogique (annee, filiere, niveau, promotion, groupe,
 * semestre, session) constitue la cle metier de l'en-tete et n'est donc jamais
 * modifiable : seules les dates le sont.
 *
 * <p>Les deux bornes sont facultatives : une borne absente retombe sur la
 * periode de l'annee universitaire. Passee {@code dateFin}, les salles des
 * seances sont automatiquement liberees.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableDatesRequestDto {

    private LocalDate dateDebut;

    private LocalDate dateFin;
}
