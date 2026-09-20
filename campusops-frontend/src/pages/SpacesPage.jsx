import { useEffect, useState, useCallback } from 'react'
import {
  fetchSpaces,
  searchSpaces,
  getSpacesByBuilding,
  getSpacesByType,
  deactivateSpace,
  activateSpace,
  deleteSpace,
  getSpaceDeletionImpact,
} from '../api/spacesApi'
import { fetchBuildings } from '../api/buildingsApi'
import { SPACE_TYPES, spaceTypeLabel, labSpecialityLabel } from '../constants/spaceTypes'
import { StatusBadge } from '../components/Badge'
import SpaceFormModal from '../components/SpaceFormModal'
import SpaceEquipmentsModal from '../components/SpaceEquipmentsModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

export default function SpacesPage() {
  const [spaces, setSpaces] = useState([])
  const [buildings, setBuildings] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [buildingFilter, setBuildingFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', space: null })
  const [equipmentsModal, setEquipmentsModal] = useState({ open: false, space: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, space: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadBuildings = useCallback(async () => {
    try {
      setBuildings(await fetchBuildings())
    } catch {
      // silencieux
    }
  }, [])

  const loadSpaces = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchSpaces(keyword.trim())
      } else if (buildingFilter) {
        data = await getSpacesByBuilding(Number(buildingFilter))
      } else if (typeFilter) {
        data = await getSpacesByType(typeFilter)
      } else {
        data = await fetchSpaces({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      if (buildingFilter) data = data.filter((s) => String(s.buildingId) === String(buildingFilter))
      if (typeFilter) data = data.filter((s) => s.type === typeFilter)
      if (statusFilter) data = data.filter((s) => s.actif === (statusFilter === 'active'))
      setSpaces(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des espaces')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, buildingFilter, typeFilter])

  useEffect(() => {
    loadBuildings()
    loadSpaces()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadSpaces()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, buildingFilter, typeFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadSpaces()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', space: null })
  const openEdit = (space) => setFormModal({ open: true, mode: 'edit', space })
  const closeForm = () => setFormModal({ open: false, mode: 'create', space: null })

  const openEquipments = (space) => setEquipmentsModal({ open: true, space })
  const closeEquipments = () => setEquipmentsModal({ open: false, space: null })

  const handleFormSuccess = () => {
    closeForm()
    loadSpaces()
  }

  const askDeactivate = (space) => setConfirmDialog({ open: true, type: 'deactivate', space })
  const askActivate = (space) => setConfirmDialog({ open: true, type: 'activate', space })
  const askDelete = (space) => setDeleteTarget(space)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, space: null })

  const handleConfirm = async () => {
    const { type, space } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateSpace(space.id)
      } else if (type === 'activate') {
        await activateSpace(space.id)
      }
      closeConfirm()
      loadSpaces()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: "Désactiver l'espace",
      message: `Voulez-vous désactiver "${confirmDialog.space?.nom}" ? Il ne pourra plus être réservé.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: "Activer l'espace",
      message: `Voulez-vous réactiver "${confirmDialog.space?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Espaces pédagogiques</h1>
          <p className="text-sm text-ink/50 mt-1">{spaces.length} espace(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvel espace
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row flex-wrap gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 min-w-[220px] flex gap-2">
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
          value={buildingFilter}
          onChange={(e) => setBuildingFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les bâtiments</option>
          {buildings.map((b) => (
            <option key={b.id} value={b.id}>{b.code} — {b.nom}</option>
          ))}
        </select>

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les types</option>
          {SPACE_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Code</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Type</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Capacité</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Étage</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Bâtiment</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && spaces.length === 0 && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Aucun espace trouvé</td></tr>
            )}
            {!loading && spaces.map((space) => (
              <tr key={space.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/80">{space.code}</td>
                <td className="px-4 py-3 font-medium text-ink">
                  {space.nom}
                  {space.reserve && space.code !== 'AMPC' && (
                    <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                      Réservé
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-ink/70">
                  {spaceTypeLabel(space.type)}
                  {space.speciality && (
                    <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-heading/8 text-heading border border-heading/20">
                      {labSpecialityLabel(space.speciality)}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-ink/70">{space.capacite}</td>
                <td className="px-4 py-3 text-ink/70">{space.floorNom}</td>
                <td className="px-4 py-3 text-ink/70">{space.buildingCode} — {space.buildingNom}</td>
                <td className="px-4 py-3"><StatusBadge active={space.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(space)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  <button
                    onClick={() => openEquipments(space)}
                    className="ml-2 px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Équipements
                  </button>
                  {space.actif ? (
                    <button
                      onClick={() => askDeactivate(space)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(space)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(space)}
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

      <SpaceFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.space}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <SpaceEquipmentsModal
        open={equipmentsModal.open}
        space={equipmentsModal.space}
        onClose={closeEquipments}
        onSuccess={closeEquipments}
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
        entityName="la salle"
        impactFn={getSpaceDeletionImpact}
        deleteFn={deleteSpace}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadSpaces}
      />
    </div>
  )
}
