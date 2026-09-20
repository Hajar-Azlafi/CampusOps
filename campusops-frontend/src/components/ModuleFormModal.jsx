import { useState, useEffect } from 'react'
import { createModule, updateModule } from '../api/modulesApi'
import { fetchPrograms } from '../api/programsApi'
import { fetchSemesters } from '../api/semestersApi'
import { IconX } from './icons'

const emptyForm = { nom: '', code: '', description: '', programId: '', semesterId: '' }

export default function ModuleFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [programs, setPrograms] = useState([])
  const [semesters, setSemesters] = useState([])
  const [loadingSemesters, setLoadingSemesters] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Chargement des filières accessibles (bornées au périmètre côté backend :
  // ADMIN → toutes ; responsable pédagogique → uniquement les siennes).
  useEffect(() => {
    if (!open) return
    fetchPrograms({ actif: true })
      .then((data) => setPrograms(data || []))
      .catch(() => setPrograms([]))
  }, [open])

  // Hydratation du formulaire (création vs édition).
  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        code: initialData.code ?? '',
        description: initialData.description ?? '',
        programId: initialData.programId != null ? String(initialData.programId) : '',
        semesterId: initialData.semesterId != null ? String(initialData.semesterId) : '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  // Cascade filière → semestre : les semestres proposés sont ceux du niveau de
  // la filière sélectionnée (le semestre porte son niveau). On ne réinitialise
  // PAS semesterId ici pour préserver la valeur lors de l'édition ; la remise à
  // zéro se fait dans le handler de changement de filière (choix utilisateur).
  const selectedProgram = programs.find((p) => String(p.id) === String(form.programId))
  const selectedLevelId = selectedProgram?.levelId ?? null

  useEffect(() => {
    if (!open) return
    if (!form.programId) {
      setSemesters([])
      return
    }
    if (!selectedLevelId) {
      setSemesters([])
      return
    }
    let cancelled = false
    setLoadingSemesters(true)
    fetchSemesters({ levelId: selectedLevelId })
      .then((data) => { if (!cancelled) setSemesters(data || []) })
      .catch(() => { if (!cancelled) setSemesters([]) })
      .finally(() => { if (!cancelled) setLoadingSemesters(false) })
    return () => { cancelled = true }
  }, [open, form.programId, selectedLevelId])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const handleProgramChange = (e) => {
    const value = e.target.value
    // Changer de filière invalide le semestre précédemment choisi.
    setForm((f) => ({ ...f, programId: value, semesterId: '' }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)

    const payload = {
      nom: form.nom.trim(),
      code: form.code.trim() ? form.code.trim() : null,
      description: form.description.trim() ? form.description.trim() : null,
      programId: form.programId ? Number(form.programId) : null,
      semesterId: form.semesterId ? Number(form.semesterId) : null,
    }

    try {
      if (mode === 'create') {
        const created = await createModule(payload)
        onSuccess(created)
      } else {
        const updated = await updateModule(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Un module identique existe déjà pour ce contexte (filière + semestre)')
      } else if (err.response?.status === 403) {
        setError("Vous n'avez pas accès à cette filière")
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

  const inputClass = 'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal'
  const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">
            {mode === 'create' ? 'Nouveau module' : 'Modifier le module'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={labelClass}>Nom du module</label>
            <input
              type="text"
              required
              value={form.nom}
              onChange={handleChange('nom')}
              placeholder="ex. Anglais"
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>
              Code <span className="text-ink/40 font-normal">(optionnel)</span>
            </label>
            <input
              type="text"
              maxLength={40}
              value={form.code}
              onChange={handleChange('code')}
              placeholder="ex. ANG-S1-01"
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>Filière</label>
            <select
              required
              value={form.programId}
              onChange={handleProgramChange}
              className={inputClass}
            >
              <option value="">— Sélectionner une filière —</option>
              {programs.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nom}{p.code ? ` (${p.code})` : ''}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className={labelClass}>Semestre</label>
            <select
              required
              value={form.semesterId}
              onChange={handleChange('semesterId')}
              disabled={!form.programId || !selectedLevelId || loadingSemesters}
              className={`${inputClass} disabled:bg-ink/5 disabled:text-ink/40`}
            >
              <option value="">
                {!form.programId
                  ? '— Choisir d\'abord une filière —'
                  : loadingSemesters
                    ? 'Chargement...'
                    : '— Sélectionner un semestre —'}
              </option>
              {semesters.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.nom}{s.actif === false ? ' (inactif)' : ''}
                </option>
              ))}
            </select>
            {form.programId && !selectedLevelId && (
              <p className="text-xs text-amber-700 mt-1.5">
                Cette filière n'a pas de niveau associé : aucun semestre disponible.
              </p>
            )}
          </div>

          <div>
            <label className={labelClass}>
              Description <span className="text-ink/40 font-normal">(optionnel)</span>
            </label>
            <textarea
              rows={3}
              value={form.description}
              onChange={handleChange('description')}
              placeholder="Brève description du module..."
              className={`${inputClass} resize-none`}
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
