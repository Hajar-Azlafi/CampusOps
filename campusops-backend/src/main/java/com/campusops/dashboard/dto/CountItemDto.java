package com.campusops.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Element generique d'une distribution statistique : une cle technique, un
 * libelle affichable et une valeur numerique. Utilise pour les repartitions
 * (par type, par statut, par utilisateur, par periode...).
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
public class CountItemDto {

    /** Cle technique (ex. valeur d'enum) ou identifiant. */
    private String key;

    /** Libelle affichable. */
    private String label;

    /** Valeur agregee. */
    private long value;
}
