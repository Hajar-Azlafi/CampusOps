package com.campusops.exception;

import com.campusops.audit.service.AuditService;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.Role;
import com.campusops.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** Module trace dans le journal d'audit pour les erreurs systeme. */
    private static final String MODULE = "Systeme";

    private final AuditService auditService;
    private final NotificationService notificationService;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Adresse e-mail ou mot de passe incorrect.", request, null);
    }

    /**
     * Compte temporairement verrouille apres trop de tentatives de connexion
     * (Module 11, §7). Contrairement aux identifiants errones, le message est
     * conserve : il indique le delai restant et la marche a suivre. Aucune
     * enumeration n'est possible, un compte inexistant n'etant jamais verrouille.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(
            LockedException ex, HttpServletRequest request) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Compte temporairement verrouillé. Réessayez plus tard."
                : ex.getMessage();
        return buildResponse(HttpStatus.LOCKED, message, request, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(
            DisabledException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Votre compte est désactivé. Contactez l'administrateur.", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Accès interdit", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " : " + fieldError.getDefaultMessage())
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Erreur de validation des données",
                request,
                validationErrors
        );
    }

    /**
     * Filet de securite de <b>dernier recours</b> pour la gestion des
     * suppressions (et modifications) : une contrainte d'integrite de la base
     * (cle etrangere, unicite, colonne non nulle) a ete violee alors que
     * l'analyse metier ne l'avait pas anticipee — cas rare des relations
     * facultatives (nullable) non couvertes par un comptage.
     *
     * <p>On ne laisse <b>jamais</b> remonter l'erreur SQL brute
     * (« could not execute statement », « violates foreign key constraint »…) :
     * elle est transformee en message metier et renvoyee en <b>409 CONFLICT</b>.
     * Contrairement aux erreurs reellement inattendues, il ne s'agit pas d'une
     * defaillance systeme : on se contente d'un {@code log.warn}, sans tracer
     * d'incident ni alerter les administrateurs (pas de spam).</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violation d'integrite sur {} : {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildResponse(
                HttpStatus.CONFLICT,
                "Cette action est impossible car cet élément est encore utilisé "
                        + "ailleurs dans l'application. Retirez ou modifiez d'abord "
                        + "les éléments qui en dépendent, puis réessayez.",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Erreur inattendue : ", ex);
        traceUnexpectedError(ex, request);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur inattendue est survenue",
                request,
                null
        );
    }

    /**
     * Journalise l'erreur systeme et alerte les administrateurs. La tracabilite
     * ne doit jamais masquer l'erreur d'origine : toute defaillance ici est
     * simplement consignee dans les logs.
     */
    private void traceUnexpectedError(Exception ex, HttpServletRequest request) {
        try {
            String description = String.format("Erreur inattendue sur %s : %s",
                    request.getRequestURI(), ex.getMessage());
            auditService.record(AuditAction.SYSTEM, MODULE, description);
            // Alerte emise par le systeme, sans action utilisateur : soumise au
            // reglage « notifications automatiques » (Module 11, §8).
            notificationService.notifySystemRoles(EnumSet.of(Role.ADMIN), NotificationType.WARNING,
                    "Erreur systeme detectee", description, null);
        } catch (Exception tracingError) {
            log.warn("Echec de la tracabilite de l'erreur systeme : {}", tracingError.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request, List<String> validationErrors) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}