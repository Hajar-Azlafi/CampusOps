import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  fetchReservations,
  getMyReservations,
  searchReservations,
  getReservationsByPeriod,
  approveReservation,
  rejectReservation,
  cancelReservation,
} from '../api/reservationsApi'
import { fetchSpaces } from '../api/spacesApi'
import { useAuth } from '../context/AuthContext'
import { useAcademicYear } from '../context/AcademicYearContext'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { RESERVATION_TYPES, reservationTypeLabel } from '../constants/reservationTypes'
import { RESERVATION_STATUSES, reservationStatusLabel } from '../constants/reservationStatuses'
import { reservationPriorityLabel } from '../constants/reservationPriorities'
import { roleLabel } from '../constants/roles'
import { ReservationStatusBadge, PriorityBadge } from '../components/Badge'
import ReservationFormModal from '../components/ReservationFormModal'
import ReservationDetailsModal from '../components/ReservationDetailsModal'
import ConfirmDialog from '../components/ConfirmDialog'
import Pagination from '../components/Pagination'

const BOOKING_ROLES = ['ADMIN', 'ENSEIGNANT', 'RESPONSABLE_CLUB']
// La validation/refus des demandes est reservee a l'administration. Le
// Responsable pedagogique cree des demandes mais ne peut jamais approuver
// (y compris les siennes) : la decision finale reste a l'ADMIN.
const APPROVAL_ROLES = ['ADMIN']
const TERMINAL_STATUSES = ['CANCELLED', 'REJECTED', 'COMPLETED']

// Renvoie le lundi de la semaine contenant la date fournie (format yyyy-MM-dd)
function mondayOf(dateStr) {
  const d = dateStr ? new Date(dateStr + 'T00:00:00') : new Date()
  const day = (d.getDay() + 6) % 7 // 0 = lundi
  d.setDate(d.getDate() - day)
  return d
}

function toISODate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function addDays(d, n) {
  const c = new Date(d)
  c.setDate(c.getDate() + n)
  return c
}

const DAY_LABELS = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche']

export default function ReservationsPage() {
  const { user } = useAuth()
  const { selectedYearId } = useAcademicYear()
  // Formats de date et d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDate, formatHeure } = useSettings()
  const canApprove = APPROVAL_ROLES.includes(user?.role)

  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [spaces, setSpaces] = useState([])

  const [view, setView] = useState('list') // 'list' | 'calendar'
  const [keyword, setKeyword] = useState('')
  const [statutFilter, setStatutFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  // Un enseignant ou un responsable de club ne doit voir que ses propres
  // demandes : le champ de portee est fixe sur 'me' et non modifiable.
  const [scopeFilter, setScopeFilter] = useState(canApprove ? 'all' : 'me')

  const [page, setPage] = useState(1)
  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(reservations, page)

  // Vue calendrier
  const [weekStart, setWeekStart] = useState(() => toISODate(mondayOf()))
  const [weekReservations, setWeekReservations] = useState([])
  const [weekLoading, setWeekLoading] = useState(false)

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', reservation: null })
  const [detailsModal, setDetailsModal] = useState({ open: false, reservation: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, reservation: null })
  const [actionLoading, setActionLoading] = useState(false)

  const loadSpaces = useCallback(async () => {
    try {
      const sp = await fetchSpaces({ actif: true })
      setSpaces(sp)
    } catch {
      // silencieux
    }
  }, [])

  const loadReservations = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchReservations(keyword.trim(), selectedYearId)
      } else if (scopeFilter === 'me') {
        data = await getMyReservations()
      } else {
        data = await fetchReservations({ statut: statutFilter || undefined, academicYearId: selectedYearId })
      }
      // Filtres appliqués côté client lorsqu'ils ne sont pas portés par la requête
      if (statutFilter && (keyword.trim() || scopeFilter === 'me')) {
        data = data.filter((r) => r.statut === statutFilter)
      }
      if (typeFilter) {
        data = data.filter((r) => r.type === typeFilter)
      }
      setReservations(data)
      setPage(1)
    } catch {
      setErrorMsg('Impossible de charger les réservations')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statutFilter, typeFilter, scopeFilter, selectedYearId])

  const loadWeek = useCallback(async () => {
    setWeekLoading(true)
    try {
      const start = new Date(weekStart + 'T00:00:00')
      const end = addDays(start, 6)
      const data = await getReservationsByPeriod(weekStart, toISODate(end))
      setWeekReservations(data)
    } catch {
      setWeekReservations([])
    } finally {
      setWeekLoading(false)
    }
  }, [weekStart])

  useEffect(() => {
    loadSpaces()
    loadReservations()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadReservations()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statutFilter, typeFilter, scopeFilter, selectedYearId])

  useEffect(() => {
    if (view === 'calendar') loadWeek()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, weekStart])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadReservations()
  }

  const openEdit = (reservation) => setFormModal({ open: true, mode: 'edit', reservation })
  const closeForm = () => setFormModal({ open: false, mode: 'create', reservation: null })
  const handleFormSuccess = () => {
    closeForm()
    loadReservations()
    if (view === 'calendar') loadWeek()
  }

  const openDetails = (reservation) => setDetailsModal({ open: true, reservation })
  const closeDetails = () => setDetailsModal({ open: false, reservation: null })

  const askAction = (type, reservation) => setConfirmDialog({ open: true, type, reservation })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, reservation: null })

  const handleConfirm = async () => {
    const { type, reservation } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'approve') await approveReservation(reservation.id)
      else if (type === 'reject') await rejectReservation(reservation.id)
      else if (type === 'cancel') await cancelReservation(reservation.id)
      closeConfirm()
      loadReservations()
      if (view === 'calendar') loadWeek()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || "L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    approve: {
      title: 'Valider la réservation',
      message: `Confirmer la validation de la réservation de "${confirmDialog.reservation?.spaceNom}" ?`,
      confirmLabel: 'Valider',
      danger: false,
    },
    reject: {
      title: 'Refuser la réservation',
      message: `Confirmer le refus de la réservation de "${confirmDialog.reservation?.spaceNom}" ?`,
      confirmLabel: 'Refuser',
      danger: true,
    },
    cancel: {
      title: 'Annuler la réservation',
      message: `Voulez-vous annuler la réservation de "${confirmDialog.reservation?.spaceNom}" ?`,
      confirmLabel: 'Annuler la réservation',
      danger: true,
    },
  }[confirmDialog.type] || {}

  const canEdit = (r) => (r.userId === user?.id || user?.role === 'ADMIN') && !TERMINAL_STATUSES.includes(r.statut)
  const canCancel = (r) => (r.userId === user?.id || user?.role === 'ADMIN') && !TERMINAL_STATUSES.includes(r.statut)
  const canDecide = (r) => canApprove && r.statut === 'PENDING'

  const weekDays = useMemo(() => {
    const start = new Date(weekStart + 'T00:00:00')
    return Array.from({ length: 7 }, (_, i) => {
      const d = addDays(start, i)
      const iso = toISODate(d)
      return {
        label: DAY_LABELS[i],
        iso,
        items: weekReservations
          .filter((r) => r.date === iso)
          .sort((a, b) => a.heureDebut.localeCompare(b.heureDebut)),
      }
    })
  }, [weekStart, weekReservations])

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Réservations</h1>
          <p className="text-sm text-ink/50 mt-1">{reservations.length} réservation(s)</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-ink/15 overflow-hidden">
            <button
              onClick={() => setView('list')}
              className={`px-3 py-2 text-sm font-medium transition-colors ${view === 'list' ? 'bg-blueprint-800 text-white' : 'text-ink/60 hover:bg-ink/5'}`}
            >
              Liste
            </button>
            <button
              onClick={() => setView('calendar')}
              className={`px-3 py-2 text-sm font-medium transition-colors ${view === 'calendar' ? 'bg-blueprint-800 text-white' : 'text-ink/60 hover:bg-ink/5'}`}
            >
              Calendrier
            </button>
          </div>
          {/* La creation d'une reservation passe uniquement par "Rechercher & Reserver" :
              aucun bouton de creation directe ici, pour tous les roles y compris l'admin. */}
        </div>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      {view === 'list' && (
        <>
          <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
            <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Rechercher par motif, espace ou utilisateur..."
                className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
              <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
                Rechercher
              </button>
            </form>

           {canApprove && (
              <select
                value={scopeFilter}
                onChange={(e) => setScopeFilter(e.target.value)}
                className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              >
                <option value="all">Toutes les réservations</option>
                <option value="me">Mes réservations</option>
              </select>
            )}

            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Tous les types</option>
              {RESERVATION_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>

            <select
              value={statutFilter}
              onChange={(e) => setStatutFilter(e.target.value)}
              className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Tous les statuts</option>
              {RESERVATION_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>

          <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-ink/10 bg-ink/[0.02]">
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Date</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Horaire</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Espace</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Demandeur</th>
                  {canApprove && <th className="text-left px-4 py-3 font-medium text-ink/50">Rôle</th>}
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Motif</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Priorité</th>
                  <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr><td colSpan={9} className="text-center py-10 text-ink/40">Chargement...</td></tr>
                )}
                {!loading && reservations.length === 0 && (
                  <tr><td colSpan={9} className="text-center py-10 text-ink/40">Aucune réservation trouvée</td></tr>
                )}
                {!loading && pageItems.map((r) => (
                  <tr key={r.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                    <td className="px-4 py-3 text-ink/80 whitespace-nowrap align-top">{formatDate(r.date)}</td>
                    <td className="px-4 py-3 font-mono text-ink/70 whitespace-nowrap align-top">{formatHeure(r.heureDebut)} — {formatHeure(r.heureFin)}</td>
                    <td className="px-4 py-3 text-ink/70 align-top min-w-[160px]">{r.spaceCode} — {r.spaceNom}</td>
                    <td className="px-4 py-3 text-ink/70 align-top">{reservationTypeLabel(r.type)}</td>
                    <td className="px-4 py-3 text-ink/70 align-top min-w-[140px]">{r.userNomComplet}</td>
                    {canApprove && <td className="px-4 py-3 text-ink/70 align-top">{roleLabel(r.userRole)}</td>}
                    <td className="px-4 py-3 text-ink/70 align-top min-w-[220px] max-w-[320px] whitespace-normal break-words">{r.motif}</td>
                    <td className="px-4 py-3"><PriorityBadge priority={r.priorite} label={reservationPriorityLabel(r.priorite)} /></td>
                    <td className="px-4 py-3"><ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} /></td>
                    <td className="px-4 py-3 text-right whitespace-nowrap align-top">
                      <button
                        onClick={() => openDetails(r)}
                        className="px-3 py-1.5 text-xs font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
                      >
                        Détails
                      </button>
                      {canDecide(r) && (
                        <>
                          <button
                            onClick={() => askAction('approve', r)}
                            className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                          >
                            Valider
                          </button>
                          <button
                            onClick={() => askAction('reject', r)}
                            className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                          >
                            Refuser
                          </button>
                        </>
                      )}
                      {canEdit(r) && (
                        <button
                          onClick={() => openEdit(r)}
                          className="ml-2 px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                        >
                          Modifier
                        </button>
                      )}
                      {canCancel(r) && (
                        <button
                          onClick={() => askAction('cancel', r)}
                          className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                        >
                          Annuler
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {!loading && afficher && (
            <Pagination page={pageAffichee} totalPages={totalPages} onChange={setPage} />
          )}
        </>
      )}

      {view === 'calendar' && (
        <>
          <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-wrap items-center gap-3">
            <button
              onClick={() => setWeekStart(toISODate(addDays(new Date(weekStart + 'T00:00:00'), -7)))}
              className="px-3 py-2 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
            >
              ← Semaine précédente
            </button>
            <div className="flex items-center gap-2">
              <label className="text-sm text-ink/60">Semaine du</label>
              <input
                type="date"
                value={weekStart}
                onChange={(e) => setWeekStart(toISODate(mondayOf(e.target.value)))}
                className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <button
              onClick={() => setWeekStart(toISODate(addDays(new Date(weekStart + 'T00:00:00'), 7)))}
              className="px-3 py-2 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
            >
              Semaine suivante →
            </button>
            <button
              onClick={() => setWeekStart(toISODate(mondayOf()))}
              className="px-3 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
            >
              Cette semaine
            </button>
          </div>

          {weekLoading ? (
            <p className="text-sm text-ink/40 py-10 text-center">Chargement du calendrier...</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-7 gap-3">
              {weekDays.map((day) => (
                <div key={day.iso} className="bg-surface border border-ink/10 rounded-xl p-3 min-h-[120px]">
                  <div className="mb-2">
                    <p className="text-sm font-medium text-ink">{day.label}</p>
                    <p className="font-mono text-[11px] text-ink/40">{day.iso}</p>
                  </div>
                  <div className="space-y-2">
                    {day.items.length === 0 && (
                      <p className="text-xs text-ink/30">—</p>
                    )}
                    {day.items.map((r) => (
                      <button
                        key={r.id}
                        onClick={() => openDetails(r)}
                        className="w-full text-left rounded-lg border border-ink/10 hover:border-signal/40 hover:bg-signal/5 px-2 py-1.5 transition-colors"
                      >
                        <p className="font-mono text-[11px] text-ink/60">{formatHeure(r.heureDebut)}—{formatHeure(r.heureFin)}</p>
                        <p className="text-xs font-medium text-ink truncate">{r.spaceCode}</p>
                        <p className="text-[11px] text-ink/50 truncate">{reservationTypeLabel(r.type)}</p>
                        <div className="mt-1">
                          <ReservationStatusBadge status={r.statut} label={reservationStatusLabel(r.statut)} />
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <ReservationFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.reservation}
        spaces={spaces}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <ReservationDetailsModal
        open={detailsModal.open}
        reservation={detailsModal.reservation}
        onClose={closeDetails}
      />

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmContent.title}
        message={confirmContent.message}
        confirmLabel={confirmContent.confirmLabel}
        danger={confirmContent.danger}
        loading={actionLoading}
        onConfirm={handleConfirm}
        onCancel={closeConfirm}
      />
    </div>
  )
}
