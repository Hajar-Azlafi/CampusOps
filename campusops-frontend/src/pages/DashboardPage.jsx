import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchDashboardStatistics } from '../api/dashboardApi'
import { useAuth } from '../context/AuthContext'
import { useAcademicYear } from '../context/AcademicYearContext'
import { roleLabel } from '../constants/roles'
import { spaceTypeLabel } from '../constants/spaceTypes'
import { reservationTypeLabel } from '../constants/reservationTypes'
import { weekDayLabel } from '../constants/weekDays'
import {
  StatCard,
  BarChart,
  LineChart,
  DonutChart,
  HBarList,
  OccupancyBar,
  GaugeRing,
  CHART_PALETTE,
} from '../components/charts'
import SessionTypeStatsCard from '../components/SessionTypeStatsCard'
import {
  IconBuilding,
  IconLayers,
  IconDoor,
  IconTool,
  IconUsers,
  IconBookmark,
  IconFileText,
  IconArrowRight,
  IconSearchLocation,
} from '../components/icons'

function Section({ title, subtitle, children, right }) {
  return (
    <section className="bg-surface border border-ink/10 rounded-xl p-5 mb-6">
      <div className="flex items-start justify-between mb-4 gap-3">
        <div>
          <h2 className="text-sm font-semibold text-ink">{title}</h2>
          {subtitle ? <p className="text-xs text-ink/50 mt-0.5">{subtitle}</p> : null}
        </div>
        {right}
      </div>
      {children}
    </section>
  )
}

function relabel(items, labelFn) {
  return (items ?? []).map((it) => ({ ...it, label: labelFn(it.key) || it.label }))
}

function plural(n, word) {
  const v = n ?? 0
  return `${v} ${word}${v > 1 ? 's' : ''}`
}

/**
 * Lignes des classements d'usage des espaces. Les espaces portent des noms
 * génériques et répétés (« Salle de cours » ×5) : le code et la localisation
 * sont indispensables pour savoir de quelle salle on parle, et le détail
 * séances / réservations pour comprendre d'où vient le total.
 */
function spaceUsageItems(list) {
  return (list ?? []).map((s) => ({
    key: s.spaceId,
    label: [s.nom, s.code].filter(Boolean).join(' · '),
    value: s.totalUtilisations ?? 0,
    hint: [
      [s.buildingNom, s.floorNom].filter(Boolean).join(' · '),
      `${plural(s.seances, 'séance')} · ${plural(s.reservations, 'réservation')}`,
    ]
      .filter(Boolean)
      .join(' — '),
  }))
}

/**
 * Lignes du classement des utilisateurs les plus actifs.
 *
 * La valeur classante est le nombre **total** de demandes ; le detail par statut
 * est indispensable pour lire le classement, car une demande refusee ou annulee
 * n'est pas la meme activite qu'une reservation reellement accordee.
 */
function userActivityItems(list) {
  return (list ?? []).map((u) => ({
    key: u.userId,
    label: u.nom,
    value: u.total ?? 0,
    hint: [
      roleLabel(u.role),
      `${plural(u.validees, 'validée')} · ${u.enAttente ?? 0} en attente · ${plural(u.nonAbouties, 'non aboutie')}`,
    ]
      .filter(Boolean)
      .join(' — '),
  }))
}

export default function DashboardPage() {
  const { user } = useAuth()
  const { selectedYearId, selectedYear } = useAcademicYear()
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    fetchDashboardStatistics(selectedYearId)
      .then(setStats)
      .catch(() => setError("Impossible de charger les statistiques du tableau de bord."))
      .finally(() => setLoading(false))
  }, [selectedYearId])

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">
            Bonjour, {user?.firstName}
          </h1>
          <p className="text-sm text-ink/50 mt-1">
            {roleLabel(user?.role)} · Tableau de bord
          </p>
        </div>
        <Link
          to="/reports"
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
        >
          <IconFileText className="w-4 h-4" />
          Rapports & exports
        </Link>
      </div>

      {loading ? (
        <p className="text-sm text-ink/40">Chargement des statistiques...</p>
      ) : error ? (
        <div className="border border-red-200 bg-red-50 text-red-700 rounded-xl p-4 text-sm">
          {error}
        </div>
      ) : stats ? (
        <DashboardContent stats={stats} yearLabel={selectedYear?.libelle} selectedYearId={selectedYearId} />
      ) : null}
    </div>
  )
}

function DashboardContent({ stats, yearLabel, selectedYearId }) {
  const { overview, espaces, reservations, temporel } = stats

  const typesEspaces = relabel(espaces?.repartitionParType, spaceTypeLabel)
  const typesReservations = relabel(reservations?.repartitionParType, reservationTypeLabel)
  const joursCharges = relabel(temporel?.joursLesPlusCharges, weekDayLabel)

  const plusUtilises = spaceUsageItems(espaces?.espacesLesPlusUtilises)
  const moinsUtilises = spaceUsageItems(espaces?.espacesLesMoinsUtilises)
  // Échelle commune aux deux classements : sans elle, le plus utilisé des
  // « moins utilisés » remplissait toute la barre et se lisait comme un record.
  const usageMax = Math.max(
    1,
    ...plusUtilises.map((d) => d.value),
    ...moinsUtilises.map((d) => d.value),
  )
  // Sous-titre calculé sur les mêmes données que le donut, pour qu'il ne puisse
  // pas annoncer un total différent de celui affiché au centre de l'anneau.
  const totalEspacesTypes = typesEspaces.reduce((sum, t) => sum + (t.value ?? 0), 0)

  // Classement des utilisateurs : le nombre d'utilisateurs actifs sert a dire
  // combien le top 5 n'affiche pas, pour qu'un classement court se distingue
  // d'un classement tronque.
  const utilisateursActifs = userActivityItems(reservations?.utilisateursLesPlusActifs)
  const autresUtilisateurs = Math.max(
    0,
    (reservations?.nombreUtilisateursActifs ?? 0) - utilisateursActifs.length,
  )
  const periode = yearLabel ? `l'année ${yearLabel}` : 'la période consultée'

  return (
    <>
      {/* ---- Vue d'ensemble ---- */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
        <StatCard label="Bâtiments" value={overview?.totalBatiments} tone="blue" icon={IconBuilding} />
        <StatCard label="Étages" value={overview?.totalEtages} tone="blue" icon={IconLayers} />
        <StatCard label="Espaces" value={overview?.totalEspaces} tone="default" icon={IconDoor} />
        <StatCard label="Équipements" value={overview?.totalEquipements} tone="default" icon={IconTool} />
        <StatCard label="Utilisateurs" value={overview?.totalUtilisateurs} tone="default" icon={IconUsers} />
        <StatCard label="Réservations" value={overview?.totalReservations} tone="blue" icon={IconBookmark} />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
        <StatCard label="Espaces actifs" value={overview?.espacesActifs} tone="emerald" />
        <StatCard label="Espaces inactifs" value={overview?.espacesInactifs} tone="muted" />
        <StatCard label="Enseignants" value={overview?.totalEnseignants} tone="purple" />
        <StatCard label="Responsables club" value={overview?.totalResponsablesClub} tone="purple" />
        <StatCard label="Aujourd'hui" value={overview?.reservationsAujourdhui} tone="amber" hint="Réservations" />
        <StatCard label="Cette semaine" value={overview?.reservationsCetteSemaine} tone="amber" hint={`Ce mois : ${overview?.reservationsCeMois ?? 0}`} />
      </div>

      {/* ---- Occupation des espaces ---- */}
      <Section
        title="Taux d'occupation"
        subtitle="Calculé à partir des séances planifiées actives (jours ouvrés × créneaux actifs)."
      >
        <div className="grid lg:grid-cols-[auto_1fr] gap-6 items-start">
          <div className="flex flex-col items-center justify-center py-2">
            <GaugeRing rate={espaces?.tauxOccupationGlobal?.tauxOccupation ?? 0} label="Global" />
            <p className="text-xs text-ink/50 mt-3 text-center">
              {espaces?.tauxOccupationGlobal?.creneauxOccupes ?? 0} /{' '}
              {espaces?.tauxOccupationGlobal?.creneauxDisponibles ?? 0} créneaux
            </p>
          </div>
          <div className="grid sm:grid-cols-2 gap-x-8 gap-y-3">
            <div>
              <p className="text-xs font-medium text-ink/60 mb-2">Par bâtiment</p>
              <div className="space-y-2.5">
                {(espaces?.tauxOccupationParBatiment ?? []).slice(0, 6).map((b) => (
                  <OccupancyBar
                    key={b.id}
                    label={b.nom}
                    rate={b.tauxOccupation}
                    hint={`${b.nombreEspaces} espace(s)`}
                  />
                ))}
                {!espaces?.tauxOccupationParBatiment?.length && (
                  <p className="text-sm text-ink/40">Aucune donnée disponible</p>
                )}
              </div>
            </div>
            <div>
              <p className="text-xs font-medium text-ink/60 mb-2">Par étage</p>
              <div className="space-y-2.5">
                {(espaces?.tauxOccupationParEtage ?? []).slice(0, 6).map((f) => (
                  <OccupancyBar
                    key={f.id}
                    label={f.nom}
                    rate={f.tauxOccupation}
                    hint={`${f.nombreEspaces} espace(s)`}
                  />
                ))}
                {!espaces?.tauxOccupationParEtage?.length && (
                  <p className="text-sm text-ink/40">Aucune donnée disponible</p>
                )}
              </div>
            </div>
          </div>
        </div>
      </Section>

      {/* ---- Répartition des séances par type (§6/§19) — données réelles ---- */}
      <div className="mb-6">
        <SessionTypeStatsCard
          context={{ academicYearId: selectedYearId }}
          subtitle={`Répartition réelle des séances planifiées actives sur ${periode}`}
        />
      </div>

      {/* ---- Usage des espaces : où se concentre l'occupation ---- */}
      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <Section
          title="Espaces les plus utilisés"
          subtitle="Top 5 des espaces qui cumulent le plus d'occupations sur l'année (séances de l'emploi du temps + réservations). Repère les salles saturées."
        >
          <HBarList data={plusUtilises} scaleMax={usageMax} color={CHART_PALETTE[1]} />
        </Section>
        <Section
          title="Espaces les moins utilisés"
          subtitle="Les 5 espaces actifs les moins occupés, à la même échelle que les plus utilisés. Repère les salles sous-exploitées, à réaffecter ou à proposer en priorité."
        >
          <HBarList data={moinsUtilises} scaleMax={usageMax} color={CHART_PALETTE[3]} />
        </Section>
      </div>

      <Section
        title="Répartition des espaces par type"
        subtitle={`${totalEspacesTypes} espaces actifs répartis en ${typesEspaces.length} types · Capacité totale : ${(espaces?.capaciteTotale ?? 0).toLocaleString('fr-FR')} places`}
      >
        <DonutChart data={typesEspaces} size={170} legendCols={4} showPercent />
      </Section>

      {/* ---- Réservations ---- */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
        <StatCard label="En attente" value={reservations?.enAttente} tone="amber" />
        <StatCard label="Validées" value={reservations?.approuvees} tone="emerald" />
        <StatCard label="Refusées" value={reservations?.refusees} tone="red" />
        <StatCard label="Annulées" value={reservations?.annulees} tone="muted" />
        <StatCard label="Terminées" value={reservations?.terminees} tone="default" />
      </div>

      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <Section
          title="Réservations par type"
          subtitle={`${reservations?.total ?? 0} demandes sur ${periode}, tous statuts confondus`}
        >
          <DonutChart data={typesReservations} size={160} showPercent />
        </Section>
        <Section
          title="Utilisateurs les plus actifs"
          subtitle={`Top 5 par nombre de demandes de réservation sur ${periode}, tous statuts confondus. Le détail sous chaque nom distingue les réservations réellement accordées.`}
        >
          <HBarList
            data={utilisateursActifs}
            color={CHART_PALETTE[4]}
            emptyLabel={`Aucune réservation enregistrée sur ${periode}`}
          />
          {autresUtilisateurs > 0 ? (
            <p className="text-[11px] text-ink/45 mt-3 pt-3 border-t border-ink/10">
              {autresUtilisateurs === 1
                ? "1 autre utilisateur a réservé sur cette période sans apparaître dans ce top 5."
                : `${autresUtilisateurs} autres utilisateurs ont réservé sur cette période sans apparaître dans ce top 5.`}
            </p>
          ) : utilisateursActifs.length > 0 ? (
            <p className="text-[11px] text-ink/45 mt-3 pt-3 border-t border-ink/10">
              Classement complet : {plural(reservations?.nombreUtilisateursActifs, 'utilisateur')} au
              total {(reservations?.nombreUtilisateursActifs ?? 0) > 1 ? 'ont' : 'a'} réservé sur{' '}
              {periode}.
            </p>
          ) : null}
        </Section>
      </div>

      {/* ---- Statistiques temporelles ---- */}
      <Section title="Activité mensuelle" subtitle={`Réservations mois par mois sur ${periode}`}>
        <LineChart data={temporel?.activiteMensuelle ?? []} color={CHART_PALETTE[0]} />
      </Section>

      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <Section title="Activité des 7 derniers jours" subtitle={`Bornée à ${periode}`}>
          <BarChart data={temporel?.activiteQuotidienne ?? []} color={CHART_PALETTE[1]} />
        </Section>
        <Section title="Activité des 8 dernières semaines" subtitle={`Bornée à ${periode}`}>
          <BarChart data={temporel?.activiteHebdomadaire ?? []} color={CHART_PALETTE[6]} />
        </Section>
      </div>

      <div className="grid lg:grid-cols-2 gap-6 mb-6">
        <Section title="Heures de forte occupation" subtitle="Par heure de début de réservation">
          <BarChart data={temporel?.heuresDeForteOccupation ?? []} color={CHART_PALETTE[2]} />
        </Section>
        <Section title="Jours les plus chargés">
          <BarChart data={joursCharges} color={CHART_PALETTE[4]} />
        </Section>
      </div>

      <div className="border border-dashed border-ink/15 rounded-xl p-6 text-center mb-2">
        <p className="text-sm text-ink/60 mb-3">
          Réservez un espace pédagogique disponible en quelques clics.
        </p>
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/availability"
            className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors shadow-sm"
          >
            <IconSearchLocation className="w-4 h-4" />
            Rechercher un espace disponible
            <IconArrowRight className="w-4 h-4" />
          </Link>
          <Link
            to="/reservations"
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-heading border border-heading/25 hover:bg-heading/5 rounded-lg transition-colors"
          >
            Réservations
          </Link>
        </div>
      </div>
    </>
  )
}

