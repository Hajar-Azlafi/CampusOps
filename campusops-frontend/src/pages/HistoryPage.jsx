import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchMyHistory } from '../api/auditApi'
import { useAcademicYear } from '../context/AcademicYearContext'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { AUDIT_ACTIONS, auditActionLabel } from '../constants/auditActions'
import { AuditActionBadge } from '../components/Badge'
import Pagination from '../components/Pagination'

export default function HistoryPage() {
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDateHeure } = useSettings()
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [actionFilter, setActionFilter] = useState('')
  const [moduleFilter, setModuleFilter] = useState('')
  const [dateFilter, setDateFilter] = useState('')

  const loadHistory = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchMyHistory(selectedYearId)
      setEntries(data)
    } catch {
      setErrorMsg("Impossible de charger l'historique")
    } finally {
      setLoading(false)
    }
  }, [selectedYearId])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  const modules = useMemo(
    () => [...new Set(entries.map((e) => e.module).filter(Boolean))].sort(),
    [entries],
  )

  const filtered = useMemo(() => {
    let data = entries
    if (actionFilter) data = data.filter((e) => e.action === actionFilter)
    if (moduleFilter) data = data.filter((e) => e.module === moduleFilter)
    if (dateFilter) data = data.filter((e) => e.createdAt?.startsWith(dateFilter))
    return data
  }, [entries, actionFilter, moduleFilter, dateFilter])

  const [page, setPage] = useState(1)
  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(filtered, page)

  useEffect(() => {
    setPage(1)
  }, [actionFilter, moduleFilter, dateFilter])

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Mon historique</h1>
        <p className="text-sm text-ink/50 mt-1">{filtered.length} action(s) enregistrée(s)</p>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
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
          value={dateFilter}
          onChange={(e) => setDateFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        />

        {(actionFilter || moduleFilter || dateFilter) && (
          <button
            onClick={() => { setActionFilter(''); setModuleFilter(''); setDateFilter('') }}
            className="px-3 py-2 text-sm font-medium text-ink/60 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
          >
            Réinitialiser
          </button>
        )}
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Date</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Action</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Module</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Description</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={4} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={4} className="text-center py-10 text-ink/40">Aucune action trouvée</td></tr>
            )}
            {!loading && pageItems.map((e) => (
              <tr key={e.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/60 whitespace-nowrap">{formatDateHeure(e.createdAt)}</td>
                <td className="px-4 py-3"><AuditActionBadge action={e.action} label={auditActionLabel(e.action)} /></td>
                <td className="px-4 py-3 text-ink/70 whitespace-nowrap">{e.module}</td>
                <td className="px-4 py-3 text-ink/70">{e.description}</td>
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
