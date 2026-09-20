package com.campusops.settings.mapper;

import com.campusops.settings.dto.DisplaySettingsDto;
import com.campusops.settings.dto.ImportSettingsDto;
import com.campusops.settings.dto.MediaInfoDto;
import com.campusops.settings.dto.NotificationSettingsDto;
import com.campusops.settings.dto.ReservationSettingsDto;
import com.campusops.settings.dto.SecuritySettingsDto;
import com.campusops.settings.dto.UniversitySettingsDto;
import com.campusops.settings.dto.WorkingHoursSettingsDto;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.entity.SettingsMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Conversion entre la configuration globale et ses fragments par section.
 *
 * <p>{@code nullValuePropertyMappingStrategy = IGNORE} : lors d'une mise a jour,
 * une propriete absente du corps de requete laisse la valeur en place au lieu de
 * l'ecraser avec {@code null}. Les champs derives (motifs de format, etat SMTP,
 * bornes des creneaux...) sont volontairement ignores ici : ils sont calcules par
 * le service, qui seul connait les autres modules.</p>
 *
 * <p>{@code unmappedTargetPolicy = IGNORE} est indispensable ici : chaque methode
 * de mise a jour ne concerne qu'<b>une section</b> de la configuration et laisse
 * donc, par construction, toutes les autres colonnes intactes.</p>
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SettingsMapper {

    // ===================== Entite -> DTO de section =====================

    UniversitySettingsDto toUniversityDto(AppSettings settings);

    ReservationSettingsDto toReservationDto(AppSettings settings);

    @Mapping(target = "creneauxDebut", ignore = true)
    @Mapping(target = "creneauxFin", ignore = true)
    @Mapping(target = "creneauxActifs", ignore = true)
    WorkingHoursSettingsDto toWorkingHoursDto(AppSettings settings);

    SecuritySettingsDto toSecurityDto(AppSettings settings);

    @Mapping(target = "smtpConfigure", ignore = true)
    @Mapping(target = "expediteur", ignore = true)
    NotificationSettingsDto toNotificationDto(AppSettings settings);

    @Mapping(target = "plafondServeurMo", ignore = true)
    @Mapping(target = "formatsSupportes", ignore = true)
    ImportSettingsDto toImportDto(AppSettings settings);

    @Mapping(target = "motifDate", ignore = true)
    @Mapping(target = "motifHeure", ignore = true)
    DisplaySettingsDto toDisplayDto(AppSettings settings);

    // ===================== DTO de section -> Entite =====================

    void updateUniversity(UniversitySettingsDto dto, @MappingTarget AppSettings settings);

    void updateReservation(ReservationSettingsDto dto, @MappingTarget AppSettings settings);

    void updateWorkingHours(WorkingHoursSettingsDto dto, @MappingTarget AppSettings settings);

    void updateSecurity(SecuritySettingsDto dto, @MappingTarget AppSettings settings);

    void updateNotification(NotificationSettingsDto dto, @MappingTarget AppSettings settings);

    void updateImport(ImportSettingsDto dto, @MappingTarget AppSettings settings);

    void updateDisplay(DisplaySettingsDto dto, @MappingTarget AppSettings settings);

    // ===================== Medias =====================

    @Mapping(target = "present", constant = "true")
    @Mapping(target = "url", ignore = true)
    MediaInfoDto toMediaInfoDto(SettingsMedia media);
}
