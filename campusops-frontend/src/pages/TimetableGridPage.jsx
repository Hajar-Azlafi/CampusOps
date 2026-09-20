import { useEffect, useState, useMemo, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getTimetable, getTimetableSeances } from '../api/timetablesApi'
import { deactivateSchedule } from '../api/schedulesApi'
import { fetchTimeSlots } from '../api/timeSlotsApi'
import { fetchSpaces } from '../api/spacesApi'
import { TimetableStatusBadge } from '../components/Badge'
import { timetableStatusLabel } from '../constants/timetableStatuses'
import { sessionTypeLabel } from '../constants/sessionTypes'
import { presenceTypeLabel } from '../constants/presenceTypes'
import { useSettings } from '../context/SettingsContext'
import SeanceFormModal from '../components/SeanceFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import { IconArrowRight, IconDownload } from '../components/icons'

// Grille de consultation (§10) : Lundi–Samedi × créneaux configurables.
// Les jours et les créneaux affichés sont dérivés des séances réelles afin de
// rester génériques (aucun horaire codé en dur, §26). L'ajout / la modification
// manuelle d'une séance reste possible tant que l'emploi du temps n'est pas archivé.
const DAY_LABELS = {
  LUNDI: 'Lundi', MARDI: 'Mardi', MERCREDI: 'Mercredi',
  JEUDI: 'Jeudi', VENDREDI: 'Vendredi', SAMEDI: 'Samedi', DIMANCHE: 'Dimanche',
}
const BASE_DAYS = ['LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI']

// Libellé de la période de validité (§20) à partir des deux bornes facultatives.
// Le motif de date vient des paramètres de l'établissement (Module 11, §10) :
// il est passé en argument pour garder la fonction pure.
function periodeLabel(dateDebut, dateFin, formatDate) {
  const debut = dateDebut ? formatDate(dateDebut) : null
  const fin = dateFin ? formatDate(dateFin) : null
  if (debut && fin) return `${debut} → ${fin}`
  if (debut) return `À partir du ${debut}`
  if (fin) return `Jusqu'au ${fin}`
  return 'Toute l\'année universitaire'
}

// Couleur du bloc séance selon le type (§10). Extensible via SESSION_TYPES.
function seanceTypeStyle(type) {
  switch (type) {
    case 'COURS': return 'border-l-blue-400 bg-blue-50'
    case 'TD': return 'border-l-emerald-400 bg-emerald-50'
    case 'TP': return 'border-l-purple-400 bg-purple-50'
    case 'EXAMEN': return 'border-l-red-400 bg-red-50'
    default: return 'border-l-ink/30 bg-ink/5'
  }
}

function ContextChip({ label, value }) {
  if (!value) return null
  return (
    <div className="flex flex-col">
      <span className="text-xs text-ink/40">{label}</span>
      <span className="text-sm font-medium text-ink">{value}</span>
    </div>
  )
}

export default function TimetableGridPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDate, formatHeure } = useSettings()

  const [header, setHeader] = useState(null)
  const [seances, setSeances] = useState([])
  const [timeSlotOptions, setTimeSlotOptions] = useState([])
  const [spaceOptions, setSpaceOptions] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [seanceModal, setSeanceModal] = useState({ open: false, mode: 'create', data: null })
  const [deleteState, setDeleteState] = useState({ open: false, seance: null })
  const [deleteLoading, setDeleteLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const [head, list] = await Promise.all([
        getTimetable(id),
        getTimetableSeances(id),
      ])
      setHeader(head)
      setSeances((Array.isArray(list) ? list : []).filter((s) => s.actif !== false))
    } catch (err) {
      if (err.response?.status === 403) setErrorMsg("Vous n'avez pas accès à cet emploi du temps.")
      else if (err.response?.status === 404) setErrorMsg('Emploi du temps introuvable.')
      else setErrorMsg("Impossible de charger l'emploi du temps.")
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { load() }, [load])

  // Référentiels pour la modale de séance (chargés une fois, silencieux si échec).
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [slots, spaces] = await Promise.all([
          fetchTimeSlots({ actif: true }),
          fetchSpaces({ actif: true }),
        ])
        if (cancelled) return
        setTimeSlotOptions(Array.isArray(slots) ? slots : [])
        setSpaceOptions(Array.isArray(spaces) ? spaces : [])
      } catch {
        // sans référentiels, la consultation reste possible ; l'ajout sera limité
      }
    })()
    return () => { cancelled = true }
  }, [])

  const editable = header && header.statut !== 'ARCHIVE'

  // Jours affichés : Lundi–Samedi, plus Dimanche uniquement si une séance y tombe.
  const days = useMemo(() => {
    const hasSunday = seances.some((s) => s.jour === 'DIMANCHE')
    return hasSunday ? [...BASE_DAYS, 'DIMANCHE'] : BASE_DAYS
  }, [seances])

  // Créneaux (lignes) : distincts, triés par heure de début.
  const timeSlots = useMemo(() => {
    const map = new Map()
    for (const s of seances) {
      if (s.timeSlotId != null && !map.has(s.timeSlotId)) {
        map.set(s.timeSlotId, {
          id: s.timeSlotId,
          debut: s.timeSlotHeureDebut || '',
          fin: s.timeSlotHeureFin || '',
        })
      }
    }
    return [...map.values()].sort((a, b) => a.debut.localeCompare(b.debut))
  }, [seances])

  // Index (jour + créneau) → séances.
  const cells = useMemo(() => {
    const map = new Map()
    for (const s of seances) {
      const key = `${s.jour}__${s.timeSlotId}`
      if (!map.has(key)) map.set(key, [])
      map.get(key).push(s)
    }
    return map
  }, [seances])

  const openCreate = () => setSeanceModal({ open: true, mode: 'create', data: null })
  const openEdit = (seance) => setSeanceModal({ open: true, mode: 'edit', data: seance })

  const handleSeanceSaved = () => {
    setSeanceModal({ open: false, mode: 'create', data: null })
    load()
  }

  const handleDeleteSeance = async () => {
    if (!deleteState.seance) return
    setDeleteLoading(true)
    try {
      await deactivateSchedule(deleteState.seance.id)
      setDeleteState({ open: false, seance: null })
      load()
    } catch {
      setErrorMsg('La suppression de la séance a échoué.')
      setDeleteState({ open: false, seance: null })
    } finally {
      setDeleteLoading(false)
    }
  }

  return (
    <div>
      <button
        onClick={() => navigate('/timetables')}
        className="inline-flex items-center gap-1.5 text-sm text-ink/60 hover:text-ink mb-4 transition-colors"
      >
        <IconArrowRight className="w-4 h-4 rotate-180" />
        Emplois du temps
      </button>

      {loading && (
        <div className="bg-surface border border-ink/10 rounded-xl p-10 text-center text-ink/40">
          Chargement...
        </div>
      )}

      {!loading && errorMsg && !header && (
        <div className="bg-surface border border-ink/10 rounded-xl p-10 text-center">
          <p className="text-red-700 mb-4">{errorMsg}</p>
          <button
            onClick={() => navigate('/timetables')}
            className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
          >
            Retour à la liste
          </button>
        </div>
      )}

      {!loading && header && (
        <>
          {/* En-tête de contexte (§17) */}
          <div className="bg-surface border border-ink/10 rounded-xl p-5 mb-5">
            <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-3 mb-3">
                  <h1 className="font-display text-2xl font-semibold text-ink">{header.programNom}</h1>
                  <TimetableStatusBadge statut={header.statut} label={timetableStatusLabel(header.statut)} />
                  {header.expire && header.statut !== 'ARCHIVE' && (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                      Expiré
                    </span>
                  )}
                </div>
                <div className="flex flex-wrap gap-x-8 gap-y-3">
                  <ContextChip label="Niveau" value={header.levelNom} />
                  <ContextChip label="Groupe" value={header.groupNom} />
                  <ContextChip label="Semestre" value={header.semesterNom} />
                  <ContextChip label="Année" value={header.academicYearLibelle} />
                  <ContextChip label="Période" value={periodeLabel(header.dateDebut, header.dateFin, formatDate)} />
                  <ContextChip label="Importé par" value={header.importeParNom || '—'} />
                </div>
                <p className="mt-3 text-xs text-ink/45">
                  La période est héritée du semestre : elle se modifie dans la gestion des semestres, pas ici.
                </p>
                {(header.expire || header.statut === 'ARCHIVE') && (
                  <p className="mt-3 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 inline-flex">
                    {header.statut === 'ARCHIVE'
                      ? 'Emploi du temps archivé : les salles de ses séances sont libérées et de nouveau réservables.'
                      : 'Période de validité dépassée : les salles de ses séances sont automatiquement libérées et de nouveau réservables.'}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {editable && (
                  <button
                    onClick={openCreate}
                    className="px-4 py-2.5 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
                  >
                    Ajouter une séance
                  </button>
                )}
                <button
                  disabled
                  title="Export PDF / Excel — disponible prochainement"
                  className="flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-ink/40 border border-ink/15 rounded-lg cursor-not-allowed"
                >
                  <IconDownload className="w-4 h-4" />
                  Exporter
                </button>
              </div>
            </div>
          </div>

          {errorMsg && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
              {errorMsg}
            </p>
          )}

          {/* Grille */}
          {seances.length === 0 ? (
            <div className="bg-surface border border-ink/10 rounded-xl p-10 text-center">
              <p className="text-ink/40 mb-4">Aucune séance enregistrée pour cet emploi du temps.</p>
              {editable && (
                <button
                  onClick={openCreate}
                  className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
                >
                  Ajouter une séance
                </button>
              )}
            </div>
          ) : (
            <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="border-b border-ink/10 bg-ink/[0.02]">
                    <th className="text-left px-4 py-3 font-medium text-ink/50 text-sm w-32 whitespace-nowrap">Créneau</th>
                    {days.map((d) => (
                      <th key={d} className="text-left px-4 py-3 font-medium text-ink/50 text-sm min-w-[9rem]">
                        {DAY_LABELS[d]}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {timeSlots.map((slot) => (
                    <tr key={slot.id} className="border-b border-ink/5 last:border-0 align-top">
                      <td className="px-4 py-3 text-sm font-medium text-ink/70 whitespace-nowrap">
                        {formatHeure(slot.debut)}<span className="text-ink/30"> – </span>{formatHeure(slot.fin)}
                      </td>
                      {days.map((d) => {
                        const items = cells.get(`${d}__${slot.id}`) || []
                        return (
                          <td key={d} className="px-2 py-2 align-top">
                            <div className="flex flex-col gap-2">
                              {items.map((s) => (
                                <div
                                  key={s.id}
                                  title={s.commentaire || undefined}
                                  className={`border-l-4 rounded-md px-3 py-2 ${seanceTypeStyle(s.type)}`}
                                >
                                  <p className="text-sm font-semibold text-ink leading-tight">{s.matiere}</p>
                                  <p className="text-xs text-ink/60 mt-0.5">
                                    {s.typeSeanceNom || sessionTypeLabel(s.type)}{s.enseignant ? ` · ${s.enseignant}` : ''}
                                  </p>
                                  <p className="text-xs text-ink/50 mt-0.5">
                                    {s.typePresence === 'DISTANCIEL'
                                      ? presenceTypeLabel('DISTANCIEL')
                                      : (s.spaceCode || s.spaceNom || presenceTypeLabel('PRESENTIEL'))}
                                  </p>
                                  {editable && (
                                    <div className="flex gap-3 mt-1.5 pt-1.5 border-t border-ink/10">
                                      <button onClick={() => openEdit(s)} className="text-xs font-medium text-heading hover:underline">
                                        Modifier
                                      </button>
                                      <button onClick={() => setDeleteState({ open: true, seance: s })} className="text-xs font-medium text-red-600 hover:underline">
                                        Supprimer
                                      </button>
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        </>
      )}

      <SeanceFormModal
        open={seanceModal.open}
        mode={seanceModal.mode}
        initialData={seanceModal.data}
        timetable={header}
        timeSlots={timeSlotOptions}
        spaces={spaceOptions}
        onClose={() => setSeanceModal({ open: false, mode: 'create', data: null })}
        onSuccess={handleSeanceSaved}
      />

      <ConfirmDialog
        open={deleteState.open}
        title="Supprimer la séance"
        message={
          deleteState.seance
            ? `Supprimer la séance « ${deleteState.seance.matiere} » du ${DAY_LABELS[deleteState.seance.jour] || deleteState.seance.jour} ?`
            : ''
        }
        confirmLabel="Supprimer"
        danger
        loading={deleteLoading}
        onConfirm={handleDeleteSeance}
        onCancel={() => setDeleteState({ open: false, seance: null })}
      />
    </div>
  )
}
