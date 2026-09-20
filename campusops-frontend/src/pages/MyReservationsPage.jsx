import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyReservations } from '../api/reservationsApi'
import { useSettings } from '../context/SettingsContext'
import { ReservationStatusBadge } from '../components/Badge'
import MyReservationDetailsModal from '../components/MyReservationDetailsModal'
import { IconCalendar, IconClock, IconBuilding, IconSearchLocation, IconDoor } from '../components/icons'

const STATUS_FILTERS = [
  { value: '', label: 'Toutes' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'APPROVED', label: 'Approuvées' },
  { value: 'REJECTED', label: 'Refusées' },
  { value: 'CANCELLED', label: 'Annulées' },
]

const statusAccent = {
  PENDING: 'border-l-amber-400',
  APPROVED: 'border-l-emerald-500',
  REJECTED: 'border-l-red-400',
  CANCELLED: 'border-l-ink/20',
  COMPLETED: 'border-l-blue-400',
}

function formatLongDate(dateStr) {
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function ReservationCard({ r, onOpen }) {
  // Format d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatHeure } = useSettings()
  return (
    <button
      onClick={() => onOpen(r)}
      className={`text-left bg-surface border border-ink/10 border-l-4 ${statusAccent[r.statut] || 'border-l-ink/15'} rounded-xl p-4 hover:shadow-md transition-shadow w-full`}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-medium text-ink">{r.spaceNom}</p>
          <p className="text-xs font-mono text-heading/50">{r.spaceCode}</p>
        </div>
        <ReservationStatusBadge status={r.statut} label={statusLabel(r.statut)} />
      </div>

      <div className="mt-3 space-y-1.5 text-sm text-ink/70">
        <p className="flex items-center gap-2">
          <IconCalendar className="w-4 h-4 text-heading-soft/60 shrink-0" />
          {formatLongDate(r.date)}
        </p>
        <p className="flex items-center gap-2">
          <IconClock className="w-4 h-4 text-heading-soft/60 shrink-0" />
          {formatHeure(r.heureDebut)} → {formatHeure(r.heureFin)}
        </p>
        {(r.buildingNom || r.floorNom) && (
          <p className="flex items-center gap-2">
            <IconBuilding className="w-4 h-4 text-heading-soft/60 shrink-0" />
            {[r.buildingNom, r.floorNom].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      {r.motif && (
        <div className="mt-3 pt-3 border-t border-ink/5">
          <p className="text-xs text-ink/40 mb-0.5">Motif</p>
          <p className="text-sm text-ink/70 truncate">{r.motif}</p>
        </div>
      )}

      <p className="mt-3 text-xs font-medium text-heading-soft text-right">Voir les détails →</p>
    </button>
  )
}

function statusLabel(value) {
  return STATUS_FILTERS.find((s) => s.value === value)?.label ?? value
}

export default function MyReservationsPage() {
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [detailsModal, setDetailsModal] = useState({ open: false, reservation: null })

  const load = () => {
    setLoading(true)
    setErrorMsg('')
    getMyReservations()
      .then(setReservations)
      .catch(() => setErrorMsg('Impossible de charger vos réservations pour le moment'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const filtered = useMemo(
    () => (statusFilter ? reservations.filter((r) => r.statut === statusFilter) : reservations),
    [reservations, statusFilter],
  )

  const todayISO = new Date().toISOString().slice(0, 10)

  const upcoming = useMemo(
    () =>
      filtered
        .filter((r) => r.date >= todayISO && (r.statut === 'PENDING' || r.statut === 'APPROVED'))
        .sort((a, b) => (a.date + a.heureDebut).localeCompare(b.date + b.heureDebut)),
    [filtered, todayISO],
  )

  const history = useMemo(
    () =>
      filtered
        .filter((r) => !(r.date >= todayISO && (r.statut === 'PENDING' || r.statut === 'APPROVED')))
        .sort((a, b) => (b.date + b.heureDebut).localeCompare(a.date + a.heureDebut)),
    [filtered, todayISO],
  )

  const openDetails = (reservation) => setDetailsModal({ open: true, reservation })
  const closeDetails = () => setDetailsModal({ open: false, reservation: null })

  const handleCancelled = (updated) => {
    setReservations((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
    closeDetails()
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Mes réservations</h1>
        <p className="text-sm text-ink/50 mt-1">Suivez vos demandes et vos réservations d'espaces.</p>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setStatusFilter(f.value)}
            className={`px-3.5 py-1.5 rounded-full text-sm font-medium border transition-colors ${
              statusFilter === f.value
                ? 'bg-blueprint-800 text-white border-blueprint-800'
                : 'bg-surface text-ink/60 border-ink/15 hover:bg-heading/5'
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

      {loading && <p className="text-sm text-ink/40 py-10 text-center">Chargement...</p>}

      {!loading && !errorMsg && reservations.length === 0 && (
        <div className="bg-surface border border-ink/10 rounded-xl py-16 text-center">
          <div className="w-14 h-14 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
            <IconDoor className="w-7 h-7 text-heading/30" />
          </div>
          <p className="mt-4 text-sm font-medium text-ink">Vous n'avez encore aucune réservation.</p>
          <p className="mt-1 text-sm text-ink/50 max-w-sm mx-auto">
            Recherchez un espace disponible pour effectuer votre première demande de réservation.
          </p>
          <Link
            to="/availability"
            className="inline-flex items-center gap-2 mt-4 px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            <IconSearchLocation className="w-4 h-4" />
            Rechercher un espace
          </Link>
        </div>
      )}

      {!loading && !errorMsg && reservations.length > 0 && filtered.length === 0 && (
        <div className="bg-surface border border-ink/10 rounded-xl py-12 text-center">
          <p className="text-sm text-ink/50">Aucune réservation ne correspond à ce filtre</p>
        </div>
      )}

      {!loading && upcoming.length > 0 && (
        <div className="mb-8">
          <h2 className="text-sm font-semibold text-heading mb-3">Mes réservations à venir</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {upcoming.map((r) => (
              <ReservationCard key={r.id} r={r} onOpen={openDetails} />
            ))}
          </div>
        </div>
      )}

      {!loading && history.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-heading mb-3">Historique</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {history.map((r) => (
              <ReservationCard key={r.id} r={r} onOpen={openDetails} />
            ))}
          </div>
        </div>
      )}

      <MyReservationDetailsModal
        open={detailsModal.open}
        reservation={detailsModal.reservation}
        onClose={closeDetails}
        onCancelled={handleCancelled}
      />
    </div>
  )
}