import { useEffect, useState, useCallback } from 'react'
import {
  fetchPromotions,
  searchPromotions,
  getPromotionsByProgram,
  getPromotionsByAcademicYear,
  deactivatePromotion,
  activatePromotion,
  deletePromotion,
  getPromotionDeletionImpact,
} from '../api/promotionsApi'
import { fetchPrograms } from '../api/programsApi'
import { fetchLevels } from '../api/levelsApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { StatusBadge } from '../components/Badge'
import PromotionFormModal from '../components/PromotionFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

export default function PromotionsPage() {
  const [promotions, setPromotions] = useState([])
  const [programs, setPrograms] = useState([])
  const [levels, setLevels] = useState([])
  const [academicYears, setAcademicYears] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [programFilter, setProgramFilter] = useState('')
  const [yearFilter, setYearFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', promotion: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, promotion: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadParents = useCallback(async () => {
    try {
      const [programsData, levelsData, academicYearsData] = await Promise.all([
        fetchPrograms({ actif: true }),
        fetchLevels(),
        fetchAcademicYears(),
      ])
      setPrograms(programsData)
      setLevels(levelsData)
      setAcademicYears(academicYearsData)
    } catch {
      // silencieux : les filtres restent vides
    }
  }, [])

  const loadPromotions = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchPromotions(keyword.trim())
      } else if (yearFilter) {
        data = await getPromotionsByAcademicYear(Number(yearFilter))
      } else if (programFilter) {
        data = await getPromotionsByProgram(Number(programFilter))
      } else {
        data = await fetchPromotions({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      // Raffinements côté client pour combiner les filtres
      if (yearFilter) {
        data = data.filter((p) => String(p.academicYearId) === String(yearFilter))
      }
      if (programFilter) {
        data = data.filter((p) => String(p.programId) === String(programFilter))
      }
      if (statusFilter) {
        data = data.filter((p) => p.actif === (statusFilter === 'active'))
      }
      setPromotions(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des promotions')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, programFilter, yearFilter])

  useEffect(() => {
    loadParents()
    loadPromotions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadPromotions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, programFilter, yearFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadPromotions()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', promotion: null })
  const openEdit = (promotion) => setFormModal({ open: true, mode: 'edit', promotion })
  const closeForm = () => setFormModal({ open: false, mode: 'create', promotion: null })

  const handleFormSuccess = () => {
    closeForm()
    loadPromotions()
  }

  const askDeactivate = (promotion) => setConfirmDialog({ open: true, type: 'deactivate', promotion })
  const askActivate = (promotion) => setConfirmDialog({ open: true, type: 'activate', promotion })
  const askDelete = (promotion) => setDeleteTarget(promotion)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, promotion: null })

  const handleConfirm = async () => {
    const { type, promotion } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivatePromotion(promotion.id)
      } else if (type === 'activate') {
        await activatePromotion(promotion.id)
      }
      closeConfirm()
      loadPromotions()
    } catch {
      setErrorMsg("L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver la promotion',
      message: `Voulez-vous désactiver "${confirmDialog.promotion?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer la promotion',
      message: `Voulez-vous réactiver "${confirmDialog.promotion?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Promotions</h1>
          <p className="text-sm text-ink/50 mt-1">{promotions.length} promotion(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvelle promotion
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={yearFilter}
          onChange={(e) => setYearFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Toutes les années</option>
          {academicYears.map((y) => (
            <option key={y.id} value={y.id}>
              {y.libelle}{y.actif ? ' (active)' : ''}
            </option>
          ))}
        </select>

        <select
          value={programFilter}
          onChange={(e) => setProgramFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Toutes les filières</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>{p.nom}</option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les statuts</option>
          <option value="active">Actives</option>
          <option value="inactive">Inactives</option>
        </select>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && promotions.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucune promotion trouvée</td></tr>
            )}
            {!loading && promotions.map((promotion) => (
              <tr key={promotion.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{promotion.nom}</td>
                <td className="px-4 py-3 text-ink/70">{promotion.programNom}</td>
                <td className="px-4 py-3 text-ink/70">{promotion.levelNom}</td>
                <td className="px-4 py-3 text-ink/70">{promotion.academicYearLibelle}</td>
                <td className="px-4 py-3"><StatusBadge active={promotion.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(promotion)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {promotion.actif ? (
                    <button
                      onClick={() => askDeactivate(promotion)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(promotion)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(promotion)}
                    className="ml-2 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
                  >
                    Supprimer
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <PromotionFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.promotion}
        programs={programs}
        levels={levels}
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

      <DeleteDialog
        target={deleteTarget}
        entityName="la promotion"
        impactFn={getPromotionDeletionImpact}
        deleteFn={deletePromotion}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadPromotions}
      />
    </div>
  )
}
