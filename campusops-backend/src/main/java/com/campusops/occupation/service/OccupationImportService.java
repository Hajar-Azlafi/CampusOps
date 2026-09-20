package com.campusops.occupation.service;

import com.campusops.audit.service.AuditService;
import com.campusops.availability.engine.WorkingCalendarService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.OccupationCategorie;
import com.campusops.enums.OccupationType;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.group.entity.Group;
import com.campusops.group.repository.GroupRepository;
import com.campusops.notification.service.NotificationService;
import com.campusops.occupation.dto.OccupationImportPreviewDto;
import com.campusops.occupation.dto.OccupationImportResultDto;
import com.campusops.occupation.dto.OccupationImportRowDto;
import com.campusops.occupation.dto.OccupationRequestDto;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.settings.service.ImportPolicyService;
import com.campusops.space.entity.Space;
import com.campusops.space.repository.SpaceRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Import d'un planning d'<b>occupations supplémentaires</b> (onglets « Planning
 * soutenances » et « Autre »), en DEUX PHASES, exactement comme l'import
 * d'examens et d'emploi du temps (§5/§7/§8/§24) :
 *
 * <ol>
 *   <li>{@link #preview} : le fichier est analysé ligne par ligne et confronté à
 *       l'occupation réelle des salles, SANS rien enregistrer. Un rapport détaillé
 *       est renvoyé (compteurs + erreurs par ligne).</li>
 *   <li>{@link #confirm} : rejoue l'analyse et, uniquement si AUCUNE ligne n'est
 *       en erreur, enregistre les occupations via
 *       {@link OccupationSupplementaireService#create}.</li>
 * </ol>
 *
 * <p><b>Une seule logique de disponibilité</b> : ce service ne réimplémente
 * aucune règle de conflit. Chaque ligne est validée par
 * {@link OccupationSupplementaireService#dryRunImportErrors}, qui délègue au
 * moteur central ({@code AvailabilityEngine} + {@code WorkingCalendarService}) —
 * donc emplois du temps réguliers, réservations bloquantes ET autres occupations
 * supplémentaires (examens, soutenances, autres) sont pris en compte de la même
 * façon que pour une réservation. Seuls les conflits <em>internes au fichier</em>
 * sont détectés ici, puisqu'aucune ligne n'est encore enregistrée en phase 1.</p>
 *
 * <p><b>Contexte hors fichier</b> (§5) : la catégorie (soutenance / autre) et,
 * éventuellement, la filière et la promotion sont choisies dans l'interface et
 * rattachées automatiquement à chaque ligne. Le fichier ne contient que
 * l'événement : date, heures libres, salle, type, intitulé, groupe facultatif,
 * responsable et description.</p>
 *
 * <p><b>Aucune création implicite</b> (§21/§29) : salles et groupes sont résolus
 * par correspondance et jamais créés. Une valeur inconnue est une erreur de
 * ligne, pas un nouvel enregistrement.</p>
 *
 * <p><b>Sécurité (§12)</b> : une soutenance exige une filière accessible ; une
 * occupation « autre » sans filière est réservée à l'administrateur. Le contrôle
 * est fait ici à l'entrée (message clair) ET rejoué pour chaque ligne par le
 * service métier — rien n'est laissé au frontend.</p>
 */
@Service
@RequiredArgsConstructor
public class OccupationImportService {

    /** Module tracé dans le journal d'audit (§34). */
    private static final String MODULE = "Occupations supplémentaires";

    /**
     * En-têtes attendus, dans cet ordre, pour les deux catégories : un seul
     * format de fichier évite de faire diverger deux analyseurs.
     */
    private static final String[] HEADERS = {
            "Date", "Heure début", "Heure fin", "Salle (code)", "Type",
            "Intitulé", "Groupe (optionnel)", "Responsable (optionnel)",
            "Description / motif"
    };

    private static final int COL_DATE = 0;
    private static final int COL_DEBUT = 1;
    private static final int COL_FIN = 2;
    private static final int COL_SALLE = 3;
    private static final int COL_TYPE = 4;
    private static final int COL_INTITULE = 5;
    private static final int COL_GROUPE = 6;
    private static final int COL_RESPONSABLE = 7;
    private static final int COL_DESCRIPTION = 8;

    /** Longueurs maximales des colonnes texte (mêmes bornes que l'entité). */
    private static final int MAX_INTITULE = 180;
    private static final int MAX_RESPONSABLE = 150;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final SpaceRepository spaceRepository;
    private final ProgramRepository programRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;
    private final OccupationSupplementaireService occupationService;
    private final WorkingCalendarService workingCalendar;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccessScopeService accessScope;
    private final ImportPolicyService importPolicy;

    // ----- Phase 1 : prévisualisation (aucun enregistrement) -----

    @Transactional(readOnly = true)
    public OccupationImportPreviewDto preview(OccupationCategorie categorie, Long programId,
                                              Long promotionId, MultipartFile file) {
        ImportContext ctx = resolveContext(categorie, programId, promotionId);
        List<ParsedRow> rows = analyze(ctx, file);
        return buildPreview(ctx, originalFilename(file), rows);
    }

    // ----- Phase 2 : confirmation (transactionnelle) -----

    @Transactional
    public OccupationImportResultDto confirm(OccupationCategorie categorie, Long programId,
                                             Long promotionId, MultipartFile file) {
        ImportContext ctx = resolveContext(categorie, programId, promotionId);
        List<ParsedRow> rows = analyze(ctx, file);

        if (rows.isEmpty()) {
            throw new BadRequestException("Aucune occupation détectée dans le fichier.");
        }
        long enErreur = rows.stream().filter(r -> !r.isValid()).count();
        // Validation automatique (Module 11, §9) : active (defaut), refus en bloc ;
        // desactivee, seules les lignes valides sont enregistrees.
        if (enErreur > 0 && importPolicy.isValidationStricte()) {
            throw new BadRequestException("Le fichier contient " + enErreur
                    + " ligne(s) en erreur : corrigez-les puis relancez l'import. "
                    + "Aucune occupation n'a été enregistrée.");
        }
        List<ParsedRow> retenues = rows.stream().filter(ParsedRow::isValid).toList();
        if (retenues.isEmpty()) {
            throw new BadRequestException("Aucune ligne valide dans le fichier : "
                    + enErreur + " ligne(s) en erreur. Aucune occupation n'a été enregistrée.");
        }

        int saved = 0;
        for (ParsedRow pr : retenues) {
            // Persistance via le service métier : il re-résout le contexte,
            // re-applique le périmètre et re-vérifie la disponibilité (verrou
            // pessimiste compris). Toute erreur résiduelle — deux imports
            // concurrents, par exemple — fait échouer la transaction entière.
            occupationService.create(toRequest(ctx, pr));
            saved++;
        }

        String fileName = originalFilename(file);
        String lignesIgnorees = enErreur == 0 ? ""
                : String.format(" — %d ligne(s) en erreur ignorée(s)", enErreur);
        String description = String.format(
                "Import d'occupations supplémentaires (%s%s) depuis '%s' : %d occupation(s) enregistrée(s)%s",
                categorieLabel(ctx.categorie).toLowerCase(),
                ctx.program != null ? " / " + ctx.program.getNom() : " / sans filière",
                fileName, saved, lignesIgnorees);
        auditService.record(AuditAction.EXCEL_IMPORT, MODULE, description);
        notificationService.notifyUser(accessScope.getCurrentUser(),
                NotificationType.SCHEDULE_IMPORTED,
                "Import d'occupations supplémentaires terminé", description, deepLink(ctx));

        return OccupationImportResultDto.builder()
                .fileName(fileName)
                .occupationsEnregistrees(saved)
                .message(saved + " occupation(s) enregistrée(s)."
                        + (enErreur == 0 ? ""
                        : " " + enErreur + " ligne(s) en erreur ont été ignorées"
                        + " (validation automatique désactivée)."))
                .build();
    }

    /** Construit la requête d'occupation (IDs résolus) attendue par le service métier. */
    private OccupationRequestDto toRequest(ImportContext ctx, ParsedRow pr) {
        OccupationRequestDto request = new OccupationRequestDto();
        request.setType(pr.type);
        request.setIntitule(pr.intitule);
        request.setDate(pr.date);
        request.setHeureDebut(pr.debut);
        request.setHeureFin(pr.fin);
        request.setSpaceId(pr.space != null ? pr.space.getId() : null);
        request.setProgramId(ctx.program != null ? ctx.program.getId() : null);
        request.setPromotionId(ctx.promotion != null ? ctx.promotion.getId() : null);
        request.setGroupId(pr.group != null ? pr.group.getId() : null);
        request.setResponsable(pr.responsable);
        request.setCommentaire(pr.description);
        return request;
    }

    // ----- Analyse (partagée entre preview et confirm) -----

    /**
     * Analyse le fichier ligne par ligne : structure (date, heures, type, salle,
     * groupe), puis conflits — DÉLÉGUÉS au moteur central via
     * {@link OccupationSupplementaireService#dryRunImportErrors} — et enfin
     * conflits internes au fichier. Toutes les erreurs d'une ligne sont
     * collectées : on ne s'arrête pas à la première.
     */
    private List<ParsedRow> analyze(ImportContext ctx, MultipartFile file) {
        // Taille, extension ET signature binaire, selon les parametres d'import (§9).
        importPolicy.verifierFichier(file, "des occupations supplémentaires");

        RefData ref = new RefData(
                spaceRepository.findAll(),
                ctx.promotion != null
                        ? groupRepository.findByPromotionId(ctx.promotion.getId())
                        : List.of());
        List<ParsedRow> rows = new ArrayList<>();
        List<ParsedRow> accepted = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = getDataSheet(workbook, ctx);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                ParsedRow pr = new ParsedRow();
                pr.rowNumber = rowIndex + 1;
                pr.dateRaw = readDateCell(row, COL_DATE);
                pr.debutRaw = readTimeCell(row, COL_DEBUT);
                pr.finRaw = readTimeCell(row, COL_FIN);
                pr.salleRaw = getCellValue(row, COL_SALLE);
                pr.typeRaw = getCellValue(row, COL_TYPE);
                pr.intituleRaw = getCellValue(row, COL_INTITULE);
                pr.groupeRaw = getCellValue(row, COL_GROUPE);
                pr.responsableRaw = getCellValue(row, COL_RESPONSABLE);
                pr.descriptionRaw = getCellValue(row, COL_DESCRIPTION);

                parseStructure(pr, ctx, ref);
                if (pr.isValid()) {
                    // Jour ouvrable, horaires d'ouverture, durée minimale et
                    // occupation réelle de la salle : source unique de vérité.
                    for (String err : occupationService.dryRunImportErrors(toRequest(ctx, pr))) {
                        pr.errors.add(err);
                        pr.conflit = true;
                    }
                }
                if (pr.isValid()) {
                    checkFileConflicts(pr, accepted);
                }
                if (pr.isValid()) {
                    accepted.add(pr);
                }
                rows.add(pr);
            }
        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + e.getMessage());
        }

        return rows;
    }

    /**
     * Analyse structurelle d'une ligne : date, heures libres, type, salle, groupe
     * et champs texte. Les bornes d'ouverture et la durée minimale ne sont PAS
     * vérifiées ici — c'est le moteur central qui s'en charge.
     */
    private void parseStructure(ParsedRow pr, ImportContext ctx, RefData ref) {
        try {
            pr.date = parseDate(pr.dateRaw);
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.dateInvalide = true;
        }
        try {
            pr.debut = parseTime(pr.debutRaw, "heure de début");
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.heureInvalide = true;
        }
        try {
            pr.fin = parseTime(pr.finRaw, "heure de fin");
        } catch (IllegalArgumentException e) {
            pr.errors.add(e.getMessage());
            pr.heureInvalide = true;
        }
        if (pr.debut != null && pr.fin != null && !pr.fin.isAfter(pr.debut)) {
            pr.errors.add("L'heure de fin doit être postérieure à l'heure de début.");
            pr.heureInvalide = true;
        }

        resolveType(pr, ctx);
        resolveSalle(pr, ref.spaces);
        resolveGroupe(pr, ctx, ref);

        pr.intitule = trimToNull(pr.intituleRaw);
        pr.responsable = trimToNull(pr.responsableRaw);
        pr.description = trimToNull(pr.descriptionRaw);

        // Une occupation « autre » n'a que son intitulé pour être identifiable :
        // il est donc exigé. Pour une soutenance, il reste facultatif (le sujet).
        if (pr.intitule == null && ctx.categorie == OccupationCategorie.AUTRE) {
            pr.errors.add("L'intitulé de l'activité est obligatoire.");
        }
        if (pr.intitule != null && pr.intitule.length() > MAX_INTITULE) {
            pr.errors.add("L'intitulé ne peut dépasser " + MAX_INTITULE + " caractères.");
        }
        if (pr.responsable != null && pr.responsable.length() > MAX_RESPONSABLE) {
            pr.errors.add("Le responsable ne peut dépasser " + MAX_RESPONSABLE + " caractères.");
        }
    }

    /**
     * Résout le type d'occupation dans la <b>catégorie importée</b> : un fichier de
     * soutenances ne peut donc pas glisser un « Examen », et réciproquement. La
     * colonne est facultative : vide, elle prend le type par défaut de la
     * catégorie. Libellé français ou nom technique acceptés, accents et casse
     * ignorés.
     */
    private void resolveType(ParsedRow pr, ImportContext ctx) {
        if (isBlank(pr.typeRaw)) {
            pr.type = ctx.defaultType;
            return;
        }
        String needle = normalizeType(pr.typeRaw);
        OccupationType match = ctx.typesAutorises.stream()
                .filter(t -> normalizeType(t.getLibelle()).equals(needle)
                        || normalizeType(t.name()).equals(needle))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Type d'occupation « " + pr.typeRaw.trim()
                    + " » inconnu pour cette catégorie. Valeurs acceptées : "
                    + libellesAutorises(ctx) + ".");
            pr.typeInvalide = true;
        } else {
            pr.type = match;
        }
    }

    /** Résout la salle par code. Une occupation porte TOUJOURS sur une salle. */
    private void resolveSalle(ParsedRow pr, List<Space> allSpaces) {
        String code = isBlank(pr.salleRaw) ? "" : pr.salleRaw.trim();
        if (code.isBlank()) {
            pr.errors.add("Une occupation doit indiquer une salle (code).");
            pr.salleManquante = true;
            return;
        }
        Space match = allSpaces.stream()
                .filter(s -> s.getCode() != null && s.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucune salle trouvée avec le code « " + code + " ».");
            pr.salleInexistante = true;
            return;
        }
        // §5/§21/§23 : une salle inactive — ou dont l'étage/bâtiment est désactivé —
        // ne peut pas accueillir une nouvelle occupation importée. On utilise
        // reason(...) (non levant) pour nommer la cause EXACTE et joindre l'erreur
        // aux autres erreurs de la ligne. Redondant avec la garde du service métier
        // (via dryRunImportErrors) mais catégorise l'erreur en « salle » dès la
        // phase structurelle, et couvre le cas d'un parent (étage/bâtiment) inactif
        // que l'ancien contrôle isActif() seul manquait.
        String motifSalle = ReferentialStatus.reason(match);
        if (motifSalle != null) {
            pr.errors.add(motifSalle);
            pr.salleInexistante = true;
            return;
        }
        pr.space = match;
    }

    /**
     * Résout le groupe (facultatif) dans la promotion du contexte. Vide = toute la
     * promotion, ou aucun public précis si le contexte n'en désigne pas.
     */
    private void resolveGroupe(ParsedRow pr, ImportContext ctx, RefData ref) {
        if (isBlank(pr.groupeRaw)) {
            pr.group = null;
            return;
        }
        if (ctx.promotion == null) {
            pr.errors.add("Un groupe ne peut être précisé que si une promotion est "
                    + "choisie dans le contexte d'import : laissez la colonne vide.");
            pr.groupeInexistant = true;
            return;
        }
        String needle = stripAccents(pr.groupeRaw.trim());
        Group match = ref.groups.stream()
                .filter(g -> stripAccents(g.getNom()).equalsIgnoreCase(needle))
                .findFirst()
                .orElse(null);
        if (match == null) {
            pr.errors.add("Aucun groupe « " + pr.groupeRaw.trim() + " » dans la promotion "
                    + ctx.promotion.getNom() + " (laissez vide pour toute la promotion).");
            pr.groupeInexistant = true;
        } else {
            pr.group = match;
        }
    }

    /**
     * Conflits INTERNES au fichier : deux lignes qui occupent la <b>même salle</b>
     * le même jour sur des horaires qui se chevauchent. Le public n'est
     * volontairement pas confronté ici : plusieurs soutenances d'une même
     * promotion se tiennent normalement en parallèle dans des salles différentes.
     */
    private void checkFileConflicts(ParsedRow pr, List<ParsedRow> accepted) {
        for (ParsedRow other : accepted) {
            if (!pr.date.equals(other.date)
                    || !pr.space.getId().equals(other.space.getId())
                    || !overlaps(pr.debut, pr.fin, other.debut, other.fin)) {
                continue;
            }
            pr.errors.add("Conflit de salle avec la ligne " + other.rowNumber
                    + " du fichier (même salle, horaires qui se chevauchent).");
            pr.conflit = true;
            return;
        }
    }

    // ----- Construction du rapport de prévisualisation -----

    private OccupationImportPreviewDto buildPreview(ImportContext ctx, String fileName,
                                                    List<ParsedRow> rows) {
        List<OccupationImportRowDto> lignes = new ArrayList<>();
        int valides = 0;
        int enErreur = 0;
        int conflits = 0;
        int sallesInexistantes = 0;
        int sallesManquantes = 0;
        int datesInvalides = 0;
        int heuresInvalides = 0;
        int typesInvalides = 0;
        int groupesInexistants = 0;
        for (ParsedRow pr : rows) {
            lignes.add(toRowDto(pr));
            if (pr.isValid()) {
                valides++;
            } else {
                enErreur++;
            }
            if (pr.conflit) {
                conflits++;
            }
            if (pr.salleInexistante) {
                sallesInexistantes++;
            }
            if (pr.salleManquante) {
                sallesManquantes++;
            }
            if (pr.dateInvalide) {
                datesInvalides++;
            }
            if (pr.heureInvalide) {
                heuresInvalides++;
            }
            if (pr.typeInvalide) {
                typesInvalides++;
            }
            if (pr.groupeInexistant) {
                groupesInexistants++;
            }
        }

        return OccupationImportPreviewDto.builder()
                .fileName(fileName)
                .categorie(categorieLabel(ctx.categorie))
                .filiere(ctx.program != null ? ctx.program.getNom() : null)
                .promotion(ctx.promotion != null ? ctx.promotion.getNom() : null)
                .occupationsDetectees(rows.size())
                .occupationsValides(valides)
                .lignesEnErreur(enErreur)
                .conflits(conflits)
                .sallesInexistantes(sallesInexistantes)
                .sallesManquantes(sallesManquantes)
                .datesInvalides(datesInvalides)
                .heuresInvalides(heuresInvalides)
                .typesInvalides(typesInvalides)
                .groupesInexistants(groupesInexistants)
                // Confirmable seulement si au moins une ligne et AUCUNE erreur (§8).
                .confirmable(!rows.isEmpty() && enErreur == 0)
                .lignes(lignes)
                .build();
    }

    private OccupationImportRowDto toRowDto(ParsedRow pr) {
        return OccupationImportRowDto.builder()
                .ligne(pr.rowNumber)
                .date(pr.dateRaw)
                .heureDebut(pr.debutRaw)
                .heureFin(pr.finRaw)
                .salle(pr.salleRaw)
                .type(pr.typeRaw)
                .intitule(pr.intituleRaw)
                .groupe(pr.groupeRaw)
                .responsable(pr.responsableRaw)
                .description(pr.descriptionRaw)
                .valide(pr.isValid())
                .erreurs(new ArrayList<>(pr.errors))
                .build();
    }

    // ----- Modèle Excel contextualisé (§24/§25) -----

    /**
     * Génère le modèle d'import pour une catégorie donnée : une feuille de saisie
     * (« Soutenances » ou « Autres occupations »), une feuille « Instructions »
     * (règles + rappel du contexte, qui n'est PAS à saisir) et une feuille
     * « Valeurs autorisées » (types de la catégorie, salles, groupes, horaires
     * d'ouverture). Aucun nom d'établissement ni de personne codé en dur (§16/§26).
     */
    @Transactional(readOnly = true)
    public byte[] generateTemplate(OccupationCategorie categorie, Long programId, Long promotionId) {
        ImportContext ctx = resolveContext(categorie, programId, promotionId);

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            buildDataSheet(workbook, headerStyle, ctx);
            buildInstructionsSheet(workbook, headerStyle, ctx);
            buildAllowedValuesSheet(workbook, headerStyle, ctx);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la génération du modèle Excel.");
        }
    }

    private void buildDataSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet(sheetName(ctx.categorie));
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }
        for (String[] example : exampleRows(ctx)) {
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            for (int c = 0; c < example.length; c++) {
                row.createCell(c).setCellValue(example[c]);
            }
        }
    }

    /**
     * Deux lignes d'exemple cohérentes avec la catégorie et avec les données
     * réelles de l'établissement (première salle active, premiers créneaux, premier
     * groupe de la promotion). Les intitulés restent <b>non nominatifs</b> (§16).
     */
    private String[][] exampleRows(ImportContext ctx) {
        List<TimeSlot> slots = workingCalendar.creneauxActifs();
        LocalTime open = workingCalendar.openingTime();
        String debut1 = slots.isEmpty() ? open.format(HOUR) : slots.get(0).getHeureDebut().format(HOUR);
        String fin1 = slots.isEmpty()
                ? open.plusMinutes(Math.max(60, workingCalendar.minSlotMinutes())).format(HOUR)
                : slots.get(0).getHeureFin().format(HOUR);
        String debut2 = slots.size() < 2 ? fin1 : slots.get(1).getHeureDebut().format(HOUR);
        String fin2 = slots.size() < 2
                ? LocalTime.parse(fin1, HOUR).plusMinutes(Math.max(60, workingCalendar.minSlotMinutes())).format(HOUR)
                : slots.get(1).getHeureFin().format(HOUR);
        String salle = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(Space::getCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse("CODE-SALLE");
        String groupe = (ctx.promotion == null) ? "" : groupRepository
                .findByPromotionId(ctx.promotion.getId()).stream()
                .map(Group::getNom)
                .findFirst()
                .orElse("");
        String jour = LocalDate.now().plusDays(7).format(DAY);

        if (ctx.categorie == OccupationCategorie.SOUTENANCE) {
            return new String[][]{
                    {jour, debut1, fin1, salle, "Soutenance", "Soutenance de PFE — jury 1",
                            groupe, "Président du jury", "Prévoir un vidéoprojecteur"},
                    {jour, debut2, fin2, salle, "Soutenance", "Soutenance de stage — jury 2",
                            "", "", "Groupe laissé vide : toute la promotion"}
            };
        }
        return new String[][]{
                {jour, debut1, fin1, salle, "Réunion", "Réunion pédagogique de filière",
                        "", "Coordination pédagogique", "Salle à disposition sur toute la plage"},
                {jour, debut2, fin2, salle, "Conférence", "Conférence d'ouverture",
                        groupe, "Club scientifique", "Prévoir sonorisation"}
        };
    }

    private void buildInstructionsSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Instructions");
        sheet.setColumnWidth(0, 12000);

        int r = 0;
        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Comment remplir ce modèle — " + categorieLabel(ctx.categorie));
        titleCell.setCellStyle(headerStyle);

        String feuille = sheetName(ctx.categorie);
        String ouverture = workingCalendar.openingTime().format(HOUR)
                + " à " + workingCalendar.closingTime().format(HOUR);
        String intituleRegle = (ctx.categorie == OccupationCategorie.AUTRE)
                ? "OBLIGATOIRE : il identifie l'activité (réunion, conférence, activité de club...)."
                : "facultatif : sujet ou numéro de jury.";

        String[] lines = {
                "",
                "1. Renseignez UNE occupation par ligne dans la feuille « " + feuille + " ».",
                "2. Colonnes obligatoires : Date, Heure début, Heure fin, Salle (code)"
                        + (ctx.categorie == OccupationCategorie.AUTRE ? ", Intitulé." : "."),
                "3. La Date est au format JJ/MM/AAAA (exemple : " + LocalDate.now().plusDays(7).format(DAY) + ").",
                "4. Les heures sont LIBRES au format HH:mm (exemple : 09:00) : aucun créneau officiel",
                "   n'est imposé. Elles doivent tenir dans les horaires d'ouverture (" + ouverture + ")",
                "   et durer au moins " + workingCalendar.minSlotMinutes() + " minutes.",
                "5. La Salle (code) est OBLIGATOIRE : reprenez un code existant de la feuille",
                "   « Valeurs autorisées ». L'import ne crée jamais de salle.",
                "6. La colonne Type est facultative : laissée vide, elle vaut « "
                        + ctx.defaultType.getLibelle() + " ». Les seules valeurs acceptées ici sont",
                "   celles de la feuille « Valeurs autorisées » — un examen s'importe depuis",
                "   l'onglet « Planning examens ».",
                "7. L'Intitulé est " + intituleRegle,
                "   N'y indiquez JAMAIS le nom d'un étudiant ou d'un enseignant.",
                "8. Le Groupe est facultatif : vide = toute la promotion. Il n'est exploitable que si",
                "   une promotion a été choisie dans l'application avant l'import.",
                "9. Le Responsable et la Description sont facultatifs (organisateur, motif, consignes).",
                "10. Les accents et la casse sont ignorés lors de l'import.",
                "",
                "Détection des conflits",
                "   Une salle est refusée si elle est déjà occupée sur le créneau demandé par un emploi",
                "   du temps, une réservation acceptée, un examen, une soutenance ou une autre",
                "   occupation : c'est la même logique centrale que la recherche de salle libre.",
                "   Les jours non ouvrables (week-end, jours fériés, fermetures) sont également refusés.",
                "",
                "Import en deux temps",
                "   Le fichier est d'abord ANALYSÉ sans rien enregistrer : vous voyez le détail ligne par",
                "   ligne. Rien n'est enregistré tant qu'une seule ligne reste en erreur.",
                "",
                "Rappel du contexte (déjà sélectionné dans l'application — NE PAS le saisir ici) :",
                "   • Catégorie : " + categorieLabel(ctx.categorie),
                "   • Filière : " + (ctx.program != null ? ctx.program.getNom() : "aucune (occupation non rattachée)"),
                "   • Promotion : " + (ctx.promotion != null ? ctx.promotion.getNom() : "aucune")
        };
        for (String line : lines) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
    }

    private void buildAllowedValuesSheet(Workbook workbook, CellStyle headerStyle, ImportContext ctx) {
        Sheet sheet = workbook.createSheet("Valeurs autorisées");

        String[] headers = {"Types acceptés", "Salles disponibles (code — nom)",
                "Groupes de la promotion", "Horaires d'ouverture"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 8000);
        }

        List<String> types = ctx.typesAutorises.stream()
                .map(OccupationType::getLibelle)
                .toList();
        List<String> salles = spaceRepository.findAll().stream()
                .filter(Space::isActif)
                .map(s -> s.getCode() + " — " + s.getNom())
                .sorted()
                .toList();
        List<String> groupes = (ctx.promotion == null) ? List.of() : groupRepository
                .findByPromotionId(ctx.promotion.getId()).stream()
                .map(Group::getNom)
                .sorted()
                .toList();
        List<String> horaires = List.of(
                "Ouverture : " + workingCalendar.openingTime().format(HOUR),
                "Fermeture : " + workingCalendar.closingTime().format(HOUR),
                "Durée minimale : " + workingCalendar.minSlotMinutes() + " min");

        int maxRows = List.of(types.size(), salles.size(), groupes.size(), horaires.size())
                .stream().max(Integer::compareTo).orElse(0);
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < types.size()) {
                row.createCell(0).setCellValue(types.get(i));
            }
            if (i < salles.size()) {
                row.createCell(1).setCellValue(salles.get(i));
            }
            if (i < groupes.size()) {
                row.createCell(2).setCellValue(groupes.get(i));
            }
            if (i < horaires.size()) {
                row.createCell(3).setCellValue(horaires.get(i));
            }
        }
    }

    // ----- Contexte d'import -----

    /**
     * Résout et valide le contexte : catégorie importable (jamais {@code EXAMEN}),
     * filière et promotion facultatives mais cohérentes entre elles, puis contrôle
     * d'accès (§12). Aucune entité n'est créée ici.
     */
    private ImportContext resolveContext(OccupationCategorie categorie, Long programId,
                                         Long promotionId) {
        if (categorie == null || categorie == OccupationCategorie.EXAMEN) {
            throw new BadRequestException("Seules les catégories « soutenance » et « autre » "
                    + "s'importent ici : un planning d'examens s'importe depuis l'onglet "
                    + "« Planning examens ».");
        }

        ImportContext ctx = new ImportContext();
        ctx.categorie = categorie;
        ctx.typesAutorises = OccupationType.ofCategorie(categorie);
        ctx.defaultType = (categorie == OccupationCategorie.SOUTENANCE)
                ? OccupationType.SOUTENANCE : OccupationType.AUTRE;

        if (programId != null) {
            ctx.program = programRepository.findById(programId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Filière introuvable avec l'id " + programId));
        }
        if (promotionId != null) {
            ctx.promotion = promotionRepository.findById(promotionId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Promotion introuvable avec l'id " + promotionId));
            if (ctx.program != null && (ctx.promotion.getProgram() == null
                    || !ctx.promotion.getProgram().getId().equals(ctx.program.getId()))) {
                throw new BadRequestException(
                        "La promotion sélectionnée n'appartient pas à la filière indiquée.");
            }
            if (ctx.program == null) {
                ctx.program = ctx.promotion.getProgram();
            }
        }

        if (ctx.categorie == OccupationCategorie.SOUTENANCE && ctx.program == null) {
            throw new BadRequestException(
                    "Un planning de soutenances doit être rattaché à une filière.");
        }
        // Périmètre (§12) : contrôlé dès l'entrée pour un message net, puis rejoué
        // ligne par ligne par le service métier.
        if (ctx.program != null) {
            accessScope.assertProgramAccessible(ctx.program.getId());
        } else {
            accessScope.requireAdmin();
        }
        return ctx;
    }

    /** Libellé lisible de la catégorie, utilisé dans les rapports et les modèles. */
    private String categorieLabel(OccupationCategorie categorie) {
        return (categorie == OccupationCategorie.SOUTENANCE) ? "Soutenances" : "Autres occupations";
    }

    /** Nom de la feuille de saisie attendue dans le fichier. */
    private String sheetName(OccupationCategorie categorie) {
        return (categorie == OccupationCategorie.SOUTENANCE) ? "Soutenances" : "Autres occupations";
    }

    /** Lien profond vers l'onglet concerné du module « Occupation supplémentaire ». */
    private String deepLink(ImportContext ctx) {
        return (ctx.categorie == OccupationCategorie.SOUTENANCE)
                ? "/occupations?onglet=soutenances" : "/occupations?onglet=autre";
    }

    private String libellesAutorises(ImportContext ctx) {
        return ctx.typesAutorises.stream()
                .map(OccupationType::getLibelle)
                .collect(Collectors.joining(", "));
    }

    // ----- Utilitaires de parsing -----

    /**
     * Feuille de saisie : celle qui porte le nom attendu (accents et casse
     * ignorés), sinon la première — un fichier renommé reste ainsi importable.
     */
    private Sheet getDataSheet(Workbook workbook, ImportContext ctx) {
        String expected = stripAccents(sheetName(ctx.categorie));
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (stripAccents(sheet.getSheetName().trim()).equalsIgnoreCase(expected)) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    /** Date au format {@code JJ/MM/AAAA} (ou {@code J/M/AAAA}, ou ISO par robustesse). */
    private LocalDate parseDate(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("La date de l'occupation est obligatoire.");
        }
        String value = raw.trim();
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{DAY, DAY_SHORT}) {
            try {
                return LocalDate.parse(value, fmt);
            } catch (Exception ignored) {
                // essai suivant
            }
        }
        try {
            return LocalDate.parse(value); // ISO yyyy-MM-dd
        } catch (Exception e) {
            throw new IllegalArgumentException("La date « " + raw
                    + " » n'est pas valide (format attendu JJ/MM/AAAA).");
        }
    }

    /**
     * Heure au format {@code HH:mm}. Tolère « 9h30 » et la <b>fraction de journée</b>
     * qu'Excel renvoie parfois pour une cellule d'heure (0,354166… = 08:30).
     */
    private LocalTime parseTime(String raw, String label) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("L'" + label + " est obligatoire.");
        }
        String value = raw.trim().replace('h', ':').replace('H', ':');
        if (value.matches("\\d*[.,]\\d+")) {
            try {
                return LocalTime.parse(fractionToTime(
                        Double.parseDouble(value.replace(',', '.'))), HOUR);
            } catch (Exception ignored) {
                // on laisse la validation standard ci-dessous produire le message
            }
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e1) {
            try {
                return LocalTime.parse(value);
            } catch (Exception e2) {
                throw new IllegalArgumentException("L'" + label + " « " + raw
                        + " » n'est pas valide (format attendu HH:mm).");
            }
        }
    }

    /** Lit une cellule de date et renvoie « JJ/MM/AAAA » (cellule date Excel ou texte). */
    private String readDateCell(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt != null) {
                        yield dt.toLocalDate().format(DAY);
                    }
                }
                // Nombre brut : renvoyé tel quel, parseDate signalera l'erreur.
                double numeric = cell.getNumericCellValue();
                yield (numeric == Math.floor(numeric))
                        ? String.valueOf((long) numeric) : String.valueOf(numeric);
            }
            case STRING -> cell.getStringCellValue().trim();
            default -> "";
        };
    }

    /**
     * Lit une cellule d'heure et renvoie « HH:mm ». Une cellule d'heure Excel est
     * stockée comme une FRACTION DE JOURNÉE : elle est normalisée ici, avant toute
     * validation, sinon « 08:30 » arriverait sous la forme « 0.354166666666667 ».
     */
    private String readTimeCell(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dt = cell.getLocalDateTimeCellValue();
                    if (dt != null) {
                        yield dt.toLocalTime().format(HOUR);
                    }
                }
                yield fractionToTime(cell.getNumericCellValue());
            }
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Convertit une fraction de journée Excel en heure « HH:mm » (arrondi minute). */
    private String fractionToTime(double numeric) {
        double fraction = numeric - Math.floor(numeric);
        long totalMinutes = Math.round(fraction * 24 * 60);
        totalMinutes = ((totalMinutes % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    /** Chevauchement demi-ouvert {@code [debut, fin)}, convention commune au projet (§9). */
    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private String normalizeType(String value) {
        return stripAccents(value).trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (!getCellValue(row, i).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numeric = cell.getNumericCellValue();
                if (numeric == Math.floor(numeric)) {
                    yield String.valueOf((long) numeric);
                }
                yield String.valueOf(numeric);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String originalFilename(MultipartFile file) {
        return (file != null && file.getOriginalFilename() != null)
                ? file.getOriginalFilename() : "import_occupations.xlsx";
    }

    // ----- Structures internes -----

    /** Contexte d'import résolu : catégorie, types acceptés, filière/promotion. */
    private static class ImportContext {
        private OccupationCategorie categorie;
        private List<OccupationType> typesAutorises;
        private OccupationType defaultType;
        private Program program;     // nullable pour la catégorie AUTRE
        private Promotion promotion; // nullable
    }

    /** Référentiels chargés une seule fois pour toute l'analyse (§21). */
    private static class RefData {
        private final List<Space> spaces;
        private final List<Group> groups;

        private RefData(List<Space> spaces, List<Group> groups) {
            this.spaces = spaces;
            this.groups = groups;
        }
    }

    /** État d'analyse d'une ligne (valeurs brutes + résolues + erreurs). */
    private static class ParsedRow {
        private int rowNumber;
        private String dateRaw;
        private String debutRaw;
        private String finRaw;
        private String salleRaw;
        private String typeRaw;
        private String intituleRaw;
        private String groupeRaw;
        private String responsableRaw;
        private String descriptionRaw;

        private LocalDate date;
        private LocalTime debut;
        private LocalTime fin;
        private OccupationType type;
        private Space space;
        private Group group; // nullable : null = toute la promotion
        private String intitule;
        private String responsable;
        private String description;

        private final List<String> errors = new ArrayList<>();
        private boolean conflit;
        private boolean salleInexistante;
        private boolean salleManquante;
        private boolean dateInvalide;
        private boolean heureInvalide;
        private boolean typeInvalide;
        private boolean groupeInexistant;

        private boolean isValid() {
            return errors.isEmpty();
        }
    }













}

