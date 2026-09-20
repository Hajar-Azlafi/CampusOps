import { useEffect, useState } from 'react'

/**
 * Flux de suppression métier réutilisable et cohérent dans toute l'application
 * (refonte des suppressions). Dès que `target` est non nul, la modale s'ouvre,
 * appelle l'aperçu d'impact (`impactFn(id)` → GET /{id}/impact-suppression) et
 * affiche l'un des trois états prévus :
 *
 *   - BLOQUÉ   (canDelete === false) : la cible est utilisée par des éléments
 *              métier / historiques. On les liste ; le bouton « Supprimer » est
 *              masqué. C'est la « suppression après modification » : l'utilisateur
 *              retire d'abord ces usages, puis la suppression redevient possible.
 *   - CASCADE  (requiresConfirmation === true) : la suppression entraînera celle
 *              d'enfants structurels, listés avec leurs compteurs. Confirmation
 *              explicite requise (garde-fou anti-suppression accidentelle).
 *   - SIMPLE   : aucune dépendance, confirmation directe.
 *
 * À la confirmation, `deleteFn(id, true)` est appelé (cascade confirmée), puis
 * `onDeleted()` pour rafraîchir listes / compteurs / sélecteurs sans rechargement
 * manuel. Aucune erreur SQL brute n'atteint l'utilisateur : le message métier de
 * l'aperçu (ou du back en dernier recours) est affiché tel quel.
 *
 * Composant autonome : chaque page garde son ConfirmDialog pour
 * activer/désactiver et n'a qu'à piloter `target`.
 */
export default function DeleteDialog({
  target,
  getLabel = (t) => t?.nom ?? t?.libelle ?? t?.code ?? t?.name ?? '',
  impactFn,
  deleteFn,
  onClose,
  onDeleted,
  entityName = '',
}) {
  const open = Boolean(target)
  const [impact, setImpact] = useState(null)
  const [loadingImpact, setLoadingImpact] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!target) return
    let cancelled = false
    setImpact(null)
    setError('')
    setLoadingImpact(true)
    Promise.resolve(impactFn(target.id))
      .then((data) => {
        if (!cancelled) setImpact(data)
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err?.response?.data?.message ||
              "Impossible d'analyser les dépendances de cet élément. Réessayez.",
          )
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingImpact(false)
      })
    return () => {
      cancelled = true
    }
  }, [target, impactFn])

  if (!open) return null

  const label = getLabel(target)
  const canDelete = impact?.canDelete ?? false
  const cascade = impact?.cascade ?? []
  const blocking = impact?.blocking ?? []
  const isCascade = canDelete && cascade.length > 0
  // Libellé prêt à afficher (« 5 filières »), avec repli si le champ label
  // n'est pas sérialisé côté back.
  const depLabel = (d) =>
    d?.label ?? `${d?.count ?? 0} ${(d?.count ?? 0) <= 1 ? d?.singular ?? '' : d?.plural ?? ''}`.trim()

  const handleConfirm = async () => {
    if (!canDelete) return
    setDeleting(true)
    setError('')
    try {
      await deleteFn(target.id, true) // cascade confirmée
      onDeleted?.()
      onClose?.()
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          'La suppression a échoué, veuillez réessayer.',
      )
    } finally {
      setDeleting(false)
    }
  }

  const title = entityName
    ? `Supprimer ${entityName} « ${label} »`
    : `Supprimer « ${label} »`

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={deleting ? undefined : onClose}
      />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-md p-6">
        <h3 className="font-display text-lg font-semibold text-ink mb-3">{title}</h3>

        {loadingImpact && (
          <p className="text-sm text-ink/50 py-4">Analyse des dépendances…</p>
        )}

        {!loadingImpact && impact && (
          <div className="space-y-4">
            {/* BLOQUÉ : usages métier / historiques à retirer d'abord. */}
            {!canDelete && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                <p className="text-sm font-medium text-red-800 mb-2">
                  Suppression impossible : des éléments y sont encore rattachés.
                </p>
                <ul className="text-sm text-red-700 list-disc list-inside space-y-0.5">
                  {blocking.map((d, i) => (
                    <li key={i}>{depLabel(d)}</li>
                  ))}
                </ul>
                <p className="text-xs text-red-600/80 mt-2">
                  Modifiez ou supprimez d'abord ces éléments, puis réessayez.
                </p>
              </div>
            )}

            {/* CASCADE : enfants structurels supprimés avec la cible. */}
            {isCascade && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                <p className="text-sm font-medium text-amber-800 mb-2">
                  Cette suppression entraînera aussi la suppression de :
                </p>
                <ul className="text-sm text-amber-700 list-disc list-inside space-y-0.5">
                  {cascade.map((d, i) => (
                    <li key={i}>{depLabel(d)}</li>
                  ))}
                </ul>
                <p className="text-xs text-amber-600/80 mt-2">
                  Cette action est irréversible.
                </p>
              </div>
            )}

            {/* SIMPLE : aucune dépendance. */}
            {canDelete && cascade.length === 0 && (
              <p className="text-sm text-ink/60">
                Confirmez-vous la suppression de « {label} » ? Cette action est
                irréversible.
              </p>
            )}
          </div>
        )}

        {error && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mt-3">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-3 mt-6">
          <button
            onClick={onClose}
            disabled={deleting}
            className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors disabled:opacity-50"
          >
            {canDelete ? 'Annuler' : 'Fermer'}
          </button>
          {!loadingImpact && canDelete && (
            <button
              onClick={handleConfirm}
              disabled={deleting}
              className="px-4 py-2 text-sm font-medium text-white rounded-lg bg-danger hover:bg-danger-strong disabled:opacity-50 transition-colors"
            >
              {deleting ? '...' : isCascade ? 'Tout supprimer' : 'Supprimer'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
