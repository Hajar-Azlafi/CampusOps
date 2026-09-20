package com.campusops.settings.controller;

import com.campusops.enums.SettingsMediaType;
import com.campusops.settings.dto.DisplaySettingsDto;
import com.campusops.settings.dto.ImportSettingsDto;
import com.campusops.settings.dto.MediaInfoDto;
import com.campusops.settings.dto.NotificationSettingsDto;
import com.campusops.settings.dto.PublicBrandingDto;
import com.campusops.settings.dto.ReservationSettingsDto;
import com.campusops.settings.dto.SecuritySettingsDto;
import com.campusops.settings.dto.SettingsResponseDto;
import com.campusops.settings.dto.SettingsUpdateRequestDto;
import com.campusops.settings.dto.UniversitySettingsDto;
import com.campusops.settings.dto.WorkingHoursSettingsDto;
import com.campusops.settings.service.SettingsMediaService;
import com.campusops.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * API REST de la configuration globale (§13).
 *
 * <h2>Securite (§14)</h2>
 * <p>Aucune annotation {@code @PreAuthorize} : le projet fait porter le controle
 * de role par la couche service. Chaque lecture et chaque ecriture de
 * {@link SettingsService} / {@link SettingsMediaService} appelle
 * {@code accessScope.requireAdmin()}, qui leve {@code AccessDeniedException}
 * (traduite en 403 par le {@code GlobalExceptionHandler}). Masquer l'entree de
 * menu cote frontend ne suffit donc pas : un enseignant, un responsable
 * pedagogique ou un responsable de club qui appellerait ces URL directement
 * recoit un 403.</p>
 *
 * <h2>Les trois exceptions publiques</h2>
 * <p>{@code GET /api/settings/branding}, {@code GET /api/settings/logo} et
 * {@code GET /api/settings/favicon} sont volontairement accessibles sans
 * authentification : la page de connexion doit afficher le nom, le logo et les
 * couleurs de l'universite <em>avant</em> qu'un jeton existe. Elles n'exposent
 * que de l'identite visuelle, jamais un parametre metier.</p>
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingsMediaService mediaService;

    // ===================== Configuration complete =============================

    /** Toutes les sections en une requete : alimente la page Parametres. */
    @GetMapping
    public ResponseEntity<SettingsResponseDto> get() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    /**
     * Mise a jour partielle : seules les sections presentes dans le corps sont
     * appliquees, les autres restent inchangees.
     */
    @PutMapping
    public ResponseEntity<SettingsResponseDto> update(
            @Valid @RequestBody SettingsUpdateRequestDto request) {
        return ResponseEntity.ok(settingsService.updateAll(request));
    }

    // ===================== Sections ciblees ===================================

    @GetMapping("/university")
    public ResponseEntity<UniversitySettingsDto> getUniversity() {
        return ResponseEntity.ok(settingsService.getUniversity());
    }

    @PutMapping("/university")
    public ResponseEntity<UniversitySettingsDto> updateUniversity(
            @Valid @RequestBody UniversitySettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateUniversity(dto));
    }

    @GetMapping("/reservations")
    public ResponseEntity<ReservationSettingsDto> getReservations() {
        return ResponseEntity.ok(settingsService.getReservation());
    }

    @PutMapping("/reservations")
    public ResponseEntity<ReservationSettingsDto> updateReservations(
            @Valid @RequestBody ReservationSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateReservation(dto));
    }

    @GetMapping("/working-hours")
    public ResponseEntity<WorkingHoursSettingsDto> getWorkingHours() {
        return ResponseEntity.ok(settingsService.getWorkingHours());
    }

    @PutMapping("/working-hours")
    public ResponseEntity<WorkingHoursSettingsDto> updateWorkingHours(
            @Valid @RequestBody WorkingHoursSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateWorkingHours(dto));
    }

    @GetMapping("/security")
    public ResponseEntity<SecuritySettingsDto> getSecurity() {
        return ResponseEntity.ok(settingsService.getSecurity());
    }

    @PutMapping("/security")
    public ResponseEntity<SecuritySettingsDto> updateSecurity(
            @Valid @RequestBody SecuritySettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateSecurity(dto));
    }

    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> getNotifications() {
        return ResponseEntity.ok(settingsService.getNotification());
    }

    @PutMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> updateNotifications(
            @Valid @RequestBody NotificationSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateNotification(dto));
    }

    @GetMapping("/import")
    public ResponseEntity<ImportSettingsDto> getImport() {
        return ResponseEntity.ok(settingsService.getImport());
    }

    @PutMapping("/import")
    public ResponseEntity<ImportSettingsDto> updateImport(
            @Valid @RequestBody ImportSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateImport(dto));
    }

    @GetMapping("/display")
    public ResponseEntity<DisplaySettingsDto> getDisplay() {
        return ResponseEntity.ok(settingsService.getDisplay());
    }

    @PutMapping("/display")
    public ResponseEntity<DisplaySettingsDto> updateDisplay(
            @Valid @RequestBody DisplaySettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateDisplay(dto));
    }

    // ===================== Identite visuelle publique =========================

    /**
     * Nom, couleurs, theme et URL des medias, sans authentification : consomme
     * par la page de connexion et par le chargement initial du frontend.
     */
    @GetMapping("/branding")
    public ResponseEntity<PublicBrandingDto> getBranding() {
        return ResponseEntity.ok(settingsService.getPublicBranding());
    }

    // ===================== Logo & favicon (§11) ===============================

    /** Octets du logo, sans authentification (utilise dans la balise img). */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        return serve(SettingsMediaType.LOGO);
    }

    /** Octets du favicon, sans authentification. */
    @GetMapping("/favicon")
    public ResponseEntity<byte[]> favicon() {
        return serve(SettingsMediaType.FAVICON);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaInfoDto> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(mediaService.upload(SettingsMediaType.LOGO, file));
    }

    @PostMapping(value = "/favicon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaInfoDto> uploadFavicon(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(mediaService.upload(SettingsMediaType.FAVICON, file));
    }

    @DeleteMapping("/logo")
    public ResponseEntity<Void> deleteLogo() {
        mediaService.delete(SettingsMediaType.LOGO);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/favicon")
    public ResponseEntity<Void> deleteFavicon() {
        mediaService.delete(SettingsMediaType.FAVICON);
        return ResponseEntity.noContent().build();
    }

    /**
     * Renvoie les octets d'un media, ou 404 si aucun fichier n'est enregistre.
     *
     * <p>Le type MIME servi est celui <b>detecte a l'upload</b> par la signature
     * binaire, jamais celui annonce par le navigateur emetteur, et
     * {@code X-Content-Type-Options: nosniff} interdit au navigateur de deviner
     * autre chose : un fichier deguise ne peut donc pas etre reinterprete comme
     * du HTML ou du script servi depuis notre origine. La reponse est en
     * {@code Content-Disposition: inline} pour s'afficher dans une balise
     * {@code <img>}, et brievement cachee — l'URL publique porte un parametre
     * {@code ?v=<horodatage>} qui change a chaque remplacement, ce qui suffit a
     * invalider le cache immediatement apres une modification.</p>
     */
    private ResponseEntity<byte[]> serve(SettingsMediaType type) {
        return mediaService.find(type)
                .<ResponseEntity<byte[]>>map(media -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(media.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + media.getFileName() + "\"")
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                        .body(media.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
