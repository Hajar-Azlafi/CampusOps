import { useEffect, useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchOccupations, deleteOccupation } from '../../api/occupationsApi'
import { useAcademicYear } from '../../context/AcademicYearContext'
import { useSettings } from '../../context/SettingsContext'
import { usePagination } from '../../hooks/usePagination'
import {
  OCCUPATION_CATEGORIES,
  occupationTabSlug,
  occupationTypesOf,
} from '../../constants/occupationTypes'
import OccupationFormModal from './OccupationFormModal'
import ConfirmDialog from '../ConfirmDialog'
import Pagination from '../Pagination'

// Liste des occupations supplémentaires GÉNÉRIQUES d'une catégorie : soutenances
// (onglet « Planning soutenances ») ou occupations ponctuelles diverses (onglet
// « Autre »). Un SEUL composant sert les deux onglets : mêmes endpoints, même
// DTO, mêmes règles — seuls les libellés et deux détails d'affichage changent.
// C'est la traduction, côté interface, de l'exigence « pas trois systèmes
// indépendants ».
//
// La liste est bornée au périmètre du demandeur côté backend : ADMIN → tout ;
// responsable pédagogique → uniquement ses filières (une occupation « autre »
// sans filière reste réservée à l'administrateur).

const CONFIG = {
  [OCCUPATION_CATEGORIES.SOUTENANCE]: {
    heading: 'Soutenances programmées',
    unit: 'soutenance(s)',
    createLabel: '+ Programmer une soutenance',
    emptyAll: 'Aucune soutenance. Cliquez sur « Programmer une soutenance » pour en créer une.',
    emptyFiltered: 'Aucune soutenance ne correspond aux filtres.',
    deleteTitle: 'Supprimer la soutenance',
    loadError: 'Impossible de charger les soutenances.',
  },
  [OCCUPATION_CATEGORIES.AUTRE]: {
    heading: 'Occupations enregistrées',
    unit: 'occupation(s)',
    createLabel: '+ Ajouter une occupation',
    emptyAll: 'Aucune occupation. Cliquez sur « Ajouter une occupation » pour en créer une.',
    emptyFiltered: 'Aucune occupation ne correspond aux filtres.',
    deleteTitle: "Supprimer l'occupation",
    loadError: 'Impossible de charger les occupations.',
  },
}

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
function formatOccupationDate(iso, formatDate) {
  if (!iso) return '—'
  const d = new Date(`${iso}T00:00:00`)
  const jour = Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleDateString('fr-FR', { weekday: 'short' })
  return [jour, formatDate(iso)].filter(Boolean).join(' ')
}

/** Rattachement pédagogique affichable : le plus précis d'abord. */
function audienceLabel(occupation) {
  if (occupation.groupNom) return occupation.groupNom
  if (occupation.promotionNom) return occupation.promotionNom
  if (occupation.programNom) return 'Toute la filière'
  return 'Tout l’établissement'
}

export default function OccupationsTab({ categorie }) {
  const navigate = useNavigate()
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDate, formatHeure } = useSettings()
  const config = CONFIG[categorie]
  // La catégorie « autre » regroupe plusieurs types (événement, réunion,
  // conférence...) : le filtre par type n'a de sens que dans ce cas.
  const showType = occupationTypesOf(categorie).length > 1

  const [occupations, setOccupations] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [filters, setFilters] = useState({ type: '', programId: '', date: '' })
  const [page, setPage] = useState(1)

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', entity: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, occupation: null })
  const [deleteLoading, setDeleteLoading] = useState(false)

  const loadOccupations = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchOccupations({ categorie })
      setOccupations(Array.isArray(data) ? data : [])
      setPage(1)
    } catch {
      setErrorMsg(config.loadError)
    } finally {
      setLoading(false)
    }
  }, [categorie, config.loadError])

  useEffect(() => {
    loadOccupations()
  }, [loadOccupations])

  // Changer d'onglet réinitialise les filtres : ils ne portent pas sur les mêmes
  // valeurs d'une catégorie à l'autre.
  useEffect(() => {
    setFilters({ type: '', programId: '', date: '' })
  }, [categorie])

  const options = useMemo(() => ({
    types: distinctOptions(occupations, 'type', 'typeLibelle'),
    programs: distinctOptions(occupations, 'programId', 'programNom'),
  }), [occupations])

  const filtered = useMemo(() => {
    const list = occupations.filter((o) => (
      // Cadrage par l'année sélectionnée dans l'en-tête. Une occupation non
      // rattachée à une année reste visible : la masquer la rendrait
      // introuvable depuis l'interface.
      (!selectedYearId || o.academicYearId == null
        || String(o.academicYearId) === String(selectedYearId)) &&
      (!filters.type || String(o.type) === filters.type) &&
      (!filters.programId || String(o.programId) === filters.programId) &&
      (!filters.date || String(o.date) === filters.date)
    ))
    // Tri chronologique : par date puis heure de début.
    return [...list].sort((a, b) => {
      const byDate = String(a.date).localeCompare(String(b.date))
      if (byDate !== 0) return byDate
      return String(a.heureDebut || '').localeCompare(String(b.heureDebut || ''))
    })
  }, [occupations, filters, selectedYearId])

  const visibleCount = useMemo(() => occupations.filter(
    (o) => !selectedYearId || o.academicYearId == null
      || String(o.academicYearId) === String(selectedYearId),
  ).length, [occupations, selectedYearId])

  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(filtered, page)

  useEffect(() => { setPage(1) }, [filters])

  const setFilter = (key, value) => setFilters((f) => ({ ...f, [key]: value }))
  const resetFilters = () => setFilters({ type: '', programId: '', date: '' })
  const hasActiveFilter = Object.values(filters).some((v) => v)

  const openCreate = () => setFormModal({ open: true, mode: 'create', entity: null })
  const openEdit = (entity) => setFormModal({ open: true, mode: 'edit', entity })
  const closeForm = () => setFormModal({ open: false, mode: 'create', entity: null })

  const handleFormSuccess = () => {
    closeForm()
    loadOccupations()
  }

  const handleDelete = async () => {
    const occupation = confirmDialog.occupation
    if (!occupation) return
    setDeleteLoading(true)
    setErrorMsg('')
    try {
      await deleteOccupation(occupation.id)
      setConfirmDialog({ open: false, occupation: null })
      await loadOccupations()
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'La suppression a échoué, veuillez réessayer.')
      setConfirmDialog({ open: false, occupation: null })
    } finally {
      setDeleteLoading(false)
    }
  }

  const columnCount = showType ? 7 : 6

  return (
    <div>
      {/* Barre d'actions de l'onglet : le titre du module est porté par la page. */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
        <div>
          <h2 className="font-display text-lg font-semibold text-ink">{config.heading}</h2>
          <p className="text-sm text-ink/50 mt-0.5">
            {filtered.length} {config.unit}
            {hasActiveFilter && visibleCount !== filtered.length ? ` sur ${visibleCount}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => navigate(`/occupations/import?onglet=${occupationTabSlug(categorie)}`)}
            className="px-4 py-2.5 border border-heading/25 text-heading hover:bg-heading/5 text-sm font-medium rounded-lg transition-colors"
          >
            Importer un planning
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            {config.createLabel}
          </button>
        </div>
      </div>

      {/* Filtres : type (catégorie « autre » seulement) / filière / date. */}
      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
        <div className="flex flex-wrap gap-3">
          {showType && (
            <select
              value={filters.type}
              onChange={(e) => setFilter('type', e.target.value)}
              className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Tous les types</option>
              {options.types.map((o) => (
                <option key={o.value} value={String(o.value)}>{o.label}</option>
              ))}
            </select>
          )}
          <select
            value={filters.programId}
            onChange={(e) => setFilter('programId', e.target.value)}
            className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          >
            <option value="">Toutes les filières</option>
            {options.programs.map((o) => (
              <option key={o.value} value={String(o.value)}>{o.label}</option>
            ))}
          </select>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Horaire</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Intitulé</th>
              {showType && (
                <th className="text-left px-4 py-3 font-medium text-ink/50">Type</th>
              )}
              <th className="text-left px-4 py-3 font-medium text-ink/50">Rattachement</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Salle</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={columnCount} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={columnCount} className="text-center py-10 text-ink/40">
                {visibleCount === 0 ? config.emptyAll : config.emptyFiltered}
              </td></tr>
            )}
            {!loading && pageItems.map((o) => (
              <tr key={o.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015] align-top">
                <td className="px-4 py-3 text-ink whitespace-nowrap capitalize">
                  {formatOccupationDate(o.date, formatDate)}
                </td>
                <td className="px-4 py-3 text-ink/70 whitespace-nowrap">
                  {formatHeure(o.heureDebut)} — {formatHeure(o.heureFin)}
                  {o.dureeMinutes != null && (
                    <span className="block text-xs text-ink/40">{o.dureeMinutes} min</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className="font-medium text-ink">{o.intitule || o.typeLibelle}</span>
                  {o.responsable && (
                    <span className="block text-xs text-ink/40">Responsable : {o.responsable}</span>
                  )}
                </td>
                {showType && (
                  <td className="px-4 py-3">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-heading/5 text-heading border border-heading/15">
                      {o.typeLibelle}
                    </span>
                  </td>
                )}
                <td className="px-4 py-3 text-ink/70">
                  {o.programNom || '—'}
                  <span className="block text-xs text-ink/40">{audienceLabel(o)}</span>
                </td>
                <td className="px-4 py-3 text-ink/70">
                  {o.spaceCode ? `${o.spaceCode} — ${o.spaceNom}` : o.spaceNom || '—'}
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(o)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  <button
                    onClick={() => setConfirmDialog({ open: true, occupation: o })}
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

      <OccupationFormModal
        open={formModal.open}
        mode={formModal.mode}
        categorie={categorie}
        initialData={formModal.entity}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <ConfirmDialog
        open={confirmDialog.open}
        title={config.deleteTitle}
        message={
          confirmDialog.occupation
            ? `Voulez-vous supprimer « ${confirmDialog.occupation.intitule
                || confirmDialog.occupation.typeLibelle} » `
              + `du ${formatOccupationDate(confirmDialog.occupation.date, formatDate)} `
              + `(${formatHeure(confirmDialog.occupation.heureDebut)} — `
              + `${formatHeure(confirmDialog.occupation.heureFin)}) ? `
              + 'La salle sera libérée sur ce créneau. Cette action est définitive.'
            : ''
        }
        confirmLabel="Supprimer"
        danger
        loading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setConfirmDialog({ open: false, occupation: null })}
      />
    </div>
  )
}
