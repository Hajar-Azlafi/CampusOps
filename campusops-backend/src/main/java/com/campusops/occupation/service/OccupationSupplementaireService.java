package com.campusops.occupation.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.availability.engine.AvailabilityEngine;
import com.campusops.availability.engine.DayContext;
import com.campusops.availability.engine.OccupancyInterval;
import com.campusops.availability.engine.SlotVerdict;
import com.campusops.availability.engine.SpaceOccupancy;
import com.campusops.availability.engine.WorkingCalendarService;
import com.campusops.enums.OccupationCategorie;
import com.campusops.enums.OccupationType;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.occupation.dto.OccupationRequestDto;
import com.campusops.occupation.dto.OccupationResponseDto;
import com.campusops.occupation.entity.OccupationSupplementaire;
import com.campusops.occupation.mapper.OccupationMapper;
import com.campusops.occupation.repository.OccupationSupplementaireRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gestion des <b>occupations supplémentaires génériques</b> : soutenances
 * (onglet « Planning soutenances ») et occupations ponctuelles diverses (onglet
 * « Autre » : événements, réunions, activités de club, conférences...).
 *
 * <p>Les <b>examens</b> ne passent <em>pas</em> par ce service : ils gardent leur
 * propre {@code ExamenService} (contexte pédagogique complet). En revanche, tous
 * partagent la même table et la même notion d'occupation, donc le moteur central
 * de disponibilité les voit tous — c'est ce qui garantit qu'une salle occupée par
 * une soutenance ou un événement n'est jamais proposée comme libre.</p>
 *
 * <p><b>Détection de conflit unifiée</b> : la validation d'un créneau (jour
 * ouvrable, bornes d'ouverture, durée minimale, absence d'occupation bloquante)
 * délègue intégralement au {@link AvailabilityEngine} et au
 * {@link WorkingCalendarService} — exactement la même logique que la réservation,
 * verrou pessimiste sur l'espace compris (§21).</p>
 *
 * <p><b>Périmètre (§12)</b> : une soutenance exige une filière, contrôlée par
 * {@link AccessScopeService}. Une occupation « autre » peut être sans filière :
 * elle est alors réservée à l'administrateur.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OccupationSupplementaireService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private final OccupationSupplementaireRepository occupationRepository;
    private final OccupationMapper occupationMapper;
    private final SpaceRepository spaceRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AccessScopeService accessScope;
    private final AvailabilityEngine availabilityEngine;
    private final WorkingCalendarService workingCalendar;

    // ----- Commandes -----

    public OccupationResponseDto create(OccupationRequestDto request) {
        OccupationContext ctx = resolveContext(request);
        assertScope(ctx);
        validateSlotOrThrow(ctx, null);

        OccupationSupplementaire entity = OccupationSupplementaire.builder()
                .type(ctx.type)
                .intitule(trimToNull(request.getIntitule()))
                .date(ctx.date)
                .heureDebut(ctx.heureDebut)
                .heureFin(ctx.heureFin)
                .space(ctx.space)
                .program(ctx.program)
                .promotion(ctx.promotion)
                .group(ctx.group)
                .academicYear(ctx.academicYear)
                .responsable(trimToNull(request.getResponsable()))
                .commentaire(trimToNull(request.getCommentaire()))
                .actif(true)
                .build();

        return occupationMapper.toResponseDto(occupationRepository.save(entity));
    }

    public OccupationResponseDto update(Long id, OccupationRequestDto request) {
        OccupationSupplementaire existing = findAccessibleGenericOrThrow(id);
        OccupationContext ctx = resolveContext(request);
        assertScope(ctx);
        validateSlotOrThrow(ctx, existing.getId());

        existing.setType(ctx.type);
        existing.setIntitule(trimToNull(request.getIntitule()));
        existing.setDate(ctx.date);
        existing.setHeureDebut(ctx.heureDebut);
        existing.setHeureFin(ctx.heureFin);
        existing.setSpace(ctx.space);
        existing.setProgram(ctx.program);
        existing.setPromotion(ctx.promotion);
        existing.setGroup(ctx.group);
        existing.setAcademicYear(ctx.academicYear);
        existing.setResponsable(trimToNull(request.getResponsable()));
        existing.setCommentaire(trimToNull(request.getCommentaire()));

        return occupationMapper.toResponseDto(occupationRepository.save(existing));
    }

    public void delete(Long id) {
        OccupationSupplementaire existing = findAccessibleGenericOrThrow(id);
        occupationRepository.delete(existing);
    }

    // ----- Consultations -----

    @Transactional(readOnly = true)
    public OccupationResponseDto getById(Long id) {
        return occupationMapper.toResponseDto(findAccessibleGenericOrThrow(id));
    }

    /**
     * Liste les occupations d'une <b>catégorie</b> (SOUTENANCE ou AUTRE), filtrée
     * par le périmètre de l'utilisateur. La catégorie {@code EXAMEN} est refusée :
     * les examens ont leur propre API.
     */
    @Transactional(readOnly = true)
    public List<OccupationResponseDto> listByCategorie(OccupationCategorie categorie,
                                                       Long programId, LocalDate date) {
        if (categorie == null || categorie == OccupationCategorie.EXAMEN) {
            throw new BadRequestException(
                    "Seules les catégories « soutenance » et « autre » sont gérées ici.");
        }
        List<OccupationType> types = OccupationType.ofCategorie(categorie);

        List<OccupationSupplementaire> occupations;
        if (accessScope.isAdmin()) {
            occupations = occupationRepository.findByTypeIn(types);
        } else {
            // RP : seulement ses filières. Les occupations sans filière (« autre »
            // non rattachée) restent invisibles — elles relèvent de l'ADMIN.
            List<Long> myProgramIds = accessScope.myProgramIds();
            occupations = (myProgramIds == null || myProgramIds.isEmpty())
                    ? List.of()
                    : occupationRepository.findByTypeInAndProgramIdIn(types, myProgramIds);
        }

        return occupations.stream()
                .filter(o -> programId == null
                        || (o.getProgram() != null && o.getProgram().getId().equals(programId)))
                .filter(o -> date == null || date.equals(o.getDate()))
                .sorted(Comparator.comparing(OccupationSupplementaire::getDate)
                        .thenComparing(OccupationSupplementaire::getHeureDebut))
                .map(occupationMapper::toResponseDto)
                .toList();
    }

    // ----- Import (validation « à blanc » pour la prévisualisation 2 phases) -----

    /**
     * Validation <b>à blanc</b> d'une occupation pour l'import en deux phases :
     * résout le contexte, applique le périmètre et détecte les conflits <b>sans
     * rien enregistrer</b>, en RENVOYANT les messages d'erreur. Source unique des
     * règles, réutilisée par le service d'import pour ne pas les faire diverger.
     */
    @Transactional(readOnly = true)
    public List<String> dryRunImportErrors(OccupationRequestDto request) {
        List<String> errors = new ArrayList<>();
        OccupationContext ctx;
        try {
            ctx = resolveContext(request);
        } catch (BadRequestException | ResourceNotFoundException e) {
            errors.add(e.getMessage());
            return errors;
        }
        try {
            assertScope(ctx);
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
            return errors;
        }
        try {
            // Sans verrou : une prévisualisation ne doit ni écrire ni immobiliser
            // les salles du fichier pendant toute son analyse.
            validateSlotOrThrow(ctx, null, false);
        } catch (BadRequestException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    // ----- Sécurité de périmètre (§12) -----

    /**
     * Une soutenance exige une filière et l'accès à celle-ci. Une occupation
     * « autre » avec filière suit la même règle ; sans filière, elle est réservée
     * à l'administrateur.
     */
    private void assertScope(OccupationContext ctx) {
        if (ctx.category == OccupationCategorie.SOUTENANCE) {
            if (ctx.program == null) {
                throw new BadRequestException(
                        "Une soutenance doit être rattachée à une filière.");
            }
            accessScope.assertProgramAccessible(ctx.program.getId());
            return;
        }
        // Catégorie AUTRE.
        if (ctx.program != null) {
            accessScope.assertProgramAccessible(ctx.program.getId());
        } else {
            accessScope.requireAdmin();
        }
    }

    // ----- Validation du créneau (même moteur que la réservation) -----

    /**
     * Vérifie que le créneau {@code [heureDebut, heureFin)} est réservable :
     * durée minimale exploitable, jour ouvrable, bornes d'ouverture et absence
     * d'occupation bloquante (emploi du temps, autres occupations, réservations
     * acceptées). Verrou pessimiste sur l'espace pour sérialiser les écritures
     * concurrentes (§21).
     */
    private void validateSlotOrThrow(OccupationContext ctx, Long excludeOccupationId) {
        validateSlotOrThrow(ctx, excludeOccupationId, true);
    }

    /**
     * @param lockSpace {@code true} sur le chemin d'écriture (création / mise à
     *                  jour) ; {@code false} pour une validation « à blanc »
     *                  (prévisualisation d'import), qui applique exactement les
     *                  mêmes règles mais sans poser de verrou.
     */
    private void validateSlotOrThrow(OccupationContext ctx, Long excludeOccupationId,
                                     boolean lockSpace) {
        if (!availabilityEngine.isLongEnough(ctx.heureDebut, ctx.heureFin)) {
            throw new BadRequestException(availabilityEngine.tooShortMessage());
        }
        // Verrou pessimiste : la salle est figée le temps de la revalidation +
        // écriture, comme pour une réservation (SELECT ... FOR UPDATE).
        Space target = lockSpace
                ? spaceRepository.lockById(ctx.space.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Espace introuvable avec l'id " + ctx.space.getId()))
                : ctx.space;

        // §23 : une salle inactive (ou dont l'etage/batiment est desactive) n'est
        // pas selectionnable pour une nouvelle occupation (soutenance/autre), ni en
        // saisie directe ni a l'import. Message nommant la cause exacte. Choke point
        // commun aux chemins d'ecriture ET a la previsualisation d'import (dryRun).
        ReferentialStatus.requireUsable(target);

        DayContext day = workingCalendar.describeDay(ctx.date);
        SpaceOccupancy occ = availabilityEngine.computeOccupancyExcludingOccupation(
                target, ctx.date, excludeOccupationId);
        SlotVerdict verdict = availabilityEngine.evaluateSlot(
                day, occ, ctx.heureDebut, ctx.heureFin);

        // Une occupation supplémentaire n'a pas de logique de priorité : toute
        // situation non libre (y compris un chevauchement avec une demande en
        // attente) est refusée — la salle ne doit pas être doublement engagée.
        if (verdict.status() != SlotVerdict.Status.FREE) {
            throw new BadRequestException(conflictMessage(verdict));
        }
    }

    private String conflictMessage(SlotVerdict verdict) {
        if (verdict.status() == SlotVerdict.Status.PENDING_OVERLAP && verdict.blocker() != null) {
            OccupancyInterval i = verdict.blocker();
            return String.format(
                    "Une demande de réservation est en attente sur ce créneau (%s à %s) : "
                            + "l'espace ne peut pas être engagé pour cette occupation.",
                    i.debut().format(HOUR), i.fin().format(HOUR));
        }
        return verdict.reason();
    }

    // ----- Résolution / cohérence du contexte -----

    private OccupationContext resolveContext(OccupationRequestDto request) {
        OccupationContext ctx = new OccupationContext();

        ctx.type = request.getType();
        if (ctx.type == null) {
            throw new BadRequestException("Le type d'occupation est obligatoire.");
        }
        ctx.category = ctx.type.getCategorie();
        if (ctx.category == OccupationCategorie.EXAMEN) {
            throw new BadRequestException(
                    "Les examens se gèrent depuis l'onglet « Planning examens ».");
        }

        ctx.date = request.getDate();
        ctx.heureDebut = request.getHeureDebut();
        ctx.heureFin = request.getHeureFin();
        if (ctx.date == null || ctx.heureDebut == null || ctx.heureFin == null) {
            throw new BadRequestException(
                    "La date, l'heure de début et l'heure de fin sont obligatoires.");
        }
        if (!ctx.heureFin.isAfter(ctx.heureDebut)) {
            throw new BadRequestException(
                    "L'heure de fin doit être postérieure à l'heure de début.");
        }

        ctx.space = findSpaceOrThrow(request.getSpaceId());

        if (request.getProgramId() != null) {
            ctx.program = findProgramOrThrow(request.getProgramId());
        }
        if (request.getPromotionId() != null) {
            ctx.promotion = findPromotionOrThrow(request.getPromotionId());
            // Cohérence : si une filière est aussi fournie, la promotion doit lui
            // appartenir ; sinon on dérive la filière de la promotion.
            if (ctx.program != null
                    && !ctx.promotion.getProgram().getId().equals(ctx.program.getId())) {
                throw new BadRequestException(
                        "La promotion sélectionnée n'appartient pas à la filière indiquée.");
            }
            if (ctx.program == null) {
                ctx.program = ctx.promotion.getProgram();
            }
        }
        if (request.getGroupId() != null) {
            ctx.group = findGroupOrThrow(request.getGroupId());
            if (ctx.promotion == null) {
                ctx.promotion = ctx.group.getPromotion();
                if (ctx.program == null && ctx.promotion != null) {
                    ctx.program = ctx.promotion.getProgram();
                }
            } else if (!ctx.group.getPromotion().getId().equals(ctx.promotion.getId())) {
                throw new BadRequestException(
                        "Le groupe sélectionné n'appartient pas à la promotion indiquée.");
            }
        }

        // Année de rattachement : celle de la promotion si connue, sinon l'année
        // couvrant la date (active ou non), pour le filtrage temporel des listes.
        if (ctx.promotion != null && ctx.promotion.getAcademicYear() != null) {
            ctx.academicYear = ctx.promotion.getAcademicYear();
        } else {
            ctx.academicYear = workingCalendar.resolveAcademicYear(ctx.date)
                    .orElseGet(() -> academicYearRepository.findFirstByActifTrue().orElse(null));
        }

        return ctx;
    }

    /**
     * Charge une occupation <b>générique</b> (jamais un examen) et vérifie le
     * périmètre. Un examen atteint par cette API renvoie 404 : il se gère via son
     * propre module.
     */
    private OccupationSupplementaire findAccessibleGenericOrThrow(Long id) {
        OccupationSupplementaire occupation = occupationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Occupation introuvable avec l'id " + id));
        if (occupation.getCategorie() == OccupationCategorie.EXAMEN) {
            throw new ResourceNotFoundException("Occupation introuvable avec l'id " + id);
        }
        // Périmètre : filière requise pour les soutenances ; « autre » sans filière
        // reste réservée à l'ADMIN.
        if (occupation.getProgram() != null) {
            accessScope.assertProgramAccessible(occupation.getProgram().getId());
        } else {
            accessScope.requireAdmin();
        }
        return occupation;
    }

    // ----- Lookups -----

    private Space findSpaceOrThrow(Long id) {
        if (id == null) {
            throw new BadRequestException("L'espace est obligatoire.");
        }
        return spaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    private Program findProgramOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + id));
    }

    private Promotion findPromotionOrThrow(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + id));
    }

    private Group findGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Groupe introuvable avec l'id " + id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Contexte résolu et validé d'une occupation générique. */
    private static class OccupationContext {
        private OccupationType type;
        private OccupationCategorie category;
        private LocalDate date;
        private LocalTime heureDebut;
        private LocalTime heureFin;
        private Space space;
        private Program program;   // nullable pour « autre »
        private Promotion promotion; // nullable
        private Group group;       // nullable
        private AcademicYear academicYear; // nullable
    }
}
