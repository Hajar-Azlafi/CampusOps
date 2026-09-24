import { useEffect, useState, useCallback } from 'react'
import {
  searchAvailability,
  getAvailableNow,
  getAvailableToday,
  getSearchWindow,
} from '../api/availabilityApi'
import { fetchBuildings } from '../api/buildingsApi'
import { getFloorsByBuilding } from '../api/floorsApi'
import { fetchEquipments } from '../api/equipmentsApi'
import { fetchSpaces } from '../api/spacesApi'
import { fetchUsers } from '../api/usersApi'
import { getMyScope } from '../api/meApi'
import { fetchGroups } from '../api/groupsApi'
import { useAuth } from '../context/AuthContext'
import { SPACE_TYPES, spaceTypeLabel } from '../constants/spaceTypes'
import { availStatus, availAffichage } from '../constants/availabilityStatus'
import {
  DURATION_OPTIONS,
  MIN_SLOT_MINUTES,
  formatDuree,
  toMinutes,
  validateSearchDate,
} from '../constants/reservationHours'
import AvailableSpaceDetailsModal from '../components/AvailableSpaceDetailsModal'
import ReservationFormModal from '../components/ReservationFormModal'
import {
  IconSearchLocation,
  IconClock,
  IconGrid,
  IconClipboard,
  IconDoor,
  IconCalendar,
  IconCalendarRange,
  IconGauge,
  IconUsers,
  IconBuilding,
  IconLayers,
  IconSliders,
  IconChevronDown,
  IconBookmark,
  IconAlertTriangle,
  IconEye,
} from '../components/icons'

const inputClass =
  'w-full px-3 py-2 border border-heading/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors'
const labelClass = 'flex items-center gap-1.5 text-xs font-medium text-heading/70 mb-1.5'

function pad2(n) {
  return String(n).padStart(2, '0')
}

function todayISO() {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

// Onglets de disponibilité : recherche instantanée (maintenant / aujourd'hui)
// ou recherche personnalisée par critères.
const MODES = [
  { value: 'now', label: 'Maintenant' },
  { value: 'today', label: "Aujourd'hui" },
  { value: 'custom', label: 'Personnalisée' },
]

// Valeur du sélecteur « durée souhaitée » signifiant « je saisis moi-même ».
const CUSTOM_DURATION = 'custom'

export default function AvailabilityPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  // Le responsable pédagogique réserve pour SES filières : la demande doit
  // porter un contexte pédagogique (filière obligatoire, groupe facultatif).
  const isRp = user?.role === 'RESPONSABLE_PEDAGOGIQUE'

  const [mode, setMode] = useState('custom')

  // Règles de saisie annoncées par le serveur (GET /availability/window) :
  // bornes du champ date, heure limite de la journée en cours, horaires dérivés
  // des créneaux, durée minimale exploitable, durées proposées et créneaux
  // officiels. Le client ne recalcule rien : il borne son formulaire (§1, §4).
  const [searchWindow, setSearchWindow] = useState(null)

  const [form, setForm] = useState({
    date: todayISO(),
    // Heures OPTIONNELLES (§2) : vides par défaut, la recherche porte alors sur
    // la journée entière et renvoie les vraies périodes libres.
    heureDebut: '',
    heureFin: '',
    // Durée souhaitée optionnelle (§6) : '' = aucune, sinon minutes ou 'custom'.
    dureeChoix: '',
    dureePersonnalisee: '',
    buildingId: '',
    floorId: '',
    type: '',
    capaciteMin: '',
  })
  const [selectedEquipmentIds, setSelectedEquipmentIds] = useState([])
  const [advancedOpen, setAdvancedOpen] = useState(false)

  const [buildings, setBuildings] = useState([])
  const [floors, setFloors] = useState([])
  const [equipments, setEquipments] = useState([])
  const [spaces, setSpaces] = useState([])
  const [amphitheatres, setAmphitheatres] = useState([])
  const [users, setUsers] = useState([])
  const [programs, setPrograms] = useState([])
  const [groups, setGroups] = useState([])

  const [results, setResults] = useState([])
  // Enveloppe de contexte renvoyee par le backend (jour ouvrable, horaires
  // derives des creneaux, annee universitaire couvrant la date, messages). Seule
  // source de verite pour l'affichage : aucun recalcul cote client (§2, §19).
  const [meta, setMeta] = useState(null)
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [viewMode, setViewMode] = useState('cards')
  const [detailsModal, setDetailsModal] = useState({ open: false, space: null })
  const [reservationModal, setReservationModal] = useState({
    open: false,
    spaceId: null,
    date: '',
    heureDebut: '',
    heureFin: '',
    // Fenetre reservable (§9) : bornes min/max transmises au formulaire.
    minTime: '',
    maxTime: '',
    // Periodes libres et creneaux officiels de CET espace (§5) : le formulaire
    // permet de choisir une periode puis, dedans, un creneau officiel ou des
    // heures libres — sans jamais depasser les bornes de la periode.
    periodes: [],
    creneaux: [],
  })

  // ---------------------------------------------------------------------------
  // Validation de saisie côté client — miroir des règles serveur (§1, §2, §6).
  // Le serveur revalide systématiquement : ces contrôles évitent seulement des
  // allers-retours réseau inutiles et rendent l'erreur immédiate.
  // ---------------------------------------------------------------------------
  const minSlotMinutes = searchWindow?.dureeMinimaleMinutes ?? MIN_SLOT_MINUTES
  const dureesProposees =
    Array.isArray(searchWindow?.dureesProposees) && searchWindow.dureesProposees.length > 0
      ? searchWindow.dureesProposees
      : DURATION_OPTIONS

  // Durée souhaitée effective (minutes) ou null si non renseignée.
  const dureeMinutes = (() => {
    if (form.dureeChoix === CUSTOM_DURATION) {
      const n = Number(form.dureePersonnalisee)
      return Number.isFinite(n) && n > 0 ? n : null
    }
    if (form.dureeChoix === '') return null
    const n = Number(form.dureeChoix)
    return Number.isFinite(n) && n > 0 ? n : null
  })()

  const dateError = validateSearchDate(form.date, searchWindow)

  // Les heures vont par paire : une seule borne ne permet pas de définir un
  // créneau. Vide + vide = journée entière (§2).
  const hoursFilled = !!form.heureDebut && !!form.heureFin
  const hoursPartial = (!!form.heureDebut) !== (!!form.heureFin)

  const timeError = (() => {
    if (hoursPartial) {
      return form.heureDebut
        ? "Renseignez aussi l'heure de fin, ou laissez les deux vides pour chercher sur la journée entière."
        : "Renseignez aussi l'heure de début, ou laissez les deux vides pour chercher sur la journée entière."
    }
    if (!hoursFilled) return ''
    if (form.heureFin <= form.heureDebut) {
      return "L'heure de fin doit être postérieure à l'heure de début."
    }
    const duree = toMinutes(form.heureFin) - toMinutes(form.heureDebut)
    if (minSlotMinutes > 0 && duree < minSlotMinutes) {
      return `Un créneau doit durer au moins ${formatDuree(minSlotMinutes)} : une plage plus courte n'est pas exploitable.`
    }
    return ''
  })()

  const dureeError = (() => {
    if (form.dureeChoix === CUSTOM_DURATION && form.dureePersonnalisee !== '' && !dureeMinutes) {
      return 'La durée souhaitée doit être un nombre de minutes supérieur à zéro.'
    }
    if (dureeMinutes && minSlotMinutes > 0 && dureeMinutes < minSlotMinutes) {
      return `La durée souhaitée doit être d'au moins ${formatDuree(minSlotMinutes)}.`
    }
    // Durée + heures explicites : la durée doit tenir dans la plage demandée.
    if (dureeMinutes && hoursFilled && !timeError) {
      const plage = toMinutes(form.heureFin) - toMinutes(form.heureDebut)
      if (dureeMinutes > plage) {
        return `La durée souhaitée (${formatDuree(dureeMinutes)}) ne tient pas dans la plage ${form.heureDebut} – ${form.heureFin} (${formatDuree(plage)}).`
      }
    }
    return ''
  })()

  const formError = dateError || timeError || dureeError

  useEffect(() => {
    fetchBuildings({ actif: true }).then(setBuildings).catch(() => {})
    fetchEquipments({ actif: true }).then(setEquipments).catch(() => {})
    fetchSpaces({ actif: true }).then(setSpaces).catch(() => {})
  }, [])

  // Règles de saisie serveur (§1) : on borne le champ date et on repositionne la
  // date par défaut si celle du client n'est plus recherchable (date passée, ou
  // heure limite de la journée en cours dépassée).
  useEffect(() => {
    getSearchWindow()
      .then((w) => {
        setSearchWindow(w)
        setForm((prev) => {
          let date = prev.date
          if (w?.dateMin && date < w.dateMin) date = w.dateParDefaut || w.dateMin
          if (w?.dateMax && date > w.dateMax) date = w.dateMax
          return date === prev.date ? prev : { ...prev, date }
        })
      })
      .catch(() => setSearchWindow(null))
  }, [])

  // L'administrateur peut réserver au nom d'un autre utilisateur : on charge la
  // liste des bénéficiaires possibles (hors comptes désactivés).
  useEffect(() => {
    if (!isAdmin) return
    fetchUsers()
      .then((data) => setUsers(data.filter((u) => u.isActive !== false && u.role !== 'ADMIN')))
      .catch(() => setUsers([]))
  }, [isAdmin])

  // Pour le responsable pédagogique, on charge ses filières (périmètre) et les
  // groupes de son périmètre afin d'alimenter le formulaire de demande.
  useEffect(() => {
    if (!isRp) return
    getMyScope()
      .then((scope) => setPrograms(scope.programs || []))
      .catch(() => setPrograms([]))
    fetchGroups({ actif: true })
      .then(setGroups)
      .catch(() => setGroups([]))
  }, [isRp])

  useEffect(() => {
    if (!form.buildingId) {
      setFloors([])
      return
    }
    getFloorsByBuilding(Number(form.buildingId))
      .then((data) => setFloors(data.filter((f) => f.actif)))
      .catch(() => setFloors([]))
  }, [form.buildingId])

  // Compute amphitheatre list for the selected building (if any)
  useEffect(() => {
    if (!form.buildingId) {
      setAmphitheatres([])
      return
    }
    const list = spaces.filter(
      (s) => s.type === 'AMPHITHEATER' && String(s.buildingId) === String(form.buildingId),
    )
    setAmphitheatres(list)
  }, [form.buildingId, spaces])

  const setField = (name, value) => {
    setForm((prev) => {
      const next = { ...prev, [name]: value }
      if (name === 'buildingId') {
        next.floorId = ''
        next.exactSpaceQuery = ''
      }
      return next
    })
  }

  const toggleEquipment = (id) => {
    setSelectedEquipmentIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const buildCriteria = useCallback(() => {
    const criteria = { date: form.date }
    // Heures OPTIONNELLES (§2) : on ne les transmet QUE si les deux sont
    // renseignées. Sans elles, le backend analyse la journée entière.
    if (form.heureDebut && form.heureFin) {
      criteria.heureDebut = form.heureDebut
      criteria.heureFin = form.heureFin
    }
    // Durée souhaitée (§6/§7) : n'a de sens que transmise au backend, qui ne
    // garde alors que les espaces offrant un bloc libre CONTINU aussi long.
    if (dureeMinutes) criteria.dureeMinutes = dureeMinutes
    if (form.buildingId) criteria.buildingId = Number(form.buildingId)
    if (form.floorId) criteria.floorId = Number(form.floorId)
    if (form.type) criteria.type = form.type
    if (form.capaciteMin !== '') criteria.capaciteMin = Number(form.capaciteMin)
    if (selectedEquipmentIds.length > 0) criteria.equipmentIds = selectedEquipmentIds
    if (form.exactSpaceQuery) criteria.exactSpaceQuery = form.exactSpaceQuery
    return criteria
  }, [form, selectedEquipmentIds, dureeMinutes])

  // Applique l'enveloppe de reponse : on garde le contexte complet (meta) et on
  // en extrait la liste des espaces. Le backend est seul juge de la
  // disponibilite ; le client ne fait qu'afficher (§2, §19).
  const applyResponse = (data) => {
    setMeta(data || null)
    setResults(Array.isArray(data?.espaces) ? data.espaces : [])
    setSearched(true)
    // Chaque reponse re-annonce les bornes de date et l'heure limite : on
    // rafraichit les regles de saisie pour rester aligne meme apres un long
    // sejour sur la page (passage de minuit, depassement du cutoff).
    if (data?.dateMin) {
      setSearchWindow((prev) => ({
        ...(prev || {}),
        dateMin: data.dateMin,
        dateMax: data.dateMax ?? null,
        heureLimiteRecherche: data.heureLimiteRecherche ?? prev?.heureLimiteRecherche,
        limiteJourneeDepassee: data.limiteJourneeDepassee ?? prev?.limiteJourneeDepassee,
        messageConfiguration: data.messageConfiguration ?? null,
        dureeMinimaleMinutes: data.dureeMinimaleMinutes ?? prev?.dureeMinimaleMinutes,
        anneeActiveLibelle: data.anneeActiveLibelle ?? prev?.anneeActiveLibelle,
      }))
    }
  }

  const runSearch = async (e) => {
    if (e) e.preventDefault()
    // Saisie invalide (§1, §2, §6) : on n'appelle même pas le serveur.
    if (formError) return
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await searchAvailability(buildCriteria())
      applyResponse(data)
    } catch (err) {
      setErrorMsg(
        err.response?.data?.message ||
          "La recherche a échoué, veuillez vérifier les critères et réessayer",
      )
    } finally {
      setLoading(false)
    }
  }

  const runQuickSearch = async (quickMode) => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = quickMode === 'now' ? await getAvailableNow() : await getAvailableToday()
      applyResponse(data)
    } catch (err) {
      setErrorMsg(err.response?.data?.message || "La recherche a échoué, veuillez réessayer")
    } finally {
      setLoading(false)
    }
  }

  const handleModeChange = (next) => {
    setMode(next)
    setErrorMsg('')
    if (next === 'now') runQuickSearch('now')
    else if (next === 'today') runQuickSearch('today')
  }

  const openDetails = (space) => setDetailsModal({ open: true, space })
  const closeDetails = () => setDetailsModal({ open: false, space: null })

  const handleReserve = (space, slot) => {
    // §5 — Reserver depuis un espace libre : on transmet au formulaire TOUTES
    // les periodes libres de cet espace et les creneaux officiels qui tiennent
    // dedans. L'utilisateur choisit une periode, puis un creneau officiel ou des
    // heures libres, sans jamais pouvoir depasser les bornes de la periode. Le
    // backend re-verifie de toute facon la disponibilite avant d'enregistrer.
    const periodes = Array.isArray(space.periodesLibres) ? space.periodesLibres : []
    const creneaux = Array.isArray(space.creneauxProposes) ? space.creneauxProposes : []
    // La date provient du contexte serveur (meta) : indispensable pour les modes
    // « maintenant » / « aujourd'hui » qui n'utilisent pas le formulaire.
    const date = meta?.date || form.date
    // Fenetre reservable calculee par le backend (§9), utilisee pour choisir la
    // periode a proposer par defaut. Un creneau clique (slot) est prioritaire.
    const ancre = slot?.heureDebut || space.fenetreDebut || ''
    const winStart = space.fenetreDebut || ''
    const winEnd = space.fenetreFin || ''
    let index = periodes.findIndex(
      (p) => ancre && p.heureDebut <= ancre && ancre < p.heureFin,
    )
    if (index < 0) index = periodes.length > 0 ? 0 : -1
    const periode = index >= 0 ? periodes[index] : null
    const minTime = periode?.heureDebut || winStart
    const maxTime = periode?.heureFin || winEnd
    // Horaire propose : le creneau clique, sinon le creneau explicitement
    // recherche s'il tient dans la periode, sinon la fenetre du backend.
    let debut = winStart || minTime
    let fin = winEnd || maxTime
    if (
      mode === 'custom' &&
      hoursFilled &&
      minTime &&
      maxTime &&
      form.heureDebut >= minTime &&
      form.heureFin <= maxTime
    ) {
      debut = form.heureDebut
      fin = form.heureFin
    }
    if (slot?.heureDebut && slot?.heureFin) {
      debut = slot.heureDebut
      fin = slot.heureFin
    }
    setReservationModal({
      open: true,
      spaceId: space.spaceId,
      date,
      heureDebut: debut,
      heureFin: fin,
      minTime,
      maxTime,
      periodes,
      creneaux,
    })
    closeDetails()
  }

  const closeReservation = () =>
    setReservationModal({
      open: false,
      spaceId: null,
      date: '',
      heureDebut: '',
      heureFin: '',
      minTime: '',
      maxTime: '',
      periodes: [],
      creneaux: [],
    })

  const handleReservationSuccess = () => {
    closeReservation()
    if (mode === 'now') runQuickSearch('now')
    else if (mode === 'today') runQuickSearch('today')
    else runSearch()
  }

  const resultCount = results.length

  // Titre de l'en-tête des résultats (§3) : porte toujours la date, jamais un
  // « aujourd'hui » implicite.
  const titreResultats =
    (meta?.mode || '').toUpperCase() === 'NOW' ? 'Espaces libres maintenant' : 'Espaces libres'

  // Plage réellement analysée par le serveur : le créneau demandé s'il y en a
  // un, sinon la fenêtre de journée retenue (bornée à l'instant présent pour
  // aujourd'hui, ce qui évite d'afficher des périodes déjà écoulées).
  const plageAnalysee = (() => {
    if (meta?.heureDebutRecherche && meta?.heureFinRecherche) {
      return `${meta.heureDebutRecherche} → ${meta.heureFinRecherche}`
    }
    if (meta?.fenetreJourDebut && meta?.fenetreJourFin) {
      return `${meta.fenetreJourDebut} → ${meta.fenetreJourFin}`
    }
    return ''
  })()

  // Un message serveur explique déjà l'absence de résultats : on n'ajoute pas le
  // vide « aucun espace ne correspond », qui serait trompeur.
  const hasBlockingMessage = !!(
    meta?.messageFermeture ||
    meta?.messageValidation ||
    meta?.messageConfiguration ||
    (meta?.dateValide === false && meta?.messageDate)
  )

  return (
    <div>
      <div className="mb-6">
        <span className="inline-flex items-center gap-1.5 font-mono text-[11px] tracking-[0.15em] text-heading-soft uppercase">
          <IconSearchLocation className="w-3.5 h-3.5" />
          Disponibilité en temps réel
        </span>
        <h1 className="font-display text-2xl font-semibold text-ink mt-1.5">
          Recherche d'espaces disponibles
        </h1>
        <p className="text-sm text-ink/50 mt-1">
          Trouvez en temps réel une salle libre selon vos critères et réservez-la directement
        </p>
      </div>

      {/* Sélecteur de disponibilité : Maintenant | Aujourd'hui | Personnalisée */}
      <div className="inline-flex items-center gap-1 bg-heading/[0.05] border border-heading/10 rounded-xl p-1 mb-4">
        {MODES.map((m) => {
          const active = mode === m.value
          return (
            <button
              key={m.value}
              type="button"
              onClick={() => handleModeChange(m.value)}
              className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                active
                  ? 'bg-blueprint-800 text-white shadow-sm'
                  : 'text-heading/70 hover:text-heading hover:bg-surface/60'
              }`}
            >
              {m.label}
            </button>
          )
        })}
      </div>

      {mode === 'custom' ? (
        <form onSubmit={runSearch} className="bg-surface border border-heading/10 rounded-xl shadow-sm overflow-hidden mb-4">
          <div className="px-5 py-3 border-b border-heading/10 bg-heading/[0.035] flex items-center gap-2">
            <IconClipboard className="w-4 h-4 text-heading-soft" />
            <span className="text-sm font-semibold text-heading">Critères de recherche</span>
          </div>

          <div className="p-5">
            {/* Ligne 1 — date (bornée par le serveur), heures optionnelles, durée */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div>
                <label className={labelClass}><IconCalendar className="w-3.5 h-3.5" /> Date</label>
                <input
                  type="date"
                  value={form.date}
                  min={searchWindow?.dateMin || undefined}
                  max={searchWindow?.dateMax || undefined}
                  onChange={(e) => setField('date', e.target.value)}
                  className={`${inputClass} ${dateError ? 'border-red-300 focus:ring-red-400 focus:border-red-400' : ''}`}
                  aria-invalid={!!dateError}
                  required
                />
              </div>
              <div>
                <label className={labelClass}>
                  <IconClock className="w-3.5 h-3.5" /> Heure de début
                  <span className="font-normal text-ink/35">(optionnel)</span>
                </label>
                <input
                  type="time"
                  value={form.heureDebut}
                  onChange={(e) => setField('heureDebut', e.target.value)}
                  className={`${inputClass} ${timeError ? 'border-red-300 focus:ring-red-400 focus:border-red-400' : ''}`}
                  aria-invalid={!!timeError}
                />
              </div>
              <div>
                <label className={labelClass}>
                  <IconClock className="w-3.5 h-3.5" /> Heure de fin
                  <span className="font-normal text-ink/35">(optionnel)</span>
                </label>
                <input
                  type="time"
                  value={form.heureFin}
                  onChange={(e) => setField('heureFin', e.target.value)}
                  className={`${inputClass} ${timeError ? 'border-red-300 focus:ring-red-400 focus:border-red-400' : ''}`}
                  aria-invalid={!!timeError}
                />
              </div>
              <div>
                <label className={labelClass}>
                  <IconGauge className="w-3.5 h-3.5" /> Durée souhaitée
                  <span className="font-normal text-ink/35">(optionnel)</span>
                </label>
                <select
                  value={form.dureeChoix}
                  onChange={(e) => setField('dureeChoix', e.target.value)}
                  className={`${inputClass} ${dureeError ? 'border-red-300 focus:ring-red-400 focus:border-red-400' : ''}`}
                >
                  <option value="">Peu importe</option>
                  {dureesProposees.map((d) => (
                    <option key={d} value={d}>{formatDuree(d)}</option>
                  ))}
                  <option value={CUSTOM_DURATION}>Personnalisée...</option>
                </select>
                {form.dureeChoix === CUSTOM_DURATION && (
                  <input
                    type="number"
                    min={minSlotMinutes || 1}
                    step="5"
                    value={form.dureePersonnalisee}
                    onChange={(e) => setField('dureePersonnalisee', e.target.value)}
                    placeholder={`Minutes (min. ${minSlotMinutes || 1})`}
                    className={`${inputClass} mt-2`}
                  />
                )}
              </div>
            </div>

            {/* Explication de la recherche sans heures (§2) et de la durée (§7) */}
            <p className="mt-2 text-[11px] text-ink/45 leading-relaxed">
             
              {dureeMinutes
                ? ` Seuls les espaces offrant un bloc libre continu d'au moins ${formatDuree(dureeMinutes)} sont retenus (deux périodes séparées par une occupation ne sont jamais additionnées).`
                : ''}
            </p>

            {/* Créneaux officiels de l'établissement : remplissent les deux heures
                en un clic, plutôt qu'une saisie manuelle approximative. */}
            {Array.isArray(searchWindow?.creneaux) && searchWindow.creneaux.length > 0 && (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <span className="text-[11px] font-medium text-ink/45">Créneaux officiels :</span>
                {searchWindow.creneaux.map((c) => {
                  const active = form.heureDebut === c.heureDebut && form.heureFin === c.heureFin
                  return (
                    <button
                      key={c.id}
                      type="button"
                      title={c.nom || undefined}
                      onClick={() =>
                        setForm((prev) => ({
                          ...prev,
                          heureDebut: active ? '' : c.heureDebut,
                          heureFin: active ? '' : c.heureFin,
                        }))
                      }
                      className={`px-2.5 py-1 rounded-lg text-[11px] font-medium border transition-colors ${
                        active
                          ? 'bg-blueprint-800 text-white border-blueprint-800 shadow-sm'
                          : 'bg-heading/[0.04] text-heading/70 border-heading/15 hover:bg-heading/10'
                      }`}
                    >
                      {c.label || `${c.heureDebut} → ${c.heureFin}`}
                    </button>
                  )
                })}
                {hoursFilled && (
                  <button
                    type="button"
                    onClick={() => setForm((prev) => ({ ...prev, heureDebut: '', heureFin: '' }))}
                    className="px-2.5 py-1 rounded-lg text-[11px] font-medium text-ink/50 border border-transparent hover:bg-ink/5 transition-colors"
                  >
                    Journée entière
                  </button>
                )}
              </div>
            )}

            {/* Ligne 2 — localisation, type et capacité */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
              <div>
                <label className={labelClass}><IconBuilding className="w-3.5 h-3.5" /> Bâtiment</label>
                <select
                  value={form.buildingId}
                  onChange={(e) => setField('buildingId', e.target.value)}
                  className={inputClass}
                >
                  <option value="">Tous les bâtiments</option>
                  {buildings.map((b) => (
                    <option key={b.id} value={b.id}>{b.nom}</option>
                  ))}
                </select>
              </div>
              {amphitheatres.length > 0 && (
                <div>
                  <label className={labelClass}><IconBookmark className="w-3.5 h-3.5" /> Amphithéâtre</label>
                  <select
                    value={form.exactSpaceQuery || ''}
                    onChange={(e) => setField('exactSpaceQuery', e.target.value)}
                    className={inputClass}
                  >
                    <option value="">Tous les amphithéâtres</option>
                    {amphitheatres.map((a) => (
                      <option key={a.id} value={a.code || a.nom}>{a.code ? `${a.code} — ${a.nom}` : a.nom}</option>
                    ))}
                  </select>
                </div>
              )}
              <div>
                <label className={labelClass}><IconLayers className="w-3.5 h-3.5" /> Étage</label>
                <select
                  value={form.floorId}
                  onChange={(e) => setField('floorId', e.target.value)}
                  disabled={!form.buildingId}
                  className={`${inputClass} disabled:bg-heading/[0.03] disabled:cursor-not-allowed`}
                >
                  <option value="">Tous les étages</option>
                  {floors.map((f) => (
                    <option key={f.id} value={f.id}>{f.nom}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}><IconDoor className="w-3.5 h-3.5" /> Type d'espace</label>
                <select
                  value={form.type}
                  onChange={(e) => setField('type', e.target.value)}
                  className={inputClass}
                >
                  <option value="">Tous les types</option>
                  {SPACE_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}><IconUsers className="w-3.5 h-3.5" /> Capacité minimale</label>
                <input
                  type="number"
                  min="0"
                  value={form.capaciteMin}
                  onChange={(e) => setField('capaciteMin', e.target.value)}
                  placeholder="Ex. 30"
                  className={inputClass}
                />
              </div>
            </div>

            {/* Filtre avancé repliable — équipements */}
            {equipments.length > 0 && (
              <div className="mt-4 border-t border-heading/10 pt-4">
                <button
                  type="button"
                  onClick={() => setAdvancedOpen((v) => !v)}
                  className="flex items-center gap-2 text-sm font-medium text-heading/80 hover:text-heading transition-colors"
                >
                  <IconSliders className="w-4 h-4" />
                  Filtres avancés
                  {selectedEquipmentIds.length > 0 && (
                    <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-signal text-white text-[10px] font-semibold">
                      {selectedEquipmentIds.length}
                    </span>
                  )}
                  <IconChevronDown className={`w-4 h-4 transition-transform ${advancedOpen ? 'rotate-180' : ''}`} />
                </button>

                {advancedOpen && (
                  <div className="mt-3">
                    <label className={labelClass}>Équipements souhaités</label>
                    <div className="flex flex-wrap gap-2 mt-1">
                      {equipments.map((eq) => {
                        const active = selectedEquipmentIds.includes(eq.id)
                        return (
                          <button
                            key={eq.id}
                            type="button"
                            onClick={() => toggleEquipment(eq.id)}
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                              active
                                ? 'bg-blueprint-800 text-white border-blueprint-800 shadow-sm'
                                : 'bg-heading/[0.04] text-heading/70 border-heading/15 hover:bg-heading/10'
                            }`}
                          >
                            {eq.nom}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )}
              </div>
            )}

            {formError && (
              <p className="flex items-center gap-2 mt-4 text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                <IconAlertTriangle className="w-4 h-4 shrink-0" />
                {formError}
              </p>
            )}

            {/* Bouton d'action principal — orange, large et bien visible */}
            <div className="flex justify-end mt-5">
              <button
                type="submit"
                disabled={loading || !!formError}
                className="inline-flex items-center justify-center gap-2 px-8 py-3 bg-signal hover:bg-signal/90 text-white text-sm font-semibold rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm hover:shadow focus:outline-none focus:ring-2 focus:ring-signal/40"
              >
                <IconSearchLocation className="w-4 h-4" />
                {loading ? 'Recherche...' : 'Rechercher'}
              </button>
            </div>
          </div>
        </form>
      ) : (
        <div className="bg-surface border border-heading/10 rounded-xl shadow-sm px-5 py-4 mb-4 flex items-start gap-3 text-sm text-ink/60">
          <span className="w-9 h-9 rounded-lg bg-heading/[0.06] flex items-center justify-center shrink-0">
            <IconClock className="w-4 h-4 text-heading-soft" />
          </span>
          <div>
            <p>
              {mode === 'now'
                ? "Espaces libres à l'instant présent, tous bâtiments confondus."
                : "Espaces disposant d'au moins un créneau libre sur la journée affichée."}
            </p>
            {/* §3 — la date concernée n'est jamais ambiguë : elle est rappelée ici
                puis en tête des résultats, et le cutoff est explicite. */}
            {meta?.libelleDate && (
              <p className="mt-0.5 text-ink/45">
                Journée analysée : <span className="font-medium text-heading/80">{meta.libelleDate}</span>
              </p>
            )}
          </div>
        </div>
      )}

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      {/* Date refusée (§1) : passée, ou hors année universitaire active. Le
          serveur a bloqué la recherche ; on affiche son message tel quel. */}
      {searched && meta?.dateValide === false && meta?.messageDate && (
        <div className="flex items-start gap-2.5 text-sm text-red-800 bg-red-50 border border-red-200 rounded-lg px-3.5 py-3 mb-4">
          <IconAlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-red-600" />
          <div>
            <p className="font-medium">{meta.messageDate}</p>
            {meta.dateMin && (
              <p className="mt-0.5 text-red-700">
                Dates recherchables : à partir du {meta.dateMin}
              </p>
            )}
          </div>
        </div>
      )}

      {/* Saisie refusée par le serveur (heures, plage vide, durée trop longue) :
          message distinct d'une fermeture, pour ne pas induire en erreur. */}
      {searched && meta?.messageValidation && (
        <div className="flex items-start gap-2.5 text-sm text-red-800 bg-red-50 border border-red-200 rounded-lg px-3.5 py-3 mb-4">
          <IconAlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-red-600" />
          <p>{meta.messageValidation}</p>
        </div>
      )}

      {/* Jour non ouvrable (dimanche/férié §3, §4) ou hors horaires :
          message fourni par le backend, seule source de vérité. */}
      {searched && meta?.messageFermeture && (
        <div className="flex items-start gap-2.5 text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-3.5 py-3 mb-4">
          <IconAlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-amber-600" />
          <div>
            <p className="font-medium">{meta.messageFermeture}</p>
            {meta.jourOuvrable && meta.heureOuverture && meta.heureFermeture && (
              <p className="mt-0.5 text-amber-700">
                Horaires d'ouverture : {meta.heureOuverture} – {meta.heureFermeture}.
              </p>
            )}
          </div>
        </div>
      )}

      {/* Configuration incohérente (aucune année active, ou année terminée) :
          plus aucune date n'est recherchable, il faut une action administrateur. */}
      {searched && meta?.messageConfiguration && (
        <div className="flex items-start gap-2.5 text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-3.5 py-3 mb-4">
          <IconCalendarRange className="w-4 h-4 shrink-0 mt-0.5 text-amber-600" />
          <p>{meta.messageConfiguration}</p>
        </div>
      )}

      {/* Aucune année universitaire ne couvre la date : information seulement, on
          n'affiche JAMAIS « toutes les salles libres » (l'occupation réelle —
          emplois du temps, examens, réservations — reste vérifiée par date). */}
      {searched && meta?.messageAnneeUniversitaire && (
        <div className="flex items-start gap-2.5 text-sm text-blue-800 bg-blue-50 border border-blue-200 rounded-lg px-3.5 py-3 mb-4">
          <IconCalendar className="w-4 h-4 shrink-0 mt-0.5 text-blue-600" />
          <p>{meta.messageAnneeUniversitaire}</p>
        </div>
      )}

      {/* §3 — En-tête des résultats portant la DATE concernée en clair, pour
          lever toute ambiguïté (« Espaces libres — Mardi 1 septembre 2026 »),
          plus le contexte réellement analysé (plage, durée, année). */}
      {searched && meta?.libelleDate && (
        <div className="bg-surface border border-heading/10 rounded-xl px-4 py-3 mb-3">
          <h2 className="flex items-center gap-2 font-display text-base font-semibold text-ink">
            <IconCalendarRange className="w-4 h-4 text-heading-soft shrink-0" />
            {titreResultats} — <span className="text-heading">{meta.libelleDate}</span>
          </h2>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink/45">
            {plageAnalysee && (
              <span className="inline-flex items-center gap-1">
                <IconClock className="w-3 h-3" /> Plage analysée {plageAnalysee}
              </span>
            )}
            {meta.dureeSouhaiteeMinutes > 0 && (
              <span className="inline-flex items-center gap-1">
                <IconGauge className="w-3 h-3" /> Bloc continu ≥ {formatDuree(meta.dureeSouhaiteeMinutes)}
              </span>
            )}
            {meta.anneeUniversitaireLibelle && (
              <span>Année {meta.anneeUniversitaireLibelle}</span>
            )}
          </div>
        </div>
      )}

      {searched && (
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm text-ink/60">
            {loading ? (
              'Recherche en cours...'
            ) : (
              <>
                <span className="font-semibold text-heading">{resultCount}</span> espace{resultCount > 1 ? 's' : ''} trouvé{resultCount > 1 ? 's' : ''}
                {meta?.jourOuvrable && meta?.heureOuverture && meta?.heureFermeture && (
                  <span className="text-ink/40"> · ouverture {meta.heureOuverture}–{meta.heureFermeture}</span>
                )}
              </>
            )}
          </p>
          <div className="flex items-center gap-1 bg-heading/[0.05] rounded-lg p-1">
            <button
              onClick={() => setViewMode('cards')}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                viewMode === 'cards' ? 'bg-surface text-heading shadow-sm' : 'text-ink/50 hover:text-ink/70'
              }`}
            >
              <IconGrid className="w-4 h-4" /> Cartes
            </button>
            <button
              onClick={() => setViewMode('table')}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                viewMode === 'table' ? 'bg-surface text-heading shadow-sm' : 'text-ink/50 hover:text-ink/70'
              }`}
            >
              <IconClipboard className="w-4 h-4" /> Tableau
            </button>
          </div>
        </div>
      )}

      {searched && !loading && resultCount === 0 && !hasBlockingMessage && (
        <div className="bg-surface border border-heading/10 rounded-xl py-16 text-center">
          <div className="w-14 h-14 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
            <IconDoor className="w-7 h-7 text-heading/30" />
          </div>
          <p className="mt-3 text-sm text-ink/50">
            Aucun espace disponible ne correspond à ces critères
          </p>
          {meta?.dureeSouhaiteeMinutes > 0 && (
            <p className="mt-1 text-xs text-ink/40">
              Aucun espace n'offre un bloc libre continu de {formatDuree(meta.dureeSouhaiteeMinutes)}
              {plageAnalysee ? ` entre ${plageAnalysee.replace(' → ', ' et ')}` : ''}. Essayez une
              durée plus courte ou une autre date.
            </p>
          )}
        </div>
      )}

      {searched && !loading && resultCount > 0 && viewMode === 'cards' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {results.map((s) => {
            const st = availStatus(s)
            return (
            <div
              key={s.spaceId}
              className="flex flex-col bg-surface border border-heading/10 rounded-xl overflow-hidden hover:shadow-md hover:shadow-heading/5 transition-all"
            >
              <div className="p-4 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium text-ink truncate">{s.nom}</p>
                    <p className="text-xs font-mono text-heading/50 mt-0.5">{s.code}</p>
                  </div>
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border whitespace-nowrap shrink-0 ${st.badgeClass}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${st.dotClass}`} /> {st.label}
                  </span>
                </div>

                <div className="mt-3 grid grid-cols-2 gap-y-1.5 gap-x-2 text-xs text-ink/60">
                  <span className="flex items-center gap-1.5"><IconUsers className="w-3.5 h-3.5 text-heading/40" /> {s.capacite ?? '—'} places</span>
                  <span className="flex items-center gap-1.5"><IconDoor className="w-3.5 h-3.5 text-heading/40" /> {spaceTypeLabel(s.type)}</span>
                  <span className="flex items-center gap-1.5"><IconBuilding className="w-3.5 h-3.5 text-heading/40" /> {s.buildingNom}</span>
                  <span className="flex items-center gap-1.5"><IconLayers className="w-3.5 h-3.5 text-heading/40" /> {s.floorNom}</span>
                </div>

                {availAffichage(s) && (
                  <div className="mt-3 flex items-center gap-1.5 text-xs font-medium text-heading-soft bg-heading/[0.05] rounded-md px-2 py-1 w-fit">
                    <IconClock className="w-3.5 h-3.5" />
                    <span>{availAffichage(s)}</span>
                  </div>
                )}

                {/* Périodes libres réelles (§4/§7) : chaque puce est un bloc
                    CONTINU réservable ; deux puces ne s'additionnent jamais. */}
                {Array.isArray(s.periodesLibres) && s.periodesLibres.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {s.periodesLibres.slice(0, 4).map((p, i) => (
                      <span
                        key={`${p.heureDebut}-${p.heureFin}-${i}`}
                        className="px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200"
                      >
                        {p.label || `${p.heureDebut} → ${p.heureFin}`}
                      </span>
                    ))}
                    {s.periodesLibres.length > 4 && (
                      <span className="px-2 py-0.5 rounded text-[11px] text-ink/40">
                        +{s.periodesLibres.length - 4}
                      </span>
                    )}
                  </div>
                )}

                {s.plusLongueDureeMinutes > 0 && (
                  <p className="mt-1.5 text-[11px] text-ink/45">
                    Plus long bloc continu : {formatDuree(s.plusLongueDureeMinutes)}
                  </p>
                )}

                {s.motifIndisponibilite && (
                  <p className="mt-2 text-[11px] text-ink/50 leading-snug">{s.motifIndisponibilite}</p>
                )}

                {s.nombreEquipements > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {s.equipments.slice(0, 3).map((eq) => (
                      <span
                        key={eq.id}
                        className="px-2 py-0.5 rounded text-[11px] border bg-signal/10 text-heading/70 border-signal/20"
                      >
                        {eq.nom}
                      </span>
                    ))}
                    {s.nombreEquipements > 3 && (
                      <span className="px-2 py-0.5 rounded text-[11px] text-ink/40">
                        +{s.nombreEquipements - 3}
                      </span>
                    )}
                  </div>
                )}
              </div>

              <div className="flex items-center gap-2 px-4 py-3 border-t border-heading/10 bg-heading/[0.015]">
                <button
                  onClick={() => openDetails(s)}
                  className="inline-flex items-center justify-center gap-1.5 flex-1 px-3 py-2 text-xs font-medium text-heading border border-heading/20 rounded-lg hover:bg-heading/5 transition-colors"
                >
                  <IconEye className="w-3.5 h-3.5" /> Voir les détails
                </button>
                {st.canReserve && (
                  <button
                    onClick={() => handleReserve(s)}
                    className={`inline-flex items-center justify-center gap-1.5 flex-1 px-3 py-2 text-xs font-semibold text-white rounded-lg transition-colors shadow-sm ${
                      st.key === 'EN_ATTENTE' ? 'bg-amber-500 hover:bg-amber-500/90' : 'bg-signal hover:bg-signal/90'
                    }`}
                  >
                    <IconBookmark className="w-3.5 h-3.5" /> {st.actionLabel}
                  </button>
                )}
              </div>
            </div>
            )
          })}
        </div>
      )}

      {searched && !loading && resultCount > 0 && viewMode === 'table' && (
        <div className="bg-surface border border-heading/10 rounded-xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-heading/10 bg-heading/[0.035]">
                <th className="text-left px-4 py-3 font-medium text-heading/70">Espace</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Type</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Capacité</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Bâtiment / Étage</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Équipements</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Disponibilité</th>
                <th className="text-left px-4 py-3 font-medium text-heading/70">Statut</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {results.map((s) => {
                const st = availStatus(s)
                return (
                <tr key={s.spaceId} className="border-b border-ink/5 last:border-0 hover:bg-heading/[0.02]">
                  <td className="px-4 py-3">
                    <span className="font-medium text-ink">{s.nom}</span>
                    <span className="block text-xs font-mono text-heading/50">{s.code}</span>
                  </td>
                  <td className="px-4 py-3 text-ink/70">{spaceTypeLabel(s.type)}</td>
                  <td className="px-4 py-3 text-ink/70">{s.capacite ?? '—'}</td>
                  <td className="px-4 py-3 text-ink/70">{s.buildingNom} / {s.floorNom}</td>
                  <td className="px-4 py-3 text-ink/70">
                    {s.nombreEquipements}
                    {s.equipementsCorrespondants > 0 && (
                      <span className="text-emerald-700"> ({s.equipementsCorrespondants} ✓)</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-heading-soft whitespace-nowrap font-medium">
                    {availAffichage(s) || '—'}
                    {s.plusLongueDureeMinutes > 0 && (
                      <span className="block text-[11px] font-normal text-ink/40">
                        Plus long bloc : {formatDuree(s.plusLongueDureeMinutes)}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border ${st.badgeClass}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${st.dotClass}`} /> {st.label}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => openDetails(s)}
                        className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                      >
                        Détails
                      </button>
                      {st.canReserve && (
                        <button
                          onClick={() => handleReserve(s)}
                          className={`px-3 py-1.5 text-xs font-semibold text-white rounded-lg transition-colors ${
                            st.key === 'EN_ATTENTE' ? 'bg-amber-500 hover:bg-amber-500/90' : 'bg-signal hover:bg-signal/90'
                          }`}
                        >
                          {st.actionLabel}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <AvailableSpaceDetailsModal
        open={detailsModal.open}
        space={detailsModal.space}
        onClose={closeDetails}
        onReserve={handleReserve}
      />

      <ReservationFormModal
        open={reservationModal.open}
        mode="create"
        isAdmin={isAdmin}
        currentUser={user}
        users={users}
        pedagogical={isRp}
        programs={programs}
        groups={groups}
        initialData={{
          spaceId: reservationModal.spaceId,
          date: reservationModal.date,
          heureDebut: reservationModal.heureDebut,
          heureFin: reservationModal.heureFin,
        }}
        minTime={reservationModal.minTime}
        maxTime={reservationModal.maxTime}
        periodes={reservationModal.periodes}
        creneaux={reservationModal.creneaux}
        minSlotMinutes={minSlotMinutes}
        openingTime={meta?.heureOuverture || searchWindow?.heureOuverture || ''}
        closingTime={meta?.heureFermeture || searchWindow?.heureFermeture || ''}
        spaces={spaces}
        onClose={closeReservation}
        onSuccess={handleReservationSuccess}
      />
    </div>
  )
}