import { useCallback } from 'react'
import { updateNotificationSettings } from '../../api/settingsApi'
import { REMINDER_OPTIONS } from '../../constants/settingsOptions'
import {
  ErrorBanner,
  ReadOnlyField,
  SaveBar,
  SelectField,
  SettingsSection,
  SuccessBanner,
  ToggleField,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Notifications » (§8). Les interrupteurs sont évalués par
// NotificationService (point de passage unique de toute notification) et par
// EmailService avant chaque envoi. La configuration SMTP du serveur n'est pas
// touchée : elle est seulement rappelée en lecture seule, car des e-mails
// activés sans serveur configuré ne partiraient pas.

const BOOLEENS = [
  'notificationsActivees',
  'emailsActives',
  'notificationsAutomatiquesActivees',
]

function toForm(dto) {
  const values = {}
  BOOLEENS.forEach((champ) => {
    values[champ] = Boolean(dto?.[champ])
  })
  values.frequenceRappels = dto?.frequenceRappels ?? 'QUOTIDIENNE'
  return values
}

function toPayload(values) {
  const payload = { frequenceRappels: values.frequenceRappels }
  BOOLEENS.forEach((champ) => {
    payload[champ] = values[champ]
  })
  return payload
}

function validate(values) {
  const errors = {}
  if (!values.frequenceRappels) {
    errors.frequenceRappels = 'Choisissez une fréquence de rappels.'
  }
  return errors
}

export default function NotificationsTab({ settings, onSaved }) {
  const onSave = useCallback(
    (values) =>
      updateNotificationSettings(toPayload(values)).then((saved) => {
        onSaved('notifications', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const form = useSectionForm({ initial: toForm(settings.notifications), validate, onSave })
  const { values, setField, errors } = form
  const dto = settings.notifications ?? {}
  const toutCoupe = !values.notificationsActivees
  const rappelsActifs = values.frequenceRappels !== 'JAMAIS'
  const smtpConfigure = Boolean(dto.smtpConfigure)

  return (
    <form onSubmit={form.submit} noValidate>
      <SettingsSection
        title="Notifications et e-mails"
        description="Ces interrupteurs sont vérifiés avant chaque envoi. Ils n’effacent rien : les notifications déjà reçues restent consultables."
      >
        <SuccessBanner message={form.notice} />
        <ErrorBanner message={form.errorMsg} />
        <ToggleField
          label="Activer les notifications"
          description="Interrupteur général. Désactivé, plus aucune notification n’est créée dans l’application, quelle qu’en soit l’origine."
          checked={values.notificationsActivees}
          onChange={(value) => setField('notificationsActivees', value)}
        />
        <ToggleField
          label="Activer les notifications automatiques"
          description="Ne concerne que celles émises par le système sans action humaine : rappels et diffusions. Les notifications liées à une action (réservation approuvée, refusée…) continuent de partir."
          checked={values.notificationsAutomatiquesActivees}
          onChange={(value) => setField('notificationsAutomatiquesActivees', value)}
          disabled={toutCoupe}
        />
        <ToggleField
          label="Activer l’envoi d’e-mails"
          description="Concerne les e-mails applicatifs (compte créé, mot de passe oublié, rappels). Sans serveur SMTP configuré, l’activation reste sans effet."
          checked={values.emailsActives}
          onChange={(value) => setField('emailsActives', value)}
        />
        {toutCoupe && (
          <p className="text-xs text-amber-700 mt-2">Les notifications sont coupées : l’interrupteur des notifications automatiques et la fréquence des rappels resteront sans effet jusqu’à leur réactivation.</p>
        )}

        <div className="mt-5 pt-5 border-t border-ink/10 grid gap-4 md:grid-cols-2">
          <SelectField
            label="Fréquence des rappels"
            value={values.frequenceRappels}
            onChange={(value) => setField('frequenceRappels', value)}
            options={REMINDER_OPTIONS}
            error={errors.frequenceRappels}
            hint={
              rappelsActifs
                ? 'Un rappel est envoyé aux utilisateurs concernés avant la date de leur réservation.'
                : 'Aucun rappel automatique. Les autres notifications ne sont pas affectées.'
            }
            disabled={toutCoupe}
          />
          <div className="grid gap-4">
            <ReadOnlyField
              label="Serveur SMTP"
              value={smtpConfigure ? 'Configuré' : 'Non configuré'}
              hint={
                smtpConfigure
                  ? 'Défini dans la configuration du serveur. Non modifiable depuis cette page.'
                  : 'Aucun serveur d’envoi n’est déclaré côté serveur : les e-mails ne partiront pas, même activés ici.'
              }
            />
            <ReadOnlyField
              label="Adresse expéditrice"
              value={dto.expediteur || '—'}
              hint="Adresse utilisée comme expéditeur des e-mails applicatifs."
            />
          </div>
        </div>
        <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={form.reset} />
      </SettingsSection>
    </form>
  )
}
