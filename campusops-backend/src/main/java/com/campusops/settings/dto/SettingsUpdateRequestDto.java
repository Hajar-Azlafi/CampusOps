package com.campusops.settings.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corps de {@code PUT /api/settings} : mise a jour globale, section par section.
 *
 * <p>Toutes les sections sont facultatives — seules celles reellement fournies
 * sont appliquees. Une section presente est en revanche validee integralement
 * ({@link Valid}), ce qui evite les mises a jour partielles incoherentes (par
 * exemple une heure d'ouverture sans heure de fermeture).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingsUpdateRequestDto {

    @Valid
    private UniversitySettingsDto universite;

    @Valid
    private ReservationSettingsDto reservations;

    @Valid
    private WorkingHoursSettingsDto horaires;

    @Valid
    private SecuritySettingsDto securite;

    @Valid
    private NotificationSettingsDto notifications;

    @Valid
    private ImportSettingsDto imports;

    @Valid
    private DisplaySettingsDto affichage;
}
