import { useState, useEffect } from 'react'
import { createSpace, updateSpace } from '../api/spacesApi'
import { fetchFloors } from '../api/floorsApi'
import { SPACE_TYPES, SPECIALIZABLE_SPACE_TYPES, LAB_SPECIALITIES } from '../constants/spaceTypes'
import { IconX } from './icons'

const emptyForm = { nom: '', code: '', type: '', capacite: 1, description: '', floorId: '', speciality: '' }

export default function SpaceFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [floors, setFloors] = useState([])
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    fetchFloors({ actif: true })
      .then(setFloors)
      .catch(() => setFloors([]))
  }, [open])

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        code: initialData.code ?? '',
        type: initialData.type ?? '',
        capacite: initialData.capacite ?? 1,
        description: initialData.description ?? '',
        floorId: initialData.floorId ?? '',
        speciality: initialData.speciality ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  // La spécialité ne concerne que les espaces spécialisables (laboratoire, salle
  // informatique) : changer de type vers un autre espace efface la spécialité.
  const isSpecializable = SPECIALIZABLE_SPACE_TYPES.includes(form.type)
  const handleTypeChange = (e) => {
    const type = e.target.value
    setForm((f) => ({
      ...f,
      type,
      speciality: SPECIALIZABLE_SPACE_TYPES.includes(type) ? f.speciality : '',
    }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!form.floorId) {
      setError('Veuillez sélectionner un étage')
      return
    }
    if (!form.type) {
      setError('Veuillez sélectionner un type')
      return
    }

    setIsSubmitting(true)
    const payload = {
      nom: form.nom.trim(),
      code: form.code.trim(),
      type: form.type,
      capacite: Number(form.capacite),
      description: form.description.trim() || null,
      floorId: Number(form.floorId),
      speciality: isSpecializable && form.speciality ? form.speciality : null,
    }

    try {
      if (mode === 'create') {
        const created = await createSpace(payload)
        onSuccess(created)
      } else {
        const updated = await updateSpace(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce code est déjà utilisé dans ce bâtiment')
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
            {mode === 'create' ? 'Nouvel espace' : "Modifier l'espace"}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Étage</label>
            <select
              required
              value={form.floorId}
              onChange={handleChange('floorId')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner un étage...</option>
              {floors.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.buildingCode} — {f.nom} (n°{f.numero})
                </option>
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

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Type</label>
              <select
                required
                value={form.type}
                onChange={handleTypeChange}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              >
                <option value="">Sélectionner...</option>
                {SPACE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Capacité</label>
              <input
                type="number"
                min={1}
                required
                value={form.capacite}
                onChange={handleChange('capacite')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>

          {isSpecializable && (
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">
                Spécialité du laboratoire
              </label>
              <select
                value={form.speciality}
                onChange={handleChange('speciality')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              >
                <option value="">Non spécifiée</option>
                {LAB_SPECIALITIES.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
              <p className="mt-1 text-xs text-ink/50">
                Distingue les laboratoires par domaine (informatique, réseaux, physique,
                chimie…). Facultative : laissez « Non spécifiée » si le laboratoire n'est pas dédié.
              </p>
            </div>
          )}

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
