import { spaceTypeLabel } from '../constants/spaceTypes'
import { availStatus, availAffichage } from '../constants/availabilityStatus'
import { formatDuree } from '../constants/reservationHours'
import { IconX, IconCheckCircle, IconClock, IconBookmark } from './icons'

const row = 'flex justify-between gap-4 py-2 border-b border-ink/5 last:border-0'
const key = 'text-sm text-ink/50'
const val = 'text-sm text-ink/80 text-right'

export default function AvailableSpaceDetailsModal({ open, space, onClose, onReserve }) {
  if (!open || !space) return null
  const s = space
  const st = availStatus(s)
  const affichage = availAffichage(s)
  const periodes = Array.isArray(s.periodesLibres) ? s.periodesLibres : []
  // Creneaux officiels entierement contenus dans une periode libre : reservables
  // en un clic, en alternative a la saisie de deux heures (§5).
  const creneaux = Array.isArray(s.creneauxProposes) ? s.creneauxProposes : []

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">Détails de l'espace</h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        {/* Etat de disponibilite (§13) : distinct selon DISPONIBLE / EN_ATTENTE /
            INDISPONIBLE. On n'affiche jamais un simple « Disponible » pour une
            demande en attente. */}
        <div className="flex flex-wrap items-center gap-2 mb-4">
          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${st.badgeClass}`}>
            <IconCheckCircle className="w-3.5 h-3.5" />
            {st.label}
          </span>
          {affichage && (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
              <IconClock className="w-3.5 h-3.5" />
              {affichage}
            </span>
          )}
        </div>

        {/* Motif non nominatif (§16) : pourquoi le créneau est en attente ou
            indisponible, sans jamais exposer d'information personnelle. */}
        {s.motifIndisponibilite && (
          <p className="mb-4 text-sm text-ink/60 bg-ink/[0.03] border border-ink/10 rounded-lg px-3 py-2">
            {s.motifIndisponibilite}
          </p>
        )}

        <div>
          <div className={row}><span className={key}>Espace</span><span className={val}>{s.code} — {s.nom}</span></div>
          <div className={row}><span className={key}>Type</span><span className={val}>{spaceTypeLabel(s.type)}</span></div>
          <div className={row}><span className={key}>Capacité</span><span className={val}>{s.capacite ?? '—'} place(s)</span></div>
          <div className={row}><span className={key}>Bâtiment</span><span className={val}>{s.buildingCode} — {s.buildingNom}</span></div>
          <div className={row}>
            <span className={key}>Étage</span>
            <span className={val}>{s.floorNom}{s.floorNumero != null ? ` (niveau ${s.floorNumero})` : ''}</span>
          </div>
          <div className={row}>
            <span className={key}>Prochaine occupation</span>
            <span className={val}>
              {s.prochaineOccupation
                ? `${s.prochaineOccupation}${s.prochaineOccupationSource ? ` · ${s.prochaineOccupationSource}` : ''}`
                : 'Aucune'}
            </span>
          </div>
        </div>

        {/* Périodes réellement libres de la journée (§8, §11). Chaque puce est un
            bloc CONTINU : deux puces ne s'additionnent jamais (§7). Cliquer une
            puce ouvre la réservation bornée à cette période (§5). */}
        {periodes.length > 0 && (
          <div className="mt-5">
            <p className="text-sm text-ink/50 mb-2">
              Périodes libres
              {s.plusLongueDureeMinutes > 0 && (
                <span className="ml-2 text-xs text-ink/40">
                  · plus long bloc continu : {formatDuree(s.plusLongueDureeMinutes)}
                </span>
              )}
            </p>
            <div className="flex flex-wrap gap-2">
              {periodes.map((p, i) => {
                const chip = (
                  <>
                    <IconClock className="w-3.5 h-3.5" />
                    {p.label || `${p.heureDebut} → ${p.heureFin}`}
                    {p.dureeMinutes > 0 && (
                      <span className="text-emerald-600/70">({formatDuree(p.dureeMinutes)})</span>
                    )}
                  </>
                )
                const cls =
                  'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200'
                return onReserve && st.canReserve ? (
                  <button
                    key={`${p.heureDebut}-${p.heureFin}-${i}`}
                    type="button"
                    onClick={() => onReserve(s, { heureDebut: p.heureDebut, heureFin: p.heureFin })}
                    className={`${cls} hover:bg-emerald-100 transition-colors`}
                    title="Réserver sur cette période"
                  >
                    {chip}
                  </button>
                ) : (
                  <span key={`${p.heureDebut}-${p.heureFin}-${i}`} className={cls}>
                    {chip}
                  </span>
                )
              })}
            </div>
          </div>
        )}

        {/* Créneaux officiels proposables tels quels (§5) : l'utilisateur n'a pas
            à saisir deux heures, il reprend la logique de créneaux de l'app. */}
        {creneaux.length > 0 && onReserve && st.canReserve && (
          <div className="mt-4">
            <p className="text-sm text-ink/50 mb-2">Créneaux réservables en un clic</p>
            <div className="flex flex-wrap gap-2">
              {creneaux.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  title={c.nom || undefined}
                  onClick={() => onReserve(s, { heureDebut: c.heureDebut, heureFin: c.heureFin })}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium bg-signal/10 text-heading/80 border border-signal/25 hover:bg-signal/20 transition-colors"
                >
                  <IconBookmark className="w-3.5 h-3.5" />
                  {c.label || `${c.heureDebut} → ${c.heureFin}`}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="mt-5">
          <p className="text-sm text-ink/50 mb-2">
            Équipements ({s.nombreEquipements})
            {s.equipementsCorrespondants > 0 && (
              <span className="ml-2 text-xs text-emerald-700">
                · {s.equipementsCorrespondants} correspondant(s)
              </span>
            )}
          </p>
          {s.equipments && s.equipments.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {s.equipments.map((eq) => (
                <span
                  key={eq.id}
                  className="px-2.5 py-1 rounded-lg text-xs font-medium bg-ink/[0.04] text-ink/70 border border-ink/10"
                >
                  {eq.nom}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-ink/40">Aucun équipement associé</p>
          )}
        </div>

        <div className="flex justify-end gap-3 pt-6">
          <button onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
            Fermer
          </button>
          {onReserve && st.canReserve && (
            <button
              onClick={() => onReserve(s)}
              className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white rounded-lg transition-colors shadow-sm ${
                st.key === 'EN_ATTENTE' ? 'bg-amber-500 hover:bg-amber-500/90' : 'bg-signal hover:bg-signal/90'
              }`}
            >
              <IconBookmark className="w-4 h-4" />
              {st.key === 'EN_ATTENTE' ? 'Demander quand même' : 'Réserver cet espace'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
