import { useEffect, useState, useCallback } from 'react'
import {
  fetchDepartments,
  searchDepartments,
  deactivateDepartment,
  activateDepartment,
  deleteDepartment,
  getDepartmentDeletionImpact,
} from '../api/departmentsApi'
import { StatusBadge } from '../components/Badge'
import DepartmentFormModal from '../components/DepartmentFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

export default function DepartmentsPage() {
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', department: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, department: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadDepartments = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchDepartments(keyword.trim())
      } else {
        data = await fetchDepartments({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      if (statusFilter && keyword.trim()) {
        data = data.filter((d) => d.actif === (statusFilter === 'active'))
      }
      setDepartments(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des départements')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter])

  useEffect(() => {
    loadDepartments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadDepartments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadDepartments()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', department: null })
  const openEdit = (department) => setFormModal({ open: true, mode: 'edit', department })
  const closeForm = () => setFormModal({ open: false, mode: 'create', department: null })

  const handleFormSuccess = () => {
    closeForm()
    loadDepartments()
  }

  const askDeactivate = (department) => setConfirmDialog({ open: true, type: 'deactivate', department })
  const askActivate = (department) => setConfirmDialog({ open: true, type: 'activate', department })
  const askDelete = (department) => setDeleteTarget(department)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, department: null })

  const handleConfirm = async () => {
    const { type, department } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateDepartment(department.id)
      } else if (type === 'activate') {
        await activateDepartment(department.id)
      }
      closeConfirm()
      loadDepartments()
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
      title: 'Désactiver le département',
      message: `Voulez-vous désactiver "${confirmDialog.department?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer le département',
      message: `Voulez-vous réactiver "${confirmDialog.department?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Départements</h1>
          <p className="text-sm text-ink/50 mt-1">{departments.length} département(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau département
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

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
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
            {!loading && departments.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucun département trouvé</td></tr>
            )}
            {!loading && departments.map((department) => (
              <tr key={department.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/80">{department.code}</td>
                <td className="px-4 py-3 font-medium text-ink">{department.nom}</td>
                <td className="px-4 py-3 text-ink/60 max-w-md whitespace-pre-line break-words">
                  {department.description || '—'}
                </td>
                <td className="px-4 py-3"><StatusBadge active={department.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(department)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {department.actif ? (
                    <button
                      onClick={() => askDeactivate(department)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(department)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(department)}
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

      <DepartmentFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.department}
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
        entityName="le département"
        impactFn={getDepartmentDeletionImpact}
        deleteFn={deleteDepartment}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadDepartments}
      />
    </div>
  )
}
