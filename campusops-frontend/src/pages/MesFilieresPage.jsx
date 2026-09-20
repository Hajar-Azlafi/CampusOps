import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyScope } from '../api/meApi'
import { IconAcademicCap, IconUsers, IconArrowRight, IconLock } from '../components/icons'

/**
 * "Mes filieres" (Section 3 & 10) : le responsable pedagogique consulte les
 * filieres dont il a la charge. Vue en LECTURE SEULE — la creation et la
 * modification des filieres restent reservees a l'administrateur (Section 4).
 * Le backend ne renvoie de toute facon que les filieres de son perimetre.
 */
export default function MesFilieresPage() {
  const [programs, setPrograms] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    let active = true
    getMyScope()
      .then((scope) => {
        if (active) setPrograms(scope?.programs || [])
      })
      .catch(() => {
        if (active) setErrorMsg('Impossible de charger vos filières pour le moment')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Mes filières</h1>
        <p className="text-sm text-ink/50 mt-1">
          Les filières dont vous assurez l'organisation pédagogique.
        </p>
      </div>

      <div className="mb-6 flex items-start gap-2.5 rounded-lg border border-blue-200 bg-blue-50/60 px-4 py-3 text-sm text-blue-800">
        <IconLock className="mt-0.5 h-4 w-4 shrink-0" />
        <p>
          Cette page est en consultation seule. La création et la modification des filières
          relèvent de l'administration. Vous gérez les groupes, emplois du temps et demandes de
          réservation de vos filières.
        </p>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <p className="text-sm text-ink/40 py-10 text-center">Chargement...</p>
      ) : programs.length === 0 ? (
        <div className="bg-surface border border-ink/10 rounded-xl py-16 text-center">
          <div className="w-14 h-14 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
            <IconAcademicCap className="w-7 h-7 text-heading/30" />
          </div>
          <p className="mt-4 text-sm font-medium text-ink">Aucune filière ne vous est affectée.</p>
          <p className="mt-1 text-sm text-ink/50 max-w-sm mx-auto">
            Contactez l'administration pour qu'une ou plusieurs filières vous soient attribuées.
          </p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {programs.map((p) => (
            <div key={p.id} className="bg-surface border border-ink/10 rounded-xl p-5 flex flex-col">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-heading/5 text-heading">
                  <IconAcademicCap className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <h2 className="font-medium text-ink leading-snug">{p.nom}</h2>
                  {p.code && <p className="text-xs font-mono text-heading/50 mt-0.5">{p.code}</p>}
                </div>
              </div>

              <dl className="mt-4 space-y-1.5 text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-ink/50">Département</dt>
                  <dd className="text-ink/80 text-right">{p.departmentNom || '—'}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-ink/50">Niveau / cycle</dt>
                  <dd className="text-ink/80 text-right">{p.levelNom || '—'}</dd>
                </div>
              </dl>

              {p.description && (
                <p className="mt-3 pt-3 border-t border-ink/5 text-sm text-ink/60 line-clamp-3">
                  {p.description}
                </p>
              )}

              <Link
                to="/rp/groupes"
                className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-heading-soft hover:text-heading"
              >
                <IconUsers className="h-4 w-4" />
                Voir les groupes
                <IconArrowRight className="h-4 w-4" />
              </Link>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
