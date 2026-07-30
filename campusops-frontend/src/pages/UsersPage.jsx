import { useEffect, useState, useCallback } from 'react'
import { fetchUsers, searchUsers, deactivateUser, reactivateUser, resetUserPassword, changeUserRole } from '../api/usersApi'
import { ROLES, roleLabel } from '../constants/roles'
import { RoleBadge, StatusBadge } from '../components/Badge'
import UserFormModal from '../components/UserFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import TempPasswordModal from '../components/TempPasswordModal'
import UserRowActions from '../components/UserRowActions'
import { Link } from 'react-router-dom'


export default function UsersPage() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [departmentFilter, setDepartmentFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', user: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, user: null })
  const [tempPasswordModal, setTempPasswordModal] = useState({ open: false, email: '', password: '' })
  const [actionLoading, setActionLoading] = useState(false)

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = keyword.trim()
        ? await searchUsers(keyword.trim())
        : await fetchUsers({ role: roleFilter || undefined, department: departmentFilter.trim() || undefined })
      setUsers(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des utilisateurs')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, roleFilter, departmentFilter])

  useEffect(() => {
    loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleFilter, departmentFilter])

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
      setTempPasswordModal({ open: true, email: user.email, password: tempPassword })
    }
  }

  const askDeactivate = (user) => setConfirmDialog({ open: true, type: 'deactivate', user })
  const askReactivate = (user) => setConfirmDialog({ open: true, type: 'reactivate', user })
  const askResetPassword = (user) => setConfirmDialog({ open: true, type: 'reset', user })
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
        setTempPasswordModal({ open: true, email: result.email, password: result.temporaryPassword })
      }
      closeConfirm()
      loadUsers()
    } catch {
      setErrorMsg("L'action a echoue, veuillez reessayer")
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
      setErrorMsg('Impossible de changer le role')
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Desactiver le compte',
      message: `Voulez-vous desactiver le compte de ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName} ? Il ne pourra plus se connecter.`,
      confirmLabel: 'Desactiver',
      danger: true,
    },
    reactivate: {
      title: 'Reactiver le compte',
      message: `Voulez-vous reactiver le compte de ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName} ?`,
      confirmLabel: 'Reactiver',
      danger: false,
    },
    reset: {
      title: 'Reinitialiser le mot de passe',
      message: `Un nouveau mot de passe temporaire sera genere pour ${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName}.`,
      confirmLabel: 'Reinitialiser',
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
    className="px-4 py-2.5 border border-blueprint-800/25 text-blueprint-800 text-sm font-medium rounded-lg hover:bg-blueprint-800/5 transition-colors"
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

      <div className="bg-white border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom, prenom ou email..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-blueprint-800 border border-blueprint-800/25 rounded-lg hover:bg-blueprint-800/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les roles</option>
          {ROLES.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>

        <input
          value={departmentFilter}
          onChange={(e) => setDepartmentFilter(e.target.value)}
          placeholder="Departement"
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm w-full md:w-40 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        />
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-white border border-ink/10 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Email</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Role</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Departement</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucun utilisateur trouve</td></tr>
            )}
            {!loading && users.map((user) => (
              <tr key={user.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{user.firstName} {user.lastName}</td>
                <td className="px-4 py-3 text-ink/70">{user.email}</td>
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

      <TempPasswordModal
        open={tempPasswordModal.open}
        email={tempPasswordModal.email}
        password={tempPasswordModal.password}
        onClose={() => setTempPasswordModal({ open: false, email: '', password: '' })}
      />
    </div>
  )
}