package com.campusops.availability.service;

import com.campusops.availability.dto.AvailabilitySearchRequestDto;
import com.campusops.availability.dto.AvailabilitySearchResponseDto;
import com.campusops.availability.dto.AvailableEquipmentDto;
import com.campusops.availability.dto.AvailableSpaceResponseDto;
import com.campusops.availability.dto.FreePeriodDto;
import com.campusops.availability.dto.OfficialSlotDto;
import com.campusops.availability.dto.SearchWindowDto;
import com.campusops.availability.engine.AvailabilityEngine;
import com.campusops.availability.engine.DateLabels;
import com.campusops.availability.engine.DayContext;
import com.campusops.availability.engine.FreeInterval;
import com.campusops.availability.engine.OccupancyInterval;
import com.campusops.availability.engine.SearchDateWindow;
import com.campusops.availability.engine.SlotVerdict;
import com.campusops.availability.engine.SpaceOccupancy;
import com.campusops.availability.engine.WorkingCalendarService;
import com.campusops.enums.OccupancySource;
import com.campusops.equipment.entity.Equipment;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Recherche des espaces disponibles, entierement adossee au <b>moteur central</b>
 * {@link AvailabilityEngine} et au {@link WorkingCalendarService} (§2, §19). Ce
 * service n'implemente aucune regle d'occupation en propre : il orchestre les
 * trois modes de recherche (§6, §10, §11), assemble les DTO et applique les
 * filtres/tri metier (§18).
 *
 * <ul>
 *   <li><b>NOW</b> (§10) : reference = instant present ; fenetre = maintenant →
 *       prochaine occupation.</li>
 *   <li><b>TODAY</b> (§11) : periodes libres <b>restantes</b> du jour dans les
 *       bornes d'ouverture, sans saisie d'heures.</li>
 *   <li><b>CUSTOM</b> (§6) : date choisie, heures <b>optionnelles</b> ; sans
 *       heures, on calcule les periodes libres de la journee entiere ; avec
 *       heures, on evalue le creneau demande.</li>
 * </ul>
 *
 * <p>Quatre regles transverses s'appliquent a tous les modes :</p>
 * <ol>
 *   <li><b>Dates ouvertes a la recherche</b> : jamais dans le passe, jamais la
 *       journee en cours passe l'heure limite, toujours dans l'annee
 *       universitaire active ({@link SearchDateWindow}). Une date refusee
 *       renvoie {@code dateValide=false} + message, et aucun espace.</li>
 *   <li><b>Date affichee</b> : le libelle francais de la date analysee est
 *       calcule ici et renvoye au client, qui l'affiche en tete des resultats.</li>
 *   <li><b>Aucun micro-creneau</b> : une periode libre plus courte que la duree
 *       minimale exploitable n'est ni calculee, ni affichee, ni reservable.</li>
 *   <li><b>Duree souhaitee</b> : si elle est fournie, seuls les espaces offrant
 *       un bloc libre <b>continu</b> assez long sont retournes.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private enum SearchMode { NOW, TODAY, CUSTOM }

    private final SpaceRepository spaceRepository;
    private final AvailabilityEngine engine;
    private final WorkingCalendarService calendar;

    /**
     * Parametres communs a tous les espaces d'une meme recherche : calcules une
     * seule fois puis partages, conformement au principe « une seule source de
     * verite, un seul calcul » (§2, §19).
     */
    private record SearchContext(
            DayContext ctx,
            AvailabilitySearchRequestDto request,
            Set<Long> wantedEquipmentIds,
            /** Debut de la journee analysee (jamais dans le passe pour aujourd'hui). */
            LocalTime dayStart,
            /** Fin de la journee analysee (= heure de fermeture). */
            LocalTime dayEnd,
            /** Debut de la fenetre analysee : journee entiere, ou plage demandee. */
            LocalTime windowStart,
            /** Fin de la fenetre analysee : journee entiere, ou plage demandee. */
            LocalTime windowEnd,
            /** Duree souhaitee demandee, ou null. */
            Integer dureeSouhaitee,
            /** Duree minimale retenue = max(duree exploitable, duree souhaitee). */
            long minDuree,
            /** Creneaux officiels actifs, charges une seule fois. */
            List<TimeSlot> creneaux) {
    }

    // ----- Points d'entree -----

    /** Recherche personnalisee (§6) : date obligatoire, heures et duree optionnelles. */
    public AvailabilitySearchResponseDto search(AvailabilitySearchRequestDto request) {
        return compute(request, SearchMode.CUSTOM, null);
    }

    public AvailabilitySearchResponseDto searchByBuilding(Long buildingId, LocalDate date,
                                                          LocalTime start, LocalTime end) {
        AvailabilitySearchRequestDto request = AvailabilitySearchRequestDto.builder()
                .date(date != null ? date : LocalDate.now())
                .heureDebut(start).heureFin(end).buildingId(buildingId).build();
        return compute(request, SearchMode.CUSTOM, null);
    }

    public AvailabilitySearchResponseDto searchByFloor(Long floorId, LocalDate date,
                                                       LocalTime start, LocalTime end) {
        AvailabilitySearchRequestDto request = AvailabilitySearchRequestDto.builder()
                .date(date != null ? date : LocalDate.now())
                .heureDebut(start).heureFin(end).floorId(floorId).build();
        return compute(request, SearchMode.CUSTOM, null);
    }

    /** Espaces libres pour le <b>reste</b> des periodes de la journee courante (§11). */
    public AvailabilitySearchResponseDto availableToday() {
        AvailabilitySearchRequestDto request = AvailabilitySearchRequestDto.builder()
                .date(LocalDate.now()).build();
        return compute(request, SearchMode.TODAY, null);
    }

    /** Espaces libres a l'instant present (§10). */
    public AvailabilitySearchResponseDto availableNow() {
        AvailabilitySearchRequestDto request = AvailabilitySearchRequestDto.builder()
                .date(LocalDate.now()).build();
        return compute(request, SearchMode.NOW, LocalTime.now().withSecond(0).withNano(0));
    }

    /**
     * Regles de saisie a appliquer au formulaire de recherche : bornes de date,
     * heure limite, horaires, durees proposees et creneaux officiels. Le client
     * borne son formulaire avec ces valeurs ; le serveur revalide de toute facon.
     */
    public SearchWindowDto searchWindow() {
        SearchDateWindow window = calendar.searchDateWindow();
        List<OfficialSlotDto> creneaux = calendar.creneauxActifs().stream()
                .filter(this::isUsableSlot)
                .map(this::toSlotDto)
                .toList();
        return SearchWindowDto.builder()
                .aujourdHui(window.aujourdHui())
                .dateMin(window.dateMin())
                .dateMax(window.dateMax())
                .libelleDateMin(DateLabels.libelleLong(window.dateMin()))
                .libelleDateMax(DateLabels.libelleLong(window.dateMax()))
                .dateParDefaut(window.dateMin())
                .heureLimiteRecherche(window.heureLimite())
                .limiteJourneeDepassee(window.limiteDepassee())
                .heureOuverture(calendar.openingTime())
                .heureFermeture(calendar.closingTime())
                .dureeMinimaleMinutes(engine.minSlotMinutes())
                .dureesProposees(calendar.dureesProposees())
                .creneaux(creneaux)
                .anneeActiveId(window.anneeActiveId())
                .anneeActiveLibelle(window.anneeActiveLibelle())
                .anneeActiveDebut(window.anneeActiveDebut())
                .anneeActiveFin(window.anneeActiveFin())
                .messageConfiguration(window.messageConfiguration())
                .exploitable(window.exploitable())
                .build();
    }

    // ----- Coeur commun aux trois modes -----

    private AvailabilitySearchResponseDto compute(AvailabilitySearchRequestDto request,
                                                  SearchMode mode, LocalTime nowRef) {
        LocalDate today = LocalDate.now();
        LocalTime now = (nowRef != null) ? nowRef : LocalTime.now().withSecond(0).withNano(0);
        LocalDate date = request.getDate() != null ? request.getDate() : today;

        SearchDateWindow window = calendar.searchDateWindow(today, now);
        DayContext ctx = calendar.describeDay(date);

        AvailabilitySearchResponseDto.AvailabilitySearchResponseDtoBuilder response =
                AvailabilitySearchResponseDto.builder()
                        .date(date)
                        .libelleDate(DateLabels.libelleLong(date))
                        .mode(mode.name())
                        .jourOuvrable(ctx.working())
                        .messageFermeture(ctx.closedReason())
                        .typeFermeture(ctx.closedType())
                        .dateValide(true)
                        .dateMin(window.dateMin())
                        .dateMax(window.dateMax())
                        .heureLimiteRecherche(window.heureLimite())
                        .limiteJourneeDepassee(window.limiteDepassee())
                        .messageConfiguration(window.messageConfiguration())
                        .heureOuverture(ctx.opening())
                        .heureFermeture(ctx.closing())
                        .dureeMinimaleMinutes(engine.minSlotMinutes())
                        .anneeUniversitaireId(ctx.academicYearId())
                        .anneeUniversitaireLibelle(ctx.academicYearLibelle())
                        .anneeUniversitaireResolue(ctx.academicYearResolved())
                        .messageAnneeUniversitaire(academicYearMessage(ctx))
                        .anneeActiveId(window.anneeActiveId())
                        .anneeActiveLibelle(window.anneeActiveLibelle())
                        .anneeActiveDebut(window.anneeActiveDebut())
                        .anneeActiveFin(window.anneeActiveFin());

        // § dates ouvertes a la recherche : date passee, journee en cours
        // terminee (heure limite) ou date hors annee universitaire active.
        String motifDate = window.motifRefus(date);
        if (motifDate != null) {
            return response.dateValide(false).messageDate(motifDate)
                    .espaces(List.of()).total(0).build();
        }

        // §3, §4 : jour non ouvrable → aucune recherche possible, message clair,
        // liste vide (jamais « tout libre »).
        if (ctx.isClosed()) {
            return response.espaces(List.of()).total(0).build();
        }

        // Mode NOW : la reference doit tomber dans les horaires d'ouverture (§10).
        if (mode == SearchMode.NOW && (now.isBefore(ctx.opening()) || !now.isBefore(ctx.closing()))) {
            response.messageFermeture(String.format(
                    "En dehors des horaires d'ouverture (%s - %s) : aucune disponibilité immédiate.",
                    ctx.opening().format(HM), ctx.closing().format(HM)));
            return response.espaces(List.of()).total(0).build();
        }

        // Fenetre de journee reellement analysee : pour la journee en cours elle
        // demarre a l'heure courante, une periode deja ecoulee n'etant ni
        // affichable ni reservable (§ ambiguite de date).
        boolean estAujourdHui = date.equals(today);
        LocalTime jourDebut = estAujourdHui ? maxTime(ctx.opening(), now) : ctx.opening();
        LocalTime jourFin = ctx.closing();
        response.fenetreJourDebut(jourDebut).fenetreJourFin(jourFin);

        if (!jourDebut.isBefore(jourFin)) {
            response.messageValidation(String.format(
                    "La journée est terminée : les espaces ferment à %s. La recherche reprend au %s.",
                    ctx.closing().format(HM), DateLabels.libelleLong(window.dateMin())));
            return response.espaces(List.of()).total(0).build();
        }

        // Heures explicites (mode CUSTOM) : coherence, bornes d'ouverture, duree
        // exploitable, et creneau non deja ecoule pour la journee en cours.
        LocalTime reqDebut = request.getHeureDebut();
        LocalTime reqFin = request.getHeureFin();
        boolean explicitHours = mode == SearchMode.CUSTOM && request.hasExplicitHours();
        if (explicitHours) {
            response.heureDebutRecherche(reqDebut).heureFinRecherche(reqFin);
            String erreur = validateExplicitHours(ctx, reqDebut, reqFin, estAujourdHui, now);
            if (erreur != null) {
                return response.messageValidation(erreur).espaces(List.of()).total(0).build();
            }
        }

        // § duree souhaitee : optionnelle, elle borne la recherche par le bas.
        Integer dureeSouhaitee = request.hasDuration() ? request.getDureeMinutes() : null;
        LocalTime windowStart = explicitHours ? reqDebut : jourDebut;
        LocalTime windowEnd = explicitHours ? reqFin : jourFin;

        // Une seule borne horaire fournie : elle restreint la journee analysee au
        // lieu d'etre ignoree silencieusement (l'autre borne reste celle du jour).
        if (!explicitHours && mode == SearchMode.CUSTOM) {
            if (reqDebut != null) {
                windowStart = maxTime(windowStart, reqDebut);
                response.heureDebutRecherche(reqDebut);
            }
            if (reqFin != null) {
                windowEnd = minTime(windowEnd, reqFin);
                response.heureFinRecherche(reqFin);
            }
            if (!windowStart.isBefore(windowEnd)) {
                return response.messageValidation(String.format(
                        "La plage demandée est vide : les espaces sont ouverts de %s à %s.",
                        jourDebut.format(HM), jourFin.format(HM)))
                        .espaces(List.of()).total(0).build();
            }
        }

        if (dureeSouhaitee != null) {
            response.dureeSouhaiteeMinutes(dureeSouhaitee);
            long fenetreMinutes = Duration.between(windowStart, windowEnd).toMinutes();
            if (dureeSouhaitee > fenetreMinutes) {
                return response.messageValidation(String.format(
                        "La durée souhaitée (%s) ne tient pas dans la plage analysée (%s → %s, soit %s).",
                        DateLabels.duree(dureeSouhaitee), windowStart.format(HM),
                        windowEnd.format(HM), DateLabels.duree(fenetreMinutes)))
                        .espaces(List.of()).total(0).build();
            }
        }

        // Avec des heures explicites ET une duree, on cherche un bloc libre
        // continu de cette duree a l'interieur de la plage demandee plutot que
        // d'exiger la plage entiere.
        boolean exactSlotMode = explicitHours && dureeSouhaitee == null;

        SearchContext sc = new SearchContext(ctx, request,
                (request.getEquipmentIds() == null) ? Set.of() : new HashSet<>(request.getEquipmentIds()),
                jourDebut, jourFin, windowStart, windowEnd, dureeSouhaitee,
                Math.max(engine.minSlotMinutes(), dureeSouhaitee == null ? 0 : dureeSouhaitee),
                calendar.creneauxActifs());

        List<AvailableSpaceResponseDto> results = new ArrayList<>();
        for (Space space : spaceRepository.findByActif(true)) {
            if (!passesStructuralFilters(space, request)
                    || !passesCriteriaFilters(space, request, sc.wantedEquipmentIds())) {
                continue;
            }
            SpaceOccupancy occ = engine.computeOccupancy(space, date);
            AvailableSpaceResponseDto dto = switch (mode) {
                case NOW -> buildNow(space, occ, now, sc);
                case TODAY -> buildPeriods(space, occ, sc);
                case CUSTOM -> exactSlotMode
                        ? buildCustomHours(space, occ, reqDebut, reqFin, sc)
                        : buildPeriods(space, occ, sc);
            };
            if (dto != null) {
                results.add(dto);
            }
        }

        results.sort(buildComparator(request));
        return response.espaces(results).total(results.size()).build();
    }

    /**
     * Controles de saisie d'une plage horaire explicite. Retourne le message a
     * afficher, ou {@code null} si la plage est acceptable.
     */
    private String validateExplicitHours(DayContext ctx, LocalTime debut, LocalTime fin,
                                         boolean estAujourdHui, LocalTime now) {
        if (!fin.isAfter(debut)) {
            return "L'heure de fin doit être postérieure à l'heure de début.";
        }
        if (debut.isBefore(ctx.opening()) || fin.isAfter(ctx.closing())) {
            return String.format("Les réservations sont possibles uniquement entre %s et %s.",
                    ctx.opening().format(HM), ctx.closing().format(HM));
        }
        if (!engine.isLongEnough(debut, fin)) {
            return engine.tooShortMessage();
        }
        if (estAujourdHui && debut.isBefore(now)) {
            return String.format(
                    "Ce créneau est déjà commencé : pour aujourd'hui, choisissez une heure de début à partir de %s.",
                    now.format(HM));
        }
        return null;
    }

    // ----- Construction des DTO par mode -----

    /**
     * §10 : disponible maintenant → de l'instant present a la prochaine
     * occupation. Le bloc doit atteindre la duree minimale exploitable, sinon
     * l'espace n'est pas propose (§ micro-creneaux).
     */
    private AvailableSpaceResponseDto buildNow(Space space, SpaceOccupancy occ,
                                               LocalTime now, SearchContext sc) {
        List<FreeInterval> free = engine.freePeriods(sc.windowStart(), sc.windowEnd(),
                occ.blocking(), sc.minDuree());
        Optional<FreeInterval> containing = free.stream()
                .filter(p -> !now.isBefore(p.debut()) && now.isBefore(p.fin()))
                .findFirst();
        boolean exact = isExactQueryMatch(space, sc.request());
        if (containing.isEmpty()) {
            return exact ? unavailableDto(space, occ, sc, occupiedReason(occ, now)) : null;
        }

        FreeInterval fenetre = containing.get();
        LocalTime windowEnd = fenetre.fin();
        OccupancyInterval next = engine.nextOccupation(occ, now);
        boolean pending = next != null && next.source() == OccupancySource.RESERVATION_EN_ATTENTE
                && next.debut().isBefore(windowEnd);
        long restant = Duration.between(now, windowEnd).toMinutes();

        String affichage = "Disponible maintenant — de " + now.format(HM)
                + " à " + windowEnd.format(HM);
        return baseDto(space, sc.wantedEquipmentIds())
                .disponible(!pending)
                .statut(pending ? "EN_ATTENTE" : "DISPONIBLE")
                .demandeEnAttente(pending)
                .motifIndisponibilite(pending
                        ? "Une demande de réservation est en attente sur ce créneau." : null)
                .periodesLibres(List.of(toFreeDto(fenetre)))
                .creneauxProposes(proposedSlots(List.of(fenetre), sc))
                .plusLongueDureeMinutes(fenetre.dureeMinutes())
                .fenetreDebut(now)
                .fenetreFin(windowEnd)
                .affichageDisponibilite(affichage)
                .prochaineOccupation(next != null ? next.debut() : null)
                .prochaineOccupationSource(next != null ? next.sourceLabel() : null)
                .tempsRestantMinutes(restant)
                .build();
    }

    /**
     * §11 / §6 (sans heures) / § duree souhaitee : liste des periodes libres
     * <b>continues</b> de la fenetre analysee, filtrees par la duree retenue.
     * Deux periodes separees par une occupation ne sont jamais additionnees.
     */
    private AvailableSpaceResponseDto buildPeriods(Space space, SpaceOccupancy occ, SearchContext sc) {
        List<FreeInterval> free = engine.freePeriods(sc.windowStart(), sc.windowEnd(),
                occ.blocking(), sc.minDuree());
        boolean exact = isExactQueryMatch(space, sc.request());
        if (free.isEmpty()) {
            return exact ? unavailableDto(space, occ, sc, noPeriodReason(sc)) : null;
        }

        List<FreePeriodDto> periodes = free.stream().map(this::toFreeDto).toList();
        boolean anyPending = hasPendingInWindow(occ, sc);
        FreeInterval first = free.get(0);
        OccupancyInterval next = engine.nextOccupation(occ, sc.windowStart());

        return baseDto(space, sc.wantedEquipmentIds())
                .disponible(true)
                .statut("DISPONIBLE")
                .demandeEnAttente(anyPending)
                .motifIndisponibilite(anyPending
                        ? "Une ou plusieurs demandes sont en attente sur d'autres créneaux." : null)
                .periodesLibres(periodes)
                .creneauxProposes(proposedSlots(free, sc))
                .plusLongueDureeMinutes(engine.longestFreeMinutes(free))
                .fenetreDebut(first.debut())
                .fenetreFin(first.fin())
                .affichageDisponibilite(periodsLabel(periodes, sc))
                .prochaineOccupation(next != null ? next.debut() : null)
                .prochaineOccupationSource(next != null ? next.sourceLabel() : null)
                .tempsRestantMinutes(null)
                .build();
    }

    /**
     * §6 / §9 : creneau explicite sans duree souhaitee → verdict du moteur pour
     * exactement {@code [debut, fin)}, fenetre du formulaire bornee au reel.
     */
    private AvailableSpaceResponseDto buildCustomHours(Space space, SpaceOccupancy occ,
                                                       LocalTime debut, LocalTime fin,
                                                       SearchContext sc) {
        SlotVerdict verdict = engine.evaluateSlot(sc.ctx(), occ, debut, fin);
        boolean exact = isExactQueryMatch(space, sc.request());
        // Periodes libres de la journee analysee : servent a proposer une
        // alternative et a borner le formulaire de reservation (§9).
        List<FreeInterval> free = engine.freePeriods(sc.dayStart(), sc.dayEnd(),
                occ.blocking(), engine.minSlotMinutes());

        Optional<FreeInterval> containing = free.stream()
                .filter(p -> p.debut().isBefore(fin) && p.fin().isAfter(debut))
                .findFirst();
        LocalTime fenDebut = containing.map(p -> maxTime(p.debut(), debut)).orElse(debut);
        LocalTime fenFin = containing.map(p -> minTime(p.fin(), fin)).orElse(fin);

        OccupancyInterval next = engine.nextOccupation(occ, fin);
        Long restant = next != null ? Duration.between(fin, next.debut()).toMinutes() : null;

        return switch (verdict.status()) {
            case FREE -> baseDto(space, sc.wantedEquipmentIds())
                    .disponible(true).statut("DISPONIBLE").demandeEnAttente(false)
                    .periodesLibres(containing.map(p -> List.of(toFreeDto(p)))
                            .orElseGet(() -> free.stream().map(this::toFreeDto).toList()))
                    .creneauxProposes(proposedSlots(
                            containing.map(List::of).orElse(free), sc))
                    .plusLongueDureeMinutes(containing.map(FreeInterval::dureeMinutes)
                            .orElseGet(() -> engine.longestFreeMinutes(free)))
                    .fenetreDebut(containing.map(FreeInterval::debut).orElse(debut))
                    .fenetreFin(containing.map(FreeInterval::fin).orElse(fin))
                    .affichageDisponibilite("Disponible pour la période recherchée du "
                            + debut.format(HM) + " au " + fin.format(HM))
                    .prochaineOccupation(next != null ? next.debut() : null)
                    .prochaineOccupationSource(next != null ? next.sourceLabel() : null)
                    .tempsRestantMinutes(restant)
                    .build();
            case PENDING_OVERLAP -> baseDto(space, sc.wantedEquipmentIds())
                    .disponible(false).statut("EN_ATTENTE").demandeEnAttente(true)
                    .motifIndisponibilite(verdict.reason())
                    .periodesLibres(free.stream().map(this::toFreeDto).toList())
                    .creneauxProposes(proposedSlots(free, sc))
                    .plusLongueDureeMinutes(engine.longestFreeMinutes(free))
                    .fenetreDebut(fenDebut).fenetreFin(fenFin)
                    .affichageDisponibilite("Demande en attente sur ce créneau")
                    .prochaineOccupation(next != null ? next.debut() : null)
                    .prochaineOccupationSource(next != null ? next.sourceLabel() : null)
                    .build();
            // BLOCKED / OUT_OF_HOURS / NON_WORKING_DAY : exclu de la liste sauf
            // recherche exacte, ou l'on explique pourquoi la salle est prise (§16).
            default -> exact ? unavailableDto(space, occ, sc, verdict.reason()) : null;
        };
    }

    // ----- Assemblage DTO -----

    private AvailableSpaceResponseDto.AvailableSpaceResponseDtoBuilder baseDto(
            Space space, Set<Long> wantedEquipmentIds) {
        List<AvailableEquipmentDto> equipments = space.getEquipments().stream()
                .map(this::toEquipmentDto)
                .sorted(Comparator.comparing(AvailableEquipmentDto::getNom,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        long matching = wantedEquipmentIds.isEmpty() ? 0
                : space.getEquipments().stream()
                        .filter(e -> wantedEquipmentIds.contains(e.getId())).count();

        return AvailableSpaceResponseDto.builder()
                .spaceId(space.getId())
                .nom(space.getNom())
                .code(space.getCode())
                .type(space.getType())
                .capacite(space.getCapacite())
                .buildingId(space.getFloor().getBuilding().getId())
                .buildingNom(space.getFloor().getBuilding().getNom())
                .buildingCode(space.getFloor().getBuilding().getCode())
                .floorId(space.getFloor().getId())
                .floorNom(space.getFloor().getNom())
                .floorNumero(space.getFloor().getNumero())
                .equipments(equipments)
                .nombreEquipements(equipments.size())
                .equipementsCorrespondants((int) matching);
    }

    /** DTO d'un espace indisponible (recherche exacte) : explique le blocage (§16). */
    private AvailableSpaceResponseDto unavailableDto(Space space, SpaceOccupancy occ,
                                                    SearchContext sc, String reason) {
        List<FreeInterval> free = engine.freePeriods(sc.dayStart(), sc.dayEnd(),
                occ.blocking(), engine.minSlotMinutes());
        return baseDto(space, sc.wantedEquipmentIds())
                .disponible(false)
                .statut("INDISPONIBLE")
                .demandeEnAttente(hasPendingInWindow(occ, sc))
                .motifIndisponibilite(reason)
                .periodesLibres(free.stream().map(this::toFreeDto).toList())
                .creneauxProposes(proposedSlots(free, sc))
                .plusLongueDureeMinutes(engine.longestFreeMinutes(free))
                .affichageDisponibilite(reason)
                .build();
    }

    private FreePeriodDto toFreeDto(FreeInterval interval) {
        return FreePeriodDto.builder()
                .heureDebut(interval.debut())
                .heureFin(interval.fin())
                .label(interval.debut().format(HM) + " → " + interval.fin().format(HM))
                .dureeMinutes(interval.dureeMinutes())
                .build();
    }

    /**
     * Creneaux officiels entierement contenus dans une des periodes libres
     * retenues : ils permettent de reserver en un clic, sans saisie horaire, en
     * reutilisant les memes creneaux que les emplois du temps et les occupations
     * supplementaires.
     */
    private List<OfficialSlotDto> proposedSlots(List<FreeInterval> free, SearchContext sc) {
        if (free == null || free.isEmpty() || sc.creneaux().isEmpty()) {
            return List.of();
        }
        List<OfficialSlotDto> slots = new ArrayList<>();
        for (TimeSlot slot : sc.creneaux()) {
            if (!isUsableSlot(slot)) {
                continue;
            }
            LocalTime d = slot.getHeureDebut();
            LocalTime f = slot.getHeureFin();
            if (free.stream().anyMatch(p -> p.contains(d, f))) {
                slots.add(toSlotDto(slot));
            }
        }
        return slots;
    }

    /** Un creneau officiel n'est proposable que s'il est coherent et exploitable. */
    private boolean isUsableSlot(TimeSlot slot) {
        LocalTime d = slot.getHeureDebut();
        LocalTime f = slot.getHeureFin();
        return d != null && f != null && f.isAfter(d) && engine.isLongEnough(d, f);
    }

    private OfficialSlotDto toSlotDto(TimeSlot slot) {
        return OfficialSlotDto.builder()
                .id(slot.getId())
                .nom(slot.getNom())
                .heureDebut(slot.getHeureDebut())
                .heureFin(slot.getHeureFin())
                .label(slot.getHeureDebut().format(HM) + " → " + slot.getHeureFin().format(HM))
                .dureeMinutes(Duration.between(slot.getHeureDebut(), slot.getHeureFin()).toMinutes())
                .build();
    }

    // ----- Libelles et messages -----

    /** Texte d'affichage d'une liste de periodes libres, selon la duree demandee. */
    private String periodsLabel(List<FreePeriodDto> periodes, SearchContext sc) {
        if (periodes.size() == 1) {
            return "Disponible " + periodes.get(0).getLabel();
        }
        if (sc.dureeSouhaitee() != null) {
            return periodes.size() + " périodes de "
                    + DateLabels.duree(sc.dureeSouhaitee()) + " ou plus";
        }
        return periodes.size() + " périodes libres";
    }

    /** Motif d'absence de periode exploitable, selon la duree demandee. */
    private String noPeriodReason(SearchContext sc) {
        if (sc.dureeSouhaitee() != null) {
            return "Aucune période libre continue de " + DateLabels.duree(sc.dureeSouhaitee())
                    + " sur la plage " + sc.windowStart().format(HM) + " → "
                    + sc.windowEnd().format(HM) + ".";
        }
        return "Aucune période libre d'au moins "
                + DateLabels.duree(engine.minSlotMinutes()) + " sur la plage "
                + sc.windowStart().format(HM) + " → " + sc.windowEnd().format(HM) + ".";
    }

    /** Vrai si une demande en attente chevauche la fenetre analysee (§13). */
    private boolean hasPendingInWindow(SpaceOccupancy occ, SearchContext sc) {
        return occ.pending().stream()
                .anyMatch(i -> i.debut().isBefore(sc.windowEnd())
                        && i.fin().isAfter(sc.windowStart()));
    }

    private String occupiedReason(SpaceOccupancy occ, LocalTime instant) {
        return occ.blocking().stream()
                .filter(i -> i.overlaps(instant, instant.plusMinutes(1)))
                .findFirst()
                .map(i -> "Occupé actuellement : " + i.sourceLabel() + ".")
                .orElse("Aucune disponibilité immédiate.");
    }

    private String academicYearMessage(DayContext ctx) {
        if (ctx.academicYearResolved()) {
            return null;
        }
        return "Aucune année universitaire n'est définie pour le "
                + ctx.date().format(FR_DATE)
                + ". Les emplois du temps, occupations supplémentaires et "
                + "réservations de cette date sont "
                + "tout de même vérifiés.";
    }

    // ----- Filtres et tri (§18) -----

    private boolean passesStructuralFilters(Space space, AvailabilitySearchRequestDto request) {
        boolean base = space.isActif()
                && space.getFloor() != null
                && space.getFloor().isActif()
                && space.getFloor().getBuilding() != null
                && space.getFloor().getBuilding().isActif();
        if (!base) return false;
        if (!space.isReserve()) return true;
        return isExactQueryMatch(space, request);
    }

    private boolean isExactQueryMatch(Space space, AvailabilitySearchRequestDto request) {
        if (request == null || request.getExactSpaceQuery() == null) return false;
        String q = request.getExactSpaceQuery().trim().toLowerCase();
        if (q.isEmpty()) return false;
        String code = space.getCode() == null ? "" : space.getCode().trim().toLowerCase();
        String nom = space.getNom() == null ? "" : space.getNom().trim().toLowerCase();
        return q.equals(code) || q.equals(nom);
    }

    private boolean passesCriteriaFilters(Space space, AvailabilitySearchRequestDto request,
                                          Set<Long> wantedEquipmentIds) {
        if (request.getBuildingId() != null
                && !request.getBuildingId().equals(space.getFloor().getBuilding().getId())) {
            return false;
        }
        if (request.getFloorId() != null
                && !request.getFloorId().equals(space.getFloor().getId())) {
            return false;
        }
        if (request.getType() != null && request.getType() != space.getType()) {
            return false;
        }
        if (request.getCapaciteMin() != null
                && (space.getCapacite() == null || space.getCapacite() < request.getCapaciteMin())) {
            return false;
        }
        if (!wantedEquipmentIds.isEmpty()) {
            return spaceEquipmentIds(space).containsAll(wantedEquipmentIds);
        }
        return true;
    }

    /**
     * Tri metier (§18) : disponibles d'abord, puis batiment/etage demandes, puis
     * <b>plus petite capacite suffisante</b> (pour ne pas mobiliser un amphi
     * pour une petite reservation), puis equipements correspondants, puis code.
     */
    private Comparator<AvailableSpaceResponseDto> buildComparator(AvailabilitySearchRequestDto request) {
        Comparator<AvailableSpaceResponseDto> comparator =
                Comparator.comparingInt((AvailableSpaceResponseDto dto) -> dto.isDisponible() ? 0 : 1);

        if (request.getBuildingId() != null) {
            comparator = comparator.thenComparingInt(
                    dto -> request.getBuildingId().equals(dto.getBuildingId()) ? 0 : 1);
        }
        if (request.getFloorId() != null) {
            comparator = comparator.thenComparingInt(
                    dto -> request.getFloorId().equals(dto.getFloorId()) ? 0 : 1);
        }
        comparator = comparator.thenComparingInt(dto ->
                dto.getCapacite() == null ? Integer.MAX_VALUE : dto.getCapacite());
        return comparator
                .thenComparing(Comparator.comparingInt(
                        AvailableSpaceResponseDto::getEquipementsCorrespondants).reversed())
                .thenComparing(dto -> dto.getCode() == null ? "" : dto.getCode());
    }

    // ----- Helpers -----

    private AvailableEquipmentDto toEquipmentDto(Equipment equipment) {
        return AvailableEquipmentDto.builder()
                .id(equipment.getId())
                .nom(equipment.getNom())
                .code(equipment.getCode())
                .build();
    }

    private Set<Long> spaceEquipmentIds(Space space) {
        Set<Long> ids = new HashSet<>();
        for (Equipment equipment : space.getEquipments()) {
            ids.add(equipment.getId());
        }
        return ids;
    }

    private LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalTime minTime(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
