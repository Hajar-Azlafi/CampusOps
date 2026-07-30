import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchUsers } from '../api/usersApi'
import { useAuth } from '../context/AuthContext'
import { roleLabel } from '../constants/roles'

export default function DashboardPage() {
  const { user } = useAuth()
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchUsers()
      .then(setUsers)
      .catch(() => setUsers([]))
      .finally(() => setLoading(false))
  }, [])

  const total = users.length
  const active = users.filter((u) => u.isActive).length
  const inactive = total - active

  const roleCounts = users.reduce((acc, u) => {
    acc[u.role] = (acc[u.role] || 0) + 1
    return acc
  }, {})

  return (
    <div>
      <h1 className="font-display text-2xl font-semibold text-ink">
        Bonjour, {user?.firstName}
      </h1>
      <p className="text-sm text-ink/50 mt-1 mb-8">
        {roleLabel(user?.role)} · Tableau de bord
      </p>

      {loading ? (
        <p className="text-sm text-ink/40">Chargement...</p>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
            <div className="bg-white border border-ink/10 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-ink">{total}</p>
              <p className="text-xs text-ink/50 mt-1">Utilisateurs</p>
            </div>
            <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-emerald-700">{active}</p>
              <p className="text-xs text-emerald-700/70 mt-1">Actifs</p>
            </div>
            <div className="bg-ink/5 border border-ink/10 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-ink/60">{inactive}</p>
              <p className="text-xs text-ink/40 mt-1">Inactifs</p>
            </div>
            <div className="bg-signal/10 border border-signal/30 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-blueprint-800">
                {Object.keys(roleCounts).length}
              </p>
              <p className="text-xs text-blueprint-800/60 mt-1">Roles utilises</p>
            </div>
          </div>

          {user?.role === 'ADMIN' && (
            <div className="bg-white border border-ink/10 rounded-xl p-5 mb-8">
              <p className="text-sm font-medium text-ink mb-3">Actions rapides</p>
              <div className="flex flex-wrap gap-3">
                <Link
                  to="/users"
                  className="px-4 py-2 text-sm font-medium text-blueprint-800 border border-blueprint-800/25 rounded-lg hover:bg-blueprint-800/5 transition-colors"
                >
                  Gerer les utilisateurs
                </Link>
                <Link
                  to="/users/import"
                  className="px-4 py-2 text-sm font-medium text-blueprint-800 border border-blueprint-800/25 rounded-lg hover:bg-blueprint-800/5 transition-colors"
                >
                  Importer depuis Excel
                </Link>
              </div>
            </div>
          )}

          <div className="border border-dashed border-ink/15 rounded-xl p-8 text-center">
            <p className="text-sm text-ink/40">
              Les modules Batiments, Reservations et Emplois du temps seront disponibles ici prochainement.
            </p>
          </div>
        </>
      )}
    </div>
  )
}