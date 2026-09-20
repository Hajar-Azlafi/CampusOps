import { useState, useEffect } from 'react'
import { createSemester, updateSemester } from '../api/semestersApi'
import { fetchLevels } from '../api/levelsApi'
import { IconX } from './icons'

const emptyForm = { nom: '', ordre: 0, levelId: '', dateDebut: '', dateFin: '' }

export default function SemesterFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [levels, setLevels] = useState([])
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    fetchLevels({ actif: true })
      .then(setLevels)
      .catch(() => setLevels([]))
  }, [open])

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        ordre: initialData.ordre ?? 0,
        levelId: initialData.levelId ?? '',
        dateDebut: initialData.dateDebut ?? '',
        dateFin: initialData.dateFin ?? '',
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
      levelId: form.levelId === '' ? null : Number(form.levelId),
      dateDebut: form.dateDebut === '' ? null : form.dateDebut,
      dateFin: form.dateFin === '' ? null : form.dateFin,
    }

    try {
      if (mode === 'create') {
        const created = await createSemester(payload)
        onSuccess(created)
      } else {
        const updated = await updateSemester(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce semestre existe déjà')
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
            {mode === 'create' ? 'Nouveau semestre' : 'Modifier le semestre'}
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
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Ordre</label>
            <input
              type="number"
              min={0}
              required
              value={form.ordre}
              onChange={handleChange('ordre')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Niveau / cycle</label>
            <select
              value={form.levelId}
              onChange={handleChange('levelId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Aucun (semestre libre)</option>
              {levels.map((level) => (
                <option key={level.id} value={level.id}>{level.nom}</option>
              ))}
            </select>
            <p className="text-xs text-ink/50 mt-1">
              Un même libellé (ex. « S1 ») peut exister dans plusieurs cycles. Laisser vide pour un semestre libre (formation continue).
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Début de période</label>
              <input
                type="date"
                value={form.dateDebut}
                onChange={handleChange('dateDebut')}
                max={form.dateFin || undefined}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Fin de période</label>
              <input
                type="date"
                value={form.dateFin}
                onChange={handleChange('dateFin')}
                min={form.dateDebut || undefined}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>
          <p className="text-xs text-ink/50 -mt-2">
            La période du semestre borne ses emplois du temps et ses examens : aucune séance ni examen ne peut dépasser ces dates. Facultatif (repli sur l'année universitaire).
          </p>

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
