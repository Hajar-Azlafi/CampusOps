import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { getMyReservations } from '../api/reservationsApi'
import { reservationStatusLabel } from '../constants/reservationStatuses'
import { useSettings } from '../context/SettingsContext'
import { ReservationStatusBadge } from '../components/Badge'
import MyReservationDetailsModal from '../components/MyReservationDetailsModal'
import { IconSearchLocation, IconClock, IconCheckCircle, IconFileText } from '../components/icons'

/**
 * "Demandes de reservation" (Sections 8 & 10) : console de creation/suivi des
 * demandes du responsable pedagogique. Le RP ne valide JAMAIS une demande
 * (APPROVAL_ROLES = {ADMIN} cote backend) — il la CREE (via /availability) puis
 * la suit ici. getMyReservations() ne renvoie que ses propres demandes, donc
 * l'annulation proposee par la modale est toujours legitime.
 */
const FILTERS = [
  { value: '', label: 'Toutes' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'APPROVED', label: 'Validées' },
  { value: 'REJECTED', label: 'Refusées' },
  { value: 'CANCELLED', label: 'Annulées' },
]

const statusAccent = {
  PENDING: 'border-l-amber-400',
  APPROVED: 'border-l-emerald-400',
  REJECTED: 'border-l-red-400',
  CANCELLED: 'border-l-ink/20',
  COMPLETED: 'border-l-blue-400',
}

function formatLongDate(dateStr) {
  if (!dateStr) return ''
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function DemandeCard({ r, onOpen }) {
  // Format d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatHeure } = useSettings()
  const pedago = [r.programNom, r.groupNom, r.semesterNom].filter(Boolean).join(' · ')
  return (
    <button
      onClick={() => onOpen(r)}
      className={`text-left bg-surface border border-ink/10 border-l-[3px] ${statusAccent[r.statut] || 'border-l-ink/20'} rounded-xl p-4 hover:shadow-sm hover:border-heading/30 transition-all`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{r.spaceNom}</p>
          {r.spaceCode && <p className="text-xs font-mono text-heading/50">{r.spaceCode}</p>}
        </div>
        <ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} />
      </div>

      <p className="mt-2 text-sm text-ink/70">
        {formatLongDate(r.date)} · {formatHeure(r.heureDebut)} → {formatHeure(r.heureFin)}
      </p>

      {pedago && (
        <p className="mt-1 truncate text-xs text-ink/50">{pedago}</p>
      )}
      {r.motif && (
        <p className="mt-2 line-clamp-2 text-sm text-ink/60 border-t border-ink/5 pt-2">{r.motif}</p>
      )}
    </button>
  )
}

export default function RpDemandesPage() {
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [filter, setFilter] = useState('')
  const [selected, setSelected] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      setReservations(await getMyReservations())
    } catch {
      setErrorMsg('Impossible de charger vos demandes pour le moment')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const pending = reservations.filter((r) => r.statut === 'PENDING').length
  const approved = reservations.filter((r) => r.statut === 'APPROVED').length

  const visible = filter ? reservations.filter((r) => r.statut === filter) : reservations
  const sorted = [...visible].sort((a, b) =>
    (b.date + b.heureDebut).localeCompare(a.date + a.heureDebut)
  )

  const handleCancelled = (updated) => {
    setReservations((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
    setSelected(updated)
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Demandes de réservation</h1>
          <p className="text-sm text-ink/50 mt-1">
            Créez et suivez vos demandes de salle. Chaque demande est transmise à
            l'administration pour validation.
          </p>
        </div>
        <Link
          to="/availability"
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          <IconSearchLocation className="w-4 h-4" />
          Nouvelle demande
        </Link>
      </div>

      <div className="grid grid-cols-3 gap-3 mb-6">
        <div className="bg-surface border border-ink/10 rounded-xl px-4 py-3.5">
          <div className="flex items-center gap-2 text-ink/50">
            <IconFileText className="w-4 h-4" />
            <span className="text-xs font-medium">Total</span>
          </div>
          <p className="mt-1 text-2xl font-semibold text-ink">{reservations.length}</p>
        </div>
        <div className="bg-surface border border-ink/10 rounded-xl px-4 py-3.5">
          <div className="flex items-center gap-2 text-amber-600">
            <IconClock className="w-4 h-4" />
            <span className="text-xs font-medium">En attente</span>
          </div>
          <p className="mt-1 text-2xl font-semibold text-ink">{pending}</p>
        </div>
        <div className="bg-surface border border-ink/10 rounded-xl px-4 py-3.5">
          <div className="flex items-center gap-2 text-emerald-600">
            <IconCheckCircle className="w-4 h-4" />
            <span className="text-xs font-medium">Validées</span>
          </div>
          <p className="mt-1 text-2xl font-semibold text-ink">{approved}</p>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 mb-4">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`px-3 py-1.5 text-sm font-medium rounded-lg border transition-colors ${
              filter === f.value
                ? 'bg-blueprint-800 text-white border-blueprint-800'
                : 'text-ink/60 border-ink/15 hover:bg-ink/5'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <p className="text-sm text-ink/40 py-10 text-center">Chargement...</p>
      ) : sorted.length === 0 ? (
        <div className="bg-surface border border-ink/10 rounded-xl py-16 text-center">
          <div className="w-14 h-14 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
            <IconFileText className="w-7 h-7 text-heading/30" />
          </div>
          <p className="mt-4 text-sm font-medium text-ink">
            {filter ? 'Aucune demande dans cette catégorie.' : 'Vous n\'avez pas encore de demande.'}
          </p>
          <p className="mt-1 text-sm text-ink/50 max-w-sm mx-auto">
            Utilisez « Nouvelle demande » pour rechercher un espace disponible et soumettre une
            demande de réservation.
          </p>
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {sorted.map((r) => (
            <DemandeCard key={r.id} r={r} onOpen={setSelected} />
          ))}
        </div>
      )}

      <MyReservationDetailsModal
        open={!!selected}
        reservation={selected}
        onClose={() => setSelected(null)}
        onCancelled={handleCancelled}
      />
    </div>
  )
}
