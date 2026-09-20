import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchAuditLogs, searchAuditLogs } from '../api/auditApi'
import { fetchUsers } from '../api/usersApi'
import { useAuth } from '../context/AuthContext'
import { useAcademicYear } from '../context/AcademicYearContext'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { AUDIT_ACTIONS, auditActionLabel } from '../constants/auditActions'
import { AuditActionBadge } from '../components/Badge'
import Pagination from '../components/Pagination'

export default function AuditPage() {
  const { user } = useAuth()
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDateHeure } = useSettings()
  const isAdmin = user?.role === 'ADMIN'

  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [users, setUsers] = useState([])

  const [userFilter, setUserFilter] = useState('')
  const [actionFilter, setActionFilter] = useState('')
  const [moduleFilter, setModuleFilter] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const [page, setPage] = useState(1)
  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(logs, page)

  const hasFilters = userFilter || actionFilter || moduleFilter || startDate || endDate

  const loadUsers = useCallback(async () => {
    try {
      const data = await fetchUsers()
      setUsers(data)
    } catch {
      // silencieux : le filtre utilisateur reste vide
    }
  }, [])

  const loadLogs = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (userFilter || actionFilter || moduleFilter || startDate || endDate) {
        data = await searchAuditLogs({
          userId: userFilter || undefined,
          action: actionFilter || undefined,
          module: moduleFilter || undefined,
          start: startDate ? `${startDate}T00:00:00` : undefined,
          end: endDate ? `${endDate}T23:59:59` : undefined,
        })
      } else {
        data = await fetchAuditLogs(selectedYearId)
      }
      setLogs(data)
      setPage(1)
    } catch {
      setErrorMsg("Impossible de charger le journal d'audit")
    } finally {
      setLoading(false)
    }
  }, [userFilter, actionFilter, moduleFilter, startDate, endDate, selectedYearId])

  useEffect(() => {
    loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    loadLogs()
  }, [loadLogs])

  const modules = useMemo(
    () => [...new Set(logs.map((l) => l.module).filter(Boolean))].sort(),
    [logs],
  )

  const resetFilters = () => {
    setUserFilter('')
    setActionFilter('')
    setModuleFilter('')
    setStartDate('')
    setEndDate('')
  }

  if (!isAdmin) {
    return (
      <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
        Cette page est réservée aux administrateurs.
      </p>
    )
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Journal d'audit</h1>
        <p className="text-sm text-ink/50 mt-1">{logs.length} entrée(s)</p>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 grid grid-cols-1 md:grid-cols-3 xl:grid-cols-6 gap-3">
        <select
          value={userFilter}
          onChange={(e) => setUserFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les utilisateurs</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>
          ))}
        </select>

        <select
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Toutes les actions</option>
          {AUDIT_ACTIONS.map((a) => (
            <option key={a.value} value={a.value}>{a.label}</option>
          ))}
        </select>

        <select
          value={moduleFilter}
          onChange={(e) => setModuleFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les modules</option>
          {modules.map((m) => (
            <option key={m} value={m}>{m}</option>
          ))}
        </select>

        <input
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          aria-label="Date de début"
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        />

        <input
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          aria-label="Date de fin"
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        />

        <button
          onClick={resetFilters}
          disabled={!hasFilters}
          className="px-3 py-2 text-sm font-medium text-ink/60 border border-ink/15 rounded-lg hover:bg-ink/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          Réinitialiser
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Date</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Utilisateur</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Action</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Module</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Description</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Adresse IP</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && logs.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucune entrée trouvée</td></tr>
            )}
            {!loading && pageItems.map((l) => (
              <tr key={l.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/60 whitespace-nowrap">{formatDateHeure(l.createdAt)}</td>
                <td className="px-4 py-3 text-ink/70 whitespace-nowrap">
                  {l.userNomComplet ?? <span className="text-ink/40">Système</span>}
                </td>
                <td className="px-4 py-3"><AuditActionBadge action={l.action} label={auditActionLabel(l.action)} /></td>
                <td className="px-4 py-3 text-ink/70 whitespace-nowrap">{l.module}</td>
                <td className="px-4 py-3 text-ink/70">{l.description}</td>
                <td className="px-4 py-3 font-mono text-ink/60 whitespace-nowrap">{l.adresseIp ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!loading && afficher && (
        <Pagination page={pageAffichee} totalPages={totalPages} onChange={setPage} />
      )}
    </div>
  )
}
