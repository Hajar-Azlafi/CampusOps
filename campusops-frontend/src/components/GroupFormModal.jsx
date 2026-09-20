import { useState, useEffect } from 'react'
import { createGroup, updateGroup } from '../api/groupsApi'
import { IconX } from './icons'

const emptyForm = { nom: '', promotionId: '', anneeNiveau: '', effectif: '' }

export default function GroupFormModal({ open, mode, initialData, promotions, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        nom: initialData.nom ?? '',
        promotionId: initialData.promotionId ?? '',
        anneeNiveau: initialData.anneeNiveau ?? '',
        effectif: initialData.effectif ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  // Nombre d'années du niveau de la promotion sélectionnée → années d'étude
  // sélectionnables (1..nombreAnnees). Master 2 => 1re/2e année, Licence 1 => 1re.
  const selectedPromotion = promotions.find((p) => String(p.id) === String(form.promotionId))
  const nombreAnnees = selectedPromotion?.levelNombreAnnees ?? null
  const anneeOptions = nombreAnnees && nombreAnnees > 0
    ? Array.from({ length: nombreAnnees }, (_, i) => i + 1)
    : []

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  // Changer de promotion réinitialise l'année si elle dépasse le nouveau cycle.
  const handlePromotionChange = (e) => {
    const promotionId = e.target.value
    const promo = promotions.find((p) => String(p.id) === String(promotionId))
    const max = promo?.levelNombreAnnees ?? null
    setForm((f) => ({
      ...f,
      promotionId,
      anneeNiveau: max && Number(f.anneeNiveau) > max ? '' : f.anneeNiveau,
    }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!form.promotionId) {
      setError('Veuillez sélectionner une promotion')
      return
    }
    if (anneeOptions.length > 0 && !form.anneeNiveau) {
      setError("Veuillez préciser l'année d'étude du groupe")
      return
    }

    setIsSubmitting(true)
    const payload = {
      nom: form.nom.trim(),
      promotionId: Number(form.promotionId),
      anneeNiveau: form.anneeNiveau ? Number(form.anneeNiveau) : null,
      effectif: form.effectif ? Number(form.effectif) : null,
    }

    try {
      if (mode === 'create') {
        const created = await createGroup(payload)
        onSuccess(created)
      } else {
        const updated = await updateGroup(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce nom est déjà utilisé dans cette promotion')
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
            {mode === 'create' ? 'Nouveau groupe' : 'Modifier le groupe'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Promotion</label>
            <select
              required
              value={form.promotionId}
              onChange={handlePromotionChange}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Sélectionner une promotion...</option>
              {promotions.map((p) => (
                <option key={p.id} value={p.id}>{p.nom}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">
              Année d'étude
            </label>
            <select
              value={form.anneeNiveau}
              onChange={handleChange('anneeNiveau')}
              disabled={!form.promotionId || anneeOptions.length === 0}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface disabled:bg-ink/5 disabled:text-ink/40 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">
                {form.promotionId ? "Sélectionner l'année..." : "Choisissez d'abord une promotion"}
              </option>
              {anneeOptions.map((annee) => (
                <option key={annee} value={annee}>
                  {annee === 1 ? '1re année' : `${annee}e année`}
                </option>
              ))}
            </select>
            <p className="mt-1 text-xs text-ink/50">
              Année du groupe dans son cycle (ex. Master : 1re année = M1, 2e année = M2).
              Essentielle à la gestion et à l'import des emplois du temps.
            </p>
          </div>

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
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Effectif</label>
            <input
              type="number"
              min={1}
              max={500}
              value={form.effectif}
              onChange={handleChange('effectif')}
              placeholder="Nombre d'étudiants"
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
            <p className="mt-1 text-xs text-ink/50">
              Nombre d'étudiants du groupe. Sert à l'affectation des salles
              (effectif ≤ capacité) et à la génération des emplois du temps.
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
