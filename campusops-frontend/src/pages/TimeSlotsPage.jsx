import { useEffect, useState, useCallback } from 'react'
import {
  fetchTimeSlots,
  deleteTimeSlot,
  reorderTimeSlots,
  getTimeSlotDeletionImpact,
} from '../api/timeSlotsApi'
import { useSettings } from '../context/SettingsContext'
import TimeSlotFormModal from '../components/TimeSlotFormModal'
import DeleteDialog from '../components/DeleteDialog'

// Les créneaux horaires sont des plages de référence, définies au niveau de
// l'établissement et présentées dans un ordre fixe (§1-§5). Le statut
// actif/inactif n'est pas exposé ici : un créneau existe ou non. L'ordre est
// géré via les flèches Monter/Descendre (endpoint /time-slots/reorder).

export default function TimeSlotsPage() {
  // Format d'heure de l'établissement (Paramètres > Affichage, §10). Les bornes
  // restent saisies en 24 h dans le formulaire : seul l'affichage change.
  const { formatHeure } = useSettings()
  const [timeSlots, setTimeSlots] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', timeSlot: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [reordering, setReordering] = useState(false)

  const loadTimeSlots = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchTimeSlots()
      setTimeSlots(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des créneaux')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTimeSlots()
  }, [loadTimeSlots])

  const openCreate = () => setFormModal({ open: true, mode: 'create', timeSlot: null })
  const openEdit = (timeSlot) => setFormModal({ open: true, mode: 'edit', timeSlot })
  const closeForm = () => setFormModal({ open: false, mode: 'create', timeSlot: null })

  const handleFormSuccess = () => {
    closeForm()
    loadTimeSlots()
  }

  const askDelete = (timeSlot) => setDeleteTarget(timeSlot)

  // Déplace le créneau d'index `index` d'un cran (direction = -1 monter, +1 descendre)
  // et persiste le nouvel ordre via l'endpoint /time-slots/reorder.
  const moveSlot = async (index, direction) => {
    const target = index + direction
    if (target < 0 || target >= timeSlots.length) return

    const reordered = [...timeSlots]
    const [moved] = reordered.splice(index, 1)
    reordered.splice(target, 0, moved)

    // Optimiste : on affiche tout de suite le nouvel ordre.
    setTimeSlots(reordered)
    setReordering(true)
    setErrorMsg('')
    try {
      const updated = await reorderTimeSlots(reordered.map((s) => s.id))
      setTimeSlots(updated)
    } catch {
      setErrorMsg("Impossible de réorganiser les créneaux, veuillez réessayer")
      loadTimeSlots()
    } finally {
      setReordering(false)
    }
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Créneaux horaires</h1>
          <p className="text-sm text-ink/50 mt-1">
            {timeSlots.length} créneau(x) · Plages horaires standard utilisées dans les emplois du temps
          </p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau créneau
        </button>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50 w-28">Ordre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Créneau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Durée</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && timeSlots.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucun créneau défini</td></tr>
            )}
            {!loading && timeSlots.map((timeSlot, idx) => {
              // Calcul de la durée en minutes
              const [h1, m1] = (timeSlot.heureDebut || '00:00').split(':').map(Number)
              const [h2, m2] = (timeSlot.heureFin || '00:00').split(':').map(Number)
              const totalMin = (h2 * 60 + m2) - (h1 * 60 + m1)
              const dureeLabel = totalMin > 0
                ? `${Math.floor(totalMin / 60) > 0 ? Math.floor(totalMin / 60) + 'h' : ''}${totalMin % 60 > 0 ? (totalMin % 60) + 'min' : ''}`
                : '—'

              return (
                <tr key={timeSlot.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className="text-ink/40 tabular-nums w-5">{idx + 1}</span>
                      <div className="flex flex-col">
                        <button
                          onClick={() => moveSlot(idx, -1)}
                          disabled={idx === 0 || reordering}
                          title="Monter"
                          aria-label="Monter"
                          className="px-1.5 leading-none text-ink/50 hover:text-heading disabled:opacity-20 disabled:hover:text-ink/50"
                        >
                          ▲
                        </button>
                        <button
                          onClick={() => moveSlot(idx, 1)}
                          disabled={idx === timeSlots.length - 1 || reordering}
                          title="Descendre"
                          aria-label="Descendre"
                          className="px-1.5 leading-none text-ink/50 hover:text-heading disabled:opacity-20 disabled:hover:text-ink/50"
                        >
                          ▼
                        </button>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-ink/80">
                    {timeSlot.nom || <span className="text-ink/30">—</span>}
                  </td>
                  <td className="px-4 py-3 font-medium text-ink font-mono">
                    {formatHeure(timeSlot.heureDebut)} — {formatHeure(timeSlot.heureFin)}
                  </td>
                  <td className="px-4 py-3 text-ink/50 text-xs">{dureeLabel}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button
                      onClick={() => openEdit(timeSlot)}
                      className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                    >
                      Modifier
                    </button>
                    <button
                      onClick={() => askDelete(timeSlot)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Supprimer
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <TimeSlotFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.timeSlot}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <DeleteDialog
        target={deleteTarget}
        entityName="le créneau"
        getLabel={(t) => t?.nom || `${formatHeure(t?.heureDebut)} — ${formatHeure(t?.heureFin)}`}
        impactFn={getTimeSlotDeletionImpact}
        deleteFn={deleteTimeSlot}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadTimeSlots}
      />
    </div>
  )
}
