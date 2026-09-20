import { useEffect, useState, useCallback } from 'react'
import {
  fetchUsers,
  deactivateUser,
  reactivateUser,
} from '../api/usersApi'
import { fetchPrograms, assignResponsable } from '../api/programsApi'
import { StatusBadge } from '../components/Badge'
import ConfirmDialog from '../components/ConfirmDialog'
import { IconX, IconShield, IconAcademicCap } from '../components/icons'

const RP_ROLE = 'RESPONSABLE_PEDAGOGIQUE'

/**
 * Gestion des responsables pedagogiques (Section 13) — reservee a l'ADMIN.
 * L'admin conserve tous ses droits ; cet ecran ajoute la creation d'un
 * responsable (meme mecanisme d'auth, role verrouille sur RESPONSABLE_PEDAGOGIQUE,
 * Section 14) et l'affectation d'une ou plusieurs filieres (relation persistee
 * cote backend via Program.responsable, Section 11).
 */

/* ------------------------- Modale d'affectation ------------------------- */
function FilieresAssignModal({ open, rp, allPrograms, onClose, onSaved }) {
  const [selected, setSelected] = useState(() => new Set())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (open && rp) {
      const initial = new Set(
        allPrograms.filter((p) => p.responsableId === rp.id).map((p) => p.id)
      )
      setSelected(initial)
      setError('')
    }
  }, [open, rp, allPrograms])

  if (!open || !rp) return null

  const toggle = (id) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleSave = async () => {
    setSaving(true)
    setError('')
    const originallyMine = new Set(
      allPrograms.filter((p) => p.responsableId === rp.id).map((p) => p.id)
    )
    // Diff : filieres a rattacher (nouvellement cochees) et a detacher
    // (decochees qui lui appartenaient). On applique chaque changement puis on
    // recharge — le backend fait foi sur la relation persistee.
    const toAssign = [...selected].filter((id) => !originallyMine.has(id))
    const toUnassign = [...originallyMine].filter((id) => !selected.has(id))
    try {
      for (const id of toAssign) {
        await assignResponsable(id, rp.id)
      }
      for (const id of toUnassign) {
        await assignResponsable(id, null)
      }
      onSaved()
    } catch {
      setError("L'enregistrement a échoué, veuillez réessayer")
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={saving ? undefined : onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-start justify-between mb-1">
          <h3 className="font-display text-lg font-semibold text-ink">
            Filières de {rp.firstName} {rp.lastName}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>
        <p className="text-sm text-ink/50 mb-4">
          Cochez les filières dont ce responsable assure l'organisation pédagogique.
        </p>

        {allPrograms.length === 0 ? (
          <p className="text-sm text-ink/40 py-8 text-center">Aucune filière disponible.</p>
        ) : (
          <div className="space-y-1.5 max-h-[45vh] overflow-y-auto pr-1">
            {allPrograms.map((p) => {
              const checked = selected.has(p.id)
              const otherOwner =
                p.responsableId && p.responsableId !== rp.id
                  ? `${p.responsableFirstName ?? ''} ${p.responsableLastName ?? ''}`.trim()
                  : null
              return (
                <label
                  key={p.id}
                  className={`flex items-start gap-3 rounded-lg border px-3.5 py-2.5 cursor-pointer transition-colors ${
                    checked ? 'border-heading/40 bg-heading/[0.03]' : 'border-ink/10 hover:bg-ink/[0.015]'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggle(p.id)}
                    className="mt-0.5 h-4 w-4 accent-blueprint-800"
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-ink">{p.nom}</span>
                    <span className="block text-xs text-ink/50">
                      {[p.code, p.levelNom, p.departmentNom].filter(Boolean).join(' · ')}
                    </span>
                    {otherOwner && (
                      <span className="mt-0.5 block text-xs text-amber-700">
                        Actuellement : {otherOwner} — cocher réaffectera cette filière.
                      </span>
                    )}
                  </span>
                </label>
              )
            })}
          </div>
        )}

        {error && (
          <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mt-4">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-3 pt-6">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 disabled:opacity-50 rounded-lg transition-colors"
          >
            Annuler
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
          >
            {saving ? '...' : 'Enregistrer'}
          </button>
        </div>
      </div>
    </div>
  )
}

/* ------------------------------- Page ---------------------------------- */
export default function ResponsablesPage() {
  const [responsables, setResponsables] = useState([])
  const [programs, setPrograms] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [assignModal, setAssignModal] = useState({ open: false, rp: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, user: null })
  const [actionLoading, setActionLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const [users, allPrograms] = await Promise.all([
        fetchUsers({ role: RP_ROLE }),
        // Seules les filières actives sont proposables à l'affectation d'un
        // opérationnelle.
        fetchPrograms({ actif: true }),
      ])
      setResponsables(users)
      setPrograms(allPrograms)
    } catch {
      setErrorMsg('Impossible de charger les responsables pédagogiques')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const programsForRp = (rpId) => programs.filter((p) => p.responsableId === rpId)

  const openAssign = (rp) => setAssignModal({ open: true, rp })
  const closeAssign = () => setAssignModal({ open: false, rp: null })
  const handleAssignSaved = () => {
    closeAssign()
    load()
  }

  const askDeactivate = (user) => setConfirmDialog({ open: true, type: 'deactivate', user })
  const askActivate = (user) => setConfirmDialog({ open: true, type: 'activate', user })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, user: null })

  const handleConfirm = async () => {
    const { type, user } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') await deactivateUser(user.id)
      else if (type === 'activate') await reactivateUser(user.id)
      closeConfirm()
      load()
    } catch {
      setErrorMsg("L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le responsable',
      message: `Voulez-vous désactiver "${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName}" ? Il ne pourra plus se connecter.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Réactiver le responsable',
      message: `Voulez-vous réactiver "${confirmDialog.user?.firstName} ${confirmDialog.user?.lastName}" ?`,
      confirmLabel: 'Réactiver',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Responsables pédagogiques</h1>
        <p className="text-sm text-ink/50 mt-1">{responsables.length} responsable(s)</p>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Email</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filières</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && responsables.length === 0 && (
              <tr>
                <td colSpan={5} className="text-center py-12">
                  <div className="w-12 h-12 mx-auto rounded-full bg-heading/5 flex items-center justify-center">
                    <IconShield className="w-6 h-6 text-heading/30" />
                  </div>
                  <p className="mt-3 text-sm font-medium text-ink">Aucun responsable pédagogique.</p>
                  <p className="mt-1 text-sm text-ink/50">
                    Les responsables pédagogiques sont créés depuis la gestion des utilisateurs.
                  </p>
                </td>
              </tr>
            )}
            {!loading && responsables.map((rp) => {
              const rpPrograms = programsForRp(rp.id)
              return (
                <tr key={rp.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015] align-top">
                  <td className="px-4 py-3 font-medium text-ink">{rp.lastName} {rp.firstName}</td>
                  <td className="px-4 py-3 text-ink/70">{rp.email}</td>
                  <td className="px-4 py-3">
                    {rpPrograms.length === 0 ? (
                      <span className="text-xs text-ink/40">Aucune filière</span>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {rpPrograms.map((p) => (
                          <span
                            key={p.id}
                            className="inline-flex items-center gap-1 rounded-md bg-heading/5 px-2 py-0.5 text-xs font-medium text-heading"
                          >
                            <IconAcademicCap className="h-3 w-3" />
                            {p.code || p.nom}
                          </span>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3"><StatusBadge active={rp.isActive} /></td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button
                      onClick={() => openAssign(rp)}
                      className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                    >
                      Gérer les filières
                    </button>
                    {rp.isActive ? (
                      <button
                        onClick={() => askDeactivate(rp)}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                      >
                        Désactiver
                      </button>
                    ) : (
                      <button
                        onClick={() => askActivate(rp)}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                      >
                        Réactiver
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <FilieresAssignModal
        open={assignModal.open}
        rp={assignModal.rp}
        allPrograms={programs}
        onClose={closeAssign}
        onSaved={handleAssignSaved}
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
    </div>
  )
}
