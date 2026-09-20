import { useEffect, useState, useCallback } from 'react'
import {
  fetchLevels,
  searchLevels,
  deleteLevel,
  getLevelDeletionImpact,
} from '../api/levelsApi'
import { typeFormationLabel } from '../constants/formation'
import LevelFormModal from '../components/LevelFormModal'
import DeleteDialog from '../components/DeleteDialog'

// Les niveaux/cycles (Tronc commun, Licence, Master, Cycle ingénieur,
// Doctorat, Formation continue) sont des référentiels stables, rattachés à un
// type de formation (initiale ou continue). Le statut actif/inactif n'a pas de
// sens métier ici : on gère uniquement la création, la modification et la
// suppression.

export default function LevelsPage() {
  const [levels, setLevels] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', level: null })
  const [deleteTarget, setDeleteTarget] = useState(null)

  const loadLevels = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = keyword.trim()
        ? await searchLevels(keyword.trim())
        : await fetchLevels()
      setLevels(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des niveaux')
    } finally {
      setLoading(false)
    }
  }, [keyword])

  useEffect(() => {
    loadLevels()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadLevels()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', level: null })
  const openEdit = (level) => setFormModal({ open: true, mode: 'edit', level })
  const closeForm = () => setFormModal({ open: false, mode: 'create', level: null })

  const handleFormSuccess = () => {
    closeForm()
    loadLevels()
  }

  const askDelete = (level) => setDeleteTarget(level)

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Niveaux</h1>
          <p className="text-sm text-ink/50 mt-1">
            {levels.length} niveau{levels.length > 1 ? 'x' : ''} / cycle{levels.length > 1 ? 's' : ''}
            {levels.length > 0 && ` : ${levels.map((level) => level.nom).join(', ')}`}
          </p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau niveau
        </button>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
        <form onSubmit={handleSearchSubmit} className="flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Ordre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau / Cycle</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nombre d'années</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Type de formation</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && levels.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucun niveau trouvé</td></tr>
            )}
            {!loading && levels.map((level) => (
              <tr key={level.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 text-ink/70 w-20">{level.ordre}</td>
                <td className="px-4 py-3 font-medium text-ink">{level.nom}</td>
                <td className="px-4 py-3 text-ink/60">
                  {level.nombreAnnees != null
                    ? `${level.nombreAnnees} an${level.nombreAnnees > 1 ? 's' : ''}`
                    : <span className="text-ink/30">—</span>}
                </td>
                <td className="px-4 py-3 text-ink/60">{typeFormationLabel(level.typeFormation)}</td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(level)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  <button
                    onClick={() => askDelete(level)}
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

      <LevelFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.level}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <DeleteDialog
        target={deleteTarget}
        entityName="le niveau"
        impactFn={getLevelDeletionImpact}
        deleteFn={deleteLevel}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadLevels}
      />
    </div>
  )
}
