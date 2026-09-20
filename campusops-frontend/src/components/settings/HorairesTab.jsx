import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { updateWorkingHours } from '../../api/settingsApi'
import { WEEK_DAYS } from '../../constants/weekDays'
import { heureObligatoire, minutesDepuisMinuit } from '../../utils/settingsValidation'
import {
  ErrorBanner,
  FieldError,
  SaveBar,
  SettingsSection,
  SuccessBanner,
  TimeField,
  labelClass,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Horaires » (§6). Les jours et les bornes de la journée sont lus par
// le moteur de disponibilité et par la réservation. La grille de créneaux du
// module Créneaux horaires n'est PAS dupliquée ici : elle est rappelée en
// lecture seule, parce que ce sont les créneaux actifs qui font foi quand il en
// existe (§2).

function toForm(dto) {
  return {
    heureOuverture: dto?.heureOuverture ?? '',
    heureFermeture: dto?.heureFermeture ?? '',
    heureLimiteRecherche: dto?.heureLimiteRecherche ?? '',
    joursOuvrables: ordonner(dto?.joursOuvrables),
  }
}

/** Toujours dans l'ordre de la semaine : la comparaison « modifié » en dépend. */
function ordonner(jours) {
  const choisis = new Set(jours ?? [])
  return WEEK_DAYS.filter((jour) => choisis.has(jour.value)).map((jour) => jour.value)
}

function toPayload(values) {
  return {
    heureOuverture: values.heureOuverture,
    heureFermeture: values.heureFermeture,
    heureLimiteRecherche: values.heureLimiteRecherche,
    joursOuvrables: values.joursOuvrables,
  }
}

function validate(values) {
  const errors = {}
  const ajoute = (champ, message) => {
    if (message) errors[champ] = message
  }
  ajoute('heureOuverture', heureObligatoire(values.heureOuverture))
  ajoute('heureFermeture', heureObligatoire(values.heureFermeture))
  ajoute('heureLimiteRecherche', heureObligatoire(values.heureLimiteRecherche))

  const ouverture = minutesDepuisMinuit(values.heureOuverture)
  const fermeture = minutesDepuisMinuit(values.heureFermeture)
  const limite = minutesDepuisMinuit(values.heureLimiteRecherche)

  if (ouverture !== null && fermeture !== null && fermeture <= ouverture) {
    errors.heureFermeture = `L’heure de fermeture doit être postérieure à ${values.heureOuverture}.`
  }
  if (limite !== null && ouverture !== null && limite < ouverture) {
    errors.heureLimiteRecherche =
      `L’heure limite ne peut pas précéder l’ouverture (${values.heureOuverture}).`
  }
  if (!values.joursOuvrables.length) {
    errors.joursOuvrables = 'Sélectionnez au moins un jour ouvrable.'
  }
  return errors
}

export default function HorairesTab({ settings, onSaved }) {
  const onSave = useCallback(
    (values) =>
      updateWorkingHours(toPayload(values)).then((saved) => {
        onSaved('horaires', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const form = useSectionForm({ initial: toForm(settings.horaires), validate, onSave })
  const { values, setField, errors } = form
  const horaires = settings.horaires ?? {}

  const basculerJour = (jour) => {
    const actifs = new Set(values.joursOuvrables)
    if (actifs.has(jour)) actifs.delete(jour)
    else actifs.add(jour)
    setField('joursOuvrables', ordonner([...actifs]))
  }

  return (
    <>
      <form onSubmit={form.submit} noValidate>
        <SettingsSection
          title="Journée d’ouverture"
          description="Bornes de la journée universitaire et jours d’ouverture. Une réservation en dehors de ces bornes est refusée, sauf si l’onglet Réservations autorise explicitement le hors-horaires."
        >
          <SuccessBanner message={form.notice} />
          <ErrorBanner message={form.errorMsg} />
          <div className="grid gap-4 md:grid-cols-3">
            <TimeField
              label="Heure d’ouverture"
              required
              value={values.heureOuverture}
              onChange={(value) => setField('heureOuverture', value)}
              error={errors.heureOuverture}
            />
            <TimeField
              label="Heure de fermeture"
              required
              value={values.heureFermeture}
              onChange={(value) => setField('heureFermeture', value)}
              error={errors.heureFermeture}
            />
            <TimeField
              label="Clôture des recherches du jour"
              required
              value={values.heureLimiteRecherche}
              onChange={(value) => setField('heureLimiteRecherche', value)}
              error={errors.heureLimiteRecherche}
              hint="Passé cette heure, la journée en cours n’est plus proposée : la recherche démarre au lendemain."
            />
          </div>
          <div className="mt-5">
            <span className={labelClass}>
              Jours ouvrables
              <span className="text-red-600"> *</span>
            </span>
            <div className="flex flex-wrap gap-2">
              {WEEK_DAYS.map((jour) => {
                const actif = values.joursOuvrables.includes(jour.value)
                return (
                  <button
                    key={jour.value}
                    type="button"
                    aria-pressed={actif}
                    onClick={() => basculerJour(jour.value)}
                    className={`px-3 py-1.5 text-sm font-medium rounded-lg border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-signal ${
                      actif
                        ? 'border-signal bg-signal/10 text-heading'
                        : 'border-ink/15 text-ink/55 hover:bg-ink/[0.03]'
                    }`}
                  >
                    {jour.label}
                  </button>
                )
              })}
            </div>
            <FieldError message={errors.joursOuvrables} />
            {!errors.joursOuvrables && (
              <p className="text-xs text-ink/45 mt-1.5">
                Un jour non retenu est fermé : ni séance, ni réservation. Les fermetures exceptionnelles (fêtes, vacances) se gèrent dans{' '}
                <Link to="/calendrier" className="text-signal-dark hover:underline">
                  Calendrier des jours non ouvrables
                </Link>
                .
              </p>
            )}
          </div>
          <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={form.reset} />
        </SettingsSection>
      </form>
      <SettingsSection
        title="Grille de créneaux"
        description="Gérée par le module Créneaux horaires. Tant qu’elle contient des créneaux actifs, ce sont eux qui bornent la journée : les heures ci-dessus servent de repli et de garde-fou."
      >
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <span className={labelClass}>Amplitude des créneaux actifs</span>
            <p className="text-sm text-ink/70">
              {horaires.creneauxDebut && horaires.creneauxFin ? (
                <>
                  <span className="font-medium text-heading">{horaires.creneauxDebut}</span> →{' '}
                  <span className="font-medium text-heading">{horaires.creneauxFin}</span>
                </>
              ) : (
                <span className="text-ink/50">Aucun créneau actif : les heures d’ouverture et de fermeture s’appliquent seules.</span>
              )}
            </p>
            <p className="text-xs text-ink/45 mt-1.5">
              <Link to="/time-slots" className="text-signal-dark hover:underline">
                Gérer les créneaux horaires
              </Link>
            </p>
          </div>
          <div>
            <span className={labelClass}>Créneaux actifs</span>
            {horaires.creneauxActifs?.length ? (
              <div className="flex flex-wrap gap-1.5">
                {horaires.creneauxActifs.map((creneau) => (
                  <span
                    key={creneau}
                    className="px-2 py-1 text-xs rounded-md bg-ink/5 text-ink/60 border border-ink/10"
                  >
                    {creneau}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-ink/50">Aucun créneau défini.</p>
            )}
          </div>
        </div>
      </SettingsSection>
    </>
  )
}
