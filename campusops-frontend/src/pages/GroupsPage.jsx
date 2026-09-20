import { useEffect, useState, useCallback } from 'react'
import {
  fetchGroups,
  searchGroups,
  getGroupsByPromotion,
  getGroupsByAcademicYear,
  deactivateGroup,
  activateGroup,
  deleteGroup,
  getGroupDeletionImpact,
} from '../api/groupsApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { useAcademicYear } from '../context/AcademicYearContext'
import { StatusBadge } from '../components/Badge'
import GroupFormModal from '../components/GroupFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

export default function GroupsPage() {
  const { selectedYearId } = useAcademicYear()
  const [groups, setGroups] = useState([])
  const [promotions, setPromotions] = useState([])
  const [academicYears, setAcademicYears] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [promotionFilter, setPromotionFilter] = useState('')
  const [yearFilter, setYearFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', group: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, group: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadParents = useCallback(async () => {
    try {
      const [promotionsData, yearsData] = await Promise.all([
        fetchPromotions({ actif: true }),
        fetchAcademicYears(),
      ])
      setPromotions(promotionsData)
      setAcademicYears(yearsData)
    } catch {
      // silencieux : les filtres restent vides
    }
  }, [])

  const loadGroups = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchGroups(keyword.trim())
      } else if (yearFilter) {
        data = await getGroupsByAcademicYear(Number(yearFilter))
      } else if (promotionFilter) {
        data = await getGroupsByPromotion(Number(promotionFilter))
      } else {
        // Sans filtre année explicite, on se cale sur l'année de consultation
        // (contexte global) ; le backend défaut sur l'année active si null.
        data = await fetchGroups({
          actif: statusFilter === '' ? undefined : statusFilter === 'active',
          academicYearId: selectedYearId,
        })
      }
      // Raffinements côté client pour combiner les filtres
      if (yearFilter) {
        data = data.filter((g) => String(g.academicYearId) === String(yearFilter))
      }
      if (promotionFilter) {
        data = data.filter((g) => String(g.promotionId) === String(promotionFilter))
      }
      if (statusFilter) {
        data = data.filter((g) => g.actif === (statusFilter === 'active'))
      }
      setGroups(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des groupes')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, promotionFilter, yearFilter, selectedYearId])

  useEffect(() => {
    loadParents()
    loadGroups()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadGroups()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, promotionFilter, yearFilter, selectedYearId])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadGroups()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', group: null })
  const openEdit = (group) => setFormModal({ open: true, mode: 'edit', group })
  const closeForm = () => setFormModal({ open: false, mode: 'create', group: null })

  const handleFormSuccess = () => {
    closeForm()
    loadGroups()
  }

  const askDeactivate = (group) => setConfirmDialog({ open: true, type: 'deactivate', group })
  const askActivate = (group) => setConfirmDialog({ open: true, type: 'activate', group })
  const askDelete = (group) => setDeleteTarget(group)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, group: null })

  const handleConfirm = async () => {
    const { type, group } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateGroup(group.id)
      } else if (type === 'activate') {
        await activateGroup(group.id)
      }
      closeConfirm()
      loadGroups()
    } catch {
      setErrorMsg("L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le groupe',
      message: `Voulez-vous désactiver "${confirmDialog.group?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer le groupe',
      message: `Voulez-vous réactiver "${confirmDialog.group?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Groupes</h1>
          <p className="text-sm text-ink/50 mt-1">{groups.length} groupe(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau groupe
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom ou code..."
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

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année d'étude</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Effectif</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année univ.</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && groups.length === 0 && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Aucun groupe trouvé</td></tr>
            )}
            {!loading && groups.map((group) => (
              <tr key={group.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{group.nom}</td>
                <td className="px-4 py-3 text-ink/70">{group.programNom || group.promotionNom}</td>
                <td className="px-4 py-3 text-ink/70">{group.levelNom || '—'}</td>
                <td className="px-4 py-3 text-ink/70">
                  {group.anneeNiveau != null
                    ? (group.anneeNiveau === 1 ? '1re année' : `${group.anneeNiveau}e année`)
                    : '—'}
                </td>
                <td className="px-4 py-3 text-ink/70">{group.effectif != null ? group.effectif : '—'}</td>
                <td className="px-4 py-3 text-ink/70">{group.academicYearLibelle || '—'}</td>
                <td className="px-4 py-3"><StatusBadge active={group.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(group)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {group.actif ? (
                    <button
                      onClick={() => askDeactivate(group)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(group)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(group)}
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

      <GroupFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.group}
        promotions={promotions}
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
        entityName="le groupe"
        impactFn={getGroupDeletionImpact}
        deleteFn={deleteGroup}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadGroups}
      />
    </div>
  )
}
