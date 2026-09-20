import { useEffect, useState, useCallback } from 'react'
import {
  fetchSeanceTypes,
  deactivateSeanceType,
  activateSeanceType,
  deleteSeanceType,
  getSeanceTypeDeletionImpact,
} from '../api/seanceTypesApi'
import { StatusBadge } from '../components/Badge'
import TypeSeanceFormModal from '../components/TypeSeanceFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'

// Types de séance configurables (§7) : Cours, TD, TP, Examen, Contrôle,
// Soutenance, Autre — et tout type ajouté par l'administrateur. Le type de
// séance est distinct du type de présence (présentiel/distanciel) et de la
// session universitaire (normale/rattrapage).

export default function TypesSeancesPage() {
  const [types, setTypes] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', entity: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: 'deactivate', entity: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadTypes = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const actif = statusFilter === '' ? undefined : statusFilter === 'true'
      const data = await fetchSeanceTypes({ actif })
      setTypes(data)
    } catch {
      setErrorMsg('Impossible de charger les types de séance')
    } finally {
      setLoading(false)
    }
  }, [statusFilter])

  useEffect(() => {
    loadTypes()
  }, [loadTypes])

  const openCreate = () => setFormModal({ open: true, mode: 'create', entity: null })
  const openEdit = (entity) => setFormModal({ open: true, mode: 'edit', entity })
  const closeForm = () => setFormModal({ open: false, mode: 'create', entity: null })

  const handleFormSuccess = () => {
    closeForm()
    loadTypes()
  }

  const askDeactivate = (entity) => setConfirmDialog({ open: true, type: 'deactivate', entity })
  const askActivate = (entity) => setConfirmDialog({ open: true, type: 'activate', entity })
  const askDelete = (t) => setDeleteTarget(t)
  const closeConfirm = () => setConfirmDialog({ open: false, type: 'deactivate', entity: null })

  const handleConfirm = async () => {
    const { type, entity } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') await deactivateSeanceType(entity.id)
      else await activateSeanceType(entity.id)
      closeConfirm()
      loadTypes()
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
      title: 'Désactiver le type de séance',
      message: `Voulez-vous désactiver « ${confirmDialog.entity?.nom} » ? Il ne sera plus proposé lors de la création de séances (les séances existantes ne sont pas modifiées).`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Réactiver le type de séance',
      message: `Voulez-vous réactiver « ${confirmDialog.entity?.nom} » ? Il sera de nouveau proposé lors de la création de séances.`,
      confirmLabel: 'Réactiver',
      danger: false,
    },
  }[confirmDialog.type]

  const kw = keyword.trim().toLowerCase()
  const visibleTypes = kw
    ? types.filter(
        (t) =>
          (t.nom || '').toLowerCase().includes(kw) ||
          (t.code || '').toLowerCase().includes(kw),
      )
    : types

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Types de séance</h1>
          <p className="text-sm text-ink/50 mt-1">
            {visibleTypes.length} type(s) · Référentiel utilisé lors de la création des séances
          </p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau type de séance
        </button>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="Rechercher par nom ou code..."
          className="flex-1 px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
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
              <th className="text-left px-4 py-3 font-medium text-ink/50 w-20">Ordre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Code</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && visibleTypes.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucun type de séance</td></tr>
            )}
            {!loading && visibleTypes.map((t) => (
              <tr key={t.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 text-ink/40 tabular-nums">{t.ordre ?? '—'}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2.5">
                    <span
                      className="inline-block w-3 h-3 rounded-full border border-ink/10 shrink-0"
                      style={{ backgroundColor: t.couleur || 'transparent' }}
                    />
                    <span className="font-medium text-ink">{t.nom}</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-ink/60 font-mono text-xs">{t.code}</td>
                <td className="px-4 py-3"><StatusBadge active={t.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(t)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {t.actif ? (
                    <button
                      onClick={() => askDeactivate(t)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(t)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-300 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Réactiver
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(t)}
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

      <TypeSeanceFormModal
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
        entityName="le type de séance"
        impactFn={getSeanceTypeDeletionImpact}
        deleteFn={deleteSeanceType}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadTypes}
      />
    </div>
  )
}
