import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchSettings } from '../api/settingsApi'
import AffichageTab from '../components/settings/AffichageTab'
import ConfigurationInitialeTab from '../components/settings/ConfigurationInitialeTab'
import HorairesTab from '../components/settings/HorairesTab'
import ImportsTab from '../components/settings/ImportsTab'
import NotificationsTab from '../components/settings/NotificationsTab'
import ReservationsTab from '../components/settings/ReservationsTab'
import SecuriteTab from '../components/settings/SecuriteTab'
import UniversiteTab from '../components/settings/UniversiteTab'
import { SETTINGS_TABS, settingsTabBySlug } from '../constants/settingsOptions'
import { useSettings } from '../context/SettingsContext'

// Page « Paramètres » (Module 11). Réservée à l'ADMIN : la route la protège, et
// le backend refuse de toute façon toute écriture venant d'un autre rôle — le
// masquage du menu n'est jamais la sécurité.
//
// Un seul appel `GET /api/settings` alimente les onglets de configuration.
// Chaque section enregistre indépendamment via son propre endpoint et renvoie la
// portion enregistrée : on remplace alors cette portion dans l'état local plutôt
// que de recharger la page entière, ce qui évite de perdre les saisies en cours
// des autres onglets. L'onglet « Configuration initiale » est un simple guide de
// mise en route (aucun formulaire, aucune donnée créée depuis la page).

// Descriptions affichées sous chaque onglet. Le libellé de l'onglet lui-même
// vient de SETTINGS_TABS (`tab.label`).
const TAB_DESCRIPTIONS = {
  universite:
    'Identité de l’établissement, identité visuelle et rappel du contexte académique.',
  reservations: 'Règles appliquées à chaque demande de réservation et à chaque séance.',
  horaires: 'Bornes de la journée universitaire et jours d’ouverture.',
  securite:
    'Politique des mots de passe et verrouillage des comptes après échecs de connexion.',
  notifications: 'Interrupteurs des notifications applicatives, des e-mails et des rappels.',
  imports: 'Contrôles appliqués aux fichiers Excel avant tout import.',
  affichage: 'Couleurs de l’établissement, thème par défaut et préférences d’affichage.',
  demarrage:
    'Guide de mise en route : les étapes, dans l’ordre, pour configurer l’université de zéro.',
}

export default function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('onglet')
  const activeTab = settingsTabBySlug(requested)
  const { refresh } = useSettings()

  const [settings, setSettings] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const charger = useCallback(() => {
    setLoading(true)
    setLoadError('')
    return fetchSettings()
      .then(setSettings)
      .catch((err) => setLoadError(err.response?.data?.message || 'LOAD_ERROR'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    charger()
  }, [charger])

  // Recadrage de l'URL : un `?onglet=` absent ou inconnu retombe sur le premier.
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

  /**
   * Une section vient d'être enregistrée : on adopte la réponse du serveur (qui
   * est normalisée) et on rafraîchit l'identité visuelle globale, car le nom, le
   * logo et les couleurs sont affichés hors de cette page.
   */
  const onSaved = useCallback(
    (section, dto) => {
      setSettings((precedent) => (precedent ? { ...precedent, [section]: dto } : precedent))
      refresh()
    },
    [refresh]
  )

  /**
   * Logo ou favicon ajouté, remplacé ou supprimé : on relit les métadonnées du
   * média et on rafraîchit l'identité visuelle affichée hors de cette page.
   * BrandingMedia signale lui-même ses propres erreurs d'envoi.
   */
  const onMediaChanged = useCallback(() => {
    charger()
    refresh()
  }, [charger, refresh])

  return (
    <div>
      <div className="mb-6">
        <h1 className="font-display text-2xl font-semibold text-ink">Paramètres</h1>
        <p className="text-sm text-ink/50 mt-1">
          Configuration de l’établissement. Ces réglages sont appliqués immédiatement par
          l’application : réservations, connexion, imports, notifications et affichage.
        </p>
      </div>

      <div className="border-b border-ink/10 mb-5">
        <div
          role="tablist"
          aria-label="Sections des paramètres"
          className="flex flex-wrap gap-1 -mb-px"
        >
          {SETTINGS_TABS.map((tab) => {
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

        {loading && (
          <div className="bg-surface border border-ink/10 rounded-xl p-8 text-center text-sm text-ink/50">
            Chargement des paramètres…
          </div>
        )}

        {!loading && loadError && (
          <div className="bg-surface border border-ink/10 rounded-xl p-6">
            <p className="text-sm text-red-600">
              {loadError === 'LOAD_ERROR'
                ? 'Les paramètres n’ont pas pu être chargés. Vérifiez votre connexion puis réessayez.'
                : loadError}
            </p>
            <button
              type="button"
              onClick={charger}
              className="mt-3 px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
            >
              Réessayer
            </button>
          </div>
        )}

        {/* `key` : changer d'onglet remonte un composant neuf, sans saisie résiduelle. */}
        {/* La « Configuration initiale » est un guide autonome : elle ne dépend
            pas des paramètres chargés et reste consultable même en cas d'erreur. */}
        {activeTab.slug === 'demarrage' ? (
          <ConfigurationInitialeTab />
        ) : (
          !loading && !loadError && settings && (
            <SettingsTabPanel
              slug={activeTab.slug}
              settings={settings}
              onSaved={onSaved}
              onMediaChanged={onMediaChanged}
            />
          )
        )}
      </div>
    </div>
  )
}

/** Aiguillage vers l'onglet demandé, monté avec une clé propre à la section. */
function SettingsTabPanel({ slug, settings, onSaved, onMediaChanged }) {
  switch (slug) {
    case 'reservations':
      return <ReservationsTab key="reservations" settings={settings} onSaved={onSaved} />
    case 'horaires':
      return <HorairesTab key="horaires" settings={settings} onSaved={onSaved} />
    case 'securite':
      return <SecuriteTab key="securite" settings={settings} onSaved={onSaved} />
    case 'notifications':
      return <NotificationsTab key="notifications" settings={settings} onSaved={onSaved} />
    case 'imports':
      return <ImportsTab key="imports" settings={settings} onSaved={onSaved} />
    case 'affichage':
      return <AffichageTab key="affichage" settings={settings} onSaved={onSaved} />
    default:
      return (
        <UniversiteTab
          key="universite"
          settings={settings}
          onSaved={onSaved}
          onMediaChanged={onMediaChanged}
        />
      )
  }
}
