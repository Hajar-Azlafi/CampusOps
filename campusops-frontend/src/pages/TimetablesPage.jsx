import { useEffect, useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchTimetables,
  publishTimetable,
  archiveTimetable,
  reopenTimetable,
  deleteTimetable,
} from '../api/timetablesApi'
import { TIMETABLE_STATUSES, timetableStatusLabel } from '../constants/timetableStatuses'
import { useAcademicYear } from '../context/AcademicYearContext'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { TimetableStatusBadge } from '../components/Badge'
import ConfirmDialog from '../components/ConfirmDialog'
import Pagination from '../components/Pagination'
import { IconUpload, IconDownload } from '../components/icons'

// Options de filtre distinctes dérivées de la liste (déjà limitée au périmètre
// de l'utilisateur côté backend) : un responsable pédagogique ne voit donc que
// ses propres filières/groupes dans les filtres, sans appel supplémentaire.
function distinctOptions(list, idKey, labelKey) {
  const seen = new Map()
  for (const item of list) {
    const id = item[idKey]
    if (id != null && !seen.has(id)) seen.set(id, item[labelKey])
  }
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
}

export default function TimetablesPage() {
  const navigate = useNavigate()
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDateHeure } = useSettings()
  const [timetables, setTimetables] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [page, setPage] = useState(1)

  const [filters, setFilters] = useState({
    academicYearId: '', programId: '', levelId: '',
    groupId: '', semesterId: '', statut: '',
  })

  const [confirmDialog, setConfirmDialog] = useState({ open: false, timetable: null })
  const [actionLoadingId, setActionLoadingId] = useState(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  const loadTimetables = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchTimetables({ academicYearId: selectedYearId })
      setTimetables(Array.isArray(data) ? data : [])
      setPage(1)
    } catch {
      setErrorMsg('Impossible de charger les emplois du temps.')
    } finally {
      setLoading(false)
    }
  }, [selectedYearId])

  useEffect(() => {
    loadTimetables()
  }, [loadTimetables])

  // Listes d'options de filtre (dérivées du résultat scopé).
  const options = useMemo(() => ({
    years: distinctOptions(timetables, 'academicYearId', 'academicYearLibelle'),
    programs: distinctOptions(timetables, 'programId', 'programNom'),
    levels: distinctOptions(timetables, 'levelId', 'levelNom'),
    groups: distinctOptions(timetables, 'groupId', 'groupNom'),
    semesters: distinctOptions(timetables, 'semesterId', 'semesterNom'),
  }), [timetables])

  const filtered = useMemo(() => timetables.filter((t) => (
    (!filters.academicYearId || String(t.academicYearId) === filters.academicYearId) &&
    (!filters.programId || String(t.programId) === filters.programId) &&
    (!filters.levelId || String(t.levelId) === filters.levelId) &&
    (!filters.groupId || String(t.groupId) === filters.groupId) &&
    (!filters.semesterId || String(t.semesterId) === filters.semesterId) &&
    (!filters.statut || t.statut === filters.statut)
  )), [timetables, filters])

  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(filtered, page)

  useEffect(() => { setPage(1) }, [filters])

  const setFilter = (key, value) => setFilters((f) => ({ ...f, [key]: value }))
  const resetFilters = () => setFilters({
    academicYearId: '', programId: '', levelId: '',
    groupId: '', semesterId: '', statut: '',
  })
  const hasActiveFilter = Object.values(filters).some((v) => v)

  const runStatusAction = async (timetable, action) => {
    setActionLoadingId(timetable.id)
    setErrorMsg('')
    try {
      if (action === 'publish') await publishTimetable(timetable.id)
      else if (action === 'archive') await archiveTimetable(timetable.id)
      else if (action === 'reopen') await reopenTimetable(timetable.id)
      await loadTimetables()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || "L'action a échoué, veuillez réessayer.")
    } finally {
      setActionLoadingId(null)
    }
  }

  const handleDelete = async () => {
    const timetable = confirmDialog.timetable
    if (!timetable) return
    setDeleteLoading(true)
    setErrorMsg('')
    try {
      await deleteTimetable(timetable.id)
      setConfirmDialog({ open: false, timetable: null })
      await loadTimetables()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || "La suppression a échoué, veuillez réessayer.")
      setConfirmDialog({ open: false, timetable: null })
    } finally {
      setDeleteLoading(false)
    }
  }

  const filterSelect = (key, label, opts) => (
    <select
      value={filters[key]}
      onChange={(e) => setFilter(key, e.target.value)}
      className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
    >
      <option value="">{label}</option>
      {opts.map((o) => (
        <option key={o.value} value={String(o.value)}>{o.label}</option>
      ))}
    </select>
  )

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Emplois du temps</h1>
          <p className="text-sm text-ink/50 mt-1">
            {filtered.length} emploi(s) du temps
            {hasActiveFilter && timetables.length !== filtered.length ? ` sur ${timetables.length}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => navigate('/timetables/import')}
            title="Choisissez le contexte puis téléchargez le modèle Excel"
            className="flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
          >
            <IconDownload className="w-4 h-4" />
            Télécharger le modèle
          </button>
          <button
            onClick={() => navigate('/timetables/import')}
            className="flex items-center gap-2 px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            <IconUpload className="w-4 h-4" />
            Importer
          </button>
        </div>
      </div>

      {/* Filtres (§17) : année / filière / niveau / groupe / semestre / statut. */}
      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
        <div className="flex flex-wrap gap-3">
          {filterSelect('academicYearId', 'Toutes les années', options.years)}
          {filterSelect('programId', 'Toutes les filières', options.programs)}
          {filterSelect('levelId', 'Tous les niveaux', options.levels)}
          {filterSelect('groupId', 'Tous les groupes', options.groups)}
          {filterSelect('semesterId', 'Tous les semestres', options.semesters)}
          <select
            value={filters.statut}
            onChange={(e) => setFilter('statut', e.target.value)}
            className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          >
            <option value="">Tous les statuts</option>
            {TIMETABLE_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
          {hasActiveFilter && (
            <button
              onClick={resetFilters}
              className="px-3 py-2 text-sm font-medium text-ink/60 hover:bg-ink/5 rounded-lg transition-colors"
            >
              Réinitialiser
            </button>
          )}
        </div>
      </div>

      {errorMsg && (
        <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Groupe</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Semestre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Importé par</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Dernière modification</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={9} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={9} className="text-center py-10 text-ink/40">
                {timetables.length === 0
                  ? "Aucun emploi du temps. Cliquez sur « Importer » pour en créer un."
                  : 'Aucun emploi du temps ne correspond aux filtres.'}
              </td></tr>
            )}
            {!loading && pageItems.map((t) => {
              const busy = actionLoadingId === t.id
              return (
                <tr key={t.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015] align-top">
                  <td className="px-4 py-3 font-medium text-ink">{t.programNom}</td>
                  <td className="px-4 py-3 text-ink/70">{t.levelNom}</td>
                  <td className="px-4 py-3 text-ink/70">
                    {t.groupNom}
                    <span className="block text-xs text-ink/40">{t.nombreSeances} séance(s)</span>
                  </td>
                  <td className="px-4 py-3 text-ink/70">{t.semesterNom}</td>
                  <td className="px-4 py-3 text-ink/70 whitespace-nowrap">{t.academicYearLibelle}</td>
                  <td className="px-4 py-3">
                    <TimetableStatusBadge statut={t.statut} label={timetableStatusLabel(t.statut)} />
                    {t.expire && t.statut !== 'ARCHIVE' && (
                      <span
                        title="Période de validité dépassée : les salles sont libérées."
                        className="mt-1 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200"
                      >
                        Expiré · salles libérées
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-ink/70">{t.importeParNom || '—'}</td>
                  <td className="px-4 py-3 text-ink/60 whitespace-nowrap">{formatDateHeure(t.updatedAt)}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button
                      onClick={() => navigate(`/timetables/${t.id}`)}
                      className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                    >
                      Consulter
                    </button>
                    {t.statut === 'BROUILLON' && (
                      <button
                        onClick={() => runStatusAction(t, 'publish')}
                        disabled={busy}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 disabled:opacity-50 transition-colors"
                      >
                        {busy ? '...' : 'Publier'}
                      </button>
                    )}
                    {t.statut === 'PUBLIE' && (
                      <button
                        onClick={() => runStatusAction(t, 'reopen')}
                        disabled={busy}
                        title="Repasser en brouillon pour corriger (l'emploi du temps n'est plus diffusé)."
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 disabled:opacity-50 transition-colors"
                      >
                        {busy ? '...' : 'Dépublier'}
                      </button>
                    )}
                    {t.statut === 'PUBLIE' && (
                      <button
                        onClick={() => runStatusAction(t, 'archive')}
                        disabled={busy}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 disabled:opacity-50 transition-colors"
                      >
                        {busy ? '...' : 'Archiver'}
                      </button>
                    )}
                    {t.statut === 'ARCHIVE' && (
                      <button
                        onClick={() => runStatusAction(t, 'reopen')}
                        disabled={busy}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 disabled:opacity-50 transition-colors"
                      >
                        {busy ? '...' : 'Rouvrir'}
                      </button>
                    )}
                    <button
                      onClick={() => setConfirmDialog({ open: true, timetable: t })}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Supprimer
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {!loading && afficher && (
        <Pagination page={pageAffichee} totalPages={totalPages} onChange={setPage} />
      )}

      <ConfirmDialog
        open={confirmDialog.open}
        title="Supprimer l'emploi du temps"
        message={
          confirmDialog.timetable
            ? `Voulez-vous supprimer l'emploi du temps de « ${confirmDialog.timetable.groupNom} » `
              + `(${confirmDialog.timetable.semesterNom}) `
              + `et ses ${confirmDialog.timetable.nombreSeances} séance(s) ? Cette action est définitive.`
            : ''
        }
        confirmLabel="Supprimer"
        danger
        loading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setConfirmDialog({ open: false, timetable: null })}
      />
    </div>
  )
}
