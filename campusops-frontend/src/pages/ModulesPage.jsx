import { useEffect, useState, useCallback } from 'react'
import {
  fetchModules,
  deactivateModule,
  activateModule,
  deleteModule,
  getModuleDeletionImpact,
} from '../api/modulesApi'
import { fetchPrograms } from '../api/programsApi'
import { fetchSemesters } from '../api/semestersApi'
import { StatusBadge } from '../components/Badge'
import ModuleFormModal from '../components/ModuleFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

// Modules d'enseignement (§8-§14). Chaque module est ancré sur un contexte
// pédagogique = filière + semestre, et n'est jamais saisi en texte libre lors
// de la création d'une séance : il est choisi dans ce référentiel.

export default function ModulesPage() {
  const [modules, setModules] = useState([])
  const [programs, setPrograms] = useState([])
  const [semesters, setSemesters] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [filters, setFilters] = useState({ programId: '', semesterId: '', statut: '' })

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', entity: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: 'deactivate', entity: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  // Filières accessibles (scopées côté backend) : uniquement actives, le filtre
  // sert à cadrer la sélection de modules pour de nouvelles opérations.
  useEffect(() => {
    fetchPrograms({ actif: true })
      .then((data) => setPrograms(data || []))
      .catch(() => setPrograms([]))
  }, [])

  // Semestres du niveau de la filière filtrée (cascade du filtre).
  const filterProgram = programs.find((p) => String(p.id) === String(filters.programId))
  const filterLevelId = filterProgram?.levelId ?? null

  useEffect(() => {
    if (!filters.programId || !filterLevelId) {
      setSemesters([])
      return
    }
    let cancelled = false
    fetchSemesters({ levelId: filterLevelId })
      .then((data) => { if (!cancelled) setSemesters(data || []) })
      .catch(() => { if (!cancelled) setSemesters([]) })
    return () => { cancelled = true }
  }, [filters.programId, filterLevelId])

  const loadModules = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const actif = filters.statut === '' ? undefined : filters.statut === 'true'
      const data = await fetchModules({
        programId: filters.programId || undefined,
        semesterId: filters.semesterId || undefined,
        actif,
      })
      setModules(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des modules')
    } finally {
      setLoading(false)
    }
  }, [filters.programId, filters.semesterId, filters.statut])

  useEffect(() => {
    loadModules()
  }, [loadModules])

  const openCreate = () => setFormModal({ open: true, mode: 'create', entity: null })
  const openEdit = (entity) => setFormModal({ open: true, mode: 'edit', entity })
  const closeForm = () => setFormModal({ open: false, mode: 'create', entity: null })

  const handleFormSuccess = () => {
    closeForm()
    loadModules()
  }

  const askDeactivate = (entity) => setConfirmDialog({ open: true, type: 'deactivate', entity })
  const askActivate = (entity) => setConfirmDialog({ open: true, type: 'activate', entity })
  const askDelete = (entity) => setDeleteTarget(entity)
  const closeConfirm = () => setConfirmDialog({ open: false, type: 'deactivate', entity: null })

  const handleConfirm = async () => {
    const { type, entity } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') await deactivateModule(entity.id)
      else await activateModule(entity.id)
      closeConfirm()
      loadModules()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'opération a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le module',
      message: `Voulez-vous désactiver le module « ${confirmDialog.entity?.nom} » ? Il ne sera plus proposé lors de la création de séances (les séances existantes ne sont pas modifiées).`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Réactiver le module',
      message: `Voulez-vous réactiver le module « ${confirmDialog.entity?.nom} » ?`,
      confirmLabel: 'Réactiver',
      danger: false,
    },
  }[confirmDialog.type]

  const handleProgramFilter = (e) => {
    const value = e.target.value
    setFilters((f) => ({ ...f, programId: value, semesterId: '' }))
  }

  const kw = keyword.trim().toLowerCase()
  const visibleModules = kw
    ? modules.filter(
        (m) =>
          (m.nom || '').toLowerCase().includes(kw) ||
          (m.code || '').toLowerCase().includes(kw),
      )
    : modules

  const inputClass = 'px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal'

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Modules</h1>
          <p className="text-sm text-ink/50 mt-1">
            {visibleModules.length} module(s) · Référentiel par filière et semestre
          </p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau module
        </button>
      </div>

      <div className="flex flex-col sm:flex-row flex-wrap gap-3 mb-4">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="Rechercher par nom ou code..."
          className={`flex-1 min-w-[200px] ${inputClass}`}
        />
        <select value={filters.programId} onChange={handleProgramFilter} className={inputClass}>
          <option value="">Toutes les filières</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>{p.nom}{p.code ? ` (${p.code})` : ''}</option>
          ))}
        </select>
        <select
          value={filters.semesterId}
          onChange={(e) => setFilters((f) => ({ ...f, semesterId: e.target.value }))}
          disabled={!filters.programId || !filterLevelId}
          className={`${inputClass} disabled:bg-ink/5 disabled:text-ink/40`}
        >
          <option value="">Tous les semestres</option>
          {semesters.map((s) => (
            <option key={s.id} value={s.id}>{s.nom}</option>
          ))}
        </select>
        <select
          value={filters.statut}
          onChange={(e) => setFilters((f) => ({ ...f, statut: e.target.value }))}
          className={inputClass}
        >
          <option value="">Tous les statuts</option>
          <option value="true">Actifs</option>
          <option value="false">Inactifs</option>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Module</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Code</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Semestre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && visibleModules.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucun module</td></tr>
            )}
            {!loading && visibleModules.map((m) => (
              <tr key={m.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{m.nom}</td>
                <td className="px-4 py-3 text-ink/60 font-mono text-xs">{m.code || <span className="text-ink/30">—</span>}</td>
                <td className="px-4 py-3 text-ink/70">{m.programNom || '—'}</td>
                <td className="px-4 py-3 text-ink/70">{m.semesterNom || '—'}</td>
                <td className="px-4 py-3"><StatusBadge active={m.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(m)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {m.actif ? (
                    <button
                      onClick={() => askDeactivate(m)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(m)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-300 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Réactiver
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(m)}
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

      <ModuleFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.entity}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmContent?.title}
        message={confirmContent?.message}
        confirmLabel={confirmContent?.confirmLabel}
        danger={confirmContent?.danger}
        loading={actionLoading}
        onConfirm={handleConfirm}
        onCancel={closeConfirm}
      />

      <DeleteDialog
        target={deleteTarget}
        entityName="le module"
        impactFn={getModuleDeletionImpact}
        deleteFn={deleteModule}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadModules}
      />
    </div>
  )
}
