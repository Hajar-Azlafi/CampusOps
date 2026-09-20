import { useState, useEffect } from 'react'
import { createNonWorkingDay, updateNonWorkingDay } from '../api/nonWorkingDaysApi'
import { NON_WORKING_DAY_TYPES } from '../constants/nonWorkingDayTypes'
import { IconX } from './icons'

// Formulaire d'un jour non ouvrable (§4). L'administrateur decrit un jour ferie,
// une periode de vacances ou une fermeture exceptionnelle. La date de fin est
// facultative : laissee vide, le jour ne couvre que `dateDebut`. Le drapeau
// « previsionnel » marque une date susceptible d'evoluer (fete religieuse dont
// la date depend de l'observation) : elle reste modifiable sans rien casser.

const emptyForm = {
  dateDebut: '',
  dateFin: '',
  mois: '',
  jour: '',
  libelle: '',
  type: 'FERIE_NATIONAL',
  previsionnel: false,
  commentaire: '',
}

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

export default function NonWorkingDayFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        dateDebut: initialData.dateDebut ?? '',
        mois: initialData.dateDebut ? initialData.dateDebut.slice(5, 7) : '',
        jour: initialData.dateDebut ? initialData.dateDebut.slice(8, 10) : '',
        // On ne pre-remplit la date de fin que si elle differe reellement du
        // debut (un jour isole a dateFin === dateDebut cote backend).
        dateFin:
          initialData.dateFin && initialData.dateFin !== initialData.dateDebut
            ? initialData.dateFin
            : '',
        libelle: initialData.libelle ?? '',
        type: initialData.type ?? 'FERIE_NATIONAL',
        previsionnel: Boolean(initialData.previsionnel),
        commentaire: initialData.commentaire ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const handleToggle = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.checked }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    const isFixedHoliday = form.type === 'FERIE_NATIONAL'
    const dateDebut = isFixedHoliday
      ? `2000-${form.mois}-${form.jour}`
      : form.dateDebut

    if (isFixedHoliday && (!form.mois || !form.jour)) {
      setError('Le mois et le jour sont obligatoires pour un jour férié national.')
      return
    }

    // Coherence des bornes : une periode ne peut pas se terminer avant de
    // commencer. Le controle final est refait cote backend.
    if (!isFixedHoliday && form.dateFin && form.dateFin < dateDebut) {
      setError('La date de fin ne peut pas précéder la date de début.')
      return
    }

    setIsSubmitting(true)

    const payload = {
      dateDebut,
      // Facultative : omise si vide, le backend la ramene alors a dateDebut.
      dateFin: isFixedHoliday ? null : form.dateFin || null,
      recurrent: isFixedHoliday,
      libelle: form.libelle.trim(),
      type: form.type,
      previsionnel: form.previsionnel,
      commentaire: form.commentaire.trim() || null,
    }

    try {
      if (mode === 'create') {
        const created = await createNonWorkingDay(payload)
        onSuccess(created)
      } else {
        const updated = await updateNonWorkingDay(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Ce jour est déjà enregistré')
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
            {mode === 'create' ? 'Nouveau jour non ouvrable' : 'Modifier le jour non ouvrable'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {form.type === 'FERIE_NATIONAL' ? (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className={labelClass}>Mois</label>
                <input required type="number" min="1" max="12" value={form.mois} onChange={handleChange('mois')} className={inputClass} placeholder="1 à 12" />
              </div>
              <div>
                <label className={labelClass}>Jour</label>
                <input required type="number" min="1" max="31" value={form.jour} onChange={handleChange('jour')} className={inputClass} placeholder="1 à 31" />
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className={labelClass}>Date de début</label>
                <input required type="date" value={form.dateDebut} onChange={handleChange('dateDebut')} className={inputClass} />
              </div>
              <div>
              <label className={labelClass}>
                Date de fin <span className="text-ink/40 font-normal">(optionnel)</span>
              </label>
              <input
                type="date"
                value={form.dateFin}
                min={form.dateDebut || undefined}
                onChange={handleChange('dateFin')}
                className={inputClass}
              />
              </div>
            </div>
          )}
          <p className="-mt-2 text-[11px] text-ink/45">
            Laissez la date de fin vide pour un jour isolé ; renseignez-la pour une période
            (vacances, fermeture prolongée).
          </p>

          <div>
            <label className={labelClass}>Libellé</label>
            <input
              required
              value={form.libelle}
              onChange={handleChange('libelle')}
              placeholder="Fête du Trône, Aïd al-Fitr, Vacances d'hiver..."
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>Type</label>
            <select value={form.type} onChange={handleChange('type')} className={inputClass}>
              {NON_WORKING_DAY_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>

          {/* Date previsionnelle (§4) : les fetes religieuses dependent de
              l'observation lunaire ; on signale que la date pourra etre ajustee. */}
          <label className="flex items-start gap-3 rounded-lg border border-ink/10 bg-ink/[0.02] px-3 py-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={form.previsionnel}
              onChange={handleToggle('previsionnel')}
              className="mt-0.5 h-4 w-4 rounded border-ink/30 text-signal focus:ring-signal"
            />
            <span className="text-sm text-ink/70">
              <span className="font-medium text-ink/80">Date prévisionnelle</span>
              <span className="block text-[11px] text-ink/45">
                À cocher pour une fête religieuse dont la date dépend de l'observation et pourra
                être ajustée.
              </span>
            </span>
          </label>

          <div>
            <label className={labelClass}>Commentaire</label>
            <textarea
              rows={2}
              value={form.commentaire}
              onChange={handleChange('commentaire')}
              placeholder="Optionnel"
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
