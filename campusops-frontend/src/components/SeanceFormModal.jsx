import { useState, useEffect } from 'react'
import { createSchedule, updateSchedule } from '../api/schedulesApi'
import { fetchModules } from '../api/modulesApi'
import { fetchSeanceTypes } from '../api/seanceTypesApi'
import { WEEK_DAYS } from '../constants/weekDays'
import { PRESENCE_TYPES } from '../constants/presenceTypes'
import { IconX } from './icons'

// Ajout / modification d'UNE séance rattachée à un emploi du temps existant.
// Le contexte académique (filière, niveau, promotion, groupe, semestre, année) est
// FIXÉ par l'en-tête d'emploi du temps : on ne saisit que les données propres à la
// séance. S'appuie sur l'API /schedules existante (§29) en renseignant
// emploiDuTempsId, ce qui déclenche les mêmes contrôles de conflit (§33) et la
// règle présentiel ⇒ salle (§6) côté backend.
//
// §7-§8 : le type de séance et le module ne sont plus en saisie libre. Le type
// est choisi dans le référentiel configurable (/seance-types) et le module dans
// les modules du contexte (filière + semestre) de l'emploi du temps.

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

const emptyForm = {
  jour: '', timeSlotId: '', typeSeanceId: '', typePresence: 'PRESENTIEL',
  spaceId: '', enseignant: '', moduleId: '', commentaire: '',
}

export default function SeanceFormModal({
  open,
  mode,
  initialData,
  timetable,
  timeSlots = [],
  spaces = [],
  onClose,
  onSuccess,
}) {
  const [form, setForm] = useState(emptyForm)
  const [modules, setModules] = useState([])
  const [seanceTypes, setSeanceTypes] = useState([])
  const [loadingRefs, setLoadingRefs] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Référentiels dépendant du contexte : modules (filière + semestre de l'EDT)
  // et types de séance configurés actifs.
  useEffect(() => {
    if (!open || !timetable) return
    let cancelled = false
    setLoadingRefs(true)
    Promise.all([
      fetchModules({ programId: timetable.programId, semesterId: timetable.semesterId, actif: true }),
      fetchSeanceTypes({ actif: true }),
    ])
      .then(([mods, types]) => {
        if (cancelled) return
        setModules(mods || [])
        setSeanceTypes(types || [])
      })
      .catch(() => {
        if (cancelled) return
        setModules([])
        setSeanceTypes([])
      })
      .finally(() => { if (!cancelled) setLoadingRefs(false) })
    return () => { cancelled = true }
  }, [open, timetable])

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        jour: initialData.jour ?? '',
        timeSlotId: initialData.timeSlotId ?? '',
        typeSeanceId: initialData.typeSeanceId != null ? String(initialData.typeSeanceId) : '',
        typePresence: initialData.typePresence ?? 'PRESENTIEL',
        spaceId: initialData.spaceId ?? '',
        enseignant: initialData.enseignant ?? '',
        moduleId: initialData.moduleId != null ? String(initialData.moduleId) : '',
        commentaire: initialData.commentaire ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open || !timetable) return null

  const isPresentiel = form.typePresence === 'PRESENTIEL'
  const noModules = !loadingRefs && modules.length === 0
  // Séance legacy en cours d'édition dont la matière n'est pas (encore) rattachée
  // à un module du référentiel.
  const legacyMatiere = mode === 'edit' && !form.moduleId && initialData?.matiere

  const handleChange = (field) => (e) => {
    const value = e.target.value
    setForm((f) => {
      const next = { ...f, [field]: value }
      // Passage en distanciel : la salle n'est plus requise, on la vide.
      if (field === 'typePresence' && value === 'DISTANCIEL') next.spaceId = ''
      return next
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (isPresentiel && !form.spaceId) {
      setError('Une salle est obligatoire pour une séance en présentiel (§6).')
      return
    }
    if (!form.moduleId) {
      setError('Le module est obligatoire : choisissez-le dans la liste du contexte (§8).')
      return
    }
    if (!form.typeSeanceId) {
      setError('Le type de séance est obligatoire (§7).')
      return
    }

    setIsSubmitting(true)
    const payload = {
      jour: form.jour,
      timeSlotId: Number(form.timeSlotId),
      spaceId: form.spaceId ? Number(form.spaceId) : null,
      programId: timetable.programId,
      levelId: timetable.levelId,
      promotionId: timetable.promotionId,
      groupId: timetable.groupId,
      semesterId: timetable.semesterId,
      academicYearId: timetable.academicYearId,
      moduleId: form.moduleId ? Number(form.moduleId) : null,
      typeSeanceId: form.typeSeanceId ? Number(form.typeSeanceId) : null,
      typePresence: form.typePresence,
      enseignant: form.enseignant.trim(),
      commentaire: form.commentaire.trim() || null,
      emploiDuTempsId: timetable.id,
    }

    try {
      if (mode === 'create') {
        const created = await createSchedule(payload)
        onSuccess(created)
      } else {
        const updated = await updateSchedule(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Un conflit a été détecté pour cette séance.')
      } else if (err.response?.status === 400) {
        setError(err.response.data?.message || 'Requête invalide.')
      } else if (err.response?.data?.validationErrors?.length) {
        setError(err.response.data.validationErrors.join(' — '))
      } else {
        setError('Une erreur est survenue, veuillez réessayer.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-xl p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-1">
          <h3 className="font-display text-lg font-semibold text-ink">
            {mode === 'create' ? 'Ajouter une séance' : 'Modifier la séance'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        {/* Contexte fixe (non modifiable) */}
        <p className="text-xs text-ink/50 mb-5">
          {timetable.programNom} · {timetable.levelNom} · {timetable.groupNom} · {timetable.semesterNom}
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Jour</label>
              <select required value={form.jour} onChange={handleChange('jour')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {WEEK_DAYS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
              </select>
            </div>
            <div>
              <label className={labelClass}>Créneau</label>
              <select required value={form.timeSlotId} onChange={handleChange('timeSlotId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {timeSlots.map((t) => <option key={t.id} value={t.id}>{t.heureDebut} — {t.heureFin}</option>)}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Type de séance</label>
              <select required value={form.typeSeanceId} onChange={handleChange('typeSeanceId')} className={inputClass}>
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {seanceTypes.map((t) => <option key={t.id} value={t.id}>{t.nom}</option>)}
              </select>
            </div>
            <div>
              <label className={labelClass}>Type de présence</label>
              <select required value={form.typePresence} onChange={handleChange('typePresence')} className={inputClass}>
                {PRESENCE_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
          </div>

          <div>
            <label className={labelClass}>
              Salle {isPresentiel ? <span className="text-red-500">*</span> : <span className="text-ink/40">(facultatif en distanciel)</span>}
            </label>
            <select
              value={form.spaceId}
              onChange={handleChange('spaceId')}
              disabled={!isPresentiel}
              required={isPresentiel}
              className={inputClass}
            >
              <option value="">{isPresentiel ? 'Sélectionner une salle...' : 'Séance à distance'}</option>
              {spaces.map((s) => <option key={s.id} value={s.id}>{s.code} — {s.nom}</option>)}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Module</label>
              <select
                required
                value={form.moduleId}
                onChange={handleChange('moduleId')}
                disabled={noModules}
                className={inputClass}
              >
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {modules.map((m) => (
                  <option key={m.id} value={m.id}>{m.nom}{m.code ? ` (${m.code})` : ''}</option>
                ))}
              </select>
              {noModules && (
                <p className="text-xs text-amber-700 mt-1.5">
                  Aucun module pour ce contexte. Ajoutez-en un dans « Mes modules » avant de créer la séance.
                </p>
              )}
              {legacyMatiere && (
                <p className="text-xs text-ink/50 mt-1.5">
                  Ancienne matière : « {initialData.matiere} » — sélectionnez le module correspondant.
                </p>
              )}
            </div>
            <div>
              <label className={labelClass}>Enseignant</label>
              <input required value={form.enseignant} onChange={handleChange('enseignant')} className={inputClass} />
            </div>
          </div>

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
              disabled={isSubmitting || noModules}
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
            >
              {isSubmitting ? '...' : mode === 'create' ? 'Ajouter' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
