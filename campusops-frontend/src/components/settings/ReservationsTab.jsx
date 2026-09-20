import { useCallback } from 'react'
import { updateReservationSettings } from '../../api/settingsApi'
import { dureeLisible, entierBorne } from '../../utils/settingsValidation'
import {
  ErrorBanner,
  NumberField,
  SaveBar,
  SettingsSection,
  SuccessBanner,
  ToggleField,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Réservations » (§5). Chaque valeur est appliquée par
// ReservationService et le moteur de disponibilité : rien n'est décoratif ici.
// Les bornes reprennent celles du backend, et les explications décrivent l'effet
// réel de chaque règle — y compris ses exceptions assumées.
//
// Les deux interrupteurs (week-end, hors horaires) sont OBLIGATOIRES côté API
// (@NotNull sur ReservationSettingsDto) : ils doivent donc figurer dans la charge
// utile, faute de quoi l'enregistrement serait rejeté.

const BORNES = {
  dureeMaxReservationMinutes: { min: 15, max: 1440 },
  dureeMinReservationMinutes: { min: 0, max: 480 },
  delaiMinAvantReservationMinutes: { min: 0, max: 10080 },
  maxReservationsParUtilisateur: { min: 0, max: 500 },
  dureeMaxSeanceMinutes: { min: 15, max: 1440 },
}

const BOOLEENS = ['reservationsWeekEndAutorisees', 'reservationsHorsHorairesAutorisees']

function toForm(dto) {
  const values = {}
  Object.keys(BORNES).forEach((champ) => {
    const valeur = dto?.[champ]
    values[champ] = valeur === null || valeur === undefined ? '' : String(valeur)
  })
  BOOLEENS.forEach((champ) => {
    values[champ] = Boolean(dto?.[champ])
  })
  return values
}

function toPayload(values) {
  const payload = {}
  Object.keys(BORNES).forEach((champ) => {
    payload[champ] = Number(values[champ])
  })
  BOOLEENS.forEach((champ) => {
    payload[champ] = Boolean(values[champ])
  })
  return payload
}

function validate(values) {
  const errors = {}
  Object.entries(BORNES).forEach(([champ, bornes]) => {
    const message = entierBorne(values[champ], {
      ...bornes,
      unite: champ === 'maxReservationsParUtilisateur' ? undefined : 'minutes',
    })
    if (message) errors[champ] = message
  })

  // Règle croisée du backend : une durée minimale supérieure à la durée
  // maximale rendrait toute réservation impossible.
  const min = Number(values.dureeMinReservationMinutes)
  const max = Number(values.dureeMaxReservationMinutes)
  if (!errors.dureeMinReservationMinutes && !errors.dureeMaxReservationMinutes && min > max) {
    errors.dureeMinReservationMinutes =
      `La durée minimale ne peut pas dépasser la durée maximale (${max} minutes).`
  }
  return errors
}

export default function ReservationsTab({ settings, onSaved }) {
  const onSave = useCallback(
    (values) =>
      updateReservationSettings(toPayload(values)).then((saved) => {
        onSaved('reservations', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const form = useSectionForm({ initial: toForm(settings.reservations), validate, onSave })
  const { values, setField, errors } = form
  const nombre = (champ) => (value) => setField(champ, value)
  const minutes = 'minutes'

  return (
    <form onSubmit={form.submit} noValidate>
      <SettingsSection
        title="Règles de réservation"
        description="Appliquées à chaque demande de réservation, quelle que soit la page d’où elle part."
      >
        <SuccessBanner message={form.notice} />
        <ErrorBanner message={form.errorMsg} />
        <div className="grid gap-4 md:grid-cols-2">
          <NumberField
            label="Durée maximale d’une réservation"
            required
            suffix={minutes}
            min={15}
            max={1440}
            value={values.dureeMaxReservationMinutes}
            onChange={nombre('dureeMaxReservationMinutes')}
            error={errors.dureeMaxReservationMinutes}
            hint={hintDuree(values.dureeMaxReservationMinutes, 'Un créneau plus long est refusé.')}
          />
          <NumberField
            label="Durée minimale d’une réservation"
            required
            suffix={minutes}
            min={0}
            max={480}
            value={values.dureeMinReservationMinutes}
            onChange={nombre('dureeMinReservationMinutes')}
            error={errors.dureeMinReservationMinutes}
            hint="En dessous, le créneau est refusé et la recherche d’espaces libres ignore les trous plus courts."
          />
          <NumberField
            label="Délai minimum avant réservation"
            required
            suffix={minutes}
            min={0}
            max={10080}
            value={values.delaiMinAvantReservationMinutes}
            onChange={nombre('delaiMinAvantReservationMinutes')}
            error={errors.delaiMinAvantReservationMinutes}
            hint={hintDuree(values.delaiMinAvantReservationMinutes, '0 = réservation possible jusqu’à la dernière minute.')}
          />
          <NumberField
            label="Réservations simultanées par utilisateur"
            required
            min={0}
            max={500}
            value={values.maxReservationsParUtilisateur}
            onChange={nombre('maxReservationsParUtilisateur')}
            error={errors.maxReservationsParUtilisateur}
            hint="Réservations à venir en attente ou approuvées. 0 = illimité. Une saisie faite par l’administration n’est pas décomptée."
          />
          <NumberField
            label="Durée maximale d’une séance"
            required
            suffix={minutes}
            min={15}
            max={1440}
            value={values.dureeMaxSeanceMinutes}
            onChange={nombre('dureeMaxSeanceMinutes')}
            error={errors.dureeMaxSeanceMinutes}
            hint={hintDuree(
              values.dureeMaxSeanceMinutes,
              'S’applique aux séances d’emploi du temps et aux occupations, y compris à l’import Excel.'
            )}
          />
        </div>
        <div className="mt-5 pt-5 border-t border-ink/10 space-y-4">
          <ToggleField
            label="Réservations le week-end"
            description="Autorise les demandes tombant un samedi ou un dimanche. Désactivée, une réservation sur un jour de week-end est refusée, quelles que soient les heures demandées."
            checked={values.reservationsWeekEndAutorisees}
            onChange={(value) => setField('reservationsWeekEndAutorisees', value)}
          />
          <ToggleField
            label="Réservations hors des heures d’ouverture"
            description="Autorise les créneaux qui débordent de la plage d’ouverture (Paramètres → Horaires). Désactivée, la demande doit tenir dans les heures configurées."
            checked={values.reservationsHorsHorairesAutorisees}
            onChange={(value) => setField('reservationsHorsHorairesAutorisees', value)}
          />
        </div>
        <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={form.reset} />
      </SettingsSection>
    </form>
  )
}

/** Rappel lisible de la durée saisie, suivi de son effet. */
function hintDuree(minutes, effet) {
  const lisible = dureeLisible(minutes)
  return lisible ? `${lisible}. ${effet}` : effet
}
