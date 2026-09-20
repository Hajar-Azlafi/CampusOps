import { useCallback } from 'react'
import { updateSecuritySettings } from '../../api/settingsApi'
import { entierBorne } from '../../utils/settingsValidation'
import {
  ErrorBanner,
  NumberField,
  SaveBar,
  SettingsSection,
  SuccessBanner,
  ToggleField,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Sécurité » (§7). Ces réglages sont lus à chaque connexion par
// LoginSecurityService et à chaque changement de mot de passe par
// PasswordService : la politique affichée ici est celle réellement appliquée.
// Le stockage BCrypt et le JWT ne sont pas concernés et restent inchangés.

const BORNES = {
  dureeValiditeMotDePasseJours: { min: 0, max: 3650 },
  maxTentativesConnexion: { min: 0, max: 100 },
  dureeVerrouillageMinutes: { min: 1, max: 1440 },
  longueurMinMotDePasse: { min: 6, max: 64 },
}

// Chaque exigence de complexité : [champ backend, libellé, description].
const EXIGENCES = [
  ['majusculeObligatoire', 'Au moins une majuscule', 'Exemple : A, B, C…'],
  ['minusculeObligatoire', 'Au moins une minuscule', 'Exemple : a, b, c…'],
  ['chiffreObligatoire', 'Au moins un chiffre', 'Exemple : 0 à 9'],
  [
    'caractereSpecialObligatoire',
    'Au moins un caractère spécial',
    'Tout caractère qui n’est ni une lettre ni un chiffre. Exemple : ! @ # $ %',
  ],
]

function toForm(dto) {
  const values = {}
  Object.keys(BORNES).forEach((champ) => {
    const valeur = dto?.[champ]
    values[champ] = valeur === null || valeur === undefined ? '' : String(valeur)
  })
  EXIGENCES.forEach(([champ]) => {
    values[champ] = Boolean(dto?.[champ])
  })
  return values
}

function toPayload(values) {
  const payload = {}
  Object.keys(BORNES).forEach((champ) => {
    payload[champ] = Number(values[champ])
  })
  EXIGENCES.forEach(([champ]) => {
    payload[champ] = values[champ]
  })
  return payload
}

/** Unités affichées dans les messages d'erreur, pour rester compréhensible. */
const UNITES = {
  dureeValiditeMotDePasseJours: 'jours',
  dureeVerrouillageMinutes: 'minutes',
  longueurMinMotDePasse: 'caractères',
}

function validate(values) {
  const errors = {}
  Object.entries(BORNES).forEach(([champ, bornes]) => {
    const message = entierBorne(values[champ], { ...bornes, unite: UNITES[champ] })
    if (message) errors[champ] = message
  })
  return errors
}

// Vocabulaire employé dans l'aperçu de la règle : un mot par exigence. Le
// message de refus réellement renvoyé par PasswordService reste, lui, formulé
// côté serveur ; cet aperçu est une aide de lecture pour l'administrateur.
const MOTS = {
  majusculeObligatoire: 'une majuscule',
  minusculeObligatoire: 'une minuscule',
  chiffreObligatoire: 'un chiffre',
  caractereSpecialObligatoire: 'un caractère spécial',
}

export default function SecuriteTab({ settings, onSaved }) {
  const onSave = useCallback(
    (values) =>
      updateSecuritySettings(toPayload(values)).then((saved) => {
        onSaved('securite', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const form = useSectionForm({ initial: toForm(settings.securite), validate, onSave })
  const { values, setField, errors } = form
  const verrouillageActif = Number(values.maxTentativesConnexion) > 0
  const longueur = Number(values.longueurMinMotDePasse)
  const classesRetenues = EXIGENCES.filter(([champ]) => values[champ]).map(([champ]) => MOTS[champ])

  return (
    <form onSubmit={form.submit} noValidate>
      <SettingsSection
        title="Politique de sécurité des comptes"
        description="Appliqué à chaque tentative de connexion. Le mécanisme d’authentification (JWT) et le chiffrement des mots de passe ne sont pas modifiés par ces réglages."
      >
        <SuccessBanner message={form.notice} />
        <ErrorBanner message={form.errorMsg} />
        <div className="grid gap-4 md:grid-cols-2">
          <NumberField
            label="Durée de validité du mot de passe"
            required
            suffix="jours"
            min={0}
            max={3650}
            value={values.dureeValiditeMotDePasseJours}
            onChange={(value) => setField('dureeValiditeMotDePasseJours', value)}
            error={errors.dureeValiditeMotDePasseJours}
            hint={
              Number(values.dureeValiditeMotDePasseJours) === 0
                ? 'Aucune expiration : le mot de passe reste valable indéfiniment.'
                : 'Passé ce délai, l’utilisateur doit choisir un nouveau mot de passe à sa prochaine connexion. 0 = jamais.'
            }
          />
          <NumberField
            label="Longueur minimale du mot de passe"
            required
            suffix="caractères"
            min={6}
            max={64}
            value={values.longueurMinMotDePasse}
            onChange={(value) => setField('longueurMinMotDePasse', value)}
            error={errors.longueurMinMotDePasse}
            hint="Contrôlée à la création d’un compte, au changement et à la réinitialisation."
          />
          <NumberField
            label="Tentatives de connexion avant verrouillage"
            required
            min={0}
            max={100}
            value={values.maxTentativesConnexion}
            onChange={(value) => setField('maxTentativesConnexion', value)}
            error={errors.maxTentativesConnexion}
            hint={
              verrouillageActif
                ? 'Le compteur repart à zéro dès qu’une connexion réussit.'
                : '0 = aucun verrouillage : les échecs ne sont même pas comptés.'
            }
          />
          <NumberField
            label="Durée de verrouillage"
            required
            suffix="minutes"
            min={1}
            max={1440}
            value={values.dureeVerrouillageMinutes}
            onChange={(value) => setField('dureeVerrouillageMinutes', value)}
            error={errors.dureeVerrouillageMinutes}
            hint={
              verrouillageActif
                ? 'Durée pendant laquelle la connexion est refusée. « Mot de passe oublié » lève le verrou immédiatement.'
                : 'Sans effet tant que le nombre de tentatives est à 0.'
            }
          />
        </div>

        <div className="mt-5 pt-5 border-t border-ink/10">
          <h3 className="text-sm font-semibold text-heading mb-1">
            Complexité du mot de passe
          </h3>
          <p className="text-xs text-ink/50 mb-3">Ces exigences valent pour tous les chemins : création par l’administration, changement volontaire, réinitialisation par e-mail. Les mots de passe temporaires générés les respectent aussi.</p>
          {EXIGENCES.map(([champ, label, description]) => (
            <ToggleField
              key={champ}
              label={label}
              description={description}
              checked={values[champ]}
              onChange={(value) => setField(champ, value)}
            />
          ))}
          <div className="mt-4 p-3 rounded-lg bg-ink/[0.03] border border-ink/10">
            <p className="text-xs font-medium text-ink/60 uppercase tracking-wide mb-1">
              Règle annoncée à l’utilisateur
            </p>
            <p className="text-sm text-ink/75">
              {Number.isFinite(longueur) && longueur > 0
                ? `Le mot de passe doit contenir au moins ${longueur} caractères${
                    classesRetenues.length ? `, dont ${classesRetenues.join(', ')}` : ''
                  }.`
                : 'Renseignez une longueur minimale valide pour voir la règle appliquée.'}
            </p>
            {!classesRetenues.length && (
              <p className="text-xs text-amber-700 mt-1.5">
                Aucune classe de caractères n’est exigée : la longueur sera le seul garde-fou.
              </p>
            )}
          </div>
        </div>
        <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={form.reset} />
      </SettingsSection>
    </form>
  )
}
