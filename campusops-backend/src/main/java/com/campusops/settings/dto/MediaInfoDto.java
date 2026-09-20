package com.campusops.settings.dto;

import com.campusops.enums.SettingsMediaType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Caracteristiques d'un media d'identite visuelle (§11). Les octets ne sont
 * jamais renvoyes en JSON : le frontend consomme {@link #url}.
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
public class MediaInfoDto {

    private SettingsMediaType type;

    /** Vrai si un fichier est effectivement enregistre pour ce type. */
    private boolean present;

    private String fileName;
    private String contentType;
    private Long tailleOctets;

    /**
     * URL publique de telechargement, horodatee pour invalider le cache du
     * navigateur apres un remplacement (ex. {@code /api/settings/logo?v=...}).
     */
    private String url;

    private LocalDateTime updatedAt;
}
