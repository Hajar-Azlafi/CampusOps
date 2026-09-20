import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchSpaces,
  searchSpaces,
  getSpacesByBuilding,
  getSpacesByType,
} from '../api/spacesApi'
import { fetchBuildings } from '../api/buildingsApi'
import { SPACE_TYPES, spaceTypeLabel } from '../constants/spaceTypes'
import { StatusBadge } from '../components/Badge'
import { IconLock, IconSearchLocation } from '../components/icons'

/**
 * "Consulter les salles" (Sections 7 & 10) : le responsable pedagogique n'est
 * PAS administrateur des salles. Cette page est en LECTURE SEULE — aucune
 * creation / modification / activation. Pour obtenir une salle, il passe par
 * une demande de reservation (bouton -> /availability). Les lectures d'espaces
 * sont ouvertes a tout utilisateur authentifie cote backend (pas de requireAdmin).
 */
export default function RpSallesPage() {
  const [spaces, setSpaces] = useState([])
  const [buildings, setBuildings] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [buildingFilter, setBuildingFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')

  const loadBuildings = useCallback(async () => {
    try {
      setBuildings(await fetchBuildings({ actif: true }))
    } catch {
      // filtre facultatif : on ignore l'echec silencieusement
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
        data = await fetchSpaces({ actif: true })
      }
      if (buildingFilter) {
        data = data.filter((s) => String(s.buildingId) === String(buildingFilter))
      }
      if (typeFilter) {
        data = data.filter((s) => s.type === typeFilter)
      }
      // Catalogue de consultation RP en lecture seule : on ne propose QUE des
      // salles actives, y compris via la recherche par mot-cle / batiment / type
      // (le endpoint /spaces/search ne filtre pas le statut). Une salle inactive
      // n'est plus reservable (§5/§22) ; l'admin la gere depuis sa page dediee.
      data = data.filter((s) => s.actif)
      setSpaces(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des salles')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, buildingFilter, typeFilter])

  useEffect(() => {
    loadBuildings()
    loadSpaces()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadSpaces()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [buildingFilter, typeFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadSpaces()
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Consulter les salles</h1>
          <p className="text-sm text-ink/50 mt-1">
            {spaces.length} salle(s) · catalogue des espaces disponibles
          </p>
        </div>
        <Link
          to="/availability"
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          <IconSearchLocation className="w-4 h-4" />
          Demander une salle
        </Link>
      </div>

      <div className="mb-6 flex items-start gap-2.5 rounded-lg border border-blue-200 bg-blue-50/60 px-4 py-3 text-sm text-blue-800">
        <IconLock className="mt-0.5 h-4 w-4 shrink-0" />
        <p>
          Consultation uniquement. La gestion des salles (création, équipements, disponibilité)
          relève de l'administration. Pour réserver un espace, utilisez « Demander une salle » :
          votre demande sera transmise à l'administrateur pour validation.
        </p>
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
            <option key={b.id} value={b.id}>{b.code ? `${b.code} — ${b.nom}` : b.nom}</option>
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
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && spaces.length === 0 && (
              <tr><td colSpan={7} className="text-center py-10 text-ink/40">Aucune salle trouvée</td></tr>
            )}
            {!loading && spaces.map((space) => (
              <tr key={space.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-xs text-ink/70">{space.code}</td>
                <td className="px-4 py-3 font-medium text-ink">
                  {space.nom}
                  {space.reserve && space.code !== 'AMPC' && (
                    <span className="ml-2 inline-block rounded bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 align-middle">
                      Réservé
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-ink/70">{spaceTypeLabel(space.type)}</td>
                <td className="px-4 py-3 text-ink/70">{space.capacite}</td>
                <td className="px-4 py-3 text-ink/70">{space.floorNom || '—'}</td>
                <td className="px-4 py-3 text-ink/70">
                  {space.buildingCode ? `${space.buildingCode} — ${space.buildingNom}` : space.buildingNom}
                </td>
                <td className="px-4 py-3"><StatusBadge active={space.actif} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
