import { useState, useEffect, useMemo } from 'react'
import { createSchedule, updateSchedule } from '../api/schedulesApi'
import { fetchModules } from '../api/modulesApi'
import { fetchSeanceTypes } from '../api/seanceTypesApi'
import { WEEK_DAYS } from '../constants/weekDays'
import { IconX } from './icons'

// §7-§8 : le type de séance provient du référentiel configurable
// (/seance-types) et le module des modules du contexte choisi (filière +
// semestre), au lieu d'un type figé et d'une matière en texte libre.

const emptyForm = {
  jour: '',
  timeSlotId: '',
  spaceId: '',
  programId: '',
  levelId: '',
  promotionId: '',
  groupId: '',
  semesterId: '',
  academicYearId: '',
  typeSeanceId: '',
  enseignant: '',
  moduleId: '',
  commentaire: '',
}

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

export default function ScheduleFormModal({
  open,
  mode,
  initialData,
  programs = [],
  levels = [],
  promotions = [],
  groups = [],
  timeSlots = [],
  spaces = [],
  semesters = [],
  academicYears = [],
  onClose,
  onSuccess,
}) {
  const [form, setForm] = useState(emptyForm)
  const [modules, setModules] = useState([])
  const [seanceTypes, setSeanceTypes] = useState([])
  const [loadingModules, setLoadingModules] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        jour: initialData.jour ?? '',
        timeSlotId: initialData.timeSlotId ?? '',
        spaceId: initialData.spaceId ?? '',
        programId: initialData.programId ?? '',
        levelId: initialData.levelId ?? '',
        promotionId: initialData.promotionId ?? '',
        groupId: initialData.groupId ?? '',
        semesterId: initialData.semesterId ?? '',
        academicYearId: initialData.academicYearId ?? '',
        typeSeanceId: initialData.typeSeanceId != null ? String(initialData.typeSeanceId) : '',
        enseignant: initialData.enseignant ?? '',
        moduleId: initialData.moduleId != null ? String(initialData.moduleId) : '',
        commentaire: initialData.commentaire ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  // Types de séance configurés (chargés à l'ouverture).
  useEffect(() => {
    if (!open) return
    let cancelled = false
    fetchSeanceTypes({ actif: true })
      .then((data) => { if (!cancelled) setSeanceTypes(data || []) })
      .catch(() => { if (!cancelled) setSeanceTypes([]) })
    return () => { cancelled = true }
  }, [open])

  // Modules du contexte (filière + semestre). Rechargés dès que l'un des deux
  // change ; vidés si le contexte est incomplet.
  useEffect(() => {
    if (!open) return
    if (!form.programId || !form.semesterId) {
      setModules([])
      return
    }
    let cancelled = false
    setLoadingModules(true)
    fetchModules({ programId: form.programId, semesterId: form.semesterId, actif: true })
      .then((data) => { if (!cancelled) setModules(data || []) })
      .catch(() => { if (!cancelled) setModules([]) })
      .finally(() => { if (!cancelled) setLoadingModules(false) })
    return () => { cancelled = true }
  }, [open, form.programId, form.semesterId])

  // Cascade : promotions filtrées par filière + niveau
  const filteredPromotions = useMemo(() => {
    if (!form.programId || !form.levelId) return []
    return promotions.filter(
      (p) =>
        String(p.programId) === String(form.programId) &&
        String(p.levelId) === String(form.levelId),
    )
  }, [promotions, form.programId, form.levelId])

  // Cascade : groupes filtrés par promotion
  const filteredGroups = useMemo(() => {
    if (!form.promotionId) return []
    return groups.filter((g) => String(g.promotionId) === String(form.promotionId))
  }, [groups, form.promotionId])

  if (!open) return null

  const noModules = !loadingModules && form.programId && form.semesterId && modules.length === 0
  const contextIncomplete = !form.programId || !form.semesterId
  const legacyMatiere = mode === 'edit' && !form.moduleId && initialData?.matiere

  const handleChange = (field) => (e) => {
    const value = e.target.value
    setForm((f) => {
      const next = { ...f, [field]: value }
      // Réinitialiser les niveaux inférieurs de la cascade
      if (field === 'programId' || field === 'levelId') {
        next.promotionId = ''
        next.groupId = ''
      } else if (field === 'promotionId') {
        next.groupId = ''
      }
      // Changer de contexte (filière ou semestre) invalide le module choisi.
      if (field === 'programId' || field === 'semesterId') {
        next.moduleId = ''
      }
      return next
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

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
      spaceId: Number(form.spaceId),
      programId: Number(form.programId),
      levelId: Number(form.levelId),
      promotionId: Number(form.promotionId),
      groupId: Number(form.groupId),
      semesterId: Number(form.semesterId),
      academicYearId: Number(form.academicYearId),
      moduleId: form.moduleId ? Number(form.moduleId) : null,
      typeSeanceId: form.typeSeanceId ? Number(form.typeSeanceId) : null,
      enseignant: form.enseignant.trim(),
      commentaire: form.commentaire.trim() || null,
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
        setError(err.response.data?.message || 'Un conflit a été détecté pour cette séance')
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
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">
            {mode === 'create' ? 'Nouvelle séance' : 'Modifier la séance'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Contexte académique */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Année universitaire</label>
              <select required value={form.academicYearId} onChange={handleChange('academicYearId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {academicYears.map((a) => (
                  <option key={a.id} value={a.id}>{a.libelle}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Semestre</label>
              <select required value={form.semesterId} onChange={handleChange('semesterId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {semesters.map((s) => (
                  <option key={s.id} value={s.id}>{s.nom}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Cascade filière → niveau → promotion → groupe */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Filière</label>
              <select required value={form.programId} onChange={handleChange('programId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {programs.map((p) => (
                  <option key={p.id} value={p.id}>{p.nom}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Niveau</label>
              <select required value={form.levelId} onChange={handleChange('levelId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {levels.map((l) => (
                  <option key={l.id} value={l.id}>{l.nom}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Promotion</label>
              <select
                required
                value={form.promotionId}
                onChange={handleChange('promotionId')}
                disabled={!form.programId || !form.levelId}
                className={inputClass}
              >
                <option value="">
                  {!form.programId || !form.levelId ? 'Choisir filière + niveau...' : 'Sélectionner...'}
                </option>
                {filteredPromotions.map((p) => (
                  <option key={p.id} value={p.id}>{p.nom}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Groupe</label>
              <select
                required
                value={form.groupId}
                onChange={handleChange('groupId')}
                disabled={!form.promotionId}
                className={inputClass}
              >
                <option value="">
                  {!form.promotionId ? 'Choisir une promotion...' : 'Sélectionner...'}
                </option>
                {filteredGroups.map((g) => (
                  <option key={g.id} value={g.id}>{g.nom}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Planning */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className={labelClass}>Jour</label>
              <select required value={form.jour} onChange={handleChange('jour')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {WEEK_DAYS.map((d) => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Créneau</label>
              <select required value={form.timeSlotId} onChange={handleChange('timeSlotId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {timeSlots.map((t) => (
                  <option key={t.id} value={t.id}>{t.heureDebut} — {t.heureFin}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Type de séance</label>
              <select required value={form.typeSeanceId} onChange={handleChange('typeSeanceId')} className={inputClass}>
                <option value="">Sélectionner...</option>
                {seanceTypes.map((t) => (
                  <option key={t.id} value={t.id}>{t.nom}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className={labelClass}>Espace</label>
            <select required value={form.spaceId} onChange={handleChange('spaceId')} className={inputClass}>
              <option value="">Sélectionner un espace...</option>
              {spaces.map((s) => (
                <option key={s.id} value={s.id}>{s.code} — {s.nom}</option>
              ))}
            </select>
          </div>

          {/* Contenu pédagogique */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Enseignant</label>
              <input required value={form.enseignant} onChange={handleChange('enseignant')} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Module</label>
              <select
                required
                value={form.moduleId}
                onChange={handleChange('moduleId')}
                disabled={contextIncomplete || noModules}
                className={inputClass}
              >
                <option value="">
                  {contextIncomplete
                    ? 'Choisir filière + semestre...'
                    : loadingModules
                      ? 'Chargement...'
                      : 'Sélectionner...'}
                </option>
                {modules.map((m) => (
                  <option key={m.id} value={m.id}>{m.nom}{m.code ? ` (${m.code})` : ''}</option>
                ))}
              </select>
              {noModules && (
                <p className="text-xs text-amber-700 mt-1.5">
                  Aucun module pour ce contexte (filière + semestre).
                </p>
              )}
              {legacyMatiere && (
                <p className="text-xs text-ink/50 mt-1.5">
                  Ancienne matière : « {initialData.matiere} ».
                </p>
              )}
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
