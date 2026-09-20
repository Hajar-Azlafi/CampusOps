import { useEffect, useState, useCallback } from 'react'
import {
  fetchUsers,
  searchUsers,
  deactivateUser,
  reactivateUser,
  resetUserPassword,
  changeUserRole,
  deleteUser,
  getUserDeletionImpact,
} from '../api/usersApi'
import { ROLES, roleLabel } from '../constants/roles'
import { RoleBadge, StatusBadge } from '../components/Badge'
import UserFormModal from '../components/UserFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'
import TempPasswordModal from '../components/TempPasswordModal'
import UserRowActions from '../components/UserRowActions'
import { Link } from 'react-router-dom'
import { fetchDepartments } from '../api/departmentsApi'

export default function UsersPage() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [departments, setDepartments] = useState([])

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', user: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, user: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [tempPasswordModal, setTempPasswordModal] = useState({ open: false, email: '', password: '', emailSent: false })
  const [actionLoading, setActionLoading] = useState(false)

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data = keyword.trim()
        ? await searchUsers(keyword.trim())
        : await fetchUsers({ role: roleFilter || undefined, department: departmentFilter.trim() || undefined })
      // Filtre de statut applique cote client : l'ecran d'administration liste
      // tous les comptes (§18), le filtre [Actifs][Inactifs][Tous] restreint
      // l'affichage sans masquer l'historique.
      if (statusFilter) {
        data = data.filter((u) => u.isActive === (statusFilter === 'active'))
      }
      setUsers(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des utilisateurs')
    } finally {
      setLoading(false)
    }
  }, [keyword, roleFilter, departmentFilter, statusFilter])

  useEffect(() => {
    fetchDepartments({ actif: true }).then(setDepartments).catch(() => setDepartments([]))
  }, [])

  useEffect(() => {
    loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleFilter, departmentFilter, statusFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadUsers()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', user: null })
  const openEdit = (user) => setFormModal({ open: true, mode: 'edit', user })
  const closeForm = () => setFormModal({ open: false, mode: 'create', user: null })

  const handleFormSuccess = (user, tempPassword) => {
    closeForm()
    loadUsers()
    if (tempPassword) {
      setTempPasswordModal({ open: true, email: user.email, password: tempPassword, emailSent: user.emailSent !== false })
    }
  }

  const askDeactivate = (user) => setConfirmDialog({ open: true, type: 'deactivate', user })
  const askReactivate = (user) => setConfirmDialog({ open: true, type: 'reactivate', user })
  const askResetPassword = (user) => setConfirmDialog({ open: true, type: 'reset', user })
  const askDelete = (user) => setDeleteTarget(user)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, user: null })

  const handleConfirm = async () => {
    const { type, user } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateUser(user.id)
      } else if (type === 'reactivate') {
        await reactivateUser(user.id)
      } else if (type === 'reset') {
        const result = await resetUserPassword(user.id)
        setTempPasswordModal({
          open: true,
          email: result.email,
          password: result.temporaryPassword,
          emailSent: result.emailSent,
        })
      }
      closeConfirm()
      loadUsers()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const handleChangeRole = async (user, newRole) => {
    try {
      await changeUserRole(user.id, newRole)
      loadUsers()
    } catch {
      setErrorMsg('Impossible de changer le rôle')
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le compte',
      message: `Voulez-vous désactiver le compte de ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName} ? Il ne pourra plus se connecter.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    reactivate: {
      title: 'Réactiver le compte',
      message: `Voulez-vous réactiver le compte de ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName} ?`,
      confirmLabel: 'Réactiver',
      danger: false,
    },
    reset: {
      title: 'Réinitialiser le mot de passe',
      message: `Un nouveau mot de passe temporaire sera généré pour ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName}.`,
      confirmLabel: 'Réinitialiser',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Utilisateurs</h1>
          <p className="text-sm text-ink/50 mt-1">{users.length} compte(s)</p>
        </div>
        <div className="flex gap-3">
          <Link
            to="/users/import"
            className="px-4 py-2.5 border border-heading/25 text-heading text-sm font-medium rounded-lg hover:bg-heading/5 transition-colors"
          >
            Importer depuis Excel
          </Link>
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            + Nouvel utilisateur
          </button>
        </div>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-12 gap-3">
        <form onSubmit={handleSearchSubmit} className="min-w-0 flex flex-col sm:flex-row gap-2 sm:col-span-2 xl:col-span-6">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom, prénom ou email..."
            className="min-w-0 w-full flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="shrink-0 px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
        >
          <option value="">Tous les rôles</option>
          {ROLES.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>

        <select
          value={departmentFilter}
          onChange={(e) => setDepartmentFilter(e.target.value)}
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
        >
          <option value="">Tous les départements</option>
          {departments.map((department) => (
            <option key={department.id} value={department.nom}>{department.nom}</option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Email</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Téléphone</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Rôle</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Département</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Aucun utilisateur trouvé</td></tr>
            )}
            {!loading && users.map((user) => (
              <tr key={user.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{user.lastName} {user.firstName}</td>
                <td className="px-4 py-3 text-ink/70">{user.email}</td>
                <td className="px-4 py-3 text-ink/70">{user.phoneNumber || '—'}</td>
                <td className="px-4 py-3"><RoleBadge role={user.role} label={roleLabel(user.role)} /></td>
                <td className="px-4 py-3 text-ink/70">{user.department || '—'}</td>
                <td className="px-4 py-3"><StatusBadge active={user.isActive} /></td>
                <td className="px-4 py-3 text-right">
                  <UserRowActions
                    user={user}
                    onEdit={() => openEdit(user)}
                    onDeactivate={() => askDeactivate(user)}
                    onReactivate={() => askReactivate(user)}
                    onResetPassword={() => askResetPassword(user)}
                    onDelete={() => askDelete(user)}
                    onChangeRole={(role) => handleChangeRole(user, role)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <UserFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.user}
        departments={departments}
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
        entityName="le compte"
        getLabel={(u) => `${u?.firstName ?? ''} ${u?.lastName ?? ''}`.trim() || u?.email}
        impactFn={getUserDeletionImpact}
        deleteFn={deleteUser}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadUsers}
      />

      <TempPasswordModal
        open={tempPasswordModal.open}
        email={tempPasswordModal.email}
        password={tempPasswordModal.password}
        emailSent={tempPasswordModal.emailSent}
        onClose={() => setTempPasswordModal({ open: false, email: '', password: '', emailSent: false })}
      />
    </div>
  )
}
