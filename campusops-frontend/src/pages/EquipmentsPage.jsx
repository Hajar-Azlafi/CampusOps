import { useEffect, useState, useCallback } from 'react'
import {
  fetchEquipments,
  searchEquipments,
  deactivateEquipment,
  activateEquipment,
  deleteEquipment,
  getEquipmentDeletionImpact,
} from '../api/equipmentsApi'
import { StatusBadge } from '../components/Badge'
import EquipmentFormModal from '../components/EquipmentFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'
import TruncatedText from '../components/TruncatedText'

// Les équipements se gèrent par création, modification et activation/désactivation.
// La suppression définitive reste possible (avec aperçu d'impact) via DeleteDialog.

export default function EquipmentsPage() {
  const [equipments, setEquipments] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', equipment: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, equipment: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadEquipments = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      // `actif` transmis au backend hors recherche ; combine cote client sinon.
      const actifParam = statusFilter === '' ? undefined : statusFilter === 'active'
      let data = keyword.trim()
        ? await searchEquipments(keyword.trim())
        : await fetchEquipments({ actif: actifParam })
      if (statusFilter) {
        data = data.filter((eq) => eq.actif === (statusFilter === 'active'))
      }
      setEquipments(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des équipements')
    } finally {
      setLoading(false)
    }
  }, [keyword, statusFilter])

  useEffect(() => {
    loadEquipments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadEquipments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadEquipments()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', equipment: null })
  const openEdit = (equipment) => setFormModal({ open: true, mode: 'edit', equipment })
  const closeForm = () => setFormModal({ open: false, mode: 'create', equipment: null })

  const handleFormSuccess = () => {
    closeForm()
    loadEquipments()
  }

  const askDeactivate = (equipment) => setConfirmDialog({ open: true, type: 'deactivate', equipment })
  const askActivate = (equipment) => setConfirmDialog({ open: true, type: 'activate', equipment })
  const askDelete = (equipment) => setDeleteTarget(equipment)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, equipment: null })

  const handleConfirm = async () => {
    const { type, equipment } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateEquipment(equipment.id)
      } else if (type === 'activate') {
        await activateEquipment(equipment.id)
      }
      closeConfirm()
      loadEquipments()
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
      title: "Désactiver l'équipement",
      message: `Voulez-vous désactiver "${confirmDialog.equipment?.nom}" ? Il ne pourra plus être associé aux espaces.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: "Activer l'équipement",
      message: `Voulez-vous réactiver "${confirmDialog.equipment?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Équipements</h1>
          <p className="text-sm text-ink/50 mt-1">{equipments.length} équipement(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvel équipement
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Description</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && equipments.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucun équipement trouvé</td></tr>
            )}
            {!loading && equipments.map((equipment) => (
              <tr key={equipment.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/80">{equipment.code}</td>
                <td className="px-4 py-3 font-medium text-ink">{equipment.nom}</td>
                <td className="px-4 py-3 text-ink/70 max-w-xs">
                  <TruncatedText text={equipment.description} title={`Description — ${equipment.nom}`} />
                </td>
                <td className="px-4 py-3"><StatusBadge active={equipment.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(equipment)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {equipment.actif ? (
                    <button
                      onClick={() => askDeactivate(equipment)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(equipment)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(equipment)}
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

      <EquipmentFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.equipment}
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
        entityName="l'équipement"
        impactFn={getEquipmentDeletionImpact}
        deleteFn={deleteEquipment}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadEquipments}
      />
    </div>
  )
}
