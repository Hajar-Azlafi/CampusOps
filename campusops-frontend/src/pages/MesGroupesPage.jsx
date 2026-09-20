import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  fetchGroups,
  searchGroups,
  getGroupsByPromotion,
  getGroupsByAcademicYear,
  deactivateGroup,
  activateGroup,
} from '../api/groupsApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { StatusBadge } from '../components/Badge'
import GroupFormModal from '../components/GroupFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import { IconUsers } from '../components/icons'

/**
 * "Mes groupes" (Section 5) : le responsable pedagogique consulte, cree et
 * modifie les groupes de SES filieres uniquement. Le backend applique le
 * perimetre (assertProgramAccessible sur chaque operation ; les listes sont
 * filtrees par findByPromotion_Program_IdIn(myProgramIds)).
 *
 * La page est cadree sur UNE annee universitaire a la fois (par defaut l'annee
 * active), avec un selecteur permettant de consulter les annees passees. Cela
 * evite l'affichage « en double » d'une meme filiere presente sur plusieurs
 * annees. Les promotions proposees (filtre + formulaire) sont elles aussi
 * restreintes a l'annee selectionnee, et leur libelle rappelle l'annee.
 */
export default function MesGroupesPage() {
  const [groups, setGroups] = useState([])
  const [allPromotions, setAllPromotions] = useState([])
  const [academicYears, setAcademicYears] = useState([])
  const [selectedYearId, setSelectedYearId] = useState('')
  const [yearsInitialized, setYearsInitialized] = useState(false)
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [promotionFilter, setPromotionFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', group: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, group: null })
  const [actionLoading, setActionLoading] = useState(false)

  // Promotions restreintes a l'annee selectionnee : le backend renvoie toutes
  // les promotions du perimetre RP (toutes annees confondues), on filtre ici.
  const promotions = useMemo(
    () => allPromotions.filter((p) => String(p.academicYearId) === String(selectedYearId)),
    [allPromotions, selectedYearId],
  )

  const loadPromotions = useCallback(async () => {
    try {
      // Perimetre applique cote backend : seules les promotions des filieres du
      // responsable sont renvoyees.
      setAllPromotions(await fetchPromotions({ actif: true }))
    } catch {
      // silencieux : le formulaire indiquera l'absence de promotion
    }
  }, [])

  const loadYears = useCallback(async () => {
    try {
      const years = await fetchAcademicYears()
      setAcademicYears(years)
      const active = years.find((y) => y.actif) || years[0]
      setSelectedYearId(active ? String(active.id) : '')
    } catch {
      // silencieux : sans annee connue, on retombe sur la liste complete
    } finally {
      setYearsInitialized(true)
    }
  }, [])

  const loadGroups = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const actifParam = statusFilter === '' ? undefined : statusFilter === 'active'
      let data
      if (keyword.trim()) {
        data = await searchGroups(keyword.trim())
      } else if (promotionFilter) {
        data = await getGroupsByPromotion(Number(promotionFilter))
      } else if (selectedYearId) {
        data = await getGroupsByAcademicYear(Number(selectedYearId), { actif: actifParam })
      } else {
        data = await fetchGroups({ actif: actifParam })
      }
      // Cadrage sur l'annee selectionnee (recherche et liste globale incluses).
      if (selectedYearId) {
        data = data.filter((g) => String(g.academicYearId) === String(selectedYearId))
      }
      if (promotionFilter) {
        data = data.filter((g) => String(g.promotionId) === String(promotionFilter))
      }
      if (statusFilter) {
        data = data.filter((g) => g.actif === (statusFilter === 'active'))
      }
      setGroups(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des groupes')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, promotionFilter, selectedYearId])

  useEffect(() => {
    loadYears()
    loadPromotions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Recharge la liste des que l'annee est connue, puis a chaque changement
  // d'annee / de filtres (hors saisie de mot-cle, pilotee par le bouton).
  useEffect(() => {
    if (!yearsInitialized) return
    if (!keyword.trim()) loadGroups()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearsInitialized, selectedYearId, statusFilter, promotionFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadGroups()
  }

  const handleYearChange = (e) => {
    // Changer d'annee reinitialise le filtre promotion (les promotions different
    // d'une annee a l'autre).
    setSelectedYearId(e.target.value)
    setPromotionFilter('')
  }

  const noPromotions = promotions.length === 0

  const openCreate = () => setFormModal({ open: true, mode: 'create', group: null })
  const openEdit = (group) => setFormModal({ open: true, mode: 'edit', group })
  const closeForm = () => setFormModal({ open: false, mode: 'create', group: null })

  const handleFormSuccess = () => {
    closeForm()
    loadGroups()
  }

  const askDeactivate = (group) => setConfirmDialog({ open: true, type: 'deactivate', group })
  const askActivate = (group) => setConfirmDialog({ open: true, type: 'activate', group })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, group: null })

  const handleConfirm = async () => {
    const { type, group } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'deactivate') {
        await deactivateGroup(group.id)
      } else if (type === 'activate') {
        await activateGroup(group.id)
      }
      closeConfirm()
      loadGroups()
    } catch {
      setErrorMsg("L'action a échoué, veuillez réessayer")
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le groupe',
      message: `Voulez-vous désactiver "${confirmDialog.group?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer le groupe',
      message: `Voulez-vous réactiver "${confirmDialog.group?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Mes groupes</h1>
          <p className="text-sm text-ink/50 mt-1">
            {groups.length} groupe(s) · filières dont vous avez la charge
          </p>
        </div>
        <button
          onClick={openCreate}
          disabled={noPromotions}
          title={noPromotions ? "Aucune promotion disponible dans votre périmètre" : undefined}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau groupe
        </button>
      </div>

      {noPromotions && !loading && (
        <div className="mb-4 flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <IconUsers className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Aucune promotion n'est disponible pour vos filières sur l'année sélectionnée. Les
            promotions sont créées par l'administration ; une fois en place, vous pourrez y
            rattacher vos groupes.
          </p>
        </div>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-12 gap-3">
        <form onSubmit={handleSearchSubmit} className="min-w-0 flex flex-col sm:flex-row gap-2 sm:col-span-2 xl:col-span-6">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par nom ou code..."
            className="min-w-0 w-full flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="shrink-0 px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={selectedYearId}
          onChange={handleYearChange}
          title="Année universitaire"
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
        >
          {academicYears.length === 0 && <option value="">Toutes les années</option>}
          {academicYears.map((y) => (
            <option key={y.id} value={y.id}>
              {y.libelle}{y.actif ? ' (active)' : ''}
            </option>
          ))}
        </select>

        <select
          value={promotionFilter}
          onChange={(e) => setPromotionFilter(e.target.value)}
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
        >
          <option value="">Toutes les promotions</option>
          {promotions.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nom}{p.academicYearLibelle ? ` — ${p.academicYearLibelle}` : ''}
            </option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="min-w-0 w-full px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal xl:col-span-2"
        >
          <option value="">Tous les statuts</option>
          <option value="active">Actifs</option>
          <option value="inactive">Inactifs</option>
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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Filière</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && groups.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucun groupe trouvé</td></tr>
            )}
            {!loading && groups.map((group) => (
              <tr key={group.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{group.nom}</td>
                <td className="px-4 py-3 text-ink/70">{group.programNom || group.promotionNom}</td>
                <td className="px-4 py-3 text-ink/70">{group.levelNom || '—'}</td>
                <td className="px-4 py-3 text-ink/70">{group.academicYearLibelle || '—'}</td>
                <td className="px-4 py-3"><StatusBadge active={group.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(group)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {group.actif ? (
                    <button
                      onClick={() => askDeactivate(group)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(group)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <GroupFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.group}
        promotions={promotions}
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
    </div>
  )
}
