import { useState, useEffect } from 'react'
import { createAcademicYear, updateAcademicYear } from '../api/academicYearsApi'
import { IconX } from './icons'

const emptyForm = { libelle: '', dateDebut: '', dateFin: '', definirCommeActive: false }

export default function AcademicYearFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        libelle: initialData.libelle ?? '',
        dateDebut: initialData.dateDebut ?? '',
        dateFin: initialData.dateFin ?? '',
        definirCommeActive: false,
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
      libelle: form.libelle.trim(),
      dateDebut: form.dateDebut,
      dateFin: form.dateFin,
      definirCommeActive: form.definirCommeActive,
    }

    try {
      if (mode === 'create') {
        const created = await createAcademicYear(payload)
        onSuccess(created)
      } else {
        const updated = await updateAcademicYear(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce libellé est déjà utilisé')
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
            {mode === 'create' ? 'Nouvelle année universitaire' : "Modifier l'année universitaire"}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Libellé</label>
            <input
              required
              value={form.libelle}
              onChange={handleChange('libelle')}
              placeholder="2025-2026"
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Date de début</label>
              <input
                type="date"
                required
                value={form.dateDebut}
                onChange={handleChange('dateDebut')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Date de fin</label>
              <input
                type="date"
                required
                value={form.dateFin}
                onChange={handleChange('dateFin')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>

          {mode === 'create' && (
            <label className="flex items-start gap-3 cursor-pointer rounded-lg border border-ink/10 bg-ink/[0.015] px-3 py-2.5">
              <input
                type="checkbox"
                checked={form.definirCommeActive}
                onChange={(e) => setForm((f) => ({ ...f, definirCommeActive: e.target.checked }))}
                className="mt-0.5 w-4 h-4 accent-blueprint-800"
              />
              <span className="text-sm text-ink/80">
                Définir comme année active
                <span className="block text-xs text-ink/50 mt-0.5">
                  L'année active actuelle sera automatiquement désactivée. Ses données restent conservées.
                </span>
              </span>
            </label>
          )}

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
