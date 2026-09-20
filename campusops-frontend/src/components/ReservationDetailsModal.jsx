import { reservationTypeLabel } from '../constants/reservationTypes'
import { reservationStatusLabel } from '../constants/reservationStatuses'
import { reservationPriorityLabel } from '../constants/reservationPriorities'
import { roleLabel } from '../constants/roles'
import { useSettings } from '../context/SettingsContext'
import { ReservationStatusBadge, PriorityBadge } from './Badge'
import { IconX } from './icons'

const row = 'flex justify-between gap-4 py-2 border-b border-ink/5 last:border-0'
const key = 'text-sm text-ink/50'
const val = 'text-sm text-ink/80 text-right'

export default function ReservationDetailsModal({ open, reservation, onClose }) {
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDate, formatHeure } = useSettings()
  if (!open || !reservation) return null
  const r = reservation

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">Détails de la réservation</h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <div className="flex items-center gap-2 mb-4">
          <ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} />
          <PriorityBadge priority={r.priorite} label={reservationPriorityLabel(r.priorite)} />
        </div>

        <div>
          <div className={row}><span className={key}>Espace</span><span className={val}>{r.spaceCode} — {r.spaceNom}</span></div>
          <div className={row}><span className={key}>Type</span><span className={val}>{reservationTypeLabel(r.type)}</span></div>
          <div className={row}><span className={key}>Date</span><span className={val}>{formatDate(r.date)}</span></div>
          <div className={row}><span className={key}>Horaire</span><span className={val}>{formatHeure(r.heureDebut)} — {formatHeure(r.heureFin)}</span></div>
          <div className={row}><span className={key}>Motif</span><span className={val}>{r.motif}</span></div>
          {r.commentaire && (
            <div className={row}><span className={key}>Commentaire</span><span className={val}>{r.commentaire}</span></div>
          )}
          <div className={row}><span className={key}>Réservé par</span><span className={val}>{r.userNomComplet}</span></div>
          <div className={row}><span className={key}>Email</span><span className={val}>{r.userEmail}</span></div>
          <div className={row}><span className={key}>Rôle</span><span className={val}>{roleLabel(r.userRole)}</span></div>
        </div>

        <div className="flex justify-end pt-6">
          <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
            Fermer
          </button>
        </div>
      </div>
    </div>
  )
}
