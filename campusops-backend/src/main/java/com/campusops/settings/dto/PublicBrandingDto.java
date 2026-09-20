package com.campusops.settings.dto;

import com.campusops.enums.AppTheme;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identite visuelle publique, consommable <b>sans authentification</b> par la
 * page de connexion (§11 : le logo doit y apparaitre) et par le document HTML
 * (titre, favicon, couleurs).
 *
 * <p>Volontairement minimal : aucune donnee sensible (ni politique de securite,
 * ni parametres d'import, ni coordonnees internes) n'y figure.</p>
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
public class PublicBrandingDto {

    private String nom;
    private String nomCourt;
    private String slogan;
    private String ville;
    private String pays;

    private String couleurPrincipale;
    private String couleurSecondaire;
    private AppTheme themeParDefaut;

    /** Motif de date/heure applique par le frontend (§10). */
    private String motifDate;
    private String motifHeure;

    /** Pagination : le frontend n'a pas besoin d'etre authentifie pour la connaitre. */
    private boolean paginationActivee;
    private Integer elementsParPage;

    /** URL du logo (null si aucun logo n'est enregistre). */
    private String logoUrl;

    /** URL du favicon (null si aucun favicon n'est enregistre). */
    private String faviconUrl;
}
