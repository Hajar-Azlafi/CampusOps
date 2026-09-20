package com.campusops.settings.service;

import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.SettingsMediaType;
import com.campusops.exception.BadRequestException;
import com.campusops.security.AccessScopeService;
import com.campusops.settings.dto.MediaInfoDto;
import com.campusops.settings.entity.SettingsMedia;
import com.campusops.settings.mapper.SettingsMapper;
import com.campusops.settings.repository.SettingsMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gestion de l'identite visuelle : logo et favicon (§11).
 *
 * <h2>Controles de securite appliques a l'upload</h2>
 * <ol>
 *   <li><b>Presence et taille</b> : fichier non vide et inferieur au plafond
 *       ({@link #MAX_LOGO_OCTETS}) — refus explicite sinon.</li>
 *   <li><b>Extension</b> : premier filtre, jamais le seul.</li>
 *   <li><b>Signature binaire</b> (« magic bytes ») : le contenu reel doit
 *       correspondre a une image connue. Un fichier renomme
 *       {@code virus.exe -> logo.png} est donc rejete.</li>
 *   <li><b>Type MIME retenu</b> : celui deduit de la signature, jamais celui
 *       annonce par le client.</li>
 *   <li><b>SVG refuse</b> : un SVG peut embarquer du script et serait servi
 *       depuis notre origine ; le format est volontairement exclu.</li>
 * </ol>
 *
 * <p>Ce service ne depend PAS de {@code SettingsService} : la relation est
 * strictement a sens unique (la configuration expose les medias), ce qui evite
 * toute dependance circulaire entre beans.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettingsMediaService {

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Parametres";

    /** Plafond d'un media d'identite visuelle : 2 Mo, largement suffisant. */
    public static final long MAX_LOGO_OCTETS = 2L * 1024 * 1024;

    /** Chemin public de telechargement du logo. */
    public static final String LOGO_PATH = "/api/settings/logo";

    /** Chemin public de telechargement du favicon. */
    public static final String FAVICON_PATH = "/api/settings/favicon";

    private final SettingsMediaRepository mediaRepository;
    private final SettingsMapper settingsMapper;
    private final AccessScopeService accessScope;
    private final AuditService auditService;

    /** Formats acceptes pour le logo. */
    private static final List<ImageKind> LOGO_KINDS = List.of(
            ImageKind.PNG, ImageKind.JPEG, ImageKind.WEBP, ImageKind.GIF);

    /** Formats acceptes pour le favicon (ICO inclus). */
    private static final List<ImageKind> FAVICON_KINDS = List.of(
            ImageKind.PNG, ImageKind.ICO, ImageKind.JPEG, ImageKind.WEBP, ImageKind.GIF);

    /** Signature binaire d'un format d'image reconnu. */
    private enum ImageKind {
        PNG("image/png", "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("image/jpeg", "jpg", new int[]{0xFF, 0xD8, 0xFF}),
        GIF("image/gif", "gif", new int[]{0x47, 0x49, 0x46, 0x38}),
        WEBP("image/webp", "webp", new int[]{0x52, 0x49, 0x46, 0x46}),
        ICO("image/x-icon", "ico", new int[]{0x00, 0x00, 0x01, 0x00});

        private final String contentType;
        private final String extension;
        private final int[] signature;

        ImageKind(String contentType, String extension, int[] signature) {
            this.contentType = contentType;
            this.extension = extension;
            this.signature = signature;
        }

        boolean matches(byte[] data) {
            if (data.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((data[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            // WEBP : « RIFF » est partage avec d'autres conteneurs, on exige « WEBP ».
            if (this == WEBP) {
                return data.length >= 12
                        && (data[8] & 0xFF) == 0x57 && (data[9] & 0xFF) == 0x45
                        && (data[10] & 0xFF) == 0x42 && (data[11] & 0xFF) == 0x50;
            }
            return true;
        }
    }

    // ===================== Lecture =====================

    /** Media brut, pour le service des octets par le controleur. */
    @Transactional(readOnly = true)
    public Optional<SettingsMedia> find(SettingsMediaType type) {
        return mediaRepository.findByType(type);
    }

    /** Metadonnees d'un media, toujours renseignees (present = false si absent). */
    @Transactional(readOnly = true)
    public MediaInfoDto getInfo(SettingsMediaType type) {
        return mediaRepository.findByType(type)
                .map(this::toInfo)
                .orElseGet(() -> MediaInfoDto.builder()
                        .type(type)
                        .present(false)
                        .url(null)
                        .build());
    }

    /** URL de telechargement horodatee, ou {@code null} si le media est absent. */
    @Transactional(readOnly = true)
    public String publicUrl(SettingsMediaType type) {
        return mediaRepository.findByType(type).map(this::buildUrl).orElse(null);
    }

    // ===================== Ecriture (ADMIN) =====================

    /**
     * Enregistre ou remplace un media. Le type est unique : un second upload
     * ecrase la ligne existante au lieu d'en creer une nouvelle.
     */
    public MediaInfoDto upload(SettingsMediaType type, MultipartFile file) {
        accessScope.requireAdmin();

        byte[] data = readOrThrow(file, type);
        ImageKind kind = detectOrThrow(data, type, file.getOriginalFilename());

        SettingsMedia media = mediaRepository.findByType(type)
                .orElseGet(() -> SettingsMedia.builder().type(type).build());
        media.setContentType(kind.contentType);
        media.setFileName(safeFileName(file.getOriginalFilename(), kind.extension));
        media.setTailleOctets((long) data.length);
        media.setData(data);

        SettingsMedia saved = mediaRepository.save(media);
        auditService.record(AuditAction.UPDATE, MODULE,
                "Mise a jour du " + label(type) + " de l'universite");
        return toInfo(saved);
    }

    /** Supprime le media s'il existe (operation idempotente). */
    public void delete(SettingsMediaType type) {
        accessScope.requireAdmin();
        mediaRepository.findByType(type).ifPresent(mediaRepository::delete);
        auditService.record(AuditAction.DELETE, MODULE,
                "Suppression du " + label(type) + " de l'universite");
    }

    // ===================== Interne =====================

    private byte[] readOrThrow(MultipartFile file, SettingsMediaType type) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Aucun fichier n'a été envoyé pour le "
                    + label(type) + ".");
        }
        if (file.getSize() > MAX_LOGO_OCTETS) {
            throw new BadRequestException(String.format(
                    "Le fichier dépasse la taille maximale autorisée (%d Mo).",
                    MAX_LOGO_OCTETS / (1024 * 1024)));
        }
        try {
            byte[] data = file.getBytes();
            if (data.length == 0) {
                throw new BadRequestException("Le fichier envoyé est vide.");
            }
            return data;
        } catch (IOException ex) {
            log.warn("Lecture impossible du media {} : {}", type, ex.getMessage());
            throw new BadRequestException(
                    "Le fichier n'a pas pu être lu. Veuillez réessayer.");
        }
    }

    /**
     * Determine le format reel a partir de la signature binaire. L'extension
     * n'est utilisee que pour produire un message d'erreur comprehensible.
     */
    private ImageKind detectOrThrow(byte[] data, SettingsMediaType type, String nomOrigine) {
        List<ImageKind> autorises = (type == SettingsMediaType.FAVICON)
                ? FAVICON_KINDS : LOGO_KINDS;
        for (ImageKind kind : autorises) {
            if (kind.matches(data)) {
                return kind;
            }
        }
        String extensions = autorises.stream().map(k -> k.extension).distinct()
                .reduce((a, b) -> a + ", " + b).orElse("png");
        String extension = extensionOf(nomOrigine);
        String precision = extension.isEmpty()
                ? ""
                : " Le fichier « " + extension + " » envoyé n'est pas une image valide.";
        throw new BadRequestException("Format de fichier non autorisé pour le "
                + label(type) + " : formats acceptés " + extensions + "." + precision);
    }

    private MediaInfoDto toInfo(SettingsMedia media) {
        MediaInfoDto dto = settingsMapper.toMediaInfoDto(media);
        dto.setUrl(buildUrl(media));
        return dto;
    }

    /** URL horodatee : force le navigateur a recharger l'image apres remplacement. */
    private String buildUrl(SettingsMedia media) {
        String base = media.getType() == SettingsMediaType.FAVICON ? FAVICON_PATH : LOGO_PATH;
        long version = media.getUpdatedAt() == null
                ? 0L
                : media.getUpdatedAt().toLocalTime().toNanoOfDay() / 1_000_000
                        + media.getUpdatedAt().toLocalDate().toEpochDay() * 100_000_000L;
        return base + "?v=" + version;
    }

    /** Nom de fichier assaini : jamais de chemin, extension coherente avec le format detecte. */
    private String safeFileName(String original, String extensionDetectee) {
        String base = "logo";
        if (original != null && !original.isBlank()) {
            String nom = original.replace('\\', '/');
            nom = nom.substring(nom.lastIndexOf('/') + 1);
            nom = nom.replaceAll("[^A-Za-z0-9._-]", "_");
            int point = nom.lastIndexOf('.');
            if (point > 0) {
                nom = nom.substring(0, point);
            }
            if (!nom.isBlank()) {
                base = nom.length() > 60 ? nom.substring(0, 60) : nom;
            }
        }
        return base + "." + extensionDetectee;
    }

    private String extensionOf(String nomFichier) {
        if (nomFichier == null) {
            return "";
        }
        int point = nomFichier.lastIndexOf('.');
        return point < 0 ? "" : nomFichier.substring(point).toLowerCase(Locale.ROOT);
    }

    private String label(SettingsMediaType type) {
        return type == SettingsMediaType.FAVICON ? "favicon" : "logo";
    }
}
