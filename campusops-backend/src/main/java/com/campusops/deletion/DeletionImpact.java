package com.campusops.deletion;

import com.campusops.exception.BadRequestException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultat de l'analyse d'une suppression : ce qui se passera si l'on supprime
 * une entite donnee. C'est l'objet renvoye par les endpoints de <b>preview</b>
 * et consomme par les modales de confirmation du frontend.
 *
 * <p>Trois issues possibles, dans l'esprit de la refonte des suppressions :</p>
 * <ul>
 *   <li><b>Bloquee</b> ({@code canDelete == false}) : l'entite est utilisee par
 *       des elements metier / historiques ({@link #getBlocking()}). La
 *       suppression est refusee avec un message explicite ; l'utilisateur doit
 *       d'abord retirer ou modifier ces elements (« suppression apres
 *       modification »).</li>
 *   <li><b>Cascade a confirmer</b> ({@code canDelete == true} et
 *       {@code requiresConfirmation == true}) : la suppression entrainera celle
 *       d'enfants structurels ({@link #getCascade()}), affiches avec leurs
 *       compteurs.</li>
 *   <li><b>Simple</b> : aucune dependance, suppression directe apres une simple
 *       confirmation.</li>
 * </ul>
 *
 * <p>Les getters {@code isCanDelete} / {@code isRequiresConfirmation} /
 * {@code getMessage} sont serialises tels quels en JSON (Jackson) : le frontend
 * lit {@code canDelete}, {@code requiresConfirmation}, {@code message},
 * {@code cascade[]}, {@code blocking[]} et {@code targetLabel}.</p>
 */
@Getter
public class DeletionImpact {

    /** Libelle de la cible, deja mis en forme (« le departement « Maths » »). */
    private final String targetLabel;

    /** Enfants structurels supprimes en cascade (avec confirmation). */
    private final List<DeletionDependency> cascade = new ArrayList<>();

    /** Usages metier / historiques qui empechent la suppression. */
    private final List<DeletionDependency> blocking = new ArrayList<>();

    public DeletionImpact(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    /** Ajoute un enfant structurel (ignore si le compte est nul). */
    public DeletionImpact addCascade(long count, String singular, String plural) {
        if (count > 0) {
            cascade.add(DeletionDependency.of(count, singular, plural));
        }
        return this;
    }

    /** Ajoute un usage bloquant (ignore si le compte est nul). */
    public DeletionImpact addBlocking(long count, String singular, String plural) {
        if (count > 0) {
            blocking.add(DeletionDependency.of(count, singular, plural));
        }
        return this;
    }

    /** Vrai si la suppression est possible (aucun usage bloquant). */
    public boolean isCanDelete() {
        return blocking.isEmpty();
    }

    /** Vrai si la suppression, bien que possible, entrainera une cascade a confirmer. */
    public boolean isRequiresConfirmation() {
        return isCanDelete() && !cascade.isEmpty();
    }

    /** Message metier pret a afficher, adapte a l'issue (bloquee / cascade / simple). */
    public String getMessage() {
        if (!isCanDelete()) {
            return "Impossible de supprimer " + targetLabel
                    + " car des éléments y sont encore rattachés : " + join(blocking)
                    + ". Modifiez ou supprimez ces éléments, puis réessayez.";
        }
        if (!cascade.isEmpty()) {
            return "Supprimer " + targetLabel + " entraînera aussi la suppression de "
                    + join(cascade) + ". Cette action est irréversible.";
        }
        return "Confirmez-vous la suppression de " + targetLabel + " ? Cette action est irréversible.";
    }

    /**
     * Garde metier : leve une {@link BadRequestException} (-> HTTP 400) avec le
     * message explicite si la suppression est bloquee. A appeler cote service
     * avant toute suppression physique.
     */
    public void requireDeletable() {
        if (!isCanDelete()) {
            throw new BadRequestException(getMessage());
        }
    }

    /**
     * Garde metier complete, a appeler cote service juste avant la suppression
     * physique :
     * <ul>
     *   <li>si la suppression est <b>bloquee</b> par un usage metier, leve une
     *       {@link BadRequestException} avec le message « Impossible de
     *       supprimer… » ;</li>
     *   <li>si elle <b>entrainerait une cascade</b> non confirmee
     *       ({@code cascadeConfirmed == false}), leve une
     *       {@link BadRequestException} avec le message d'avertissement
     *       (garde-fou contre la suppression accidentelle d'un sous-arbre : le
     *       client doit rappeler l'action avec {@code ?cascade=true}).</li>
     * </ul>
     * Sinon, ne fait rien : la suppression peut proceder.
     */
    public void requireConfirmed(boolean cascadeConfirmed) {
        if (!isCanDelete()) {
            throw new BadRequestException(getMessage());
        }
        if (isRequiresConfirmation() && !cascadeConfirmed) {
            throw new BadRequestException(getMessage());
        }
    }

    /** « a, b et c » a partir des libelles prets a afficher. */
    private static String join(List<DeletionDependency> deps) {
        List<String> parts = deps.stream().map(DeletionDependency::getLabel).toList();
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1))
                + " et " + parts.get(parts.size() - 1);
    }
}
