package com.campusops.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Statistiques de repartition des seances <b>par type</b> (cahier des charges
 * §6/§19), calculees a la demande sur des donnees reelles.
 *
 * <p><b>Dynamiques et contextuelles</b> : le calcul porte sur les seances
 * actives du perimetre de l'utilisateur (ADMIN = tout ; responsable pedagogique
 * = ses seules filieres) puis, le cas echeant, sur le contexte pedagogique
 * transmis (annee, filiere, niveau, promotion, groupe, semestre, emploi du
 * temps). Aucune valeur n'est inventee : un type sans seance vaut 0.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionTypeStatsDto {

    /** Nombre total de seances comptabilisees dans le perimetre + contexte. */
    private long total;

    /**
     * Repartition par type de seance. Contient <b>tous les types actifs</b>
     * (dans leur ordre d'affichage, valeur 0 comprise), suivis des eventuels
     * types desactives encore utilises, puis d'un agregat « Non categorise »
     * uniquement s'il reste des seances non rattachables. La somme des valeurs
     * est toujours egale a {@link #total}.
     */
    private List<SessionTypeCountDto> repartition;
}
