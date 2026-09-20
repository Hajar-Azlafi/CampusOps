package com.campusops.reservation.service;

import com.campusops.audit.service.AuditService;
import com.campusops.availability.engine.AvailabilityEngine;
import com.campusops.availability.engine.DateLabels;
import com.campusops.availability.engine.DayContext;
import com.campusops.availability.engine.SlotVerdict;
import com.campusops.availability.engine.SpaceOccupancy;
import com.campusops.availability.engine.WorkingCalendarService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.ReservationPriority;
import com.campusops.enums.ReservationStatus;
import com.campusops.enums.ReservationType;
import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.notification.service.NotificationService;
import com.campusops.reservation.dto.ReservationRequestDto;
import com.campusops.reservation.dto.ReservationResponseDto;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.mapper.ReservationMapper;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.validation.ReferentialStatus;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service de reservation. Toute la logique de <b>disponibilite</b> (jour
 * ouvrable, horaires derives des creneaux, occupations bloquantes ou en
 * attente) est deleguee au <b>moteur central</b> {@link AvailabilityEngine} et
 * a {@link WorkingCalendarService} : il n'existe aucune regle de disponibilite
 * dupliquee ici (§2, §19). Ce service ne porte plus que ce qui lui est propre :
 * securite/perimetre, contexte pedagogique, priorite automatique (§17),
 * arbitrage et auto-acceptation (§12), et protection transactionnelle contre le
 * double booking (§21).
 *
 * <p>Les <b>plafonds de reservation</b> (duree maximale, delai minimum avant le
 * debut, quota par utilisateur, week-end, hors horaires) ne sont pas codes ici :
 * ils sont lus dans la configuration globale via {@link SettingsService}
 * (Parametres &gt; Reservations, Module 11 §5/§20), si bien qu'un administrateur
 * les modifie depuis l'interface sans redeploiement.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Reservations";

    /**
     * Roles autorises a effectuer des reservations. Le responsable pedagogique
     * peut demander une salle pour SES filieres (voir createReservation).
     */
    private static final Set<Role> BOOKING_ROLES =
            EnumSet.of(Role.ADMIN, Role.RESPONSABLE_PEDAGOGIQUE,
                    Role.ENSEIGNANT, Role.RESPONSABLE_CLUB);

    /**
     * Roles autorises a valider ou refuser une reservation. La validation
     * finale reste a l'administration : un responsable pedagogique DEMANDE une
     * salle mais ne peut jamais valider une reservation (y compris la sienne).
     */
    private static final Set<Role> APPROVAL_ROLES =
            EnumSet.of(Role.ADMIN);

    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ReservationMapper reservationMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AccessScopeService accessScope;
    private final AvailabilityEngine availabilityEngine;
    private final WorkingCalendarService workingCalendar;
    private final SettingsService settingsService;

    // ----- Commandes -----

    public ReservationResponseDto createReservation(ReservationRequestDto request) {
        User currentUser = getCurrentUser();
        if (!BOOKING_ROLES.contains(currentUser.getRole())) {
            throw new BadRequestException(
                    "Votre rôle ne vous autorise pas à effectuer des réservations");
        }

        // Controles de saisie (date ouverte, creneau non passe, duree exploitable)
        // avant toute prise de verrou : un refus n'a pas a serialiser les autres
        // demandes sur la meme salle.
        ensureBookableInput(request.getDate(), request.getHeureDebut(), request.getHeureFin());

        // Verrou d'ecriture pessimiste sur l'espace : serialise les demandes
        // concurrentes sur la meme salle et garantit qu'aucun double booking ne
        // peut aboutir (§21). Le verrou est tenu jusqu'au commit.
        Space space = lockSpaceOrThrow(request.getSpaceId());

        // §5/§19 : une salle inactive (ou dont l'etage/batiment est desactive)
        // n'est jamais reservable, meme par appel direct de l'API. Message nommant
        // la cause exacte (salle, etage ou batiment).
        ReferentialStatus.requireUsable(space);

        // Revalidation finale centralisee (§14) : jour ouvrable, horaires derives
        // des creneaux, absence d'occupation bloquante. Renvoie le verdict pour
        // l'arbitrage de priorite (FREE ou PENDING_OVERLAP).
        SlotVerdict verdict = revalidateOrThrow(
                space, request.getDate(), request.getHeureDebut(), request.getHeureFin(), null);

        // L'administrateur peut reserver pour le compte d'un autre utilisateur ;
        // les autres roles reservent toujours pour eux-memes.
        User beneficiaire = currentUser;
        if (request.getTargetUserId() != null
                && currentUser.getRole() == Role.ADMIN
                && !request.getTargetUserId().equals(currentUser.getId())) {
            beneficiaire = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Utilisateur introuvable avec l'id " + request.getTargetUserId()));
        }

        // Contexte pedagogique : obligatoire (filiere) pour un responsable
        // pedagogique, facultatif pour les autres roles.
        PedagogicalContext pedago = resolvePedagogicalContext(request, currentUser);

        // §5 : quota de reservations a venir du beneficiaire (configuration
        // globale). Verifie apres la resolution du beneficiaire, car c'est SON
        // quota qui compte, pas celui du demandeur.
        ensureQuotaDisponible(beneficiaire, currentUser);

        // §17 : la priorite n'est JAMAIS choisie manuellement, elle est deduite
        // du type et du role du beneficiaire.
        ReservationPriority priorite = resolvePriority(request.getType(), beneficiaire.getRole());

        // §12 : statut initial. ADMIN → valide ; « maintenant / aujourd'hui »
        // sans conflit de priorite → auto-accepte ; sinon → en attente.
        ReservationStatus statut = decideInitialStatus(
                currentUser, request.getDate(), request.getHeureDebut(), request.getHeureFin(),
                space, priorite, verdict);

        Reservation reservation = Reservation.builder()
                .space(space)
                .user(beneficiaire)
                .program(pedago.program)
                .group(pedago.group)
                .semester(pedago.semester)
                .academicYear(pedago.academicYear)
                .contenuPedagogique(pedago.contenu)
                .type(request.getType())
                .date(request.getDate())
                .heureDebut(request.getHeureDebut())
                .heureFin(request.getHeureFin())
                .motif(request.getMotif())
                .commentaire(request.getCommentaire())
                .priorite(priorite)
                .statut(statut)
                .actif(true)
                .build();

        Reservation saved = reservationRepository.save(reservation);

        auditService.record(currentUser, AuditAction.RESERVATION, MODULE,
                "Creation d'une reservation " + auditLabel(saved)
                        + " (statut " + saved.getStatut() + ")");
        // Une reservation en attente est signalee aux valideurs ; une reservation
        // deja validee (ADMIN ou auto-acceptation) ne necessite pas de validation.
        if (saved.getStatut() == ReservationStatus.PENDING) {
            notificationService.notifyRoles(APPROVAL_ROLES, NotificationType.NEW_RESERVATION,
                    "Nouvelle réservation à valider",
                    String.format("%s a demandé la réservation %s.",
                            fullName(currentUser), notifLabel(saved)),
                    "/reservations");
        } else if (saved.getStatut() == ReservationStatus.APPROVED
                && currentUser.getRole() != Role.ADMIN) {
            // Auto-acceptation (§12) : l'auteur est informe que sa demande est
            // immediatement validee, sans intervention de l'administration.
            notificationService.notifyUser(saved.getUser(), NotificationType.RESERVATION_APPROVED,
                    "Réservation confirmée",
                    String.format("Votre réservation %s a été confirmée automatiquement.",
                            notifLabel(saved)),
                    "/reservations");
        }
        return reservationMapper.toResponseDto(saved);
    }

    public ReservationResponseDto updateReservation(Long id, ReservationRequestDto request) {
        Reservation reservation = findReservationOrThrow(id);
        User currentUser = getCurrentUser();
        ensureOwnerOrAdmin(reservation, currentUser);

        if (reservation.getStatut() == ReservationStatus.CANCELLED
                || reservation.getStatut() == ReservationStatus.REJECTED
                || reservation.getStatut() == ReservationStatus.COMPLETED) {
            throw new BadRequestException(
                    "Cette réservation ne peut plus être modifiée");
        }

        // Memes controles de saisie que pour une creation : la modification ne
        // doit pas permettre de replacer une reservation sur un creneau passe,
        // hors annee active, ou trop court.
        ensureBookableInput(request.getDate(), request.getHeureDebut(), request.getHeureFin());

        // Verrou pessimiste + revalidation finale sur le nouveau creneau, en
        // s'excluant soi-meme du calcul d'occupation (§14, §21).
        Space space = lockSpaceOrThrow(request.getSpaceId());
        // §5/§19 : modifier une reservation est une nouvelle operation ; on ne
        // peut pas (re)placer une reservation sur une salle devenue inutilisable.
        ReferentialStatus.requireUsable(space);
        revalidateOrThrow(space, request.getDate(),
                request.getHeureDebut(), request.getHeureFin(), reservation.getId());

        PedagogicalContext pedago = resolvePedagogicalContext(request, currentUser);

        reservation.setSpace(space);
        reservation.setProgram(pedago.program);
        reservation.setGroup(pedago.group);
        reservation.setSemester(pedago.semester);
        reservation.setAcademicYear(pedago.academicYear);
        reservation.setContenuPedagogique(pedago.contenu);
        reservation.setType(request.getType());
        reservation.setDate(request.getDate());
        reservation.setHeureDebut(request.getHeureDebut());
        reservation.setHeureFin(request.getHeureFin());
        reservation.setMotif(request.getMotif());
        reservation.setCommentaire(request.getCommentaire());
        // §17 : priorite toujours recalculee cote serveur.
        reservation.setPriorite(
                resolvePriority(request.getType(), reservation.getUser().getRole()));
        return reservationMapper.toResponseDto(reservationRepository.save(reservation));
    }

    public ReservationResponseDto approveReservation(Long id) {
        Reservation reservation = findReservationOrThrow(id);
        if (!APPROVAL_ROLES.contains(getCurrentUser().getRole())) {
            throw new BadRequestException(
                    "Votre rôle ne vous autorise pas à valider une réservation");
        }
        if (reservation.getStatut() != ReservationStatus.PENDING) {
            throw new BadRequestException(
                    "Seule une réservation en attente peut être validée");
        }

        // Verrou pessimiste + nouveau controle de conflit au moment de la
        // validation (§14, §21) : entre la demande et la validation, le creneau
        // a pu etre pris par une autre reservation acceptee, un examen ou l'EDT.
        Space space = lockSpaceOrThrow(reservation.getSpace().getId());
        // §5/§19 : valider engage definitivement la salle. Si la salle (ou son
        // etage/batiment) a ete desactivee entre la demande et la validation, on
        // refuse : une salle inactive ne doit jamais etre engagee. L'administrateur
        // reactive la salle ou refuse la demande.
        ReferentialStatus.requireUsable(space);
        revalidateOrThrow(space, reservation.getDate(),
                reservation.getHeureDebut(), reservation.getHeureFin(), reservation.getId());

        reservation.setStatut(ReservationStatus.APPROVED);
        Reservation saved = reservationRepository.save(reservation);

        auditService.record(AuditAction.APPROVAL, MODULE,
                "Validation de la reservation " + auditLabel(saved));
        notificationService.notifyUser(saved.getUser(), NotificationType.RESERVATION_APPROVED,
                "Réservation validée",
                String.format("Votre réservation %s a été validée.", notifLabel(saved)),
                "/reservations");
        return reservationMapper.toResponseDto(saved);
    }

    public ReservationResponseDto rejectReservation(Long id) {
        Reservation reservation = findReservationOrThrow(id);
        if (!APPROVAL_ROLES.contains(getCurrentUser().getRole())) {
            throw new BadRequestException(
                    "Votre rôle ne vous autorise pas à refuser une réservation");
        }
        if (reservation.getStatut() != ReservationStatus.PENDING) {
            throw new BadRequestException(
                    "Seule une réservation en attente peut être refusée");
        }
        reservation.setStatut(ReservationStatus.REJECTED);
        Reservation saved = reservationRepository.save(reservation);

        auditService.record(AuditAction.REJECTION, MODULE,
                "Refus de la reservation " + auditLabel(saved));
        notificationService.notifyUser(saved.getUser(), NotificationType.RESERVATION_REJECTED,
                "Réservation refusée",
                String.format("Votre réservation %s a été refusée.", notifLabel(saved)),
                "/reservations");
        return reservationMapper.toResponseDto(saved);
    }

    public ReservationResponseDto cancelReservation(Long id) {
        Reservation reservation = findReservationOrThrow(id);
        User currentUser = getCurrentUser();
        ensureOwnerOrAdmin(reservation, currentUser);
        if (reservation.getStatut() == ReservationStatus.CANCELLED
                || reservation.getStatut() == ReservationStatus.REJECTED
                || reservation.getStatut() == ReservationStatus.COMPLETED) {
            throw new BadRequestException(
                    "Cette réservation ne peut plus être annulée");
        }
        reservation.setStatut(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);

        auditService.record(currentUser, AuditAction.CANCELLATION, MODULE,
                "Annulation de la reservation " + auditLabel(saved));
        // Si l'annulation est le fait d'un tiers (administrateur), le proprietaire
        // de la reservation en est informe.
        if (!saved.getUser().getId().equals(currentUser.getId())) {
            notificationService.notifyUser(saved.getUser(), NotificationType.RESERVATION_CANCELLED,
                    "Réservation annulée",
                    String.format("Votre réservation %s a été annulée.", notifLabel(saved)),
                    "/reservations");
        }
        return reservationMapper.toResponseDto(saved);
    }

    // ----- Consultations -----

    @Transactional(readOnly = true)
    public ReservationResponseDto getReservationById(Long id) {
        Reservation reservation = findReservationOrThrow(id);
        User currentUser = getCurrentUser();
        // Isolation : un responsable pedagogique (ou tout role non admin) ne peut
        // consulter que ses propres reservations ou celles de SES filieres, meme
        // en appelant directement l'API avec un identifiant arbitraire.
        if (currentUser.getRole() != Role.ADMIN
                && !reservation.getUser().getId().equals(currentUser.getId())
                && !(reservation.getProgram() != null
                     && accessScope.myProgramIdSet().contains(reservation.getProgram().getId()))) {
            throw new BadRequestException(
                    "Vous n'êtes pas autorisé à consulter cette réservation");
        }
        return reservationMapper.toResponseDto(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservations(ReservationStatus statut, Long academicYearId) {
        User currentUser = getCurrentUser();
        List<Reservation> reservations = (statut != null)
                ? reservationRepository.findByStatut(statut)
                : reservationRepository.findAll();
        List<Reservation> viewable = restrictToViewable(reservations, currentUser);
        return filterByAcademicYear(viewable, academicYearId).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    /**
     * Filtre une liste de reservations sur l'annee universitaire active (ou celle
     * explicitement demandee). Conformement a la decision produit, l'appartenance
     * a une annee est <b>derivee de la date</b> de la reservation, confrontee aux
     * bornes {@code dateDebut}/{@code dateFin} de l'{@link AcademicYear}. Aucune
     * migration ni dependance au champ {@code academicYear} (nullable) n'est
     * necessaire. Comportement de repli non cassant :
     * <ul>
     *   <li>si aucune annee n'est explicitement demandee et qu'aucune annee active
     *       n'existe, la liste est renvoyee inchangee ;</li>
     *   <li>si l'annee resolue n'a pas de bornes de dates, aucune restriction n'est
     *       appliquee (on ne peut pas deriver une fenetre).</li>
     * </ul>
     * La securite n'est jamais affectee : ce filtre s'ajoute apres
     * {@link #restrictToViewable(List, User)} et ne l'elargit jamais.
     */
    private List<Reservation> filterByAcademicYear(List<Reservation> reservations, Long academicYearId) {
        AcademicYear year;
        if (academicYearId != null) {
            year = academicYearRepository.findById(academicYearId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Année universitaire introuvable avec l'id " + academicYearId));
        } else {
            year = academicYearRepository.findFirstByActifTrue().orElse(null);
        }
        if (year == null) {
            return reservations;
        }
        LocalDate debut = year.getDateDebut();
        LocalDate fin = year.getDateFin();
        if (debut == null && fin == null) {
            return reservations;
        }
        return reservations.stream()
                .filter(r -> {
                    LocalDate d = r.getDate();
                    if (d == null) {
                        return false;
                    }
                    if (debut != null && d.isBefore(debut)) {
                        return false;
                    }
                    return fin == null || !d.isAfter(fin);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getMyReservations() {
        return reservationRepository.findByUserId(getCurrentUser().getId()).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Utilisateur introuvable avec l'id " + userId);
        }
        User currentUser = getCurrentUser();
        if (!APPROVAL_ROLES.contains(currentUser.getRole()) && !currentUser.getId().equals(userId)) {
            throw new BadRequestException(
                    "Vous n'êtes pas autorisé à consulter les réservations d'un autre utilisateur");
        }
        return reservationRepository.findByUserId(userId).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsBySpace(Long spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new ResourceNotFoundException("Espace introuvable avec l'id " + spaceId);
        }
        User currentUser = getCurrentUser();
        List<Reservation> reservations = reservationRepository.findBySpaceId(spaceId);
        return restrictToViewable(reservations, currentUser).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsByDate(LocalDate date) {
        User currentUser = getCurrentUser();
        return restrictToViewable(reservationRepository.findByDate(date), currentUser).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsByPeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("Les dates de début et de fin sont obligatoires");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("La date de fin doit être postérieure à la date de début");
        }
        User currentUser = getCurrentUser();
        return restrictToViewable(reservationRepository.findByDateBetween(start, end), currentUser).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> searchReservations(String keyword, Long academicYearId) {
        User currentUser = getCurrentUser();
        List<Reservation> reservations = reservationRepository.searchByKeyword(keyword);
        List<Reservation> viewable = restrictToViewable(reservations, currentUser);
        return filterByAcademicYear(viewable, academicYearId).stream()
                .map(reservationMapper::toResponseDto).toList();
    }

    // ----- Regles metier / helpers -----

    /**
     * Controles de <b>saisie</b> d'une demande, appliques avant toute prise de
     * verrou et avant la revalidation d'occupation. Ils ferment les portes que le
     * moteur d'occupation ne couvre pas :
     * <ol>
     *   <li><b>date ouverte a la reservation</b> : jamais dans le passe, jamais la
     *       journee en cours passe l'heure limite, toujours dans l'annee
     *       universitaire active — exactement les regles de la recherche, pour
     *       qu'aucune reservation ne puisse contourner l'interface ;</li>
     *   <li><b>creneau deja commence</b> : pour aujourd'hui, l'heure de debut ne
     *       peut pas etre dans le passe ;</li>
     *   <li><b>duree exploitable</b> : un creneau plus court que la duree minimale
     *       configuree n'est pas reservable (§ micro-creneaux).</li>
     * </ol>
     *
     * <p>Volontairement <b>non applique</b> a la validation d'une demande
     * existante ({@code approveReservation}) : une demande deposee reste
     * validable meme si les regles de saisie ont change depuis.</p>
     */
    private void ensureBookableInput(LocalDate date, LocalTime debut, LocalTime fin) {
        if (date == null || debut == null || fin == null) {
            throw new BadRequestException("La date et les heures de la réservation sont obligatoires");
        }
        String motifDate = workingCalendar.searchDateWindow().motifRefus(date);
        if (motifDate != null) {
            throw new BadRequestException(motifDate);
        }
        if (date.equals(LocalDate.now())) {
            LocalTime now = LocalTime.now().withSecond(0).withNano(0);
            if (debut.isBefore(now)) {
                throw new BadRequestException(String.format(
                        "Ce créneau est déjà commencé : pour aujourd'hui, l'heure de début doit être "
                                + "postérieure ou égale à %s.", now.format(HOUR)));
            }
        }
        if (!availabilityEngine.isLongEnough(debut, fin)) {
            throw new BadRequestException(availabilityEngine.tooShortMessage());
        }
        appliquerReglesConfigurees(date, debut, fin);
    }

    /**
     * Plafonds de la configuration globale (Parametres &gt; Reservations, §5) :
     * duree maximale, delai minimum avant le debut, et interdiction du week-end.
     * Aucune de ces valeurs n'est codee ici (§20) ; une valeur a 0 desactive la
     * regle correspondante.
     *
     * <p>Le refus du week-end est <b>distinct</b> des jours ouvrables (§6) :
     * l'universite peut ouvrir le samedi pour les cours tout en interdisant les
     * reservations ce jour-la. Le jour ouvrable, lui, est verifie par le moteur
     * central au moment de la revalidation.</p>
     */
    private void appliquerReglesConfigurees(LocalDate date, LocalTime debut, LocalTime fin) {
        AppSettings reglages = settingsService.current();

        int dureeMax = reglages.getDureeMaxReservationMinutes() == null
                ? 0 : reglages.getDureeMaxReservationMinutes();
        long duree = Duration.between(debut, fin).toMinutes();
        if (dureeMax > 0 && duree > dureeMax) {
            throw new BadRequestException(String.format(
                    "Une réservation ne peut pas dépasser %s : ce créneau en demande %s.",
                    DateLabels.duree(dureeMax), DateLabels.duree((int) duree)));
        }

        int delaiMin = reglages.getDelaiMinAvantReservationMinutes() == null
                ? 0 : reglages.getDelaiMinAvantReservationMinutes();
        if (delaiMin > 0) {
            LocalDateTime debutCreneau = LocalDateTime.of(date, debut);
            LocalDateTime plusTot = LocalDateTime.now().withSecond(0).withNano(0)
                    .plusMinutes(delaiMin);
            if (debutCreneau.isBefore(plusTot)) {
                throw new BadRequestException(String.format(
                        "Une réservation doit être demandée au moins %s à l'avance :"
                                + " le premier créneau disponible commence le %s à %s.",
                        DateLabels.duree(delaiMin),
                        plusTot.toLocalDate().format(DAY), plusTot.toLocalTime().format(HOUR)));
            }
        }

        if (!reglages.isReservationsWeekEndAutorisees() && estWeekEnd(date)) {
            throw new BadRequestException(
                    "Les réservations ne sont pas autorisées le week-end.");
        }
    }

    /** Samedi ou dimanche, quels que soient les jours ouvrables configures. */
    private static boolean estWeekEnd(LocalDate date) {
        DayOfWeek jour = date.getDayOfWeek();
        return jour == DayOfWeek.SATURDAY || jour == DayOfWeek.SUNDAY;
    }

    /**
     * Quota « nombre maximal de reservations par utilisateur » (§5). Ne comptent
     * que les reservations <b>a venir</b> encore vivantes (en attente ou
     * approuvees) : une reservation annulee, refusee ou passee ne bloque
     * personne. 0 = illimite.
     *
     * <p>N'est pas applique quand c'est l'<b>ADMIN</b> qui saisit : son action
     * est une intervention d'administration, immediatement validee, et c'est lui
     * qui fixe le quota. Un enseignant qui atteint son plafond peut donc encore
     * etre servi par l'administration.</p>
     */
    private void ensureQuotaDisponible(User beneficiaire, User demandeur) {
        if (demandeur.getRole() == Role.ADMIN) {
            return;
        }
        Integer plafond = settingsService.current().getMaxReservationsParUtilisateur();
        if (plafond == null || plafond <= 0) {
            return;
        }
        long enCours = reservationRepository.countUpcomingForUser(
                beneficiaire.getId(), LocalDate.now(),
                List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED));
        if (enCours >= plafond) {
            throw new BadRequestException(String.format(
                    "Vous avez atteint le nombre maximal de réservations à venir (%d)."
                            + " Annulez une réservation existante avant d'en demander une nouvelle.",
                    plafond));
        }
    }

    /**
     * Revalidation finale d'un creneau via le <b>moteur central</b> (§2, §14,
     * §19) : jour ouvrable (dimanche/ferie §3/§4), bornes d'ouverture derivees
     * des creneaux (§7) et absence d'occupation <b>bloquante</b> (emploi du
     * temps actif, examen, reservation acceptee). Une demande en attente ne
     * bloque pas (§13) : elle laisse passer avec le verdict {@code PENDING_OVERLAP}
     * pour l'arbitrage de priorite. Toute situation non reservable leve une
     * {@link BadRequestException} explicite et non nominative (§16).
     *
     * @param excludeReservationId reservation a exclure du calcul (cas d'une
     *                             modification), ou {@code null}.
     * @return le verdict reservable ({@code FREE} ou {@code PENDING_OVERLAP}).
     */
    private SlotVerdict revalidateOrThrow(Space space, LocalDate date,
                                          LocalTime debut, LocalTime fin, Long excludeReservationId) {
        DayContext ctx = workingCalendar.describeDay(date);
        SpaceOccupancy occ = availabilityEngine.computeOccupancy(space, date, excludeReservationId);
        SlotVerdict verdict = availabilityEngine.evaluateSlot(ctx, occ, debut, fin);
        if (!verdict.isBookable()) {
            // NON_WORKING_DAY, OUT_OF_HOURS ou BLOCKED : refus avec le motif du moteur.
            throw new BadRequestException(verdict.reason());
        }
        return verdict;
    }

    /**
     * Determine le statut initial d'une reservation (§12).
     * <ul>
     *   <li><b>ADMIN</b> : intervention directe de l'administration → validee
     *       immediatement.</li>
     *   <li>Reservation <b>du jour</b> (« maintenant » / « aujourd'hui », §10, §11)
     *       sans conflit de priorite → <b>auto-acceptee</b>.</li>
     *   <li>Reservation du jour avec une demande en attente au moins aussi
     *       prioritaire → <b>en attente</b> (a priorite egale, premier arrive,
     *       premier servi).</li>
     *   <li>Reservation d'une <b>date future</b> → toujours en attente de
     *       validation par l'administration.</li>
     * </ul>
     * Le creneau a deja passe {@link #revalidateOrThrow} : aucune occupation
     * bloquante n'est possible ici, seul un {@code PENDING_OVERLAP} demande un
     * arbitrage.
     */
    private ReservationStatus decideInitialStatus(User user, LocalDate date,
                                                  LocalTime debut, LocalTime fin,
                                                  Space space, ReservationPriority priorite,
                                                  SlotVerdict verdict) {
        if (user.getRole() == Role.ADMIN) {
            return ReservationStatus.APPROVED;
        }
        // Auto-acceptation reservee au jour meme (modes maintenant/aujourd'hui, §12).
        if (!date.equals(LocalDate.now())) {
            return ReservationStatus.PENDING;
        }
        if (verdict.status() == SlotVerdict.Status.FREE) {
            return ReservationStatus.APPROVED;
        }
        // PENDING_OVERLAP : la nouvelle demande ne l'emporte (auto-acceptation)
        // que si elle est STRICTEMENT plus prioritaire que TOUTES les demandes en
        // attente qui chevauchent le creneau ; sinon elle reste en attente.
        List<Reservation> pendings = reservationRepository.findConflicting(
                space.getId(), date, debut, fin, AvailabilityEngine.PENDING_STATUSES);
        boolean outranksAllPending = pendings.stream()
                .allMatch(p -> priorityRank(priorite) > priorityRank(p.getPriorite()));
        return outranksAllPending ? ReservationStatus.APPROVED : ReservationStatus.PENDING;
    }

    /** Rang numerique de priorite (plus grand = plus prioritaire) pour l'arbitrage (§12). */
    private int priorityRank(ReservationPriority priorite) {
        if (priorite == null) {
            return 0;
        }
        return switch (priorite) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /**
     * Determine la priorite automatiquement (§17) : jamais saisie par le client.
     * Les examens sont prioritaires sur toute autre reservation ; les
     * responsables de club passent apres les enseignants et responsables
     * pedagogiques.
     */
    private ReservationPriority resolvePriority(ReservationType type, Role role) {
        if (type == ReservationType.EXAM) {
            return ReservationPriority.HIGH;
        }
        return switch (role) {
            case ADMIN, RESPONSABLE_PEDAGOGIQUE, ENSEIGNANT -> ReservationPriority.MEDIUM;
            case RESPONSABLE_CLUB -> ReservationPriority.LOW;
        };
    }

    private void ensureOwnerOrAdmin(Reservation reservation, User currentUser) {
        boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());
        if (!isOwner && currentUser.getRole() != Role.ADMIN) {
            throw new BadRequestException(
                    "Vous n'êtes pas autorisé à modifier cette réservation");
        }
    }

    /**
     * Restreint une liste de reservations a ce que l'utilisateur courant a le
     * droit de voir. ADMIN : tout. Responsable pedagogique : ses propres
     * demandes + les reservations rattachees a SES filieres. Autres roles :
     * uniquement leurs propres reservations. Applique la regle de securite :
     * aucun acces aux donnees d'une autre filiere.
     */
    private List<Reservation> restrictToViewable(List<Reservation> reservations, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return reservations;
        }
        Long myId = currentUser.getId();
        Set<Long> myPrograms = accessScope.myProgramIdSet();
        return reservations.stream()
                .filter(r -> r.getUser().getId().equals(myId)
                        || (r.getProgram() != null
                            && myPrograms.contains(r.getProgram().getId())))
                .toList();
    }

    /**
     * Resout et valide le contexte pedagogique d'une demande de reservation.
     * Pour un responsable pedagogique, la filiere est obligatoire et doit faire
     * partie de SON perimetre (controle de securite cote backend). Pour les
     * autres roles, ces informations restent facultatives.
     */
    private PedagogicalContext resolvePedagogicalContext(ReservationRequestDto request, User currentUser) {
        PedagogicalContext ctx = new PedagogicalContext();
        if (request.getProgramId() != null) {
            ctx.program = programRepository.findById(request.getProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Filière introuvable avec l'id " + request.getProgramId()));
        }
        if (currentUser.getRole() == Role.RESPONSABLE_PEDAGOGIQUE) {
            if (ctx.program == null) {
                throw new BadRequestException(
                        "La filière concernée est obligatoire pour une demande de réservation pédagogique.");
            }
            accessScope.assertProgramAccessible(ctx.program.getId());
        }
        if (request.getGroupId() != null) {
            ctx.group = groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Groupe introuvable avec l'id " + request.getGroupId()));
        }
        // Coherence + securite : un groupe fourni doit appartenir a la filiere
        // indiquee. Empeche notamment un responsable pedagogique de rattacher (et
        // donc de lire) le groupe d'une AUTRE filiere a sa propre demande.
        if (ctx.program != null && ctx.group != null
                && !ctx.group.getPromotion().getProgram().getId().equals(ctx.program.getId())) {
            throw new BadRequestException(
                    "Le groupe sélectionné n'appartient pas à la filière indiquée.");
        }
        if (request.getSemesterId() != null) {
            ctx.semester = semesterRepository.findById(request.getSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Semestre introuvable avec l'id " + request.getSemesterId()));
        }
        if (request.getAcademicYearId() != null) {
            ctx.academicYear = academicYearRepository.findById(request.getAcademicYearId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Année universitaire introuvable avec l'id " + request.getAcademicYearId()));
        }
        ctx.contenu = request.getContenuPedagogique();
        return ctx;
    }

    /** Contexte pedagogique resolu (relations facultatives) d'une reservation. */
    private static class PedagogicalContext {
        private Program program;
        private Group group;
        private Semester semester;
        private AcademicYear academicYear;
        private String contenu;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        // Repli : recuperation par email si le principal n'est pas l'entite User.
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Utilisateur courant introuvable"));
        }
        throw new BadRequestException("Aucun utilisateur authentifié");
    }

    private Reservation findReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Réservation introuvable avec l'id " + id));
    }

    /**
     * Charge un espace en posant un verrou d'ecriture pessimiste (§21). Toutes
     * les operations d'ecriture sur les reservations d'une salle passent par ce
     * verrou, ce qui serialise les demandes concurrentes sur la meme salle.
     */
    private Space lockSpaceOrThrow(Long id) {
        return spaceRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Espace introuvable avec l'id " + id));
    }

    /** Libelle technique d'une reservation pour le journal d'audit. */
    private String auditLabel(Reservation reservation) {
        return String.format("#%d (%s le %s de %s a %s)",
                reservation.getId(),
                reservation.getSpace().getNom(),
                reservation.getDate().format(DAY),
                reservation.getHeureDebut().format(HOUR),
                reservation.getHeureFin().format(HOUR));
    }

    /** Libelle lisible d'une reservation pour les notifications. */
    private String notifLabel(Reservation reservation) {
        return String.format("de %s le %s (%s - %s)",
                reservation.getSpace().getNom(),
                reservation.getDate().format(DAY),
                reservation.getHeureDebut().format(HOUR),
                reservation.getHeureFin().format(HOUR));
    }

    private String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
