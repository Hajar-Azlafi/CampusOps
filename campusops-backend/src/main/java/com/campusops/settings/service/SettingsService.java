package com.campusops.settings.service;

import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.academicyear.service.AcademicYearService;
import com.campusops.audit.service.AuditService;
import com.campusops.availability.engine.DateLabels;
import com.campusops.enums.AuditAction;
import com.campusops.enums.SettingsMediaType;
import com.campusops.enums.WeekDay;
import com.campusops.exception.BadRequestException;
import com.campusops.mail.EmailProperties;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.service.SemesterService;
import com.campusops.settings.dto.AcademicContextDto;
import com.campusops.settings.dto.DisplaySettingsDto;
import com.campusops.settings.dto.ImportSettingsDto;
import com.campusops.settings.dto.NotificationSettingsDto;
import com.campusops.settings.dto.PublicBrandingDto;
import com.campusops.settings.dto.ReservationSettingsDto;
import com.campusops.settings.dto.SecuritySettingsDto;
import com.campusops.settings.dto.SettingsResponseDto;
import com.campusops.settings.dto.SettingsUpdateRequestDto;
import com.campusops.settings.dto.UniversitySettingsDto;
import com.campusops.settings.dto.WorkingHoursSettingsDto;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.entity.LowerCaseListConverter;
import com.campusops.settings.mapper.SettingsMapper;
import com.campusops.settings.repository.AppSettingsRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Point d'entree unique de la configuration globale (Module 11).
 *
 * <h2>Trois familles d'operations, trois niveaux d'acces</h2>
 * <ul>
 *   <li><b>Lecture interne</b> ({@link #current()} et derives) : sans controle de
 *       role. Les services metier doivent lire un parametre quel que soit le role
 *       de l'appelant — un enseignant ne modifie pas la duree maximale, mais sa
 *       reservation doit la respecter.</li>
 *   <li><b>Lecture/ecriture d'administration</b> : {@code accessScope.requireAdmin()}
 *       en premiere ligne (§14), verifie cote serveur et non seulement dans le
 *       menu.</li>
 *   <li><b>Identite visuelle publique</b> ({@link #getPublicBranding()}) : sans
 *       authentification, pour la page de connexion. Volontairement limitee aux
 *       informations de marque.</li>
 * </ul>
 *
 * <h2>Cache</h2>
 * <p>La configuration est lue a chaque calcul de disponibilite, a chaque
 * reservation et a chaque envoi de notification. Un instantane {@code volatile}
 * evite une requete par appel ; il est invalide apres <b>validation</b> de la
 * transaction d'ecriture, jamais avant, pour ne jamais publier une valeur non
 * encore engagee.</p>
 *
 * <h2>Ce que ce service ne fait pas (§2)</h2>
 * <p>Il ne gere ni annee universitaire, ni semestre, ni creneau : il delegue a
 * {@code AcademicYearService}, {@code SemesterService} et au module
 * {@code timeslot}, dont il expose seulement l'etat en lecture. Il ne depend pas
 * de {@code WorkingCalendarService} — c'est l'inverse — afin d'eviter tout cycle
 * entre beans.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService {

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Parametres";

    /** Formats que les imports Excel savent techniquement lire (Apache POI). */
    private static final List<String> FORMATS_SUPPORTES = List.of("xlsx", "xls");

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final AppSettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;
    private final SettingsMediaService mediaService;
    private final AccessScopeService accessScope;
    private final AuditService auditService;
    private final TimeSlotRepository timeSlotRepository;
    private final AcademicYearService academicYearService;
    private final SemesterService semesterService;
    private final EmailProperties emailProperties;

    /** Plafond technique du conteneur : borne haute de la taille configurable (§9). */
    @Value("${spring.servlet.multipart.max-file-size:25MB}")
    private String plafondMultipart;

    /** Instantane courant, invalide apres chaque ecriture validee. */
    private volatile AppSettings snapshot;

    // ===================== Lecture interne (sans controle de role) ===========

    /**
     * Configuration courante, telle que doivent la lire les services metier.
     *
     * <p>Aucun controle de role : la valeur d'un parametre s'applique a tous les
     * utilisateurs, seule sa <b>modification</b> est reservee a l'administrateur.
     * Si la ligne n'existe pas encore (base vierge, lecture dans une transaction
     * en lecture seule), une instance par defaut non persistee est renvoyee : le
     * systeme fonctionne alors avec les valeurs du §19 au lieu d'echouer.</p>
     */
    @Transactional(readOnly = true)
    public AppSettings current() {
        AppSettings local = snapshot;
        if (local == null) {
            local = settingsRepository.findById(AppSettings.SINGLETON_ID)
                    .orElseGet(AppSettings::defaults);
            snapshot = local;
        }
        return local;
    }

    /**
     * Charge la configuration ou cree la configuration par defaut (§19).
     * Appele par l'initialiseur de demarrage et par toute ecriture.
     */
    public AppSettings loadOrCreateDefaults() {
        return settingsRepository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    AppSettings created = settingsRepository.save(AppSettings.defaults());
                    log.info("Configuration globale absente : creation de la configuration"
                            + " par defaut (universite « {} », {} -> {}).",
                            created.getNom(), created.getHeureOuverture(),
                            created.getHeureFermeture());
                    invalidateSnapshot();
                    return created;
                });
    }

    /** Duree minimale exploitable d'un creneau, en minutes (jamais negative). */
    @Transactional(readOnly = true)
    public int minSlotMinutes() {
        return Math.max(0, valeur(current().getDureeMinReservationMinutes(), 0));
    }

    /**
     * Motif de refus lorsqu'une <b>seance</b> (cours, TD, TP, examen...) depasse
     * la duree maximale configuree (§5, « duree maximale d'une seance »), sinon
     * {@code null}. Renvoyer le message plutot que lever une exception permet
     * aux deux chemins d'utiliser la <b>meme</b> regle et le meme texte : la
     * creation unitaire le transforme en erreur 400, l'import Excel l'ajoute a
     * la liste des erreurs de la ligne (§20 : une seule source de verite).
     *
     * <p>Valeur nulle ou &lt;= 0 : regle desactivee, aucune seance refusee.</p>
     */
    @Transactional(readOnly = true)
    public String motifDureeSeanceExcessive(LocalTime debut, LocalTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            return null;
        }
        int maximum = valeur(current().getDureeMaxSeanceMinutes(), 0);
        if (maximum <= 0) {
            return null;
        }
        long duree = Duration.between(debut, fin).toMinutes();
        if (duree <= maximum) {
            return null;
        }
        return String.format(
                "Une séance ne peut pas dépasser %s : le créneau %s - %s en demande %s.",
                DateLabels.duree(maximum), DateLabels.heure(debut),
                DateLabels.heure(fin), DateLabels.duree(duree));
    }

    /** Jour de la semaine correspondant a une date, dans l'enumeration du projet. */
    public static WeekDay weekDayOf(LocalDate date) {
        return WeekDay.values()[date.getDayOfWeek().getValue() - 1];
    }

    /** Vrai si la date tombe un jour ouvrable de l'universite (§6). */
    @Transactional(readOnly = true)
    public boolean isJourOuvrable(LocalDate date) {
        Set<WeekDay> ouvrables = current().getJoursOuvrables();
        if (ouvrables == null || ouvrables.isEmpty()) {
            return date.getDayOfWeek() != DayOfWeek.SUNDAY;
        }
        return ouvrables.contains(weekDayOf(date));
    }

    /** Nom francais du jour, en minuscules, pour composer un message clair. */
    public static String nomJour(LocalDate date) {
        return weekDayOf(date).name().toLowerCase(Locale.ROOT);
    }

    /** Motif de date applique par l'interface (§10). */
    @Transactional(readOnly = true)
    public String motifDate() {
        return current().getFormatDate().getPattern();
    }

    /** Motif d'heure applique par l'interface (§10). */
    @Transactional(readOnly = true)
    public String motifHeure() {
        return current().getFormatHeure().getPattern();
    }

    /** Extensions autorisees a l'import, normalisees en minuscules et sans point. */
    @Transactional(readOnly = true)
    public List<String> formatsAutorises() {
        List<String> formats = current().getFormatsAutorises();
        return (formats == null || formats.isEmpty()) ? FORMATS_SUPPORTES : formats;
    }

    /** Formats que le systeme sait techniquement lire, quelle que soit la configuration. */
    public List<String> formatsSupportes() {
        return FORMATS_SUPPORTES;
    }

    /** Plafond technique du conteneur, en megaoctets (§9). */
    public int plafondServeurMo() {
        try {
            long octets = DataSize.parse(plafondMultipart).toBytes();
            return (int) Math.max(1, octets / (1024 * 1024));
        } catch (IllegalArgumentException ex) {
            log.warn("Valeur de spring.servlet.multipart.max-file-size illisible ({}) :"
                    + " plafond ramene a 25 Mo.", plafondMultipart);
            return 25;
        }
    }

    // ===================== Lecture d'administration (ADMIN) ==================

    /** Vue agregee de la configuration, alimentant les sept onglets (§13, §15). */
    @Transactional(readOnly = true)
    public SettingsResponseDto getSettings() {
        accessScope.requireAdmin();
        return assembler(current());
    }

    /**
     * Assemble les sept sections a partir d'une instance donnee.
     *
     * <p>Extrait de {@link #getSettings()} pour que {@link #updateAll} puisse
     * repondre depuis l'entite qu'il vient de modifier, <b>sans repasser par le
     * cache</b> : reappeler {@code getSettings()} en fin d'ecriture remettrait en
     * cache une entite encore non validee, ce que {@link #invalidateSnapshot()}
     * s'emploie justement a eviter.</p>
     */
    private SettingsResponseDto assembler(AppSettings settings) {
        return SettingsResponseDto.builder()
                .universite(settingsMapper.toUniversityDto(settings))
                .reservations(settingsMapper.toReservationDto(settings))
                .horaires(horairesDto(settings))
                .securite(settingsMapper.toSecurityDto(settings))
                .notifications(notificationsDto(settings))
                .imports(importsDto(settings))
                .affichage(affichageDto(settings))
                .logo(mediaService.getInfo(SettingsMediaType.LOGO))
                .favicon(mediaService.getInfo(SettingsMediaType.FAVICON))
                .contexteAcademique(contexteAcademique())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public UniversitySettingsDto getUniversity() {
        accessScope.requireAdmin();
        return settingsMapper.toUniversityDto(current());
    }

    @Transactional(readOnly = true)
    public ReservationSettingsDto getReservation() {
        accessScope.requireAdmin();
        return settingsMapper.toReservationDto(current());
    }

    @Transactional(readOnly = true)
    public WorkingHoursSettingsDto getWorkingHours() {
        accessScope.requireAdmin();
        return horairesDto(current());
    }

    @Transactional(readOnly = true)
    public SecuritySettingsDto getSecurity() {
        accessScope.requireAdmin();
        return settingsMapper.toSecurityDto(current());
    }

    @Transactional(readOnly = true)
    public NotificationSettingsDto getNotification() {
        accessScope.requireAdmin();
        return notificationsDto(current());
    }

    @Transactional(readOnly = true)
    public ImportSettingsDto getImport() {
        accessScope.requireAdmin();
        return importsDto(current());
    }

    @Transactional(readOnly = true)
    public DisplaySettingsDto getDisplay() {
        accessScope.requireAdmin();
        return affichageDto(current());
    }

    /**
     * Identite visuelle exploitable <b>sans authentification</b> : page de
     * connexion, titre du document, favicon, couleurs (§11, §17). N'expose aucune
     * politique de securite ni coordonnee interne.
     */
    @Transactional(readOnly = true)
    public PublicBrandingDto getPublicBranding() {
        AppSettings s = current();
        return PublicBrandingDto.builder()
                .nom(s.getNom())
                .nomCourt(s.getNomCourt())
                .slogan(s.getSlogan())
                .ville(s.getVille())
                .pays(s.getPays())
                .couleurPrincipale(s.getCouleurPrincipale())
                .couleurSecondaire(s.getCouleurSecondaire())
                .themeParDefaut(s.getThemeParDefaut())
                .motifDate(s.getFormatDate().getPattern())
                .motifHeure(s.getFormatHeure().getPattern())
                .paginationActivee(s.isPaginationActivee())
                .elementsParPage(s.getElementsParPage())
                .logoUrl(mediaService.publicUrl(SettingsMediaType.LOGO))
                .faviconUrl(mediaService.publicUrl(SettingsMediaType.FAVICON))
                .build();
    }

    // ===================== Ecriture d'administration (ADMIN) =================

    /**
     * Mise a jour globale : seules les sections effectivement fournies sont
     * appliquees, chacune apres validation croisee. Un corps entierement vide est
     * refuse plutot que silencieusement ignore.
     */
    public SettingsResponseDto updateAll(SettingsUpdateRequestDto request) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();

        List<String> sections = new ArrayList<>();
        if (request.getUniversite() != null) {
            appliquerUniversite(request.getUniversite(), settings);
            sections.add("universite");
        }
        if (request.getReservations() != null) {
            appliquerReservation(request.getReservations(), settings);
            sections.add("reservations");
        }
        if (request.getHoraires() != null) {
            appliquerHoraires(request.getHoraires(), settings);
            sections.add("horaires");
        }
        if (request.getSecurite() != null) {
            appliquerSecurite(request.getSecurite(), settings);
            sections.add("securite");
        }
        if (request.getNotifications() != null) {
            appliquerNotifications(request.getNotifications(), settings);
            sections.add("notifications");
        }
        if (request.getImports() != null) {
            appliquerImports(request.getImports(), settings);
            sections.add("imports");
        }
        if (request.getAffichage() != null) {
            appliquerAffichage(request.getAffichage(), settings);
            sections.add("affichage");
        }
        if (sections.isEmpty()) {
            throw new BadRequestException("Aucune section de paramètres n'a été fournie.");
        }

        enregistrer(settings, "Mise a jour des parametres : " + String.join(", ", sections));
        return assembler(settings);
    }

    public UniversitySettingsDto updateUniversity(UniversitySettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerUniversite(dto, settings);
        enregistrer(settings, "Mise a jour de l'identite de l'universite");
        return settingsMapper.toUniversityDto(settings);
    }

    public ReservationSettingsDto updateReservation(ReservationSettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerReservation(dto, settings);
        enregistrer(settings, "Mise a jour des regles de reservation");
        return settingsMapper.toReservationDto(settings);
    }

    public WorkingHoursSettingsDto updateWorkingHours(WorkingHoursSettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerHoraires(dto, settings);
        enregistrer(settings, "Mise a jour des horaires de fonctionnement");
        return horairesDto(settings);
    }

    public SecuritySettingsDto updateSecurity(SecuritySettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerSecurite(dto, settings);
        enregistrer(settings, "Mise a jour de la politique de securite");
        return settingsMapper.toSecurityDto(settings);
    }

    public NotificationSettingsDto updateNotification(NotificationSettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerNotifications(dto, settings);
        enregistrer(settings, "Mise a jour des parametres de notification");
        return notificationsDto(settings);
    }

    public ImportSettingsDto updateImport(ImportSettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerImports(dto, settings);
        enregistrer(settings, "Mise a jour de la politique d'import");
        return importsDto(settings);
    }

    public DisplaySettingsDto updateDisplay(DisplaySettingsDto dto) {
        accessScope.requireAdmin();
        AppSettings settings = loadOrCreateDefaults();
        appliquerAffichage(dto, settings);
        enregistrer(settings, "Mise a jour des preferences d'affichage");
        return affichageDto(settings);
    }

    // ===================== Application + validations croisees ================

    /**
     * Identite de l'universite (§3). Le fuseau horaire est verifie contre la base
     * IANA et la devise normalisee en majuscules ; les champs texte facultatifs
     * envoyes vides sont ramenes a {@code null} (l'administrateur peut donc vider
     * un champ) plutot que stockes comme chaines vides.
     */
    private void appliquerUniversite(UniversitySettingsDto dto, AppSettings settings) {
        String fuseau = texte(dto.getFuseauHoraire());
        if (fuseau != null) {
            try {
                ZoneId.of(fuseau);
            } catch (RuntimeException ex) {
                throw new BadRequestException("Le fuseau horaire « " + fuseau
                        + " » est inconnu. Exemple attendu : Africa/Casablanca.");
            }
        }

        settingsMapper.updateUniversity(dto, settings);

        settings.setNom(texte(settings.getNom()));
        settings.setNomCourt(texte(settings.getNomCourt()));
        settings.setSlogan(texte(settings.getSlogan()));
        settings.setAdresse(texte(settings.getAdresse()));
        settings.setVille(texte(settings.getVille()));
        settings.setPays(texte(settings.getPays()));
        settings.setTelephone(texte(settings.getTelephone()));
        settings.setEmail(minuscules(texte(settings.getEmail())));
        settings.setSiteWeb(texte(settings.getSiteWeb()));
        settings.setFuseauHoraire(fuseau != null ? fuseau : settings.getFuseauHoraire());
        settings.setDevise(majuscules(texte(settings.getDevise())));

        if (settings.getNom() == null) {
            throw new BadRequestException("Le nom de l'université est obligatoire.");
        }
    }

    /**
     * Regles de reservation (§5). La duree minimale exploitable ne peut pas
     * depasser la duree maximale autorisee : la combinaison rendrait toute
     * reservation impossible.
     */
    private void appliquerReservation(ReservationSettingsDto dto, AppSettings settings) {
        int min = valeur(dto.getDureeMinReservationMinutes(),
                valeur(settings.getDureeMinReservationMinutes(), 0));
        int max = valeur(dto.getDureeMaxReservationMinutes(),
                valeur(settings.getDureeMaxReservationMinutes(), 240));
        if (min > max) {
            throw new BadRequestException(String.format(
                    "La durée minimale (%d min) ne peut pas dépasser la durée maximale"
                            + " d'une réservation (%d min).", min, max));
        }
        settingsMapper.updateReservation(dto, settings);
    }

    /**
     * Horaires de fonctionnement (§6). Ouverture strictement anterieure a la
     * fermeture, heure limite de recherche coherente avec la plage, et au moins un
     * jour ouvrable.
     */
    private void appliquerHoraires(WorkingHoursSettingsDto dto, AppSettings settings) {
        LocalTime ouverture = dto.getHeureOuverture() != null
                ? dto.getHeureOuverture() : settings.getHeureOuverture();
        LocalTime fermeture = dto.getHeureFermeture() != null
                ? dto.getHeureFermeture() : settings.getHeureFermeture();
        LocalTime limite = dto.getHeureLimiteRecherche() != null
                ? dto.getHeureLimiteRecherche() : settings.getHeureLimiteRecherche();

        if (!ouverture.isBefore(fermeture)) {
            throw new BadRequestException(String.format(
                    "L'heure d'ouverture (%s) doit être antérieure à l'heure de fermeture (%s).",
                    ouverture.format(HHMM), fermeture.format(HHMM)));
        }
        if (limite.isBefore(ouverture)) {
            throw new BadRequestException(String.format(
                    "L'heure limite de recherche (%s) ne peut pas précéder l'heure"
                            + " d'ouverture (%s).", limite.format(HHMM), ouverture.format(HHMM)));
        }

        Set<WeekDay> jours = dto.getJoursOuvrables();
        if (jours != null) {
            if (jours.isEmpty()) {
                throw new BadRequestException(
                        "Au moins un jour ouvrable doit être sélectionné.");
            }
            // Ordre chronologique stable, quel que soit l'ordre d'envoi du client.
            Set<WeekDay> ordonnes = new LinkedHashSet<>();
            for (WeekDay jour : WeekDay.values()) {
                if (jours.contains(jour)) {
                    ordonnes.add(jour);
                }
            }
            dto.setJoursOuvrables(ordonnes);
        }
        settingsMapper.updateWorkingHours(dto, settings);
    }

    /**
     * Politique de securite (§7). Les bornes unitaires sont portees par la
     * validation declarative ; on verifie ici la seule combinaison incoherente :
     * un verrouillage demande sans duree exploitable.
     */
    private void appliquerSecurite(SecuritySettingsDto dto, AppSettings settings) {
        int tentatives = valeur(dto.getMaxTentativesConnexion(),
                valeur(settings.getMaxTentativesConnexion(), 0));
        int verrouillage = valeur(dto.getDureeVerrouillageMinutes(),
                valeur(settings.getDureeVerrouillageMinutes(), 15));
        if (tentatives > 0 && verrouillage <= 0) {
            throw new BadRequestException("La durée de verrouillage doit être d'au moins"
                    + " 1 minute lorsqu'un nombre maximal de tentatives est défini.");
        }
        settingsMapper.updateSecurity(dto, settings);
    }

    /** Interrupteurs de notification (§8). Aucune contrainte croisee. */
    private void appliquerNotifications(NotificationSettingsDto dto, AppSettings settings) {
        settingsMapper.updateNotification(dto, settings);
    }

    /**
     * Politique d'import (§9). La taille configurable ne peut pas depasser le
     * plafond technique du serveur, et seuls les formats que les importateurs
     * savent reellement lire sont acceptes : autoriser « .csv » afficherait un
     * parametre mensonger.
     */
    private void appliquerImports(ImportSettingsDto dto, AppSettings settings) {
        int plafond = plafondServeurMo();
        int demande = valeur(dto.getTailleMaxFichierMo(),
                valeur(settings.getTailleMaxFichierMo(), 5));
        if (demande > plafond) {
            throw new BadRequestException(String.format(
                    "La taille maximale (%d Mo) dépasse le plafond du serveur (%d Mo).",
                    demande, plafond));
        }

        if (dto.getFormatsAutorises() != null) {
            List<String> normalises = new ArrayList<>();
            for (String format : dto.getFormatsAutorises()) {
                String propre = LowerCaseListConverter.normalise(format);
                if (propre == null || propre.isEmpty()) {
                    continue;
                }
                if (!FORMATS_SUPPORTES.contains(propre)) {
                    throw new BadRequestException(String.format(
                            "Le format « %s » n'est pas pris en charge par les imports."
                                    + " Formats disponibles : %s.",
                            propre, String.join(", ", FORMATS_SUPPORTES)));
                }
                if (!normalises.contains(propre)) {
                    normalises.add(propre);
                }
            }
            if (normalises.isEmpty()) {
                throw new BadRequestException(
                        "Au moins un format de fichier doit être autorisé.");
            }
            dto.setFormatsAutorises(normalises);
        }
        settingsMapper.updateImport(dto, settings);
    }

    /**
     * Preferences d'affichage (§10, §17). Les couleurs sont normalisees en
     * majuscules pour que le frontend, la base et l'aperçu manipulent exactement
     * la meme chaine.
     */
    private void appliquerAffichage(DisplaySettingsDto dto, AppSettings settings) {
        if (dto.getCouleurPrincipale() != null) {
            dto.setCouleurPrincipale(majuscules(texte(dto.getCouleurPrincipale())));
        }
        if (dto.getCouleurSecondaire() != null) {
            dto.setCouleurSecondaire(majuscules(texte(dto.getCouleurSecondaire())));
        }
        settingsMapper.updateDisplay(dto, settings);
    }

    // ===================== Persistance et cache ==============================

    /** Enregistre, trace l'action et invalide l'instantane partage. */
    private void enregistrer(AppSettings settings, String description) {
        settings.setId(AppSettings.SINGLETON_ID);
        settingsRepository.save(settings);
        invalidateSnapshot();
        auditService.record(AuditAction.UPDATE, MODULE, description);
    }

    /**
     * Invalide l'instantane maintenant <b>et</b> apres la fin de la transaction.
     *
     * <p>La seconde invalidation est indispensable : une lecture concurrente
     * survenant avant la validation rechargerait les anciennes valeurs et les
     * remettrait en cache, ou l'instantane resterait errone si la transaction est
     * finalement annulee.</p>
     */
    private void invalidateSnapshot() {
        snapshot = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            snapshot = null;
                        }
                    });
        }
    }

    // ===================== Assemblage des fragments ==========================

    /**
     * Horaires enrichis de l'etat reel de la grille de creneaux : l'administrateur
     * voit ainsi que les bornes effectives proviennent des creneaux actifs quand
     * il en existe (§2, §6).
     */
    private WorkingHoursSettingsDto horairesDto(AppSettings settings) {
        WorkingHoursSettingsDto dto = settingsMapper.toWorkingHoursDto(settings);
        List<TimeSlot> actifs = timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(true);
        dto.setCreneauxDebut(actifs.stream().map(TimeSlot::getHeureDebut)
                .min(LocalTime::compareTo).orElse(null));
        dto.setCreneauxFin(actifs.stream().map(TimeSlot::getHeureFin)
                .max(LocalTime::compareTo).orElse(null));
        dto.setCreneauxActifs(actifs.stream().map(this::libelleCreneau).toList());
        return dto;
    }

    private String libelleCreneau(TimeSlot creneau) {
        String plage = creneau.getHeureDebut().format(HHMM) + " - "
                + creneau.getHeureFin().format(HHMM);
        return (creneau.getNom() == null || creneau.getNom().isBlank())
                ? plage
                : creneau.getNom() + " (" + plage + ")";
    }

    /** Notifications enrichies de l'etat reel du canal e-mail (§8). */
    private NotificationSettingsDto notificationsDto(AppSettings settings) {
        NotificationSettingsDto dto = settingsMapper.toNotificationDto(settings);
        dto.setSmtpConfigure(emailProperties.getMail().isEnabled());
        dto.setExpediteur(emailProperties.getMail().getFrom());
        return dto;
    }

    /** Imports enrichis du plafond technique et des formats reellement lisibles (§9). */
    private ImportSettingsDto importsDto(AppSettings settings) {
        ImportSettingsDto dto = settingsMapper.toImportDto(settings);
        dto.setPlafondServeurMo(plafondServeurMo());
        dto.setFormatsSupportes(FORMATS_SUPPORTES);
        return dto;
    }

    /** Affichage enrichi des motifs derives consommes par le frontend (§10). */
    private DisplaySettingsDto affichageDto(AppSettings settings) {
        DisplaySettingsDto dto = settingsMapper.toDisplayDto(settings);
        dto.setMotifDate(settings.getFormatDate().getPattern());
        dto.setMotifHeure(settings.getFormatHeure().getPattern());
        return dto;
    }

    /**
     * Contexte academique en lecture seule (§4) : delegue integralement aux
     * services existants. Aucune annee active n'est une situation normale sur une
     * base neuve — on renvoie {@code null} au lieu de propager une erreur.
     */
    private AcademicContextDto contexteAcademique() {
        List<AcademicYearResponseDto> actives = academicYearService.filterAcademicYears(true);
        List<AcademicYearResponseDto> toutes = academicYearService.filterAcademicYears(null);
        return AcademicContextDto.builder()
                .anneeActive(actives.isEmpty() ? null : actives.get(0))
                .semestresCourants(semesterService.getCurrentSemesters())
                .nombreAnneesHistorisees(toutes.size())
                .build();
    }

    // ===================== Utilitaires =======================================

    private static int valeur(Integer valeur, int defaut) {
        return valeur != null ? valeur : defaut;
    }

    /** Chaine nettoyee : {@code null} si vide, afin de ne jamais stocker « ». */
    private static String texte(String valeur) {
        if (valeur == null) {
            return null;
        }
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }

    private static String minuscules(String valeur) {
        return valeur == null ? null : valeur.toLowerCase(Locale.ROOT);
    }

    private static String majuscules(String valeur) {
        return valeur == null ? null : valeur.toUpperCase(Locale.ROOT);
    }






}

