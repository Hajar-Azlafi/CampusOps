import { useState, useEffect, useMemo } from 'react'
import { createOccupation, updateOccupation } from '../../api/occupationsApi'
import { fetchSpaces } from '../../api/spacesApi'
import { fetchPrograms } from '../../api/programsApi'
import { fetchPromotions } from '../../api/promotionsApi'
import { getGroupsByPromotion } from '../../api/groupsApi'
import { getSearchWindow } from '../../api/availabilityApi'
import {
  OCCUPATION_CATEGORIES,
  occupationTypesOf,
} from '../../constants/occupationTypes'
import {
  OPENING_TIME,
  CLOSING_TIME,
  MIN_SLOT_MINUTES,
  toMinutes,
  formatDuree,
} from '../../constants/reservationHours'
import { IconX } from '../icons'

// Création / modification d'UNE occupation supplémentaire générique : soutenance
// ou occupation « autre » (événement, réunion, activité de club, conférence...).
// Un seul formulaire sert les deux catégories : mêmes champs, mêmes règles ; seule
// l'exigence de rattachement change (une soutenance doit être rattachée à une
// filière, une occupation « autre » non).
//
// Les heures sont saisies librement au format HH:mm — aucun créneau officiel
// imposé — mais bornées par les horaires d'ouverture réellement annoncés par le
// serveur et par la durée minimale exploitable. La validation est visible EN
// DIRECT (champ en rouge, message nommant la borne franchie, bouton désactivé,
// recadrage au blur) : le serveur revalide ensuite sous verrou, notamment la
// disponibilité de la salle, qu'aucun contrôle client ne peut anticiper.

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const inputErrorClass =
  'w-full px-3 py-2.5 border border-red-400 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-red-400'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

const emptyForm = {
  type: '', intitule: '', spaceId: '', date: '', heureDebut: '', heureFin: '',
  programId: '', promotionId: '', groupId: '', responsable: '', commentaire: '',
}

const CONFIG = {
  [OCCUPATION_CATEGORIES.SOUTENANCE]: {
    createTitle: 'Programmer une soutenance',
    editTitle: 'Modifier la soutenance',
    intituleLabel: 'Sujet de la soutenance',
    intitulePlaceholder: 'Intitulé du sujet soutenu',
    responsableLabel: 'Responsable / président du jury',
    submitLabel: 'Programmer',
    hint: 'Une soutenance doit être rattachée à une filière. Les horaires sont libres, '
      + "dans les limites d'ouverture de l'établissement.",
  },
  [OCCUPATION_CATEGORIES.AUTRE]: {
    createTitle: 'Ajouter une occupation',
    editTitle: "Modifier l'occupation",
    intituleLabel: "Intitulé de l'activité",
    intitulePlaceholder: 'Ex. : réunion de coordination, conférence...',
    responsableLabel: "Responsable de l'activité",
    submitLabel: 'Enregistrer',
    hint: 'Le rattachement à une filière est facultatif. Sans filière, l’occupation '
      + 'relève de l’administrateur.',
  },
}

export default function OccupationFormModal({
  open, mode, categorie, initialData, onClose, onSuccess,
}) {
  const config = CONFIG[categorie] ?? CONFIG[OCCUPATION_CATEGORIES.AUTRE]
  const types = useMemo(() => occupationTypesOf(categorie), [categorie])
  const programRequired = categorie === OCCUPATION_CATEGORIES.SOUTENANCE

  const [form, setForm] = useState(emptyForm)
  const [spaces, setSpaces] = useState([])
  const [programs, setPrograms] = useState([])
  const [promotions, setPromotions] = useState([])
  const [groups, setGroups] = useState([])
  const [bounds, setBounds] = useState({
    opening: OPENING_TIME, closing: CLOSING_TIME, minSlotMinutes: MIN_SLOT_MINUTES,
  })
  const [loadingRefs, setLoadingRefs] = useState(false)
  const [loadingGroups, setLoadingGroups] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Référentiels + bornes horaires réelles, chargés à l'ouverture. Les bornes
  // viennent du serveur (dérivées des créneaux officiels) : le client ne
  // recalcule aucune règle, il ne fait que borner sa saisie.
  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoadingRefs(true)
    Promise.all([
      fetchSpaces({ actif: true }),
      fetchPrograms({ actif: true }),
      fetchPromotions({ actif: true }),
      getSearchWindow().catch(() => null),
    ])
      .then(([sps, progs, promos, window]) => {
        if (cancelled) return
        setSpaces(sps || [])
        setPrograms(progs || [])
        setPromotions(promos || [])
        if (window) {
          setBounds({
            opening: window.heureOuverture || OPENING_TIME,
            closing: window.heureFermeture || CLOSING_TIME,
            minSlotMinutes: Number.isFinite(Number(window.dureeMinimaleMinutes))
              ? Number(window.dureeMinimaleMinutes)
              : MIN_SLOT_MINUTES,
          })
        }
      })
      .catch(() => {
        if (cancelled) return
        setSpaces([]); setPrograms([]); setPromotions([])
      })
      .finally(() => { if (!cancelled) setLoadingRefs(false) })
    return () => { cancelled = true }
  }, [open])

  // Groupes de la promotion choisie (audience facultative, la plus fine).
  useEffect(() => {
    if (!open || !form.promotionId) {
      setGroups([])
      return
    }
    let cancelled = false
    setLoadingGroups(true)
    getGroupsByPromotion(form.promotionId)
      .then((grps) => { if (!cancelled) setGroups(grps || []) })
      .catch(() => { if (!cancelled) setGroups([]) })
      .finally(() => { if (!cancelled) setLoadingGroups(false) })
    return () => { cancelled = true }
  }, [open, form.promotionId])

  // Hydratation : en modification on repart de l'occupation existante, en
  // création d'un formulaire vierge dont seul le type est présélectionné quand
  // la catégorie n'en propose qu'un (cas des soutenances).
  useEffect(() => {
    if (!open) return
    if (mode === 'edit' && initialData) {
      setForm({
        type: initialData.type ?? '',
        intitule: initialData.intitule ?? '',
        spaceId: initialData.spaceId ?? '',
        date: initialData.date ?? '',
        heureDebut: (initialData.heureDebut ?? '').slice(0, 5),
        heureFin: (initialData.heureFin ?? '').slice(0, 5),
        programId: initialData.programId ?? '',
        promotionId: initialData.promotionId ?? '',
        groupId: initialData.groupId ?? '',
        responsable: initialData.responsable ?? '',
        commentaire: initialData.commentaire ?? '',
      })
    } else {
      setForm({ ...emptyForm, type: types.length === 1 ? types[0].value : '' })
    }
    setError('')
  }, [open, mode, initialData, types])

  if (!open) return null

  const handleChange = (field) => (e) => {
    const value = e.target.value
    setForm((f) => {
      const next = { ...f, [field]: value }
      // Changer de filière invalide la promotion (donc le groupe) ; changer de
      // promotion invalide le groupe qui lui était rattaché.
      if (field === 'programId') { next.promotionId = ''; next.groupId = '' }
      if (field === 'promotionId') { next.groupId = '' }
      return next
    })
    setError('')
  }

  // Promotions proposées : celles de la filière choisie. Sans filière (cas
  // « autre » relevant de l'administrateur), toute promotion reste proposable.
  const scopedPromotions = form.programId
    ? promotions.filter((p) => String(p.programId) === String(form.programId))
    : promotions

  const opening = bounds.opening
  const closing = bounds.closing
  const minSlot = bounds.minSlotMinutes
  const debutMinutes = toMinutes(form.heureDebut)
  const finMinutes = toMinutes(form.heureFin)
  const dureeChoisie =
    debutMinutes != null && finMinutes != null && finMinutes > debutMinutes
      ? finMinutes - debutMinutes
      : 0

  // Validation EN DIRECT de la plage horaire : recalculée à chaque rendu, donc
  // visible pendant la saisie et pas seulement à l'envoi. Les bornes annoncées
  // par le serveur sont testées d'abord, puis la cohérence début/fin, puis la
  // durée minimale exploitable.
  let saisieError = ''
  if (form.heureDebut && form.heureDebut < opening) {
    saisieError = `L'établissement ouvre à ${opening} : l'heure de début ne peut pas être antérieure.`
  } else if (form.heureFin && form.heureFin > closing) {
    saisieError = `L'établissement ferme à ${closing} : l'heure de fin ne peut pas être postérieure.`
  } else if (form.heureDebut && form.heureDebut >= closing) {
    saisieError = `L'heure de début doit rester avant la fermeture (${closing}).`
  } else if (form.heureDebut && form.heureFin && finMinutes <= debutMinutes) {
    saisieError = "L'heure de fin doit être postérieure à l'heure de début."
  } else if (form.heureDebut && form.heureFin && minSlot > 0 && dureeChoisie < minSlot) {
    saisieError = `Une occupation doit durer au moins ${formatDuree(minSlot)} : un créneau plus court n'est pas exploitable.`
  }

  // Champ fautif : le contour rouge doit désigner l'heure à corriger.
  const debutHorsBornes =
    Boolean(form.heureDebut) && (form.heureDebut < opening || form.heureDebut >= closing)
  const finHorsBornes =
    Boolean(form.heureFin) &&
    (form.heureFin > closing || (Boolean(form.heureDebut) && form.heureFin <= form.heureDebut))

  // Bornes natives = intersection des horaires d'ouverture et de l'autre heure
  // saisie. Le tri lexicographique de "HH:mm" zéro-paddé est chronologique.
  const maxDebutInput = [form.heureFin, closing].filter(Boolean).sort()[0] || undefined
  const minFinInput = [form.heureDebut, opening].filter(Boolean).sort().pop() || undefined

  // Une heure hors bornes ne peut pas être CONSERVÉE : en quittant le champ,
  // elle est ramenée à la borne franchie. Le recadrage se fait au blur et jamais
  // à la frappe : `type="time"` émet un onChange dès que l'heure est complète
  // (taper « 1 » donne 01:xx), recadrer immédiatement empêcherait de saisir
  // « 12:30 » dans une plage commençant à 10:25.
  const clampTime = (field) => () => {
    const value = form[field]
    if (!value) return
    let next = value
    if (next < opening) next = opening
    if (next > closing) next = closing
    if (next !== value) {
      setForm((f) => ({ ...f, [field]: next }))
      setError('')
    }
  }

  const intituleTropLong = form.intitule.trim().length > 180
  const responsableTropLong = form.responsable.trim().length > 150
  const programManquant = programRequired && !form.programId
  const champsManquants =
    !form.type || !form.spaceId || !form.date || !form.heureDebut || !form.heureFin
  const submitDisabled =
    isSubmitting ||
    loadingRefs ||
    champsManquants ||
    programManquant ||
    intituleTropLong ||
    responsableTropLong ||
    Boolean(saisieError)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (submitDisabled) return

    setIsSubmitting(true)
    const payload = {
      type: form.type,
      intitule: form.intitule.trim() || null,
      spaceId: Number(form.spaceId),
      date: form.date,
      heureDebut: form.heureDebut,
      heureFin: form.heureFin,
      programId: form.programId ? Number(form.programId) : null,
      promotionId: form.promotionId ? Number(form.promotionId) : null,
      groupId: form.groupId ? Number(form.groupId) : null,
      responsable: form.responsable.trim() || null,
      commentaire: form.commentaire.trim() || null,
    }

    try {
      const saved =
        mode === 'create'
          ? await createOccupation(payload)
          : await updateOccupation(initialData.id, payload)
      onSuccess(saved)
    } catch (err) {
      // 409 = conflit d'occupation (salle déjà prise par un emploi du temps, une
      // réservation ou une autre occupation) : seul le serveur peut le trancher.
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'La salle est déjà occupée sur ce créneau.')
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
            {mode === 'create' ? config.createTitle : config.editTitle}
          </h3>
          <button type="button" onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <p className="text-xs text-ink/50 mb-5">{config.hint}</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Type d'occupation</label>
              <select
                required
                value={form.type}
                onChange={handleChange('type')}
                className={inputClass}
                disabled={types.length === 1}
              >
                <option value="">Sélectionner...</option>
                {types.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Espace</label>
              <select
                required
                value={form.spaceId}
                onChange={handleChange('spaceId')}
                className={inputClass}
              >
                <option value="">{loadingRefs ? 'Chargement...' : 'Sélectionner...'}</option>
                {spaces.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nom}
                    {s.buildingCode ? ` — ${s.buildingCode}` : ''}
                    {s.capacite ? ` (${s.capacite} pl.)` : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className={labelClass}>{config.intituleLabel}</label>
            <input
              type="text"
              maxLength={180}
              value={form.intitule}
              onChange={handleChange('intitule')}
              placeholder={config.intitulePlaceholder}
              className={intituleTropLong ? inputErrorClass : inputClass}
            />
            {intituleTropLong && (
              <p className="text-xs text-red-600 mt-1.5">
                L'intitulé ne peut dépasser 180 caractères.
              </p>
            )}
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className={labelClass}>Date</label>
              <input
                type="date"
                required
                value={form.date}
                onChange={handleChange('date')}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Heure de début</label>
              <input
                type="time"
                required
                value={form.heureDebut}
                onChange={handleChange('heureDebut')}
                onBlur={clampTime('heureDebut')}
                min={opening}
                max={maxDebutInput}
                step={300}
                className={debutHorsBornes ? inputErrorClass : inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Heure de fin</label>
              <input
                type="time"
                required
                value={form.heureFin}
                onChange={handleChange('heureFin')}
                onBlur={clampTime('heureFin')}
                min={minFinInput}
                max={closing}
                step={300}
                className={finHorsBornes ? inputErrorClass : inputClass}
              />
            </div>
          </div>

          {/* La durée n'est affichée que si la plage est valide : jamais de total
              rassurant sur une saisie fautive, l'anomalie prend sa place. */}
          {saisieError ? (
            <p className="text-xs text-red-600" role="alert">{saisieError}</p>
          ) : (
            <p className="text-xs text-ink/50">
              Horaires libres entre {opening} et {closing}, durée minimale {formatDuree(minSlot)}.
              {dureeChoisie > 0 && ` Durée : ${formatDuree(dureeChoisie)}.`}
            </p>
          )}

          <div>
            <label className={labelClass}>
              Filière {programRequired ? '' : '(facultative)'}
            </label>
            <select
              required={programRequired}
              value={form.programId}
              onChange={handleChange('programId')}
              className={programManquant ? inputErrorClass : inputClass}
            >
              <option value="">
                {loadingRefs ? 'Chargement...' : programRequired ? 'Sélectionner...' : 'Aucune filière'}
              </option>
              {programs.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nom}{p.levelNom ? ` — ${p.levelNom}` : ''}
                </option>
              ))}
            </select>
            {programManquant && (
              <p className="text-xs text-red-600 mt-1.5">
                Une soutenance doit être rattachée à une filière.
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Promotion (facultative)</label>
              <select value={form.promotionId} onChange={handleChange('promotionId')} className={inputClass}>
                <option value="">Aucune promotion</option>
                {scopedPromotions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nom}{p.programNom ? ` — ${p.programNom}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Groupe (facultatif)</label>
              <select
                value={form.groupId}
                onChange={handleChange('groupId')}
                disabled={!form.promotionId}
                className={inputClass}
              >
                <option value="">
                  {!form.promotionId
                    ? 'Choisir une promotion...'
                    : loadingGroups
                      ? 'Chargement...'
                      : 'Toute la promotion'}
                </option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.nom}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className={labelClass}>{config.responsableLabel} (facultatif)</label>
            <input
              type="text"
              maxLength={150}
              value={form.responsable}
              onChange={handleChange('responsable')}
              placeholder="Nom de la personne ou de l'entité responsable"
              className={responsableTropLong ? inputErrorClass : inputClass}
            />
            {responsableTropLong && (
              <p className="text-xs text-red-600 mt-1.5">
                Le responsable ne peut dépasser 150 caractères.
              </p>
            )}
          </div>

          <div>
            <label className={labelClass}>Description / motif (facultatif)</label>
            <textarea
              rows={3}
              value={form.commentaire}
              onChange={handleChange('commentaire')}
              placeholder="Objet de l'occupation, précisions utiles..."
              className={inputClass}
            />
          </div>

          {error && (
            <p className="text-sm text-red-600" role="alert">{error}</p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 text-sm font-medium text-ink/70 hover:text-ink"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={submitDisabled}
              className="px-5 py-2.5 bg-signal text-white text-sm font-semibold rounded-lg hover:bg-signal/90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Enregistrement...' : mode === 'create' ? config.submitLabel : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
