import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  OCCUPATION_CATEGORIES,
  OCCUPATION_TABS,
  occupationTabBySlug,
} from '../constants/occupationTypes'
import ExamensTab from '../components/occupations/ExamensTab'
import OccupationsTab from '../components/occupations/OccupationsTab'

// Module « Occupation supplémentaire » : toute occupation d'un espace en dehors
// de l'emploi du temps régulier — examens, soutenances et occupations ponctuelles
// diverses. Ce ne sont pas trois systèmes séparés : les trois onglets écrivent
// dans la même table et passent par le même moteur central de disponibilité, si
// bien qu'une salle occupée par l'un de ces événements n'est jamais proposée
// comme libre à la recherche ni à la réservation.
//
// L'onglet actif est porté par l'URL (`?onglet=examens|soutenances|autre`) : un
// lien profond de notification d'import ou un favori retombe sur le bon onglet.

const TAB_DESCRIPTIONS = {
  examens: 'Planning des examens : import du planning, salles et amphithéâtres occupés, '
    + 'contrôle des conflits de créneaux.',
  soutenances: 'Planning des soutenances : création ou import, choix de l’espace, date et '
    + 'horaires ; l’occupation est enregistrée et les conflits détectés.',
  autre: 'Occupations ponctuelles : événements, réunions, activités pédagogiques, activités '
    + 'de clubs, conférences et autres.',
}

export default function OccupationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('onglet')
  const activeTab = occupationTabBySlug(requested)

  // Un `?onglet=` absent ou inconnu est recadré sur l'onglet par défaut, pour que
  // l'URL affichée corresponde toujours au contenu affiché.
  useEffect(() => {
    if (requested !== activeTab.slug) {
      const next = new URLSearchParams(searchParams)
      next.set('onglet', activeTab.slug)
      setSearchParams(next, { replace: true })
    }
  }, [requested, activeTab.slug, searchParams, setSearchParams])

  const selectTab = (slug) => {
    const next = new URLSearchParams(searchParams)
    next.set('onglet', slug)
    setSearchParams(next)
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Occupation supplémentaire</h1>
        <p className="text-sm text-ink/50 mt-1">
          Occupation des espaces en dehors de l’emploi du temps régulier. Toute occupation
          enregistrée ici rend la salle indisponible sur son créneau, pour la recherche comme
          pour la réservation.
        </p>
      </div>

      {/* Mini-menu horizontal : les trois sous-parties du module. */}
      <div className="border-b border-ink/10 mb-5">
        <div role="tablist" aria-label="Sous-parties des occupations supplémentaires" className="flex flex-wrap gap-1 -mb-px">
          {OCCUPATION_TABS.map((tab) => {
            const isActive = tab.slug === activeTab.slug
            return (
              <button
                key={tab.slug}
                type="button"
                role="tab"
                id={`onglet-${tab.slug}`}
                aria-selected={isActive}
                aria-controls={`panneau-${tab.slug}`}
                onClick={() => selectTab(tab.slug)}
                className={`px-4 py-2.5 text-sm font-medium rounded-t-lg border-b-2 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-signal ${
                  isActive
                    ? 'border-signal text-heading bg-signal/5'
                    : 'border-transparent text-ink/55 hover:text-ink hover:bg-ink/[0.03]'
                }`}
              >
                {tab.label}
              </button>
            )
          })}
        </div>
      </div>

      <div
        role="tabpanel"
        id={`panneau-${activeTab.slug}`}
        aria-labelledby={`onglet-${activeTab.slug}`}
      >
        <p className="text-sm text-ink/55 mb-5">{TAB_DESCRIPTIONS[activeTab.slug]}</p>

        {/* `key` : changer d'onglet remonte un composant neuf, sans état résiduel. */}
        {activeTab.categorie === OCCUPATION_CATEGORIES.EXAMEN ? (
          <ExamensTab key="examens" />
        ) : (
          <OccupationsTab key={activeTab.slug} categorie={activeTab.categorie} />
        )}
      </div>
    </div>
  )
}
