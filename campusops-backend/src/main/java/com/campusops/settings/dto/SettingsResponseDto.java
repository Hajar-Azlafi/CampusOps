package com.campusops.settings.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Vue agregee de la configuration globale — corps de {@code GET /api/settings}.
 *
 * <p>Le decoupage par section est conserve dans la reponse (et non aplati) afin
 * que la page Parametres alimente directement ses onglets et que les endpoints
 * cibles ({@code /university}, {@code /reservations}, ...) reutilisent les memes
 * fragments.</p>
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
public class SettingsResponseDto {

    private UniversitySettingsDto universite;
    private ReservationSettingsDto reservations;
    private WorkingHoursSettingsDto horaires;
    private SecuritySettingsDto securite;
    private NotificationSettingsDto notifications;
    private ImportSettingsDto imports;
    private DisplaySettingsDto affichage;

    /** Logo et favicon (metadonnees + URL de telechargement). */
    private MediaInfoDto logo;
    private MediaInfoDto favicon;

    /** Contexte academique en lecture seule (§4). */
    private AcademicContextDto contexteAcademique;

    private LocalDateTime updatedAt;
}
