import { useState, useEffect } from 'react'
import { createReservation, updateReservation } from '../api/reservationsApi'
import { RESERVATION_TYPES } from '../constants/reservationTypes'
import { formatDuree, toMinutes, validateReservationHours } from '../constants/reservationHours'
import { roleLabel } from '../constants/roles'
import { IconX, IconClock, IconBookmark } from './icons'

// Reference STABLE pour les listes optionnelles. Un `[]` ecrit directement en
// valeur par defaut serait recree a chaque rendu et relancerait sans fin les
// effets qui en dependent : ce partage evite la boucle de rendu.
const EMPTY_LIST = []

const emptyForm = {
  spaceId: '',
  type: '',
  date: '',
  heureDebut: '',
  heureFin: '',
  motif: '',
  commentaire: '',
  targetUserId: '',
  // Contexte pedagogique (rempli uniquement lorsque `pedagogical` est vrai,
  // c.-a-d. pour une demande d'un responsable pedagogique).
  programId: '',
  groupId: '',
  contenuPedagogique: '',
}

const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'
const readOnlyRow = 'flex justify-between gap-4 py-2 border-b border-ink/5 last:border-0'
const readOnlyKey = 'text-sm text-ink/50'
const readOnlyVal = 'text-sm font-medium text-ink text-right'

// Champ horaire de la fenetre bornee (§5). Un contour rouge signale
// immediatement une heure qui sort de la periode libre, sans attendre l'envoi.
const timeInputClass = (invalid) =>
  `px-2 py-1.5 border rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 ${
    invalid
      ? 'border-red-400 text-red-700 focus:ring-red-400 focus:border-red-400'
      : 'border-ink/15 focus:ring-signal focus:border-signal'
  }`

export default function ReservationFormModal({
  open,
  mode,
  initialData,
  spaces = [],
  // Fenetre reservable (§9) : bornes min/max issues de la disponibilite reelle
  // calculee par le backend. Quand elles sont fournies (creation depuis la
  // recherche), l'horaire est editable mais contraint a cette fenetre ; toute
  // valeur hors fenetre est refusee cote client avant meme l'appel reseau.
  minTime = '',
  maxTime = '',
  // §5 — Reserver depuis un espace libre. `periodes` liste les blocs libres
  // CONTINUS de l'espace : deux blocs separes par une occupation ne s'additionnent
  // jamais (§7), une reservation doit donc tenir dans UN SEUL bloc. `creneaux`
  // liste les creneaux officiels entierement contenus dans l'un de ces blocs :
  // l'utilisateur peut ainsi soit saisir deux heures, soit choisir un creneau en
  // un clic, sans jamais depasser les bornes de la periode retenue.
  periodes = EMPTY_LIST,
  creneaux = EMPTY_LIST,
  // Duree minimale exploitable annoncee par le serveur (§4). Laisser indefini
  // pour retomber sur la valeur de repli du client (MIN_SLOT_MINUTES).
  minSlotMinutes,
  // Bornes reelles d'ouverture de la journee (derivees des creneaux officiels
  // cote serveur) ; vides => valeurs de repli du client.
  openingTime = '',
  closingTime = '',
  // Réservation pour le compte d'un utilisateur (fonctionnalité administrateur).
  // Lorsque `isAdmin` est vrai et que `users` est fourni, l'administrateur peut
  // choisir le bénéficiaire de la réservation.
  isAdmin = false,
  currentUser = null,
  users = [],
  // Contexte pédagogique : lorsque `pedagogical` est vrai (demande d'un
  // responsable pédagogique), la filière concernée est obligatoire et le groupe
  // ainsi que le contenu pédagogique peuvent être précisés. Le backend impose
  // que la filière fasse partie du périmètre du responsable.
  pedagogical = false,
  programs = [],
  groups = [],
  onClose,
  onSuccess,
}) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  // Index de la periode libre retenue (-1 = aucune periode transmise : on
  // retombe alors sur la fenetre minTime/maxTime de la recherche).
  const [periodeIndex, setPeriodeIndex] = useState(-1)

  useEffect(() => {
    if (initialData) {
      setForm({
        spaceId: initialData.spaceId ?? '',
        type: initialData.type ?? '',
        date: initialData.date ?? '',
        heureDebut: initialData.heureDebut ?? '',
        heureFin: initialData.heureFin ?? '',
        motif: initialData.motif ?? '',
        commentaire: initialData.commentaire ?? '',
        targetUserId: initialData.targetUserId ?? '',
        programId: initialData.programId ?? '',
        groupId: initialData.groupId ?? '',
        contenuPedagogique: initialData.contenuPedagogique ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    // §5 — periode libre proposee par defaut : celle qui contient l'heure de
    // debut transmise par la recherche, sinon la premiere periode disponible.
    const ancre = initialData?.heureDebut || minTime || ''
    let index = periodes.findIndex((p) => ancre && p.heureDebut <= ancre && ancre < p.heureFin)
    if (index < 0 && periodes.length > 0) index = 0
    setPeriodeIndex(periodes.length > 0 ? index : -1)
    setError('')
  }, [open, mode, initialData, periodes, minTime])

  // Pour un responsable pedagogique ne gerant qu'une seule filiere, celle-ci
  // est preselectionnee automatiquement afin d'alleger la saisie.
  useEffect(() => {
    if (open && pedagogical && programs.length === 1) {
      setForm((f) => (f.programId ? f : { ...f, programId: programs[0].id }))
    }
  }, [open, pedagogical, programs])

  if (!open) return null

  // En creation, l'espace, la date et l'horaire proviennent de la recherche
  // de disponibilite et ne doivent pas etre ressaisis.
  const isCreateFromSearch = mode === 'create'
  const selectedSpace = spaces.find((s) => String(s.id) === String(form.spaceId))
  // Groupes proposes : uniquement ceux de la filiere selectionnee (le backend
  // ne renvoie deja que les groupes du perimetre du responsable).
  const scopedGroups = form.programId
    ? groups.filter((g) => String(g.programId) === String(form.programId))
    : []

  const userDisplayName = (user) => {
    const name = [user?.firstName, user?.lastName, user?.prenom, user?.nom]
      .filter(Boolean)
      .join(' ')
      .trim()
    return name || user?.email || 'Utilisateur sans nom'
  }

  // §5 — Bornes reservables EFFECTIVES : la periode libre retenue prime sur la
  // fenetre globale transmise par la recherche. L'utilisateur peut prendre une
  // partie ou la totalite de cette periode, jamais la depasser.
  const periodeActive = periodeIndex >= 0 ? periodes[periodeIndex] : null
  const borneMin = periodeActive?.heureDebut || minTime || ''
  const borneMax = periodeActive?.heureFin || maxTime || ''
  // Creneaux officiels proposables : seuls ceux entierement contenus dans la
  // periode active (on ne propose jamais un creneau qui en sortirait).
  const creneauxDansPeriode = creneaux.filter(
    (c) => (!borneMin || c.heureDebut >= borneMin) && (!borneMax || c.heureFin <= borneMax),
  )
  // Duree actuellement saisie, affichee pour rendre visible la regle des §4/§7.
  const debutMinutes = toMinutes(form.heureDebut)
  const finMinutes = toMinutes(form.heureFin)
  const dureeChoisie =
    debutMinutes != null && finMinutes != null && finMinutes > debutMinutes
      ? finMinutes - debutMinutes
      : 0

  // §5 — Validation EN DIRECT de la plage saisie : elle est recalculee a chaque
  // rendu, et non plus seulement a l'envoi. L'utilisateur voit donc
  // immediatement qu'il sort de la periode libre, et le bouton d'envoi reste
  // desactive tant que la plage n'y tient pas. Les bornes de la periode primant
  // sur tout le reste, elles sont testees avant les regles generales.
  const fenetreLabel = periodeActive ? 'période libre' : 'fenêtre disponible'
  const bornesLabel = borneMin && borneMax ? ` (${fenetreLabel} ${borneMin} – ${borneMax})` : ''
  let saisieError = ''
  if (form.heureDebut && form.heureFin) {
    if (borneMin && form.heureDebut < borneMin) {
      saisieError = `L'heure de début ne peut pas précéder ${borneMin}${bornesLabel}.`
    } else if (borneMax && form.heureFin > borneMax) {
      saisieError = `L'heure de fin ne peut pas dépasser ${borneMax}${bornesLabel} : réduisez l'heure de fin ou choisissez une autre période.`
    } else if (borneMax && form.heureDebut >= borneMax) {
      saisieError = `L'heure de début doit rester avant ${borneMax}${bornesLabel}.`
    } else {
      saisieError =
        validateReservationHours(form.date, form.heureDebut, form.heureFin, {
          opening: openingTime,
          closing: closingTime,
          minSlotMinutes,
        }) || ''
    }
  }

  // Champ fautif : signale visuellement lequel des deux horaires sort des bornes.
  const debutHorsBornes =
    Boolean(form.heureDebut) &&
    Boolean((borneMin && form.heureDebut < borneMin) || (borneMax && form.heureDebut >= borneMax))
  const finHorsBornes =
    Boolean(form.heureFin) &&
    Boolean((borneMax && form.heureFin > borneMax) || (borneMin && form.heureFin <= borneMin))

  // Bornes natives des deux champs = intersection de la periode et de l'autre
  // heure saisie. Le tri lexicographique suffit : "HH:mm" zero-padde est
  // chronologique ("09:05" < "10:00"), donc [0] = la plus tot, pop() = la plus tard.
  const maxDebutInput = [form.heureFin, borneMax].filter(Boolean).sort()[0] || undefined
  const minFinInput = [form.heureDebut, borneMin].filter(Boolean).sort().pop() || undefined

  // La saisie ne peut pas CONSERVER une valeur hors periode : en quittant le
  // champ, l'heure est ramenee a la borne la plus proche. Ce recadrage se fait
  // au blur et jamais a la frappe : `type="time"` emet un onChange des que
  // l'heure devient complete (taper « 1 » donne 01:xx), un recadrage immediat
  // empecherait donc de taper « 12:30 » dans une periode commencant a 10:25.
  const clampTime = (field) => () => {
    const value = form[field]
    if (!value || (!borneMin && !borneMax)) return
    let next = value
    if (borneMin && next < borneMin) next = borneMin
    if (borneMax && next > borneMax) next = borneMax
    if (next !== value) {
      setForm((f) => ({ ...f, [field]: next }))
      setError('')
    }
  }

  // Choisir une periode reinitialise l'horaire sur sa totalite : point de depart
  // le plus lisible, que l'utilisateur peut ensuite reduire.
  const selectPeriode = (index) => {
    const p = periodes[index]
    setPeriodeIndex(index)
    if (p) setForm((f) => ({ ...f, heureDebut: p.heureDebut, heureFin: p.heureFin }))
    setError('')
  }

  const selectCreneau = (c) => {
    setForm((f) => ({ ...f, heureDebut: c.heureDebut, heureFin: c.heureFin }))
    setError('')
  }

  const handleChange = (field) => (e) => {
    const value = e.target.value
    setForm((f) => ({ ...f, [field]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    // Validation horaire côté client : c'est EXACTEMENT le contrôle affiché en
    // direct sous les champs (`saisieError`), donc une seule règle et pas deux
    // jeux divergents. Elle borne la plage à la période libre retenue (§5) et
    // rejoue les règles générales (jour ouvrable, ouverture, durée minimale §4).
    // Le contrôle final — disponibilité revérifiée sous verrou — reste côté
    // serveur : on évite seulement ici une demande vouée à l'échec.
    if (saisieError) {
      setError(saisieError)
      return
    }
    if (!form.heureDebut || !form.heureFin) {
      setError('Veuillez renseigner une heure de début et une heure de fin valides.')
      return
    }

    // Une demande pédagogique doit être rattachée à une filière (règle imposée
    // côté backend pour un responsable pédagogique).
    if (pedagogical && !form.programId) {
      setError('Veuillez sélectionner la filière concernée par cette demande.')
      return
    }


    setIsSubmitting(true)

    const payload = {
      spaceId: Number(form.spaceId),
      type: form.type,
      date: form.date,
      heureDebut: form.heureDebut,
      heureFin: form.heureFin,
      motif: form.motif.trim(),
      commentaire: form.commentaire.trim() || null,
    }

    // L'administrateur peut créer une réservation pour le compte d'un autre
    // utilisateur. En son absence, la réservation est créée pour lui-même.
    if (mode === 'create' && isAdmin && form.targetUserId) {
      payload.targetUserId = Number(form.targetUserId)
    }

    // Contexte pédagogique (responsable pédagogique) : filière obligatoire,
    // groupe et contenu facultatifs.
    if (pedagogical) {
      payload.programId = Number(form.programId)
      if (form.groupId) payload.groupId = Number(form.groupId)
      payload.contenuPedagogique = form.contenuPedagogique.trim() || null
    }

    try {
      if (mode === 'create') {
        const created = await createReservation(payload)
        onSuccess(created)
      } else {
        const updated = await updateReservation(initialData.id, payload)
        onSuccess(updated)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError(err.response.data?.message || 'Un conflit a été détecté pour cette réservation')
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
            {mode === 'create'
              ? (isAdmin && form.targetUserId ? 'Réserver pour un utilisateur' : 'Demander une réservation')
              : 'Modifier la réservation'}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {isCreateFromSearch ? (
            // Recap en lecture seule : ces informations viennent de la recherche
            // de disponibilite et ne sont pas ressaisies par l'utilisateur.
            <div className="bg-ink/[0.03] border border-ink/10 rounded-lg px-4 py-1">
              <div className={readOnlyRow}>
                <span className={readOnlyKey}>Espace</span>
                <span className={readOnlyVal}>
                  {selectedSpace ? `${selectedSpace.code} — ${selectedSpace.nom}` : `#${form.spaceId}`}
                </span>
              </div>
              <div className={readOnlyRow}>
                <span className={readOnlyKey}>Date</span>
                <span className={readOnlyVal}>{form.date}</span>
              </div>
              {periodes.length > 1 && (
                <div className="py-2.5 border-b border-ink/5 last:border-0">
                  <p className={`${readOnlyKey} mb-2`}>Période libre</p>
                  <div className="flex flex-wrap gap-2">
                    {periodes.map((p, i) => (
                      <button
                        key={`${p.heureDebut}-${p.heureFin}-${i}`}
                        type="button"
                        onClick={() => selectPeriode(i)}
                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border transition-colors ${
                          i === periodeIndex
                            ? 'bg-success text-white border-success'
                            : 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                        }`}
                      >
                        <IconClock className="w-3.5 h-3.5" />
                        {p.label || `${p.heureDebut} → ${p.heureFin}`}
                      </button>
                    ))}
                  </div>
                 
                </div>
              )}

              {creneauxDansPeriode.length > 0 && (
                <div className="py-2.5 border-b border-ink/5 last:border-0">
                  <p className={`${readOnlyKey} mb-2`}>Créneau souhaité (en un clic)</p>
                  <div className="flex flex-wrap gap-2">
                    {creneauxDansPeriode.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        title={c.nom || undefined}
                        onClick={() => selectCreneau(c)}
                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border transition-colors ${
                          form.heureDebut === c.heureDebut && form.heureFin === c.heureFin
                            ? 'bg-signal text-white border-signal'
                            : 'bg-signal/10 text-heading/80 border-signal/25 hover:bg-signal/20'
                        }`}
                      >
                        <IconBookmark className="w-3.5 h-3.5" />
                        {c.label || `${c.heureDebut} → ${c.heureFin}`}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {(borneMin || borneMax) ? (
                <div className="py-2 border-b border-ink/5 last:border-0">
                  <div className="flex items-center justify-between gap-4">
                    <span className={readOnlyKey}>Horaire</span>
                    <div className="flex items-center gap-2">
                      {/* §5 — Les bornes natives suivent la saisie : le début ne
                          peut pas dépasser la fin retenue, et la fin ne peut pas
                          descendre sous le début. Le sélecteur natif refuse déjà
                          ces valeurs, le recadrage au blur rattrape la frappe
                          manuelle, et `saisieError` bloque l'envoi. */}
                      <input
                        type="time"
                        value={form.heureDebut}
                        min={borneMin || undefined}
                        max={maxDebutInput}
                        onChange={handleChange('heureDebut')}
                        onBlur={clampTime('heureDebut')}
                        aria-invalid={debutHorsBornes || undefined}
                        className={timeInputClass(debutHorsBornes)}
                        required
                      />
                      <span className="text-ink/40">→</span>
                      <input
                        type="time"
                        value={form.heureFin}
                        min={minFinInput}
                        max={borneMax || undefined}
                        onChange={handleChange('heureFin')}
                        onBlur={clampTime('heureFin')}
                        aria-invalid={finHorsBornes || undefined}
                        className={timeInputClass(finHorsBornes)}
                        required
                      />
                    </div>
                  </div>
                  {borneMin && borneMax && (
                    <div className="mt-1.5 flex flex-wrap items-center justify-end gap-2 text-[11px] text-ink/45">
                      <span>
                        {periodeActive ? 'Période libre' : 'Créneau disponible'} : {borneMin} – {borneMax}.
                        Tout ou partie, sans dépasser ces bornes.
                      </span>
                      {periodeActive && (
                        <button
                          type="button"
                          onClick={() => selectPeriode(periodeIndex)}
                          className="px-2 py-0.5 rounded-md border border-ink/15 hover:bg-ink/5 transition-colors"
                        >
                          Toute la période
                        </button>
                      )}
                      {/* La durée n'est affichée comme acquise que si la plage
                          tient dans la période : sinon on annonce le dépassement
                          au lieu d'un total rassurant mais irréalisable. */}
                      {saisieError ? (
                        <span className="font-medium text-red-700">
                          {debutHorsBornes || finHorsBornes ? 'Hors période' : 'Plage invalide'}
                        </span>
                      ) : (
                        dureeChoisie > 0 && (
                          <span className="font-medium text-ink/60">
                            Durée : {formatDuree(dureeChoisie)}
                          </span>
                        )
                      )}
                    </div>
                  )}
                  {/* Message immédiat : affiché dès la frappe, sans attendre
                      l'envoi, à côté même des champs concernés. */}
                  {saisieError && (
                    <p role="alert" className="mt-1.5 text-[11px] text-red-700 bg-red-50 border border-red-200 rounded-md px-2 py-1.5">
                      {saisieError}
                    </p>
                  )}
                </div>
              ) : (
                <div className={readOnlyRow}>
                  <span className={readOnlyKey}>Horaire</span>
                  <span className={readOnlyVal}>{form.heureDebut} → {form.heureFin}</span>
                </div>
              )}
            </div>
          ) : (
            <>
              <div>
                <label className={labelClass}>Espace</label>
                <select required value={form.spaceId} onChange={handleChange('spaceId')} className={inputClass}>
                  <option value="">Sélectionner un espace...</option>
                  {spaces.map((s) => (
                    <option key={s.id} value={s.id}>{s.code} — {s.nom}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className={labelClass}>Date</label>
                  <input required type="date" value={form.date} onChange={handleChange('date')} className={inputClass} />
                </div>
                <div>
                  <label className={labelClass}>Heure de début</label>
                  <input required type="time" value={form.heureDebut} onChange={handleChange('heureDebut')} className={inputClass} />
                </div>
                <div>
                  <label className={labelClass}>Heure de fin</label>
                  <input required type="time" value={form.heureFin} onChange={handleChange('heureFin')} className={inputClass} />
                </div>
              </div>
              {/* Meme controle en direct hors recherche (modification) : jour
                  ouvrable, plage d'ouverture et duree minimale exploitable. */}
              {saisieError && (
                <p role="alert" className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  {saisieError}
                </p>
              )}
            </>
          )}

          {pedagogical && (
            <div className="rounded-lg border border-blue-200 bg-blue-50/60 px-4 py-3 space-y-3">
              <p className="font-mono text-[11px] tracking-[0.12em] uppercase text-blue-700">
                Contexte pédagogique
              </p>
              <div>
                <label className={labelClass}>Filière concernée</label>
                <select required value={form.programId} onChange={handleChange('programId')} className={inputClass}>
                  <option value="">Sélectionner une filière...</option>
                  {programs.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nom}{p.code ? ` (${p.code})` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}>Groupe concerné (optionnel)</label>
                <select
                  value={form.groupId}
                  onChange={handleChange('groupId')}
                  disabled={!form.programId}
                  className={`${inputClass} disabled:bg-ink/[0.03] disabled:cursor-not-allowed`}
                >
                  <option value="">Aucun groupe précis</option>
                  {scopedGroups.map((g) => (
                    <option key={g.id} value={g.id}>{g.nom}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}>Contenu pédagogique (optionnel)</label>
                <textarea
                  rows={2}
                  value={form.contenuPedagogique}
                  onChange={handleChange('contenuPedagogique')}
                  placeholder="Cours, TD, TP, examen, sujet abordé..."
                  className={`${inputClass} resize-none`}
                />
              </div>
            </div>
          )}

          {mode === 'create' && isAdmin && users.length > 0 && (
            <div className="rounded-lg border border-signal/25 bg-signal/[0.05] px-4 py-3">
              <label className={labelClass}>Réserver pour l'utilisateur</label>
              <select
                value={form.targetUserId}
                onChange={handleChange('targetUserId')}
                className={inputClass}
              >
                <option value="">
                  {userDisplayName(currentUser)} — {roleLabel(currentUser?.role || 'ADMIN')}
                </option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {userDisplayName(u)}
                    {u.role ? ` — ${roleLabel(u.role)}` : ''}
                  </option>
                ))}
              </select>
              <p className="mt-1.5 text-xs text-ink/50">
                La réservation sera enregistrée au nom de l'utilisateur sélectionné.
              </p>
            </div>
          )}

          <div>
            <label className={labelClass}>Type de réservation</label>
            <select required value={form.type} onChange={handleChange('type')} className={inputClass}>
              <option value="">Sélectionner...</option>
              {RESERVATION_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className={labelClass}>Motif</label>
            <input required value={form.motif} onChange={handleChange('motif')} className={inputClass} />
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
              disabled={isSubmitting || Boolean(saisieError)}
              title={saisieError || undefined}
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
            >
              {isSubmitting ? '...' : mode === 'create' ? 'Demander la réservation' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}