import { useCallback, useMemo } from 'react'
import { updateImportSettings } from '../../api/settingsApi'
import { entierBorne } from '../../utils/settingsValidation'
import {
  ErrorBanner,
  FieldError,
  NumberField,
  SaveBar,
  SettingsSection,
  SuccessBanner,
  ToggleField,
  labelClass,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Imports » (§9). ImportPolicyService applique ces règles en première
// ligne des cinq imports Excel existants (utilisateurs, emplois du temps,
// séances, examens, occupations) : aucun second système d'import n'est créé.
// Les formats proposés sont ceux que le serveur sait réellement lire — en
// autoriser d'autres afficherait un paramètre mensonger.

function toForm(dto) {
  const taille = dto?.tailleMaxFichierMo
  return {
    tailleMaxFichierMo: taille === null || taille === undefined ? '' : String(taille),
    formatsAutorises: [...(dto?.formatsAutorises ?? [])],
    validationAutomatique: Boolean(dto?.validationAutomatique),
    ecrasementDonneesAutorise: Boolean(dto?.ecrasementDonneesAutorise),
  }
}

function toPayload(values) {
  return {
    tailleMaxFichierMo: Number(values.tailleMaxFichierMo),
    formatsAutorises: values.formatsAutorises,
    validationAutomatique: values.validationAutomatique,
    ecrasementDonneesAutorise: values.ecrasementDonneesAutorise,
  }
}

/** Validation dépendante du plafond serveur : construite à la lecture du DTO. */
function makeValidate(plafond) {
  return function validate(values) {
    const errors = {}
    const message = entierBorne(values.tailleMaxFichierMo, { min: 1, max: 25, unite: 'Mo' })
    if (message) {
      errors.tailleMaxFichierMo = message
    } else if (plafond && Number(values.tailleMaxFichierMo) > plafond) {
      errors.tailleMaxFichierMo =
        `Le serveur n’accepte pas plus de ${plafond} Mo par fichier.`
    }
    if (!values.formatsAutorises.length) {
      errors.formatsAutorises = 'Autorisez au moins un format de fichier.'
    }
    return errors
  }
}

export default function ImportsTab({ settings, onSaved }) {
  const dto = settings.imports ?? {}
  const plafond = dto.plafondServeurMo ?? null
  const supportes = dto.formatsSupportes ?? []

  const onSave = useCallback(
    (values) =>
      updateImportSettings(toPayload(values)).then((saved) => {
        onSaved('imports', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const validate = useMemo(() => makeValidate(plafond), [plafond])
  const form = useSectionForm({ initial: toForm(dto), validate, onSave })
  const { values, setField, errors } = form

  /** Ordre stable : la comparaison « modifié » suit celui des formats supportés. */
  const basculerFormat = (format) => {
    const retenus = new Set(values.formatsAutorises)
    if (retenus.has(format)) retenus.delete(format)
    else retenus.add(format)
    setField(
      'formatsAutorises',
      supportes.filter((candidat) => retenus.has(candidat))
    )
  }

  return (
    <form onSubmit={form.submit} noValidate>
      <SettingsSection
        title="Fichiers acceptés"
        description="Contrôles appliqués avant toute lecture, sur chacun des imports Excel de l’application. Le contenu réel du fichier est vérifié, pas seulement son extension."
      >
        <SuccessBanner message={form.notice} />
        <ErrorBanner message={form.errorMsg} />
        <div className="grid gap-4 md:grid-cols-2">
          <NumberField
            label="Taille maximale d’un fichier"
            required
            suffix="Mo"
            min={1}
            max={plafond ? Math.min(25, plafond) : 25}
            value={values.tailleMaxFichierMo}
            onChange={(value) => setField('tailleMaxFichierMo', value)}
            error={errors.tailleMaxFichierMo}
            hint={
              plafond
                ? `Un fichier plus lourd est refusé avec un message explicite. Plafond technique du serveur : ${plafond} Mo.`
                : 'Un fichier plus lourd est refusé avec un message explicite.'
            }
          />
          <div>
            <span className={labelClass}>
              Formats autorisés
              <span className="text-red-600"> *</span>
            </span>
            <div className="flex flex-wrap gap-2">
              {supportes.map((format) => {
                const actif = values.formatsAutorises.includes(format)
                return (
                  <button
                    key={format}
                    type="button"
                    aria-pressed={actif}
                    onClick={() => basculerFormat(format)}
                    className={`px-3 py-1.5 text-sm font-medium rounded-lg border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-signal ${
                      actif
                        ? 'border-signal bg-signal/10 text-heading'
                        : 'border-ink/15 text-ink/55 hover:bg-ink/[0.03]'
                    }`}
                  >
                    .{format}
                  </button>
                )
              })}
            </div>
            <FieldError message={errors.formatsAutorises} />
            {!errors.formatsAutorises && (
              <p className="text-xs text-ink/45 mt-1.5">Seuls ces formats sont techniquement lisibles. Un fichier renommé pour ressembler à un classeur autorisé est rejeté : sa signature binaire est contrôlée.</p>
            )}
          </div>
        </div>

        <div className="mt-5 pt-5 border-t border-ink/10">
          <h3 className="text-sm font-semibold text-heading mb-3">
            Traitement des lignes
          </h3>
          <ToggleField
            label="Validation automatique"
            description="Activée, un fichier contenant au moins une ligne en erreur est refusé en bloc : rien n’est enregistré et les erreurs sont listées. Désactivée, les lignes valides sont importées et les autres rapportées."
            checked={values.validationAutomatique}
            onChange={(value) => setField('validationAutomatique', value)}
          />
          <ToggleField
            label="Autoriser l’écrasement des données existantes"
            description="Permet à l’import de mettre à jour un enregistrement déjà présent au lieu de le signaler en doublon. Réservé aux référentiels identifiables sans ambiguïté (utilisateur par e-mail) ; jamais appliqué aux séances, examens et occupations, où écraser reviendrait à supprimer la réservation d’un autre utilisateur."
            checked={values.ecrasementDonneesAutorise}
            onChange={(value) => setField('ecrasementDonneesAutorise', value)}
          />
        </div>
        <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={form.reset} />
      </SettingsSection>
    </form>
  )
}
