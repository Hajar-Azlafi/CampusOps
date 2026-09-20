import { useState, useEffect } from 'react'
import { createLevel, updateLevel } from '../api/levelsApi'
import { TYPE_FORMATION_OPTIONS } from '../constants/formation'
import { IconX } from './icons'

const emptyForm = { nom: '', ordre: 0, typeFormation: 'INITIALE', nombreAnnees: 1 }

export default function LevelFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        ordre: initialData.ordre ?? 0,
        typeFormation: initialData.typeFormation ?? 'INITIALE',
        nombreAnnees: initialData.nombreAnnees ?? 1,
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

    setIsSubmitting(true)
    const payload = {
      nom: form.nom.trim(),
      ordre: Number(form.ordre),
      typeFormation: form.typeFormation,
      nombreAnnees: Number(form.nombreAnnees),
    }

    try {
      if (mode === 'create') {
        const created = await createLevel(payload)
        onSuccess(created)
      } else {
        const updated = await updateLevel(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce niveau est déjà utilisé')
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
            {mode === 'create' ? 'Nouveau niveau' : 'Modifier le niveau'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Nom du niveau / cycle</label>
            <input
              required
              value={form.nom}
              onChange={handleChange('nom')}
              placeholder="Ex. : Tronc commun, Licence, Master, Cycle ingénieur…"
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Type de formation</label>
            <select
              required
              value={form.typeFormation}
              onChange={handleChange('typeFormation')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              {TYPE_FORMATION_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Nombre d'années</label>
            <input
              type="number"
              min={1}
              max={6}
              required
              value={form.nombreAnnees}
              onChange={handleChange('nombreAnnees')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
            <p className="mt-1 text-xs text-ink/50">
              Nombre d'années d'étude du cycle (Tronc commun 2, Licence 1, Master 2, Cycle ingénieur 3).
              Détermine les semestres (S1…S<sub>2×années</sub>) et les groupes créés par année.
            </p>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Ordre d'affichage</label>
            <input
              type="number"
              min={0}
              required
              value={form.ordre}
              onChange={handleChange('ordre')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
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
