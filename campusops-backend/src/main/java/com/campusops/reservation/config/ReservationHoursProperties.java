package com.campusops.reservation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reste des reglages de reservation qui ne sont <b>pas</b> des regles metier :
 * uniquement l'ergonomie du formulaire de recherche.
 *
 * <h2>Ce qui a quitte ce fichier (Module 11, §20)</h2>
 * <p>Les horaires d'ouverture et de fermeture, les jours ouvrables, l'heure
 * limite de recherche et la duree minimale exploitable etaient auparavant des
 * proprietes {@code campusops.reservation.*}. Ils vivent desormais dans la
 * <b>configuration globale en base</b> ({@code app_settings}, page Parametres >
 * Horaires et Parametres > Reservations) et sont lus via
 * {@code SettingsService}. Les conserver ici aurait cree deux sources de verite
 * concurrentes : une valeur modifiee par l'administrateur dans l'interface et
 * une autre codee dans {@code application.properties}.</p>
 *
 * <p>Seules les <b>durees proposees</b> restent une propriete : ce n'est pas une
 * regle (aucune valeur n'est refusee a cause d'elle), seulement la liste des
 * raccourcis affiches par le selecteur « duree souhaitee ».</p>
 */
@Component
@ConfigurationProperties(prefix = "campusops.reservation")
@Getter
@Setter
public class ReservationHoursProperties {

    /**
     * Durees proposees par le formulaire de recherche lorsque l'utilisateur ne
     * saisit aucune heure (« duree souhaitee », optionnelle). Exprimees en
     * minutes ; la liste est purement indicative cote client, toute valeur
     * numerique reste acceptee par l'API.
     */
    private List<Integer> durationOptions = new ArrayList<>(List.of(60, 90, 120, 180));
}
