import { useState, useEffect } from 'react'
import { createFloor, updateFloor } from '../api/floorsApi'
import { IconX } from './icons'

const emptyForm = { nom: '', code: '', numero: 0, description: '', buildingId: '' }

export default function FloorFormModal({ open, mode, initialData, buildings, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        code: initialData.code ?? '',
        numero: initialData.numero ?? 0,
        description: initialData.description ?? '',
        buildingId: initialData.buildingId ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!form.buildingId) {
      setError('Veuillez sélectionner un bâtiment')
      return
    }

    setIsSubmitting(true)
    const payload = {
      nom: form.nom.trim(),
      code: form.code.trim(),
      numero: Number(form.numero),
      description: form.description.trim() || null,
      buildingId: Number(form.buildingId),
    }

    try {
      if (mode === 'create') {
        const created = await createFloor(payload)
        onSuccess(created)
      } else {
        const updated = await updateFloor(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce numéro ou ce code est déjà utilisé dans ce bâtiment')
      } else if (err.response?.status === 400) {
        setError(err.response.data?.message || 'Requête invalide')
      } else if (err.response?.data?.validationErrors?.length) {
        setError(err.response.data.validationErrors.join(' — '))
      } else {
        setError('Une erreur est survenue, veuillez réessayer')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">
            {mode === 'create' ? 'Nouvel étage' : "Modifier l'étage"}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Bâtiment</label>
            <select
              required
              value={form.buildingId}
              onChange={handleChange('buildingId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner un bâtiment...</option>
              {buildings.map((b) => (
                <option key={b.id} value={b.id}>{b.code} — {b.nom}</option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Nom</label>
              <input
                required
                value={form.nom}
                onChange={handleChange('nom')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Code</label>
              <input
                required
                value={form.code}
                onChange={handleChange('code')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Numéro d'étage</label>
            <input
              type="number"
              min={0}
              required
              value={form.numero}
              onChange={handleChange('numero')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
            <p className="text-xs text-ink/40 mt-1">0 = rez-de-chaussée</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Description</label>
            <textarea
              rows={3}
              value={form.description}
              onChange={handleChange('description')}
              placeholder="Optionnel"
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal resize-none"
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
              Annuler
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
            >
              {isSubmitting ? '...' : mode === 'create' ? 'Créer' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
