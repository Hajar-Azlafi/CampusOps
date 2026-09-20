import { useAcademicYear } from '../../context/AcademicYearContext'
import { useAuth } from '../../context/AuthContext'

// Roles disposant de pages annuelles (reservations, EDT, examens, groupes,
// rapports...). Le selecteur ne change que le CONTEXTE DE CONSULTATION ; il ne
// modifie jamais l'annee active en base (action reservee a la page dediee).
const YEAR_AWARE_ROLES = ['ADMIN', 'RESPONSABLE_PEDAGOGIQUE']

export default function AcademicYearSelector() {
  const { user } = useAuth()
  const { years, activeYear, selectedYearId, setSelectedYearId, loading } = useAcademicYear()

  if (!YEAR_AWARE_ROLES.includes(user?.role)) return null
  if (loading || years.length === 0) return null

  const current = selectedYearId ?? activeYear?.id ?? ''

  return (
    <label className="hidden md:flex items-center gap-2 text-xs text-ink/50">
      <span className="whitespace-nowrap">Année</span>
      <select
        value={current}
        onChange={(e) => setSelectedYearId(e.target.value)}
        title="Année universitaire de consultation"
        className="px-2.5 py-1.5 border border-ink/15 rounded-lg text-sm text-ink bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
      >
        {years.map((y) => (
          <option key={y.id} value={y.id}>
            {y.libelle}
            {activeYear && y.id === activeYear.id ? ' (active)' : ''}
          </option>
        ))}
      </select>
    </label>
  )
}
