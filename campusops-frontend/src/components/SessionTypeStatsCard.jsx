import { useEffect, useState } from 'react'
import { fetchSessionTypeStats } from '../api/schedulesApi'
import { DonutChart } from './charts'

/**
 * Répartition des séances PAR TYPE (§6/§19) — widget de tableau de bord.
 *
 * Données RÉELLES et dynamiques : le backend borne le calcul au périmètre de
 * l'utilisateur (ADMIN = tout ; responsable pédagogique = ses seules filières)
 * et l'affine avec le contexte optionnel transmis (année, filière, semestre…).
 * Aucune valeur fictive : un type sans séance vaut 0 et reste indiqué comme tel.
 *
 * Le composant est autonome (il charge ses propres données) et réutilisable tel
 * quel dans le tableau de bord ADMIN et celui du responsable pédagogique.
 */
export default function SessionTypeStatsCard({
  context = {},
  title = 'Séances par type',
  subtitle = 'Répartition réelle des séances planifiées actives',
  size = 160,
}) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // On re-déclenche le calcul si le contexte change (sérialisé pour éviter les
  // rechargements inutiles dus à la recréation de l'objet à chaque rendu).
  const contextKey = JSON.stringify(context)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    fetchSessionTypeStats(context)
      .then((data) => { if (!cancelled) setStats(data) })
      .catch(() => { if (!cancelled) setError('Statistiques indisponibles pour le moment.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contextKey])

  const repartition = stats?.repartition ?? []
  const total = stats?.total ?? 0

  // Données du donut : segments colorés avec la couleur configurée du type.
  const donutData = repartition
    .filter((r) => r.value > 0)
    .map((r) => ({
      key: r.typeSeanceId ?? `nc-${r.code ?? 'x'}`,
      label: r.label,
      value: r.value,
      color: r.couleur || undefined,
    }))

  // Types sans aucune séance : affichés explicitement à 0 (§19), sans les
  // masquer, pour que le tableau de bord reste honnête.
  const zeroTypes = repartition.filter((r) => r.value === 0)

  return (
    <section className="bg-surface border border-ink/10 rounded-xl p-5">
      <div className="mb-4">
        <h2 className="text-sm font-semibold text-ink">{title}</h2>
        {subtitle ? <p className="text-xs text-ink/50 mt-0.5">{subtitle}</p> : null}
      </div>

      {loading ? (
        <p className="text-sm text-ink/40 py-10 text-center">Chargement...</p>
      ) : error ? (
        <p className="text-sm text-ink/40 py-10 text-center">{error}</p>
      ) : total === 0 ? (
        <p className="text-sm text-ink/40 py-10 text-center">
          Aucune séance planifiée pour ce périmètre.
        </p>
      ) : (
        <>
          <DonutChart data={donutData} size={size} />
          {zeroTypes.length > 0 && (
            <p className="text-[11px] text-ink/45 mt-4 pt-3 border-t border-ink/5">
              Sans séance : {zeroTypes.map((t) => t.label).join(' · ')}
            </p>
          )}
        </>
      )}
    </section>
  )
}
