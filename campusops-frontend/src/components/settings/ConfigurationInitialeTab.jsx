import { Link } from 'react-router-dom'

// Onglet « Configuration initiale » (§15-§19) : un GUIDE de mise en route, pas un
// formulaire. Il n'écrit aucune donnée et ne crée aucun CRUD ; chaque étape
// renvoie, dans l'ordre, vers un écran d'administration DÉJÀ EN PLACE. Les étapes
// internes aux Paramètres (Université, Horaires, Règles) pointent vers l'onglet
// concerné via `?onglet=`, les autres vers la page d'administration dédiée.
//
// Toutes les routes ciblées sont réservées à l'ADMIN (mêmes gardes que le reste
// de l'application) ; ce guide n'ouvre donc que des écrans déjà accessibles à
// l'administrateur connecté.
//
// Les libellés de chaque étape (titre + texte) sont portés directement par le
// tableau STEPS ci-dessous, en français.

/**
 * Étapes, dans l'ordre du cahier des charges (§15) :
 * Université → Bâtiments/Étages → Espaces/Équipements → Départements → Filières →
 * Groupes → Années/Semestres → Horaires → Règles de réservation.
 *
 * `key` identifie l'étape (clé React de la liste) ; `title` / `text` en portent le
 * libellé. `to` est une route existante ; `secondaryTo` ajoute un raccourci
 * complémentaire quand l'étape couvre deux écrans (ex. étages, équipements, semestres).
 */
const STEPS = [
  {
    key: 'universite',
    to: '/settings?onglet=universite',
    title: 'Renseigner l’université',
    text: 'Nom, coordonnées, fuseau horaire et identité visuelle, dans l’onglet Université.',
  },
  {
    key: 'batiments',
    to: '/buildings',
    secondaryTo: '/floors',
    title: 'Créer les bâtiments et les étages',
    text: 'Déclarez les bâtiments, puis leurs étages, qui accueilleront les espaces.',
  },
  {
    key: 'espaces',
    to: '/spaces',
    secondaryTo: '/equipments',
    title: 'Ajouter les espaces et leurs équipements',
    text: 'Salles, amphis et laboratoires, avec capacité et équipements (vidéoprojecteur, ordinateurs…).',
  },
  {
    key: 'departements',
    to: '/departments',
    title: 'Déclarer les départements',
    text: 'Les départements regroupent les filières (Informatique, Mathématiques…).',
  },
  {
    key: 'filieres',
    to: '/programs',
    title: 'Créer les filières',
    text: 'Les filières (avec leur cycle et niveau) rattachées à chaque département.',
  },
  {
    key: 'groupes',
    to: '/groups',
    title: 'Constituer les groupes',
    text: 'Niveaux, promotions puis groupes d’étudiants qui suivront les emplois du temps.',
  },
  {
    key: 'annees',
    to: '/academic-years',
    secondaryTo: '/semesters',
    title: 'Ouvrir l’année et les semestres',
    text: 'Année universitaire active et semestres correspondants pour dater les plannings.',
  },
  {
    key: 'horaires',
    to: '/settings?onglet=horaires',
    title: 'Régler les horaires',
    text: 'Plage d’ouverture, jours ouvrables, créneaux et jours fériés qui bornent les réservations.',
  },
  {
    key: 'regles',
    to: '/settings?onglet=reservations',
    title: 'Fixer les règles de réservation',
    text: 'Durées, délais et autorisations (week-end, hors horaires) appliqués à chaque demande.',
  },
]

/** Petite flèche « ouvrir » ; SVG inline pour ne dépendre d'aucun jeu d'icônes. */
function ArrowIcon() {
  return (
    <svg
      className="w-3.5 h-3.5"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 10h10M11 6l4 4-4 4" />
    </svg>
  )
}

/** Bouton « Ouvrir » discret, style secondaire cohérent avec la SaveBar. */
function OpenLink({ to, label }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ink/15 text-sm font-medium text-heading hover:bg-ink/5 hover:border-ink/25 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-signal"
    >
      {label}
      <ArrowIcon />
    </Link>
  )
}

export default function ConfigurationInitialeTab() {
  return (
    <div>
      <section className="bg-surface border border-ink/10 rounded-xl p-5 mb-5">
        <h2 className="font-display text-base font-semibold text-heading">
          Mettre en route CampusOps
        </h2>
        <p className="text-sm text-ink/60 mt-1.5 leading-relaxed">
          Ces étapes préparent l’application avant les premières réservations. Suivez-les dans l’ordre : chaque étape s’appuie sur la précédente. Aucune donnée n’est créée depuis cette page — chaque bouton ouvre l’écran d’administration correspondant, déjà en place.
        </p>
      </section>

      <ol className="space-y-3">
        {STEPS.map((step, index) => (
          <li
            key={step.key}
            className="bg-surface border border-ink/10 rounded-xl p-5 flex flex-col gap-3 sm:flex-row sm:items-start sm:gap-4"
          >
            {/* Pastille numérotée : rappelle l'ordre imposé des étapes. */}
            <span
              aria-hidden="true"
              className="shrink-0 inline-flex items-center justify-center w-8 h-8 rounded-full bg-signal/10 text-signal-dark text-sm font-semibold"
            >
              {index + 1}
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-ink/40">
                {`Étape ${index + 1}`}
              </p>
              <h3 className="font-display text-sm font-semibold text-heading mt-0.5">
                {step.title}
              </h3>
              <p className="text-sm text-ink/60 mt-1 leading-relaxed">
                {step.text}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <OpenLink to={step.to} label="Ouvrir" />
                {step.secondaryTo && (
                  <OpenLink to={step.secondaryTo} label="Ouvrir" />
                )}
              </div>
            </div>
          </li>
        ))}
      </ol>

      <p className="text-sm text-ink/55 mt-5 bg-signal/[0.06] border border-signal/15 rounded-xl px-4 py-3 leading-relaxed">
        Une fois ces étapes faites, l’université est prête : les enseignants et responsables peuvent rechercher un espace et demander une réservation.
      </p>
    </div>
  )
}
