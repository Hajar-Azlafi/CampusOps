import { useState, useEffect } from 'react'
import { createPromotion, updatePromotion } from '../api/promotionsApi'
import { IconX } from './icons'

const emptyForm = { nom: '', programId: '', levelId: '', academicYearId: '' }

export default function PromotionFormModal({ open, mode, initialData, programs, levels, academicYears, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        programId: initialData.programId ?? '',
        levelId: initialData.levelId ?? '',
        academicYearId: initialData.academicYearId ?? '',
      })
    } else {
      // En création, on cible par défaut l'année universitaire active.
      const activeYear = (academicYears || []).find((y) => y.actif)
      setForm({ ...emptyForm, academicYearId: activeYear ? activeYear.id : '' })
    }
    setError('')
  }, [open, mode, initialData, academicYears])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!form.programId) {
      setError('Veuillez sélectionner une filière')
      return
    }
    if (!form.levelId) {
      setError('Veuillez sélectionner un niveau')
      return
    }
    if (!form.academicYearId) {
      setError('Veuillez sélectionner une année académique')
      return
    }

    setIsSubmitting(true)
    const payload = {
      nom: form.nom.trim(),
      programId: Number(form.programId),
      levelId: Number(form.levelId),
      academicYearId: Number(form.academicYearId),
    }

    try {
      if (mode === 'create') {
        const created = await createPromotion(payload)
        onSuccess(created)
      } else {
        const updated = await updatePromotion(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Cette promotion existe déjà')
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
            {mode === 'create' ? 'Nouvelle promotion' : 'Modifier la promotion'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
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
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Filière</label>
            <select
              required
              value={form.programId}
              onChange={handleChange('programId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner une filière...</option>
              {programs.map((p) => (
                <option key={p.id} value={p.id}>{p.nom}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Niveau</label>
            <select
              required
              value={form.levelId}
              onChange={handleChange('levelId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner un niveau...</option>
              {levels.map((l) => (
                <option key={l.id} value={l.id}>{l.nom}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Année académique</label>
            <select
              required
              value={form.academicYearId}
              onChange={handleChange('academicYearId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner une année académique...</option>
              {academicYears.map((a) => (
                <option key={a.id} value={a.id}>{a.libelle}{a.actif ? ' (active)' : ''}</option>
              ))}
            </select>
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
