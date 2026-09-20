import { useEffect, useState, useCallback } from 'react'
import {
  fetchModules,
  deactivateModule,
  activateModule,
} from '../api/modulesApi'
import { getMyScope } from '../api/meApi'
import { fetchSemesters } from '../api/semestersApi'
import { StatusBadge } from '../components/Badge'
import ModuleFormModal from '../components/ModuleFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import { IconLock, IconBookOpen } from '../components/icons'

/**
 * "Mes modules" (§20) : le responsable pédagogique gère les modules de SES
 * filières (filière + semestre). Le périmètre est appliqué côté backend — les
 * appels ne renvoient/n'acceptent que ses filières. La création/modification
 * réutilise la même modale que l'administration (bornée au périmètre).
 */
export default function MesModulesPage() {
  const [modules, setModules] = useState([])
  const [programs, setPrograms] = useState([])
  const [semesters, setSemesters] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [filters, setFilters] = useState({ programId: '', semesterId: '', statut: '' })

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', entity: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: 'deactivate', entity: null })
  const [actionLoading, setActionLoading] = useState(false)

  // Filières du responsable pédagogique (banner + filtre + cascade semestre).
  useEffect(() => {
    getMyScope()
      .then((scope) => setPrograms(scope?.programs || []))
      .catch(() => setPrograms([]))
  }, [])

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
      setErrorMsg('Impossible de charger vos modules')
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

  const hasPrograms = programs.length > 0
  const inputClass = 'px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal'

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Mes modules</h1>
          <p className="text-sm text-ink/50 mt-1">
            {visibleModules.length} module(s) · Modules de vos filières, par semestre
          </p>
        </div>
        {hasPrograms && (
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            + Nouveau module
          </button>
        )}
      </div>

      <div className="mb-6 flex items-start gap-2.5 rounded-lg border border-blue-200 bg-blue-50/60 px-4 py-3 text-sm text-blue-800">
        <IconLock className="mt-0.5 h-4 w-4 shrink-0" />
        <p>
          Vous gérez les modules des filières dont vous avez la charge. Chaque module est rattaché
          à une filière et un semestre, et sera proposé lors de la création des séances.
        </p>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      {!loading && !hasPrograms ? (
        <div className="bg-surface border border-ink/10 rounded-xl py-16 text-center">
          <div className="w-14 h-14 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
            <IconBookOpen className="w-7 h-7 text-heading/30" />
          </div>
          <p className="mt-4 text-sm font-medium text-ink">Aucune filière ne vous est affectée.</p>
          <p className="mt-1 text-sm text-ink/50 max-w-sm mx-auto">
            Contactez l'administration pour qu'une ou plusieurs filières vous soient attribuées.
          </p>
        </div>
      ) : (
        <>
          <div className="flex flex-col sm:flex-row flex-wrap gap-3 mb-4">
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="Rechercher par nom ou code..."
              className={`flex-1 min-w-[200px] ${inputClass}`}
            />
            <select value={filters.programId} onChange={handleProgramFilter} className={inputClass}>
              <option value="">Toutes mes filières</option>
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
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

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
    </div>
  )
}
