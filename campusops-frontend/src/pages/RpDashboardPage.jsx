import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useSettings } from '../context/SettingsContext'
import { getMyScope } from '../api/meApi'
import { fetchGroups } from '../api/groupsApi'
import { getMyReservations } from '../api/reservationsApi'
import { StatCard } from '../components/charts'
import SessionTypeStatsCard from '../components/SessionTypeStatsCard'
import { ReservationStatusBadge } from '../components/Badge'
import { reservationStatusLabel } from '../constants/reservationStatuses'
import {
  IconAcademicCap,
  IconUsers,
  IconClock,
  IconCheckCircle,
  IconSearchLocation,
  IconArrowRight,
  IconCalendar,
  IconFileText,
} from '../components/icons'

/**
 * Tableau de bord du Responsable pedagogique (Section 9 du cahier des charges).
 * Strictement limite a son perimetre : il n'appelle PAS les statistiques
 * globales reservees a l'administrateur (fetchDashboardStatistics), mais derive
 * ses indicateurs de son propre perimetre (filieres, groupes, demandes).
 */
function formatLongDate(dateStr) {
  if (!dateStr) return ''
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

export default function RpDashboardPage() {
  const { user } = useAuth()
  // Format d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatHeure } = useSettings()
  const [programs, setPrograms] = useState([])
  const [groups, setGroups] = useState([])
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    // Chaque source est independante : un echec (ex. aucune donnee) ne doit pas
    // bloquer l'affichage des autres indicateurs.
    Promise.allSettled([
      getMyScope(),
      fetchGroups({ actif: true }),
      getMyReservations(),
    ])
      .then(([scopeRes, groupsRes, resaRes]) => {
        if (!active) return
        if (scopeRes.status === 'fulfilled') setPrograms(scopeRes.value?.programs || [])
        if (groupsRes.status === 'fulfilled') setGroups(groupsRes.value || [])
        if (resaRes.status === 'fulfilled') setReservations(resaRes.value || [])
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const pending = reservations.filter((r) => r.statut === 'PENDING').length
  const approved = reservations.filter((r) => r.statut === 'APPROVED').length

  const recent = [...reservations]
    .sort((a, b) => (b.date + b.heureDebut).localeCompare(a.date + a.heureDebut))
    .slice(0, 5)

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">
            Bonjour, {user?.firstName}
          </h1>
          <p className="text-sm text-ink/50 mt-1">Responsable pédagogique · Tableau de bord</p>
        </div>
        <Link
          to="/availability"
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
        >
          <IconSearchLocation className="w-4 h-4" />
          Rechercher un espace
        </Link>
      </div>

      {loading ? (
        <p className="text-sm text-ink/40 py-10 text-center">Chargement...</p>
      ) : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
            <StatCard label="Mes filières" value={programs.length} tone="blue" icon={IconAcademicCap} />
            <StatCard label="Mes groupes" value={groups.length} tone="default" icon={IconUsers} />
            <StatCard label="Demandes en attente" value={pending} tone="amber" icon={IconClock} />
            <StatCard label="Demandes approuvées" value={approved} tone="emerald" icon={IconCheckCircle} />
          </div>

          <div className="grid lg:grid-cols-2 gap-6">
            {/* ---- Mes filieres ---- */}
            <section className="bg-surface border border-ink/10 rounded-xl p-5">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-semibold text-ink">Mes filières</h2>
                <Link to="/rp/filieres" className="text-xs font-medium text-heading-soft hover:text-heading">
                  Tout voir →
                </Link>
              </div>
              {programs.length === 0 ? (
                <p className="text-sm text-ink/40 py-6 text-center">
                  Aucune filière ne vous est encore affectée. Contactez l'administration.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {programs.map((p) => (
                    <div
                      key={p.id}
                      className="flex items-center gap-3 rounded-lg border border-ink/10 px-3.5 py-3"
                    >
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-heading/5 text-heading">
                        <IconAcademicCap className="h-4 w-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ink">{p.nom}</p>
                        <p className="truncate text-xs text-ink/50">
                          {[p.code, p.levelNom, p.departmentNom].filter(Boolean).join(' · ')}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* ---- Dernieres demandes ---- */}
            <section className="bg-surface border border-ink/10 rounded-xl p-5">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-semibold text-ink">Mes dernières demandes</h2>
                <Link to="/rp/demandes" className="text-xs font-medium text-heading-soft hover:text-heading">
                  Tout voir →
                </Link>
              </div>
              {recent.length === 0 ? (
                <p className="text-sm text-ink/40 py-6 text-center">
                  Vous n'avez pas encore de demande de réservation.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {recent.map((r) => (
                    <div
                      key={r.id}
                      className="flex items-center justify-between gap-3 rounded-lg border border-ink/10 px-3.5 py-3"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ink">{r.spaceNom}</p>
                        <p className="truncate text-xs text-ink/50">
                          {formatLongDate(r.date)} · {formatHeure(r.heureDebut)} → {formatHeure(r.heureFin)}
                        </p>
                      </div>
                      <ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} />
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* ---- Repartition des seances par type (§6/§19), bornee aux
                filieres du responsable pedagogique cote backend ---- */}
          <div className="mt-6">
            <SessionTypeStatsCard subtitle="Séances de vos filières · données réelles" />
          </div>

          {/* ---- Acces rapides ---- */}
          <div className="mt-6 grid sm:grid-cols-3 gap-3">
            <Link
              to="/rp/groupes"
              className="flex items-center gap-3 rounded-xl border border-ink/10 bg-surface px-4 py-3.5 hover:border-heading/30 hover:shadow-sm transition-all"
            >
              <IconUsers className="h-5 w-5 text-heading/70" />
              <span className="text-sm font-medium text-ink">Gérer mes groupes</span>
              <IconArrowRight className="ml-auto h-4 w-4 text-ink/30" />
            </Link>
            <Link
              to="/schedules"
              className="flex items-center gap-3 rounded-xl border border-ink/10 bg-surface px-4 py-3.5 hover:border-heading/30 hover:shadow-sm transition-all"
            >
              <IconCalendar className="h-5 w-5 text-heading/70" />
              <span className="text-sm font-medium text-ink">Emplois du temps</span>
              <IconArrowRight className="ml-auto h-4 w-4 text-ink/30" />
            </Link>
            <Link
              to="/rp/demandes"
              className="flex items-center gap-3 rounded-xl border border-ink/10 bg-surface px-4 py-3.5 hover:border-heading/30 hover:shadow-sm transition-all"
            >
              <IconFileText className="h-5 w-5 text-heading/70" />
              <span className="text-sm font-medium text-ink">Mes demandes</span>
              <IconArrowRight className="ml-auto h-4 w-4 text-ink/30" />
            </Link>
          </div>
        </>
      )}
    </div>
  )
}
