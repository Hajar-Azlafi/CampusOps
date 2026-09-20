package com.campusops.availability.dto;

import com.campusops.enums.SpaceType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

/**
 * Espace pedagogique retourne par la recherche, enrichi de tout ce qui permet
 * de decider et de reserver sans second calcul cote client (§8, §9, §13, §15,
 * §16).
 *
 * <p>Un espace figure dans les resultats s'il est <b>disponible</b> ou s'il
 * porte seulement une <b>demande en attente</b> (§13). Le champ {@link #statut}
 * distingue les deux etats ; {@link #motifIndisponibilite} explique un blocage
 * eventuel sans jamais exposer d'information personnelle (§16).</p>
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
public class AvailableSpaceResponseDto {

    private Long spaceId;
    private String nom;
    private String code;
    private SpaceType type;
    private Integer capacite;

    private Long buildingId;
    private String buildingNom;
    private String buildingCode;

    private Long floorId;
    private String floorNom;
    private Integer floorNumero;

    private List<AvailableEquipmentDto> equipments;
    private int nombreEquipements;
    private int equipementsCorrespondants;

    /** Vrai si reservable immediatement ; faux si seule une demande est en attente. */
    private boolean disponible;

    /** Etat machine : {@code DISPONIBLE} ou {@code EN_ATTENTE} (§13). */
    private String statut;

    /** Vrai si une demande de reservation est en attente sur le creneau (§13). */
    private boolean demandeEnAttente;

    /**
     * Motif d'indisponibilite / d'attente, non nominatif (§16). Null si l'espace
     * est totalement libre pour le creneau recherche.
     */
    private String motifIndisponibilite;

    /**
     * Periodes reellement libres de la journee dans les bornes d'ouverture
     * (§8, §11). En mode « heures explicites », se limite au chevauchement avec
     * le creneau demande. Ne contient <b>jamais</b> de micro-periode inferieure a
     * la duree minimale exploitable, ni de periode plus courte que la duree
     * souhaitee lorsqu'elle est demandee (§ micro-creneaux, § duree souhaitee).
     */
    private List<FreePeriodDto> periodesLibres;

    /**
     * Creneaux officiels de l'etablissement entierement contenus dans une des
     * {@link #periodesLibres}. Le formulaire de reservation les propose en un
     * clic, en alternative a la saisie manuelle de deux heures.
     */
    private List<OfficialSlotDto> creneauxProposes;

    /**
     * Duree, en minutes, du <b>plus long bloc libre continu</b> retenu. Deux
     * periodes separees par une occupation ne sont jamais additionnees : cette
     * valeur est donc la duree maximale reservable d'un seul tenant.
     */
    private long plusLongueDureeMinutes;

    /**
     * Fenetre reservable a proposer par defaut dans le formulaire (§9, §10) :
     * pour un creneau demande, la periode libre qui le contient ; pour
     * « maintenant », de l'instant courant a la prochaine occupation.
     */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime fenetreDebut;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime fenetreFin;

    /** Texte d'affichage dependant du mode de recherche (§15). */
    private String affichageDisponibilite;

    /** Debut de la prochaine occupation (emploi du temps, examen ou reservation) apres le creneau. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime prochaineOccupation;

    /** Origine de la prochaine occupation, non nominative (§16). */
    private String prochaineOccupationSource;

    /** Minutes restantes entre la reference et la prochaine occupation. */
    private Long tempsRestantMinutes;
}
