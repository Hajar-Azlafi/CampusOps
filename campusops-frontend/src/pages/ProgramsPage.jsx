import { useEffect, useState, useCallback } from 'react'
import {
  fetchPrograms,
  searchPrograms,
  getProgramsByDepartment,
  deleteProgram,
  deactivateProgram,
  activateProgram,
  getProgramDeactivationImpact,
  getProgramDeletionImpact,
} from '../api/programsApi'
import { fetchDepartments } from '../api/departmentsApi'
import { fetchLevels } from '../api/levelsApi'
import { typeFormationLabel } from '../constants/formation'
import { StatusBadge } from '../components/Badge'
import ProgramFormModal from '../components/ProgramFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

export default function ProgramsPage() {
  const [programs, setPrograms] = useState([])
  const [departments, setDepartments] = useState([])
  const [levels, setLevels] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', program: null })
  // `impact` : compteurs (promotions/groupes) charges a l'ouverture d'une
  // demande de desactivation, pour informer l'admin avant confirmation (§17).
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, program: null, impact: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadReferentials = useCallback(async () => {
    try {
      // Filtre par departement : uniquement les departements actifs (les
      // filieres d'un departement inactif ne sont plus operationnelles).
      const [deps, lvls] = await Promise.all([
        fetchDepartments({ actif: true }),
        fetchLevels(),
      ])
      setDepartments(deps)
      setLevels(lvls)
    } catch {
      // silencieux
    }
  }, [])

  const loadPrograms = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      // `actif` transmis au backend : '' = toutes, 'active'/'inactive' bornent.
      const actifParam = statusFilter === '' ? undefined : statusFilter === 'active'
      let data
      if (keyword.trim()) {
        data = await searchPrograms(keyword.trim())
      } else if (departmentFilter) {
        data = await getProgramsByDepartment(Number(departmentFilter), { actif: actifParam })
      } else {
        data = await fetchPrograms({ actif: actifParam })
      }
      // Raffinements cote client pour combiner les filtres avec la recherche.
      if (departmentFilter) {
        data = data.filter((p) => String(p.departmentId) === String(departmentFilter))
      }
      if (statusFilter) {
        data = data.filter((p) => p.actif === (statusFilter === 'active'))
      }
      setPrograms(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des filières.')
    } finally {
      setLoading(false)
    }
  }, [keyword, departmentFilter, statusFilter])

  useEffect(() => {
    loadReferentials()
    loadPrograms()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadPrograms()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [departmentFilter, statusFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadPrograms()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', program: null })
  const openEdit = (program) => setFormModal({ open: true, mode: 'edit', program })
  const closeForm = () => setFormModal({ open: false, mode: 'create', program: null })

  const handleFormSuccess = () => {
    closeForm()
    loadPrograms()
  }

  // Desactivation : on precharge les compteurs d'impact pour les afficher dans
  // la confirmation. En cas d'echec du chargement, la confirmation reste
  // possible sans les compteurs (le backend applique la cascade de toute facon).
  const askDeactivate = async (program) => {
    setConfirmDialog({ open: true, type: 'deactivate', program, impact: null })
    try {
      const impact = await getProgramDeactivationImpact(program.id)
      setConfirmDialog((prev) =>
        prev.open && prev.program?.id === program.id ? { ...prev, impact } : prev
      )
    } catch {
      // compteurs indisponibles : on garde la confirmation generique
    }
  }
  const askActivate = (program) => setConfirmDialog({ open: true, type: 'activate', program, impact: null })
  const askDelete = (program) => setDeleteTarget(program)
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, program: null, impact: null })

  const handleConfirm = async () => {
    const { type, program } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateProgram(program.id)
      } else if (type === 'activate') {
        await activateProgram(program.id)
      }
      closeConfirm()
      loadPrograms()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer."
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  // Message de desactivation enrichi des compteurs d'impact strictement positifs.
  const deactivateMessage = () => {
    const nom = confirmDialog.program?.nom
    const impact = confirmDialog.impact
    const parts = []
    if (impact) {
      if (impact.promotions > 0) parts.push(`${impact.promotions} promotion(s)`)
      if (impact.groupes > 0) parts.push(`${impact.groupes} groupe(s)`)
    }
    const base = `Voulez-vous désactiver « ${nom} » ?`
    if (parts.length === 0) return base
    return `${base} ${parts.join(' et ')} actif(s) deviendront également indisponibles pour les nouvelles opérations. Les données historiques sont conservées.`
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver la filière',
      message: deactivateMessage(),
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer la filière',
      message: `Voulez-vous réactiver « ${confirmDialog.program?.nom} » ? Les promotions et groupes rattachés ne seront pas réactivés automatiquement.`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Filières</h1>
          <p className="text-sm text-ink/50 mt-1">{programs.length} filière(s)</p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouvelle filière
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
          value={departmentFilter}
          onChange={(e) => setDepartmentFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les départements</option>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>{d.code} — {d.nom}</option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les statuts</option>
          <option value="active">Actives</option>
          <option value="inactive">Inactives</option>
        </select>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        <div className="table-scroll-x overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Code</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Département</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau / Cycle</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Type de formation</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Description</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && programs.length === 0 && (
              <tr><td colSpan={8} className="text-center py-10 text-ink/40">Aucune filière trouvée</td></tr>
            )}
            {!loading && programs.map((program) => (
              <tr key={program.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-mono text-ink/80">{program.code}</td>
                <td className="px-4 py-3 font-medium text-ink">{program.nom}</td>
                <td className="px-4 py-3 text-ink/70">{program.departmentNom}</td>
                <td className="px-4 py-3 text-ink/70">{program.levelNom ?? '—'}</td>
                <td className="px-4 py-3 text-ink/60">
                  {program.levelTypeFormation ? typeFormationLabel(program.levelTypeFormation) : '—'}
                </td>
                <td className="px-4 py-3 text-ink/60 max-w-md whitespace-pre-line break-words">
                  {program.description || '—'}
                </td>
                <td className="px-4 py-3"><StatusBadge active={program.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(program)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {program.actif ? (
                    <button
                      onClick={() => askDeactivate(program)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(program)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(program)}
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
      </div>

      <ProgramFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.program}
        departments={departments}
        levels={levels}
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
        entityName="la filière"
        impactFn={getProgramDeletionImpact}
        deleteFn={deleteProgram}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadPrograms}
      />
    </div>
  )
}
