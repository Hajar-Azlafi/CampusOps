import { useState, useEffect } from 'react'
import { createExamen, updateExamen } from '../api/examensApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchModules } from '../api/modulesApi'
import { fetchCurrentSemesters } from '../api/semestersApi'
import { getGroupsByPromotion } from '../api/groupsApi'
import { fetchSessions } from '../api/academicSessionsApi'
import { fetchTimeSlots } from '../api/timeSlotsApi'
import { fetchSpaces } from '../api/spacesApi'
import { IconX } from './icons'

// Ajout / modification d'UN examen daté (Lot 2 F2). Contrairement à la séance
// (liée à un en-tête d'emploi du temps), l'examen est un évènement autonome : on
// saisit ici tout son contexte. La filière, le semestre et l'année sont DÉRIVÉS
// côté backend (la matière porte filière + semestre ; la promotion porte filière
// + année) puis contrôlés pour cohérence — on ne les saisit donc pas.
//
// Audience : le groupe est FACULTATIF ; laissé vide, l'examen concerne toute la
// promotion. Le module est filtré sur la filière de la promotion choisie ET sur
// les semestres COURANTS (un examen ne se programme que dans un semestre en
// cours) pour éviter un rejet de cohérence côté serveur.

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

const emptyForm = {
  promotionId: '', moduleId: '', groupId: '', sessionId: '',
  date: '', timeSlotId: '', spaceId: '', commentaire: '',
}

export default function ExamenFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [promotions, setPromotions] = useState([])
  const [sessions, setSessions] = useState([])
  const [timeSlots, setTimeSlots] = useState([])
  const [spaces, setSpaces] = useState([])
  const [modules, setModules] = useState([])
  const [currentSemesterIds, setCurrentSemesterIds] = useState(() => new Set())
  const [groups, setGroups] = useState([])
  const [loadingRefs, setLoadingRefs] = useState(false)
  const [loadingDeps, setLoadingDeps] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Référentiels stables (indépendants de la promotion) chargés à l'ouverture.
  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoadingRefs(true)
    Promise.all([
      fetchPromotions({ actif: true }),
      fetchSessions({ actif: true }),
      fetchTimeSlots({ actif: true }),
      fetchSpaces({ actif: true }),
      fetchCurrentSemesters(),
    ])
      .then(([promos, sess, slots, sps, currents]) => {
        if (cancelled) return
        setPromotions(promos || [])
        setSessions(sess || [])
        setTimeSlots(slots || [])
        setSpaces(sps || [])
        setCurrentSemesterIds(new Set((currents || []).map((s) => s.id)))
      })
      .catch(() => {
        if (cancelled) return
        setPromotions([]); setSessions([]); setTimeSlots([]); setSpaces([])
        setCurrentSemesterIds(new Set())
      })
      .finally(() => { if (!cancelled) setLoadingRefs(false) })
    return () => { cancelled = true }
  }, [open])

  // Modules (bornés à la filière de la promotion) + groupes de la promotion.
  // Dépend aussi de `promotions` : en édition, la promotion est fixée avant que
  // la liste n'arrive, il faut donc relancer une fois la filière connue.
  useEffect(() => {
    if (!open || !form.promotionId) {
      setModules([]); setGroups([])
      return
    }
    const promo = promotions.find((p) => String(p.id) === String(form.promotionId))
    if (!promo) return
    let cancelled = false
    setLoadingDeps(true)
    Promise.all([
      fetchModules({ programId: promo.programId, actif: true }),
      getGroupsByPromotion(promo.id),
    ])
      .then(([mods, grps]) => {
        if (cancelled) return
        setModules(mods || [])
        setGroups(grps || [])
      })
      .catch(() => {
        if (cancelled) return
        setModules([]); setGroups([])
      })
      .finally(() => { if (!cancelled) setLoadingDeps(false) })
    return () => { cancelled = true }
  }, [open, form.promotionId, promotions])

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        promotionId: initialData.promotionId != null ? String(initialData.promotionId) : '',
        moduleId: initialData.moduleId != null ? String(initialData.moduleId) : '',
        groupId: initialData.groupId != null ? String(initialData.groupId) : '',
        sessionId: initialData.sessionId != null ? String(initialData.sessionId) : '',
        date: initialData.date ?? '',
        timeSlotId: initialData.timeSlotId != null ? String(initialData.timeSlotId) : '',
        spaceId: initialData.spaceId != null ? String(initialData.spaceId) : '',
        commentaire: initialData.commentaire ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  // Un examen ne se programme que dans un semestre COURANT : on ne propose donc
  // que les matières rattachées à un semestre courant. En édition, on garde
  // toujours la matière déjà sélectionnée visible même si son semestre a cessé
  // d'être courant, pour ne pas vider le champ. Si aucun courant n'est connu
  // (référentiel non chargé), on n'applique pas le filtre (repli non bloquant ;
  // le backend valide de toute façon la période).
  const visibleModules =
    currentSemesterIds.size > 0
      ? modules.filter(
          (m) => currentSemesterIds.has(m.semesterId) || String(m.id) === String(form.moduleId),
        )
      : modules

  const noModules = !!form.promotionId && !loadingDeps && visibleModules.length === 0
  const modulesFilteredOut =
    !!form.promotionId && !loadingDeps && modules.length > 0 && visibleModules.length === 0

  const handleChange = (field) => (e) => {
    const value = e.target.value
    setForm((f) => {
      const next = { ...f, [field]: value }
      // Changer de promotion invalide le module (filière différente) et le
      // groupe (rattaché à l'ancienne promotion) : on les réinitialise.
      if (field === 'promotionId') { next.moduleId = ''; next.groupId = '' }
      return next
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!form.promotionId) { setError('La promotion est obligatoire.'); return }
    if (!form.moduleId) { setError('La matière est obligatoire : choisissez-la dans la liste de la filière.'); return }
    if (!form.sessionId) { setError('La session universitaire est obligatoire.'); return }
    if (!form.date) { setError("La date de l'examen est obligatoire."); return }
    if (!form.timeSlotId) { setError('Le créneau horaire est obligatoire.'); return }
    if (!form.spaceId) { setError('La salle est obligatoire.'); return }

    setIsSubmitting(true)
    const payload = {
      date: form.date,
      timeSlotId: Number(form.timeSlotId),
      spaceId: Number(form.spaceId),
      moduleId: Number(form.moduleId),
      promotionId: Number(form.promotionId),
      groupId: form.groupId ? Number(form.groupId) : null,
      sessionId: Number(form.sessionId),
      commentaire: form.commentaire.trim() || null,
    }

    try {
      if (mode === 'create') {
        const created = await createExamen(payload)
        onSuccess(created)
      } else {
        const updated = await updateExamen(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Un conflit a été détecté pour cet examen.')
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
            {mode === 'create' ? 'Programmer un examen' : "Modifier l'examen"}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <p className="text-xs text-ink/50 mb-5">
          La filière, le semestre et l'année sont déduits de la promotion et de la matière. Seules les matières
          d'un semestre courant sont proposées, et la date doit rester dans la période du semestre.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={labelClass}>Promotion</label>
            <select required value={form.promotionId} onChange={handleChange('promotionId')} className={inputClass}>
              <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
              {promotions.map((p) => (
                <option key={p.id} value={p.id}>{p.nom}{p.programNom ? ` — ${p.programNom}` : ''}</option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Matière</label>
              <select
                required
                value={form.moduleId}
                onChange={handleChange('moduleId')}
                disabled={!form.promotionId || noModules}
                className={inputClass}
              >
                <option value="">
                  {!form.promotionId ? 'Choisir une promotion...' : loadingDeps ? 'Chargement...' : 'Sélectionner...'}
                </option>
                {visibleModules.map((m) => (
                  <option key={m.id} value={m.id}>{m.nom}{m.code ? ` (${m.code})` : ''}</option>
                ))}
              </select>
              {noModules && (
                <p className="text-xs text-amber-700 mt-1.5">
                  {modulesFilteredOut
                    ? 'Aucune matière rattachée à un semestre courant pour cette promotion. Seuls les semestres courants sont proposés.'
                    : 'Aucune matière pour la filière de cette promotion. Ajoutez-en une dans « Modules » au préalable.'}
                </p>
              )}
            </div>
            <div>
              <label className={labelClass}>
                Groupe <span className="text-ink/40">(facultatif)</span>
              </label>
              <select
                value={form.groupId}
                onChange={handleChange('groupId')}
                disabled={!form.promotionId}
                className={inputClass}
              >
                <option value="">Toute la promotion</option>
                {groups.map((g) => <option key={g.id} value={g.id}>{g.nom}</option>)}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Session</label>
              <select required value={form.sessionId} onChange={handleChange('sessionId')} className={inputClass}>
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {sessions.map((s) => <option key={s.id} value={s.id}>{s.nom}</option>)}
              </select>
            </div>
            <div>
              <label className={labelClass}>Date</label>
              <input required type="date" value={form.date} onChange={handleChange('date')} className={inputClass} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Créneau</label>
              <select required value={form.timeSlotId} onChange={handleChange('timeSlotId')} className={inputClass}>
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {timeSlots.map((t) => <option key={t.id} value={t.id}>{t.heureDebut} — {t.heureFin}</option>)}
              </select>
            </div>
            <div>
              <label className={labelClass}>Salle</label>
              <select required value={form.spaceId} onChange={handleChange('spaceId')} className={inputClass}>
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {spaces.map((s) => <option key={s.id} value={s.id}>{s.code} — {s.nom}</option>)}
              </select>
            </div>
          </div>

          <div>
            <label className={labelClass}>Commentaire</label>
            <textarea
              rows={2}
              value={form.commentaire}
              onChange={handleChange('commentaire')}
              placeholder="Optionnel (consignes, surveillant, etc.)"
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
              {isSubmitting ? '...' : mode === 'create' ? 'Programmer' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
