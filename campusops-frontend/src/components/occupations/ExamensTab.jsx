import { useEffect, useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchExamens, deleteExamen } from '../../api/examensApi'
import { useAcademicYear } from '../../context/AcademicYearContext'
import { useSettings } from '../../context/SettingsContext'
import { usePagination } from '../../hooks/usePagination'
import ExamenFormModal from '../ExamenFormModal'
import ConfirmDialog from '../ConfirmDialog'
import Pagination from '../Pagination'

// Onglet « Planning examens » du module « Occupation supplémentaire ».
//
// Un examen est une occupation supplémentaire d'un espace, avec un contexte
// pédagogique complet (matière, promotion, session, créneau officiel) : il garde
// donc son propre formulaire et son propre import, mais il partage la table et le
// moteur central de disponibilité avec les soutenances et les occupations
// « autre » — une salle occupée par un examen n'est jamais proposée comme libre.
//
// La liste est bornée au périmètre du demandeur côté backend : ADMIN → tous les
// examens ; responsable pédagogique → uniquement ceux de ses filières. Les
// options de filtre sont donc dérivées du résultat déjà scopé (aucun appel
// supplémentaire).

function distinctOptions(list, idKey, labelKey) {
  const seen = new Map()
  for (const item of list) {
    const id = item[idKey]
    if (id != null && !seen.has(id)) seen.set(id, item[labelKey])
  }
  return [...seen.entries()].map(([value, label]) => ({ value, label }))
}

/**
 * « mar. 01/09/2026 » : abréviation du jour, puis la date au format configuré
 * dans Paramètres > Affichage (§10). Le jour de la semaine est un repère de
 * lecture — il reste en français quel que soit le motif retenu.
 */
function formatExamDate(iso, formatDate) {
  if (!iso) return '—'
  const d = new Date(`${iso}T00:00:00`)
  const jour = Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleDateString('fr-FR', { weekday: 'short' })
  return [jour, formatDate(iso)].filter(Boolean).join(' ')
}

export default function ExamensTab() {
  const navigate = useNavigate()
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDate, formatHeure } = useSettings()
  const [examens, setExamens] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [page, setPage] = useState(1)

  const [filters, setFilters] = useState({
    sessionId: '', programId: '', promotionId: '', date: '',
  })

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', entity: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, examen: null })
  const [deleteLoading, setDeleteLoading] = useState(false)

  const loadExamens = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchExamens({ academicYearId: selectedYearId })
      setExamens(Array.isArray(data) ? data : [])
      setPage(1)
    } catch {
      setErrorMsg('Impossible de charger les examens.')
    } finally {
      setLoading(false)
    }
  }, [selectedYearId])

  useEffect(() => {
    loadExamens()
  }, [loadExamens])

  const options = useMemo(() => ({
    sessions: distinctOptions(examens, 'sessionId', 'sessionNom'),
    programs: distinctOptions(examens, 'programId', 'programNom'),
    promotions: distinctOptions(examens, 'promotionId', 'promotionNom'),
  }), [examens])

  const filtered = useMemo(() => {
    const list = examens.filter((e) => (
      (!filters.sessionId || String(e.sessionId) === filters.sessionId) &&
      (!filters.programId || String(e.programId) === filters.programId) &&
      (!filters.promotionId || String(e.promotionId) === filters.promotionId) &&
      (!filters.date || String(e.date) === filters.date)
    ))
    // Tri chronologique : par date puis heure de début du créneau.
    return [...list].sort((a, b) => {
      const byDate = String(a.date).localeCompare(String(b.date))
      if (byDate !== 0) return byDate
      return String(a.timeSlotHeureDebut || '').localeCompare(String(b.timeSlotHeureDebut || ''))
    })
  }, [examens, filters])

  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(filtered, page)

  useEffect(() => { setPage(1) }, [filters])

  const setFilter = (key, value) => setFilters((f) => ({ ...f, [key]: value }))
  const resetFilters = () => setFilters({ sessionId: '', programId: '', promotionId: '', date: '' })
  const hasActiveFilter = Object.values(filters).some((v) => v)

  const openCreate = () => setFormModal({ open: true, mode: 'create', entity: null })
  const openEdit = (entity) => setFormModal({ open: true, mode: 'edit', entity })
  const closeForm = () => setFormModal({ open: false, mode: 'create', entity: null })

  const handleFormSuccess = () => {
    closeForm()
    loadExamens()
  }

  const handleDelete = async () => {
    const examen = confirmDialog.examen
    if (!examen) return
    setDeleteLoading(true)
    setErrorMsg('')
    try {
      await deleteExamen(examen.id)
      setConfirmDialog({ open: false, examen: null })
      await loadExamens()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'La suppression a échoué, veuillez réessayer.')
      setConfirmDialog({ open: false, examen: null })
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
      {/* Barre d'actions de l'onglet : le titre du module est porté par la page. */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
        <div>
          <h2 className="font-display text-lg font-semibold text-ink">Examens programmés</h2>
          <p className="text-sm text-ink/50 mt-0.5">
            {filtered.length} examen(s)
            {hasActiveFilter && examens.length !== filtered.length ? ` sur ${examens.length}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => navigate('/occupations/import?onglet=examens')}
            className="px-4 py-2.5 border border-heading/25 text-heading hover:bg-heading/5 text-sm font-medium rounded-lg transition-colors"
          >
            Importer un planning
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            + Programmer un examen
          </button>
        </div>
      </div>

      {/* Filtres : session / filière / promotion / date. */}
      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
        <div className="flex flex-wrap gap-3">
          {filterSelect('sessionId', 'Toutes les sessions', options.sessions)}
          {filterSelect('programId', 'Toutes les filières', options.programs)}
          {filterSelect('promotionId', 'Toutes les promotions', options.promotions)}
          <input
            type="date"
            value={filters.date}
            onChange={(e) => setFilter('date', e.target.value)}
            className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Date</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Créneau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Matière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Audience</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Salle</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Session</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">
                {examens.length === 0
                  ? "Aucun examen. Cliquez sur « Programmer un examen » pour en créer un."
                  : 'Aucun examen ne correspond aux filtres.'}
              </td></tr>
            )}
            {!loading && pageItems.map((e) => (
              <tr key={e.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015] align-top">
                <td className="px-4 py-3 text-ink whitespace-nowrap capitalize">{formatExamDate(e.date, formatDate)}</td>
                <td className="px-4 py-3 text-ink/70 whitespace-nowrap">
                  {formatHeure(e.timeSlotHeureDebut)} — {formatHeure(e.timeSlotHeureFin)}
                </td>
                <td className="px-4 py-3">
                  <span className="font-medium text-ink">{e.moduleNom}</span>
                  <span className="block text-xs text-ink/40">{e.programNom}</span>
                </td>
                <td className="px-4 py-3 text-ink/70">
                  {e.promotionNom}
                  <span className="block text-xs text-ink/40">
                    {e.groupNom || 'Toute la promotion'}
                  </span>
                </td>
                <td className="px-4 py-3 text-ink/70">
                  {e.spaceCode ? `${e.spaceCode} — ${e.spaceNom}` : e.spaceNom || '—'}
                </td>
                <td className="px-4 py-3">
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-heading/5 text-heading border border-heading/15">
                    {e.sessionNom}
                  </span>
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(e)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  <button
                    onClick={() => setConfirmDialog({ open: true, examen: e })}
                    className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                  >
                    Supprimer
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!loading && afficher && (
        <Pagination page={pageAffichee} totalPages={totalPages} onChange={setPage} />
      )}

      <ExamenFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.entity}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <ConfirmDialog
        open={confirmDialog.open}
        title="Supprimer l'examen"
        message={
          confirmDialog.examen
            ? `Voulez-vous supprimer l'examen de « ${confirmDialog.examen.moduleNom} » `
              + `du ${formatExamDate(confirmDialog.examen.date, formatDate)} `
              + `(${confirmDialog.examen.sessionNom}) ? Cette action est définitive.`
            : ''
        }
        confirmLabel="Supprimer"
        danger
        loading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setConfirmDialog({ open: false, examen: null })}
      />
    </div>
  )
}
