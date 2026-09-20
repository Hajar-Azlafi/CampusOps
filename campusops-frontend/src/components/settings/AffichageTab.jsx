import { useCallback, useEffect } from 'react'
import { updateDisplaySettings } from '../../api/settingsApi'
import { useSettings } from '../../context/SettingsContext'
import {
  DATE_FORMAT_OPTIONS,
  THEME_OPTIONS,
  TIME_FORMAT_OPTIONS,
} from '../../constants/settingsOptions'
import { DEFAULT_ACCENT, DEFAULT_PRIMARY, readableInkOn } from '../../utils/brandColors'
import { couleurObligatoire, entierBorne } from '../../utils/settingsValidation'
import {
  ErrorBanner,
  FieldError,
  NumberField,
  SaveBar,
  SelectField,
  SettingsSection,
  SuccessBanner,
  ToggleField,
  inputClass,
  inputErrorClass,
  labelClass,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Affichage » (§10 / §17). Les deux couleurs alimentent les variables
// CSS déjà consommées par toute l'interface : aucun second système de thème
// n'est créé, et le mode sombre garde la main sur les fonds et l'encre.
// La saisie d'une couleur valide déclenche un aperçu immédiat, appliqué à toute
// l'application ; quitter l'onglet sans enregistrer rend la main au thème.

const CHOIX = ['themeParDefaut', 'formatDate', 'formatHeure']

function toForm(dto) {
  const values = {
    couleurPrincipale: dto?.couleurPrincipale ?? DEFAULT_PRIMARY,
    couleurSecondaire: dto?.couleurSecondaire ?? DEFAULT_ACCENT,
    paginationActivee: Boolean(dto?.paginationActivee),
    elementsParPage:
      dto?.elementsParPage === null || dto?.elementsParPage === undefined
        ? ''
        : String(dto.elementsParPage),
  }
  CHOIX.forEach((champ) => {
    values[champ] = dto?.[champ] ?? ''
  })
  return values
}

function toPayload(values) {
  const payload = {
    couleurPrincipale: values.couleurPrincipale.trim().toUpperCase(),
    couleurSecondaire: values.couleurSecondaire.trim().toUpperCase(),
    paginationActivee: values.paginationActivee,
    elementsParPage: Number(values.elementsParPage),
  }
  CHOIX.forEach((champ) => {
    payload[champ] = values[champ]
  })
  return payload
}

function validate(values) {
  const errors = {}
  const ajoute = (champ, message) => {
    if (message) errors[champ] = message
  }
  ajoute('couleurPrincipale', couleurObligatoire(values.couleurPrincipale))
  ajoute('couleurSecondaire', couleurObligatoire(values.couleurSecondaire))
  ajoute('elementsParPage', entierBorne(values.elementsParPage, { min: 5, max: 200 }))
  CHOIX.forEach((champ) => {
    if (!values[champ]) errors[champ] = 'Ce choix est obligatoire.'
  })
  return errors
}

/**
 * Sélecteur de couleur : la pastille native et le code hexadécimal éditent la
 * même valeur, car on ne peut pas dicter une teinte de charte à la souris.
 */
function ColorField({ label, value, onChange, error, hint, defaut }) {
  const propre = (value ?? '').trim()
  const utilisable = /^#[0-9a-fA-F]{6}$/.test(propre)
  return (
    <div>
      <label className={labelClass}>
        {label}
        <span className="text-red-600"> *</span>
      </label>
      <div className="flex items-center gap-2">
        <input
          type="color"
          aria-label={`${label} — sélecteur visuel`}
          value={utilisable ? propre : defaut}
          onChange={(event) => onChange(event.target.value.toUpperCase())}
          className="h-10 w-12 shrink-0 rounded-lg border border-ink/15 bg-surface p-1 cursor-pointer"
        />
        <input
          type="text"
          value={propre}
          onChange={(event) => onChange(event.target.value)}
          maxLength={7}
          spellCheck="false"
          placeholder={defaut}
          className={`${error ? inputErrorClass : inputClass} font-mono uppercase`}
        />
      </div>
      <FieldError message={error} />
      {!error && hint && <p className="text-xs text-ink/45 mt-1.5">{hint}</p>}
    </div>
  )
}

/** Aperçu des éléments réellement repeints par les deux couleurs. */
function ColorPreview({ principale, secondaire }) {
  const valide = (couleur) => /^#[0-9a-fA-F]{6}$/.test((couleur ?? '').trim())
  const chrome = valide(principale) ? principale : DEFAULT_PRIMARY
  const accent = valide(secondaire) ? secondaire : DEFAULT_ACCENT
  return (
    <div className="mt-5 pt-5 border-t border-ink/10">
      <span className={labelClass}>Aperçu</span>
      <div className="flex flex-wrap items-center gap-3">
        <span
          className="px-4 py-2.5 text-sm font-medium rounded-lg"
          style={{ backgroundColor: chrome, color: readableInkOn(chrome) }}
        >
          Bouton principal
        </span>
        <span
          className="px-3 py-1.5 text-xs font-semibold rounded-full"
          style={{ backgroundColor: accent, color: readableInkOn(accent) }}
        >
          Élément actif
        </span>
        <span className="text-base font-display font-semibold" style={{ color: chrome }}>
          Titre de page
        </span>
      </div>
      <p className="text-xs text-ink/45 mt-2.5">
        L’aperçu est déjà appliqué à toute l’interface (menu, boutons, onglets). Il disparaît si
        vous annulez ; l’enregistrement le rend définitif pour tous les utilisateurs.
      </p>
    </div>
  )
}

export default function AffichageTab({ settings, onSaved }) {
  const { previewColors, resetPreview, refresh } = useSettings()

  const onSave = useCallback(
    (values) =>
      updateDisplaySettings(toPayload(values)).then((saved) => {
        onSaved('affichage', saved)
        // Les couleurs enregistrées deviennent celles du provider : l'aperçu
        // temporaire n'a plus de raison d'être.
        resetPreview()
        refresh()
        return toForm(saved)
      }),
    [onSaved, refresh, resetPreview]
  )

  const form = useSectionForm({ initial: toForm(settings.affichage), validate, onSave })
  const { values, setField, errors } = form

  // Aperçu temps réel (§17) : seules des couleurs valides sont appliquées, et on
  // rend la main au thème dès que l'onglet est quitté.
  useEffect(() => {
    previewColors(values.couleurPrincipale, values.couleurSecondaire)
  }, [previewColors, values.couleurPrincipale, values.couleurSecondaire])

  useEffect(() => resetPreview, [resetPreview])

  const annuler = () => {
    form.reset()
    resetPreview()
  }

  return (
    <form onSubmit={form.submit} noValidate>
      <SettingsSection
        title="Identité visuelle"
        description="Les couleurs de l’établissement sont appliquées au thème existant. Les fonds et le texte restent gouvernés par le mode clair/sombre, qui n’est pas remplacé."
      >
        <SuccessBanner message={form.notice} />
        <ErrorBanner message={form.errorMsg} />
        <div className="grid gap-4 md:grid-cols-2">
          <ColorField
            label="Couleur principale"
            value={values.couleurPrincipale}
            onChange={(value) => setField('couleurPrincipale', value)}
            error={errors.couleurPrincipale}
            defaut={DEFAULT_PRIMARY}
            hint="Menu latéral, boutons principaux et titres. Format : #1A2B3C"
          />
          <ColorField
            label="Couleur secondaire"
            value={values.couleurSecondaire}
            onChange={(value) => setField('couleurSecondaire', value)}
            error={errors.couleurSecondaire}
            defaut={DEFAULT_ACCENT}
            hint="Couleur d’action : onglet actif, focus, pastilles."
          />
        </div>
        <ColorPreview
          principale={values.couleurPrincipale}
          secondaire={values.couleurSecondaire}
        />
      </SettingsSection>

      <SettingsSection
        title="Préférences d’affichage"
        description="Valeurs appliquées à tous les utilisateurs. Le thème par défaut ne s’impose qu’à ceux qui n’ont pas encore choisi le leur."
      >
        <div className="grid gap-4 md:grid-cols-2">
          <SelectField
            label="Thème par défaut"
            value={values.themeParDefaut}
            onChange={(value) => setField('themeParDefaut', value)}
            options={THEME_OPTIONS}
            error={errors.themeParDefaut}
            hint="« Selon le système » suit le réglage du navigateur. Un choix personnel enregistré n’est jamais écrasé."
          />
          <SelectField
            label="Format des dates"
            value={values.formatDate}
            onChange={(value) => setField('formatDate', value)}
            options={DATE_FORMAT_OPTIONS}
            error={errors.formatDate}
          />
          <SelectField
            label="Format des heures"
            value={values.formatHeure}
            onChange={(value) => setField('formatHeure', value)}
            options={TIME_FORMAT_OPTIONS}
            error={errors.formatHeure}
          />
          <NumberField
            label="Éléments par page"
            required
            min={5}
            max={200}
            value={values.elementsParPage}
            onChange={(value) => setField('elementsParPage', value)}
            error={errors.elementsParPage}
            hint="Taille de page des listes paginées."
          />
          <div className="flex items-end">
            <ToggleField
              label="Activer la pagination"
              description="Désactivée, les listes affichent l’ensemble des résultats d’un seul bloc."
              checked={values.paginationActivee}
              onChange={(value) => setField('paginationActivee', value)}
            />
          </div>
        </div>
        <SaveBar dirty={form.dirty} valid={form.valid} saving={form.saving} onCancel={annuler} />
      </SettingsSection>
    </form>
  )
}
