import { useState, useEffect, useCallback } from 'react'
import {
  fetchEquipments,
  getSpaceEquipments,
  assignSpaceEquipments,
  removeSpaceEquipment,
} from '../api/equipmentsApi'
import { IconX } from './icons'

export default function SpaceEquipmentsModal({ open, space, onClose, onSuccess }) {
  const [allEquipments, setAllEquipments] = useState([])
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [initialIds, setInitialIds] = useState(new Set())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const load = useCallback(async () => {
    if (!space) return
    setLoading(true)
    setError('')
    try {
      const [catalog, assigned] = await Promise.all([
        fetchEquipments({ actif: true }),
        getSpaceEquipments(space.id),
      ])
      const assignedIds = new Set(assigned.map((e) => e.id))
      // Inclure les equipements deja associes meme s'ils sont inactifs
      const merged = [...catalog]
      assigned.forEach((e) => {
        if (!merged.some((m) => m.id === e.id)) merged.push(e)
      })
      merged.sort((a, b) => a.nom.localeCompare(b.nom))
      setAllEquipments(merged)
      setSelectedIds(new Set(assignedIds))
      setInitialIds(new Set(assignedIds))
    } catch {
      setError('Impossible de charger les équipements')
    } finally {
      setLoading(false)
    }
  }, [space])

  useEffect(() => {
    if (open) load()
  }, [open, load])

  if (!open) return null

  const toggle = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleSave = async () => {
    setError('')
    setIsSubmitting(true)

    const toAdd = [...selectedIds].filter((id) => !initialIds.has(id))
    const toRemove = [...initialIds].filter((id) => !selectedIds.has(id))

    try {
      if (toAdd.length > 0) {
        await assignSpaceEquipments(space.id, toAdd)
      }
      for (const id of toRemove) {
        await removeSpaceEquipment(space.id, id)
      }
      onSuccess?.()
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || "L'enregistrement a échoué, veuillez réessayer")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-1">
          <h3 className="font-display text-lg font-semibold text-ink">Équipements de l'espace</h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>
        <p className="text-sm text-ink/50 mb-5">
          {space?.code} — {space?.nom}
        </p>

        {error && (
          <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
            {error}
          </p>
        )}

        {loading ? (
          <p className="text-center py-10 text-ink/40 text-sm">Chargement...</p>
        ) : (
          <div className="border border-ink/10 rounded-lg divide-y divide-ink/5 max-h-[45vh] overflow-y-auto">
            {allEquipments.length === 0 && (
              <p className="text-center py-8 text-ink/40 text-sm">Aucun équipement disponible</p>
            )}
            {allEquipments.map((eq) => (
              <label
                key={eq.id}
                className="flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-ink/[0.015]"
              >
                <input
                  type="checkbox"
                  checked={selectedIds.has(eq.id)}
                  onChange={() => toggle(eq.id)}
                  className="w-4 h-4 accent-blueprint-800"
                />
                <span className="font-mono text-xs text-ink/60 w-40 truncate">{eq.code}</span>
                <span className="text-sm text-ink flex-1">{eq.nom}</span>
                {!eq.actif && (
                  <span className="text-[10px] text-ink/40 uppercase tracking-wide">Inactif</span>
                )}
              </label>
            ))}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-5">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
            Annuler
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={isSubmitting || loading}
            className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
          >
            {isSubmitting ? '...' : 'Enregistrer'}
          </button>
        </div>
      </div>
    </div>
  )
}
