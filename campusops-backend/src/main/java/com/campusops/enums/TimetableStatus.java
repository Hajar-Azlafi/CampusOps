package com.campusops.enums;

/**
 * Statut d'un emploi du temps et effet concret de chaque état dans le système.
 *
 * <table>
 *   <tr><th>Statut</th><th>Diffusé</th><th>Occupe les salles</th><th>Sens</th></tr>
 *   <tr><td>{@code BROUILLON}</td><td>non</td><td>oui</td>
 *       <td>en cours de constitution, non diffusé ; ses séances réservent déjà
 *       les salles pour éviter les conflits pendant la saisie.</td></tr>
 *   <tr><td>{@code PUBLIE}</td><td>oui</td><td>oui</td>
 *       <td>validé et diffusé ; officiel.</td></tr>
 *   <tr><td>{@code ARCHIVE}</td><td>non</td><td>non</td>
 *       <td>conservé pour l'historique ; les salles sont automatiquement
 *       libérées (redeviennent disponibles à la réservation).</td></tr>
 * </table>
 *
 * <p>Transitions autorisées (cf. {@code EmploiDuTempsService}) :
 * BROUILLON → PUBLIE|ARCHIVE ; PUBLIE → ARCHIVE|BROUILLON ; ARCHIVE → BROUILLON.</p>
 */
public enum TimetableStatus {
    BROUILLON,
    PUBLIE,
    ARCHIVE
}
