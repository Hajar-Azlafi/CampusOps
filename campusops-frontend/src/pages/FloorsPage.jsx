import { useEffect, useState, useCallback } from 'react'
import {
  fetchFloors,
  searchFloors,
  getFloorsByBuilding,
  deactivateFloor,
  activateFloor,
  deleteFloor,
  getFloorDeletionImpact,
} from '../api/floorsApi'
import { fetchBuildings } from '../api/buildingsApi'
import { StatusBadge } from '../components/Badge'
import FloorFormModal from '../components/FloorFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'
import TruncatedText from '../components/TruncatedText'

export default function FloorsPage() {
  const [floors, setFloors] = useState([])
  const [buildings, setBuildings] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [buildingFilter, setBuildingFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', floor: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, floor: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadBuildings = useCallback(async () => {
    try {
      const data = await fetchBuildings()
      setBuildings(data)
    } catch {
      // silencieux : le filtre reste vide
    }
  }, [])

  const loadFloors = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchFloors(keyword.trim())
      } else if (buildingFilter) {
        data = await getFloorsByBuilding(Number(buildingFilter))
      } else {
        data = await fetchFloors({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      if (buildingFilter && (keyword.trim() || statusFilter)) {
        data = data.filter((f) => String(f.buildingId) === String(buildingFilter))
      }
      if (statusFilter && (keyword.trim() || buildingFilter)) {
        data = data.filter((f) => f.actif === (statusFilter === 'active'))
      }
      setFloors(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des étages')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, buildingFilter])

  useEffect(() => {
    loadBuildings()
    loadFloors()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadFloors()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, buildingFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadFloors()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', floor: null })
  const openEdit = (floor) => setFormModal({ open: true, mode: 'edit', floor })
  const closeForm = () => setFormModal({ open: false, mode: 'create', floor: null })

  const handleFormSuccess = () => {
    closeForm()
    loadFloors()
  }

  const askDeactivate = (floor) => setConfirmDialog({ open: true, type: 'deactivate', floor })
  const askActivate = (floor) => setConfirmDialog({ open: true, type: 'activate', floor })
  const askDelete = (floor) => setDeleteTarget(floor)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, floor: null })

  const handleConfirm = async () => {
    const { type, floor } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateFloor(floor.id)
      } else if (type === 'activate') {
        await activateFloor(floor.id)
      }
      closeConfirm()
      loadFloors()
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
      title: "Désactiver l'étage",
      message: `Voulez-vous désactiver "${confirmDialog.floor?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: "Activer l'étage",
      message: `Voulez-vous réactiver "${confirmDialog.floor?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Étages</h1>
          <p className="text-sm text-ink/50 mt-1">{floors.length} étage(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvel étage
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Code</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">N°</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Bâtiment</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Description</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && floors.length === 0 && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Aucun étage trouvé</td></tr>
            )}
            {!loading && floors.map((floor) => (
              <tr key={floor.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/80">{floor.code}</td>
                <td className="px-4 py-3 font-medium text-ink">{floor.nom}</td>
                <td className="px-4 py-3 text-ink/70">{floor.numero}</td>
                <td className="px-4 py-3 text-ink/70">{floor.buildingCode} — {floor.buildingNom}</td>
                <td className="px-4 py-3 text-ink/60 max-w-xs">
                  <TruncatedText text={floor.description} title={`Description — ${floor.nom}`} />
                </td>
                <td className="px-4 py-3"><StatusBadge active={floor.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(floor)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {floor.actif ? (
                    <button
                      onClick={() => askDeactivate(floor)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(floor)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(floor)}
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

      <FloorFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.floor}
        buildings={buildings}
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
        entityName="l'étage"
        impactFn={getFloorDeletionImpact}
        deleteFn={deleteFloor}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadFloors}
      />
    </div>
  )
}
