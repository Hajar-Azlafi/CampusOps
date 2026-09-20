import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { BrandInitials, BrandLogo, BrandName } from '../Brand'
import {
  IconHome,
  IconUsers,
  IconBuilding,
  IconLayers,
  IconDoor,
  IconTool,
  IconX,
  IconGrid,
  IconAcademicCap,
  IconBookOpen,
  IconClock,
  IconClipboard,
  IconClipboardCheck,
  IconCalendar,
  IconCalendarRange,
  IconBookmark,
  IconSearchLocation,
  IconFileText,
  IconBell,
  IconHistory,
  IconSettings,
  IconShield,
  IconPanelLeftClose,
  IconPanelLeftOpen,
  IconLogOut,
} from '../icons'

// Navigation regroupee par domaine fonctionnel afin de reduire la longueur du
// menu. Chaque section masque ses entrees non autorisees selon le role. Les
// libelles sont en francais ; la structure reste statique.
const navSections = [
  {
    title: null,
    items: [{ to: '/dashboard', label: 'Tableau de bord', icon: IconHome, roles: ['ADMIN'] }],
  },
  {
    title: 'Réservations',
    items: [
      { to: '/availability', label: 'Rechercher & Réserver', icon: IconSearchLocation, roles: null },
      { to: '/reservations', label: 'Réservations', icon: IconBookmark, roles: null },
    ],
  },
  {
    title: 'Administration',
    items: [
      { to: '/users', label: 'Utilisateurs', icon: IconUsers, roles: ['ADMIN'] },
      { to: '/departments', label: 'Départements', icon: IconGrid, roles: ['ADMIN'] },
      { to: '/programs', label: 'Filières', icon: IconAcademicCap, roles: ['ADMIN'] },
      { to: '/responsables', label: 'Responsables RP', icon: IconShield, roles: ['ADMIN'] },
      { to: '/levels', label: 'Niveaux', icon: IconLayers, roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Infrastructure',
    items: [
      { to: '/buildings', label: 'Bâtiments', icon: IconBuilding, roles: ['ADMIN'] },
      { to: '/floors', label: 'Étages', icon: IconLayers, roles: ['ADMIN'] },
      { to: '/spaces', label: 'Espaces', icon: IconDoor, roles: ['ADMIN'] },
      { to: '/equipments', label: 'Équipements', icon: IconTool, roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Académique',
    items: [
      { to: '/academic-years', label: 'Années universitaires', icon: IconCalendarRange, roles: ['ADMIN'] },
      { to: '/semesters', label: 'Semestres', icon: IconBookOpen, roles: ['ADMIN'] },
      { to: '/groups', label: 'Groupes', icon: IconUsers, roles: ['ADMIN'] },
      { to: '/time-slots', label: 'Créneaux horaires', icon: IconClock, roles: ['ADMIN'] },
      { to: '/calendrier', label: 'Jours fériés', icon: IconCalendarRange, roles: ['ADMIN'] },
      { to: '/types-seances', label: 'Types de séances', icon: IconClipboard, roles: ['ADMIN'] },
      { to: '/modules', label: 'Modules', icon: IconBookOpen, roles: ['ADMIN'] },
      { to: '/timetables', label: 'Emplois du temps', icon: IconCalendar, roles: ['ADMIN'] },
      { to: '/occupations', label: 'Occupation supplémentaire', icon: IconClipboardCheck, roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Suivi',
    items: [
      { to: '/reports', label: 'Rapports', icon: IconFileText, roles: ['ADMIN'] },
      { to: '/notifications', label: 'Notifications', icon: IconBell, roles: null },
      { to: '/history', label: 'Mon historique', icon: IconHistory, roles: null },
      { to: '/audit', label: 'Journal d’audit', icon: IconShield, roles: ['ADMIN'] },
    ],
  },
]

const STORAGE_KEY = 'campusops.sidebar.collapsed'
const WIDTH_KEY = 'campusops.sidebar.width'

// Largeur du menu deplie. Le defaut passe de 256 a 288 px : les libelles les
// plus longs du menu ("Occupation supplementaire", "Annees universitaires")
// etaient tronques. Au-dela, l'utilisateur ajuste lui-meme en glissant le bord
// droit, et la valeur est memorisee par navigateur.
const WIDTH_DEFAULT = 288
const WIDTH_MIN = 224
const WIDTH_MAX = 420
const WIDTH_COMPACT = 64
const WIDTH_STEP = 16

const clampWidth = (px) => Math.min(WIDTH_MAX, Math.max(WIDTH_MIN, Math.round(px)))

// La largeur stockee est relue puis recadree : une valeur absente, illisible ou
// heritee d'anciennes bornes ne doit jamais produire un menu inutilisable.
function readStoredWidth() {
  const stored = Number(localStorage.getItem(WIDTH_KEY))
  return Number.isFinite(stored) && stored > 0 ? clampWidth(stored) : WIDTH_DEFAULT
}

// Navigation dediee au Responsable pedagogique (Section 10 du cahier des
// charges). Le RP ne voit ni les utilisateurs, ni les departements, ni les
// annees universitaires, ni la configuration systeme, ni les batiments/espaces
// globaux : uniquement le perimetre pedagogique de ses filieres.
const rpNavSections = [
  {
    title: null,
    items: [{ to: '/rp', label: 'Accueil', icon: IconHome, roles: null, end: true }],
  },
  {
    title: 'Pédagogie',
    items: [
      { to: '/rp/filieres', label: 'Mes filières', icon: IconAcademicCap, roles: null },
      { to: '/rp/groupes', label: 'Mes groupes', icon: IconUsers, roles: null },
      { to: '/rp/modules', label: 'Mes modules', icon: IconBookOpen, roles: null },
      { to: '/timetables', label: 'Emplois du temps', icon: IconCalendar, roles: null },
      { to: '/occupations', label: 'Occupation supplémentaire', icon: IconClipboardCheck, roles: null },
    ],
  },
  {
    title: 'Espaces',
    items: [
      { to: '/rp/salles', label: 'Consulter les salles', icon: IconDoor, roles: null },
      { to: '/availability', label: 'Disponibilités', icon: IconSearchLocation, roles: null },
      { to: '/rp/demandes', label: 'Demandes', icon: IconBookmark, roles: null },
    ],
  },
  {
    title: 'Suivi',
    items: [
      { to: '/rp/mes-demandes', label: 'Mes demandes', icon: IconFileText, roles: null },
      { to: '/notifications', label: 'Notifications', icon: IconBell, roles: null },
      { to: '/history', label: 'Mon historique', icon: IconHistory, roles: null },
    ],
  },
]

export default function Sidebar({ mobileOpen, onClose }) {
  const { user, logout } = useAuth()
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(STORAGE_KEY) === 'true'
  )
  const [width, setWidth] = useState(readStoredWidth)
  const [dragging, setDragging] = useState(false)
  const asideRef = useRef(null)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, String(collapsed))
  }, [collapsed])

  // On n'ecrit qu'une fois le glissement termine : pendant le drag, la largeur
  // change a chaque mouvement de souris et localStorage est synchrone.
  useEffect(() => {
    if (!dragging) localStorage.setItem(WIDTH_KEY, String(width))
  }, [width, dragging])

  // Pendant le glissement, le curseur reste "col-resize" partout et la selection
  // de texte est neutralisee, sinon le pointeur surligne le contenu traverse.
  useEffect(() => {
    if (!dragging) return undefined
    const previousCursor = document.body.style.cursor
    const previousSelect = document.body.style.userSelect
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    return () => {
      document.body.style.cursor = previousCursor
      document.body.style.userSelect = previousSelect
    }
  }, [dragging])

  // Pointer events + pointer capture : le suivi continue meme si le curseur
  // sort de la poignee ou depasse les bornes, et couvre souris comme stylet.
  const startResize = (event) => {
    if (collapsed) return
    event.preventDefault()
    event.currentTarget.setPointerCapture?.(event.pointerId)
    setDragging(true)
  }

  const moveResize = (event) => {
    if (!dragging) return
    const left = asideRef.current?.getBoundingClientRect().left ?? 0
    setWidth(clampWidth(event.clientX - left))
  }

  const endResize = (event) => {
    if (!dragging) return
    // Le navigateur relache la capture de lui-meme sur pointerup/pointercancel :
    // on ne la relache explicitement que si elle est encore active, sinon
    // releasePointerCapture leve une NotFoundError.
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    setDragging(false)
  }

  // L'affordance ne doit pas etre reservee a la souris : la poignee est
  // focusable et repond aux fleches ; Home revient a la largeur par defaut.
  const handleResizeKey = (event) => {
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      setWidth((w) => clampWidth(w - WIDTH_STEP))
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      setWidth((w) => clampWidth(w + WIDTH_STEP))
    } else if (event.key === 'Home') {
      event.preventDefault()
      setWidth(WIDTH_DEFAULT)
    }
  }

  const canSee = (roles) => !roles || roles.includes(user?.role)

  // Le Responsable pedagogique dispose d'un menu dedie et strictement limite a
  // son perimetre ; tous les autres roles conservent la navigation standard.
  const sections =
    user?.role === 'RESPONSABLE_PEDAGOGIQUE' ? rpNavSections : navSections

  const visibleSections = sections
    .map((section) => ({ ...section, items: section.items.filter((i) => canSee(i.roles)) }))
    .filter((section) => section.items.length > 0)

  // Tooltip affiche a droite d'un element lorsque le menu est reduit.
  const tooltip = (label) => (
    <span className="pointer-events-none absolute left-full ml-3 z-50 hidden group-hover:block whitespace-nowrap rounded-md bg-inverse px-2.5 py-1.5 text-xs font-medium text-white shadow-lg">
      {label}
    </span>
  )

  // Contenu du menu ; `compact` = icones seules + tooltips (mode reduit).
  // `showToggle` = affiche le bouton integre de reduction/agrandissement (bureau).
  const renderContent = (compact, showToggle = false) => (
    <div className="h-full flex flex-col bg-nav text-white">
      <div
        className={`shrink-0 border-b border-white/10 flex items-center ${
          compact ? 'flex-col gap-3 px-2 py-4' : 'justify-between gap-2 px-6 py-6'
        }`}
      >
        {/* Identité de l'établissement (§11) : le logo téléversé remplace le
            logotype par défaut, et le nom court sert ici car la place est
            comptée. Sans configuration, l'affichage d'origine est conservé. */}
        {compact ? (
          <BrandLogo
            className="h-8 w-8 object-contain"
            fallback={
              <p className="font-display text-xl font-semibold text-center">
                <BrandInitials accentClass="text-signal-light" />
              </p>
            }
          />
        ) : (
          <div className="flex items-center gap-2.5 min-w-0">
            <BrandLogo className="h-9 w-9 shrink-0 object-contain" />
            <div className="min-w-0">
              <p className="font-display text-xl font-semibold truncate">
                <BrandName court accentClass="text-signal-light" />
              </p>
              <p className="font-mono text-[10px] tracking-[0.15em] text-blueprint-line/60 uppercase mt-1 truncate">
                Gestion des espaces
              </p>
            </div>
          </div>
        )}
        {showToggle && (
          <button
            type="button"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? 'Agrandir le menu' : 'Réduire le menu'}
            title={collapsed ? 'Agrandir le menu' : 'Réduire le menu'}
            className="group relative flex h-8 w-8 items-center justify-center rounded-lg text-blueprint-line/70 hover:bg-white/10 hover:text-white transition-colors"
          >
            {collapsed ? (
              <IconPanelLeftOpen className="w-5 h-5" />
            ) : (
              <IconPanelLeftClose className="w-5 h-5" />
            )}
          </button>
        )}
      </div>

      <nav className="scroll-on-dark flex-1 overflow-y-auto overscroll-contain px-3 py-4 space-y-4" style={{ overflowX: 'hidden' }}>
        {visibleSections.map((section, idx) => (
          <div key={section.title ?? `top-${idx}`} className="space-y-1">
            {section.title &&
              (compact ? (
                <div className="mx-2 my-2 border-t border-white/10" />
              ) : (
                <p className="px-3 pt-1 pb-1 font-mono text-[10px] tracking-[0.15em] uppercase text-blueprint-line/40">
                  {section.title}
                </p>
              ))}
            {section.items.map(({ to, label, icon: Icon, end }) => {
              return (
              <NavLink
                key={to}
                to={to}
                end={end}
                onClick={onClose}
                // En mode reduit, l'infobulle maison suffit. Deplie, un `title`
                // natif garantit qu'un libelle encore tronque reste lisible au
                // survol, quelle que soit la largeur choisie.
                title={compact ? undefined : label}
                className={({ isActive }) =>
                  `group relative flex items-center rounded-lg text-sm font-medium transition-colors ${
                    compact ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2.5'
                  } ${
                    isActive
                      ? 'bg-signal/15 text-signal-light border-l-2 border-signal'
                      : 'text-blueprint-line/70 hover:bg-white/5 hover:text-white border-l-2 border-transparent'
                  }`
                }
              >
                <Icon className="w-4 h-4 shrink-0" />
                {compact ? tooltip(label) : <span className="truncate">{label}</span>}
              </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      <div className="shrink-0 border-t border-white/10 p-3">
        {canSee(['ADMIN']) && (
          <NavLink
            to="/settings"
            onClick={onClose}
            title={compact ? undefined : 'Paramètres'}
            className={({ isActive }) =>
              `group relative flex items-center w-full rounded-lg text-sm font-medium transition-colors mb-1 ${
                compact ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2.5'
              } ${
                isActive
                  ? 'bg-signal/15 text-signal-light border-l-2 border-signal'
                  : 'text-blueprint-line/70 hover:bg-white/5 hover:text-white border-l-2 border-transparent'
              }`
            }
          >
            <IconSettings className="w-4 h-4 shrink-0" />
            {compact ? tooltip('Paramètres') : <span className="truncate">Paramètres</span>}
          </NavLink>
        )}
        <button
          type="button"
          onClick={logout}
          className={`group relative flex items-center w-full rounded-lg text-sm font-medium text-blueprint-line/70 hover:bg-white/5 hover:text-white transition-colors ${
            compact ? 'justify-center px-2 py-2.5' : 'gap-3 px-3 py-2.5'
          }`}
        >
          <IconLogOut className="w-4 h-4 shrink-0" />
          {compact ? tooltip('Déconnexion') : <span>Déconnexion</span>}
        </button>
      </div>
    </div>
  )

  return (
    <>
      {/* Sidebar bureau : hauteur d'ecran fixe, scroll interne independant. */}
      <aside
        ref={asideRef}
        style={{ width: collapsed ? WIDTH_COMPACT : width }}
        className={`hidden lg:block shrink-0 sticky top-0 h-screen ${
          // L'animation est retiree pendant le glissement, sinon la largeur
          // "poursuit" le curseur avec un retard visible.
          dragging ? '' : 'transition-[width] duration-200'
        }`}
      >
        <div className="relative h-full">
          {renderContent(collapsed, true)}

          {/* Poignee de redimensionnement. Elle est volontairement decalee hors
              du menu (-right-1) : la barre de defilement interne mesure 10 px et
              une poignee posee dessus lui volerait ses glissements. */}
          {!collapsed && (
            <div
              role="separator"
              aria-orientation="vertical"
              aria-label="Redimensionner le menu"
              aria-valuenow={width}
              aria-valuemin={WIDTH_MIN}
              aria-valuemax={WIDTH_MAX}
              tabIndex={0}
              onPointerDown={startResize}
              onPointerMove={moveResize}
              onPointerUp={endResize}
              onPointerCancel={endResize}
              onKeyDown={handleResizeKey}
              onDoubleClick={() => setWidth(WIDTH_DEFAULT)}
              title="Glisser pour redimensionner · double-clic pour réinitialiser"
              className="group absolute inset-y-0 -right-1 z-20 w-2 cursor-col-resize touch-none focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-signal"
            >
              <span
                aria-hidden="true"
                className={`absolute inset-y-0 left-1/2 w-0.5 -translate-x-1/2 transition-colors ${
                  dragging
                    ? 'bg-signal'
                    : 'bg-transparent group-hover:bg-signal/50 group-focus-visible:bg-signal/50'
                }`}
              />
            </div>
          )}
        </div>
      </aside>

      {/* Sidebar mobile : overlay, toujours depliee. */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={onClose} />
          <aside
            className="absolute left-0 top-0 bottom-0"
            style={{ width: WIDTH_DEFAULT, maxWidth: '85vw' }}
          >
            <button
              type="button"
              onClick={onClose}
              aria-label="Fermer le menu"
              className="absolute right-3 top-3 z-50 text-white/70 hover:text-white"
            >
              <IconX className="w-5 h-5" />
            </button>
            {renderContent(false)}
          </aside>
        </div>
      )}
    </>
  )
}
