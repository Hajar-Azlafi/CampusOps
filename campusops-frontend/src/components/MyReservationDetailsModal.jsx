import { useState } from 'react'
import { cancelReservation } from '../api/reservationsApi'
import { reservationPriorityLabel } from '../constants/reservationPriorities'
import { spaceTypeLabel } from '../constants/spaceTypes'
import { reservationStatusLabel } from '../constants/reservationStatuses'
import { useSettings } from '../context/SettingsContext'
import { ReservationStatusBadge } from './Badge'
import { IconX, IconCheckCircle, IconClock, IconAlertTriangle } from './icons'

const row = 'flex justify-between gap-4 py-2 border-b border-ink/5 last:border-0'
const key = 'text-sm text-ink/50'
const val = 'text-sm text-ink/80 text-right'
const sectionTitle = 'text-xs font-semibold text-heading/70 uppercase tracking-wide mb-1 mt-4 first:mt-0'

function formatLongDate(dateStr) {
  if (!dateStr) return ''
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/**
 * « 5 septembre 2026 à 14:30 » : la date garde le registre long des autres
 * libellés de cet écran, l'heure suit le format choisi dans Paramètres >
 * Affichage (Module 11, §10 : 24 h ou AM/PM).
 */
function formatDateTime(dateTimeStr, formatHeure) {
  if (!dateTimeStr) return ''
  const jour = formatLongDate(String(dateTimeStr).slice(0, 10))
  const heure = formatHeure(dateTimeStr)
  return heure ? `${jour} à ${heure}` : jour
}

const statusBanner = {
  PENDING: {
    icon: IconClock,
    text: 'Votre demande est en attente de validation',
    cls: 'bg-amber-50 text-amber-800 border-amber-200',
  },
  APPROVED: {
    icon: IconCheckCircle,
    text: 'Votre réservation a été approuvée',
    cls: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  REJECTED: {
    icon: IconX,
    text: 'Votre demande a été refusée',
    cls: 'bg-red-50 text-red-800 border-red-200',
  },
  CANCELLED: {
    icon: IconAlertTriangle,
    text: 'Cette réservation a été annulée',
    cls: 'bg-ink/5 text-ink/60 border-ink/15',
  },
  COMPLETED: {
    icon: IconCheckCircle,
    text: 'Cette réservation est terminée',
    cls: 'bg-blue-50 text-blue-700 border-blue-200',
  },
}

/**
 * Vue detaillee d'une reservation pour ENSEIGNANT / RESPONSABLE_CLUB :
 * axee sur le suivi personnel (statut, espace, creneau), sans les
 * informations administratives (demandeur, email, role).
 */
export default function MyReservationDetailsModal({ open, reservation, onClose, onCancelled }) {
  // Format d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatHeure } = useSettings()
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [error, setError] = useState('')

  if (!open || !reservation) return null
  const r = reservation
  const banner = statusBanner[r.statut] ?? statusBanner.PENDING
  const BannerIcon = banner.icon
  const canCancel = r.statut === 'PENDING' || r.statut === 'APPROVED'

  const handleClose = () => {
    setConfirmingCancel(false)
    setError('')
    onClose()
  }

  const handleCancel = async () => {
    setCancelling(true)
    setError('')
    try {
      const updated = await cancelReservation(r.id)
      onCancelled(updated)
    } catch (err) {
      setError(err.response?.data?.message || "L'annulation a échoué, veuillez réessayer")
      setCancelling(false)
      setConfirmingCancel(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={handleClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="font-display text-lg font-semibold text-ink">{r.spaceNom}</h3>
            <p className="text-xs font-mono text-heading/50">{r.spaceCode}</p>
          </div>
          <button onClick={handleClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <div className={`flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm font-medium mb-2 ${banner.cls}`}>
          <BannerIcon className="w-4 h-4 shrink-0" />
          {banner.text}
        </div>

        <div className="flex items-center gap-2 mb-1">
          <ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} />
        </div>

        <p className={sectionTitle}>Espace</p>
        <div className={row}><span className={key}>Type</span><span className={val}>{spaceTypeLabel(r.spaceType)}</span></div>
        <div className={row}><span className={key}>Bâtiment</span><span className={val}>{r.buildingNom || '—'}</span></div>
        <div className={row}><span className={key}>Étage</span><span className={val}>{r.floorNom || '—'}</span></div>
        <div className={row}><span className={key}>Capacité</span><span className={val}>{r.spaceCapacite ?? '—'} places</span></div>

        <p className={sectionTitle}>Réservation</p>
        <div className={row}><span className={key}>Date</span><span className={val}>{formatLongDate(r.date)}</span></div>
        <div className={row}><span className={key}>Horaire</span><span className={val}>{formatHeure(r.heureDebut)} → {formatHeure(r.heureFin)}</span></div>
        <div className={row}><span className={key}>Motif</span><span className={val}>{r.motif}</span></div>
        {r.commentaire && (
          <div className={row}><span className={key}>Commentaire</span><span className={val}>{r.commentaire}</span></div>
        )}

        <p className={sectionTitle}>Suivi</p>
        <div className={row}><span className={key}>Demande envoyée le</span><span className={val}>{formatDateTime(r.createdAt, formatHeure)}</span></div>
        <div className={row}><span className={key}>Priorité de traitement</span><span className={val}>{reservationPriorityLabel(r.priorite)}</span></div>

        {error && (
          <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mt-4">
            {error}
          </p>
        )}

        <div className="flex items-center justify-end gap-3 pt-6">
          {canCancel && !confirmingCancel && (
            <button
              onClick={() => setConfirmingCancel(true)}
              className="px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            >
              Annuler ma demande
            </button>
          )}
          {canCancel && confirmingCancel && (
            <>
              <span className="text-sm text-ink/60">Confirmer l'annulation ?</span>
              <button
                onClick={() => setConfirmingCancel(false)}
                className="px-3 py-2 text-sm font-medium text-ink/60 hover:bg-ink/5 rounded-lg transition-colors"
              >
                Non
              </button>
              <button
                onClick={handleCancel}
                disabled={cancelling}
                className="px-3 py-2 text-sm font-medium text-white bg-danger hover:bg-danger-strong disabled:opacity-50 rounded-lg transition-colors"
              >
                {cancelling ? '...' : "Oui, annuler"}
              </button>
            </>
          )}
          {!confirmingCancel && (
            <button
              onClick={handleClose}
              className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors"
            >
              Fermer
            </button>
          )}
        </div>
      </div>
    </div>
  )
}