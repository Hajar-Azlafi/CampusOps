import { useState, useEffect } from 'react'
import { createSeanceType, updateSeanceType } from '../api/seanceTypesApi'
import { IconX } from './icons'

const emptyForm = { nom: '', code: '', couleur: '#2563EB', ordre: 0 }

export default function TypeSeanceFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        code: initialData.code ?? '',
        couleur: initialData.couleur ?? '',
        ordre: initialData.ordre ?? 0,
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

    const couleur = (form.couleur || '').trim()
    const payload = {
      nom: form.nom.trim(),
      code: form.code.trim(),
      couleur: couleur ? couleur : null,
      ordre: form.ordre === '' || form.ordre === null ? 0 : Number(form.ordre),
    }

    try {
      if (mode === 'create') {
        const created = await createSeanceType(payload)
        onSuccess(created)
      } else {
        const updated = await updateSeanceType(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Un type de séance avec ce code existe déjà')
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
            {mode === 'create' ? 'Nouveau type de séance' : 'Modifier le type de séance'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Nom</label>
            <input
              type="text"
              required
              value={form.nom}
              onChange={handleChange('nom')}
              placeholder="ex. Cours magistral"
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Code</label>
              <input
                type="text"
                required
                maxLength={40}
                value={form.code}
                onChange={handleChange('code')}
                placeholder="ex. COURS"
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
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
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">
              Couleur <span className="text-ink/40 font-normal">(optionnel)</span>
            </label>
            <div className="flex items-center gap-3">
              <input
                type="color"
                value={/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(form.couleur || '') ? form.couleur : '#2563EB'}
                onChange={handleChange('couleur')}
                className="h-10 w-14 rounded-lg border border-ink/15 bg-surface p-1 cursor-pointer"
              />
              <input
                type="text"
                value={form.couleur}
                onChange={handleChange('couleur')}
                placeholder="#2563EB"
                className="flex-1 px-3 py-2.5 border border-ink/15 rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
              {form.couleur && (
                <button
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, couleur: '' }))}
                  className="text-xs text-ink/50 hover:text-ink/80 whitespace-nowrap"
                >
                  Effacer
                </button>
              )}
            </div>
            <p className="text-xs text-ink/40 mt-1.5">
              Utilisée pour distinguer visuellement ce type dans les emplois du temps et les statistiques.
            </p>
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
