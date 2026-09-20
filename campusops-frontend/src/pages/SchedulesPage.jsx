import { useEffect, useState, useCallback } from 'react'
import {
  fetchSchedules,
  searchSchedules,
  getSchedulesByPromotion,
  deactivateSchedule,
  activateSchedule,
} from '../api/schedulesApi'
import { fetchPrograms } from '../api/programsApi'
import { fetchLevels } from '../api/levelsApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchGroups } from '../api/groupsApi'
import { fetchTimeSlots } from '../api/timeSlotsApi'
import { fetchSpaces } from '../api/spacesApi'
import { fetchSemesters } from '../api/semestersApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { weekDayLabel } from '../constants/weekDays'
import { sessionTypeLabel } from '../constants/sessionTypes'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { StatusBadge } from '../components/Badge'
import ScheduleFormModal from '../components/ScheduleFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import Pagination from '../components/Pagination'

export default function SchedulesPage() {
  // Format d'heure de l'établissement (Paramètres > Affichage, §10).
  const { formatHeure } = useSettings()
  const [schedules, setSchedules] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  // Listes de référence pour la modale et les filtres
  const [programs, setPrograms] = useState([])
  const [levels, setLevels] = useState([])
  const [promotions, setPromotions] = useState([])
  const [groups, setGroups] = useState([])
  const [timeSlots, setTimeSlots] = useState([])
  const [spaces, setSpaces] = useState([])
  const [semesters, setSemesters] = useState([])
  const [academicYears, setAcademicYears] = useState([])

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [promotionFilter, setPromotionFilter] = useState('')

  const [page, setPage] = useState(1)
  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(schedules, page)

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', schedule: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, schedule: null })
  const [actionLoading, setActionLoading] = useState(false)

  const loadReferences = useCallback(async () => {
    try {
      const [prog, lvl, promo, grp, ts, sp, sem, ay] = await Promise.all([
        fetchPrograms({ actif: true }),
        fetchLevels({ actif: true }),
        fetchPromotions({ actif: true }),
        fetchGroups({ actif: true }),
        fetchTimeSlots({ actif: true }),
        fetchSpaces({ actif: true }),
        fetchSemesters({ actif: true }),
        fetchAcademicYears({ actif: true }),
      ])
      setPrograms(prog)
      setLevels(lvl)
      setPromotions(promo)
      setGroups(grp)
      setTimeSlots(ts)
      setSpaces(sp)
      setSemesters(sem)
      setAcademicYears(ay)
    } catch {
      // silencieux : les filtres/listes restent vides
    }
  }, [])

  const loadSchedules = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchSchedules(keyword.trim())
      } else if (promotionFilter) {
        data = await getSchedulesByPromotion(Number(promotionFilter))
      } else {
        data = await fetchSchedules({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      if (promotionFilter && (keyword.trim() || statusFilter)) {
        data = data.filter((s) => String(s.promotionId) === String(promotionFilter))
      }
      if (statusFilter && (keyword.trim() || promotionFilter)) {
        data = data.filter((s) => s.actif === (statusFilter === 'active'))
      }
      setSchedules(data)
      setPage(1)
    } catch {
      setErrorMsg("Impossible de charger les emplois du temps")
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, promotionFilter])

  useEffect(() => {
    loadReferences()
    loadSchedules()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadSchedules()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, promotionFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadSchedules()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', schedule: null })
  const openEdit = (schedule) => setFormModal({ open: true, mode: 'edit', schedule })
  const closeForm = () => setFormModal({ open: false, mode: 'create', schedule: null })

  const handleFormSuccess = () => {
    closeForm()
    loadSchedules()
  }

  const askDeactivate = (schedule) => setConfirmDialog({ open: true, type: 'deactivate', schedule })
  const askActivate = (schedule) => setConfirmDialog({ open: true, type: 'activate', schedule })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, schedule: null })

  const handleConfirm = async () => {
    const { type, schedule } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateSchedule(schedule.id)
      } else if (type === 'activate') {
        await activateSchedule(schedule.id)
      }
      closeConfirm()
      loadSchedules()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || "L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver la séance',
      message: `Voulez-vous désactiver la séance de "${confirmDialog.schedule?.matiere}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer la séance',
      message: `Voulez-vous réactiver la séance de "${confirmDialog.schedule?.matiere}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      {/* PLACEHOLDER_BODY */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Emplois du temps</h1>
          <p className="text-sm text-ink/50 mt-1">{schedules.length} séance(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvelle séance
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par matière ou enseignant..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={promotionFilter}
          onChange={(e) => setPromotionFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Toutes les promotions</option>
          {promotions.map((p) => (
            <option key={p.id} value={p.id}>{p.nom}</option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les statuts</option>
          <option value="active">Actifs</option>
          <option value="inactive">Inactifs</option>
        </select>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Jour</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Créneau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Espace</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Promotion / Groupe</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Matière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Enseignant</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Type</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={9} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && schedules.length === 0 && (
              <tr><td colSpan={9} className="text-center py-10 text-ink/40">Aucune séance trouvée</td></tr>
            )}
            {!loading && pageItems.map((s) => (
              <tr key={s.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 text-ink/80">{weekDayLabel(s.jour)}</td>
                <td className="px-4 py-3 font-mono text-ink/70 whitespace-nowrap">{formatHeure(s.timeSlotHeureDebut)} — {formatHeure(s.timeSlotHeureFin)}</td>
                <td className="px-4 py-3 text-ink/70">{s.spaceCode} — {s.spaceNom}</td>
                <td className="px-4 py-3 text-ink/70">{s.promotionNom} / {s.groupNom}</td>
                <td className="px-4 py-3 font-medium text-ink">{s.matiere}</td>
                <td className="px-4 py-3 text-ink/70">{s.enseignant}</td>
                <td className="px-4 py-3 text-ink/70">{s.typeSeanceNom || sessionTypeLabel(s.type)}</td>
                <td className="px-4 py-3"><StatusBadge active={s.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(s)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {s.actif ? (
                    <button
                      onClick={() => askDeactivate(s)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(s)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
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

      <ScheduleFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.schedule}
        programs={programs}
        levels={levels}
        promotions={promotions}
        groups={groups}
        timeSlots={timeSlots}
        spaces={spaces}
        semesters={semesters}
        academicYears={academicYears}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
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
