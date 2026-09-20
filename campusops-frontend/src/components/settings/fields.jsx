import { useId } from 'react'

// Briques de formulaire de la page Paramètres. Les classes reprennent exactement
// celles des formulaires existants (OccupationFormModal, pages Admin) : l'objectif
// est qu'un champ de Paramètres soit indiscernable d'un champ du reste de l'app.
//
// Chaque champ affiche son erreur EN DIRECT, sous le champ, avec la bordure
// rouge : c'est la convention retenue dans CampusOps (pas de validation au
// moment du clic sur « Enregistrer »).

export const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'
export const inputClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
export const inputErrorClass =
  'w-full px-3 py-2.5 border border-red-400 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-red-400'
export const hintClass = 'text-xs text-ink/45 mt-1.5'

/** Carte de section : titre, explication, puis les champs. */
export function SettingsSection({ title, description, children, footer }) {
  return (
    <section className="bg-surface border border-ink/10 rounded-xl p-5 mb-5">
      <div className="mb-4">
        <h2 className="font-display text-base font-semibold text-heading">{title}</h2>
        {description && <p className="text-sm text-ink/50 mt-1">{description}</p>}
      </div>
      {children}
      {footer}
    </section>
  )
}

/** Message d'erreur d'un champ (rouge, sous le champ). */
export function FieldError({ message }) {
  if (!message) return null
  return <p className="text-xs text-red-600 mt-1.5">{message}</p>
}

/** Champ texte générique ; `suggestions` alimente une liste déroulante libre. */
export function TextField({
  label,
  value,
  onChange,
  error,
  hint,
  type = 'text',
  placeholder,
  maxLength,
  suggestions,
  disabled = false,
  required = false,
}) {
  const id = useId()
  const listId = suggestions?.length ? `${id}-list` : undefined
  return (
    <div>
      <label className={labelClass} htmlFor={id}>
        {label}
        {required && <span className="text-red-600"> *</span>}
      </label>
      <input
        id={id}
        type={type}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        maxLength={maxLength}
        disabled={disabled}
        list={listId}
        aria-invalid={error ? 'true' : undefined}
        className={error ? inputErrorClass : inputClass}
      />
      {listId && (
        <datalist id={listId}>
          {suggestions.map((option) => (
            <option key={option} value={option} />
          ))}
        </datalist>
      )}
      <FieldError message={error} />
      {!error && hint && <p className={hintClass}>{hint}</p>}
    </div>
  )
}

/** Champ numérique entier, avec unité affichée à droite. */
export function NumberField({
  label,
  value,
  onChange,
  error,
  hint,
  min,
  max,
  suffix,
  required = false,
}) {
  const id = useId()
  return (
    <div>
      <label className={labelClass} htmlFor={id}>
        {label}
        {required && <span className="text-red-600"> *</span>}
      </label>
      <div className="relative">
        <input
          id={id}
          type="number"
          inputMode="numeric"
          value={value ?? ''}
          min={min}
          max={max}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={error ? 'true' : undefined}
          className={`${error ? inputErrorClass : inputClass} ${suffix ? 'pr-16' : ''}`}
        />
        {suffix && (
          <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs text-ink/45">
            {suffix}
          </span>
        )}
      </div>
      <FieldError message={error} />
      {!error && hint && <p className={hintClass}>{hint}</p>}
    </div>
  )
}

/** Heure au format HH:mm ; `step={60}` masque les secondes du contrôle natif. */
export function TimeField({ label, value, onChange, error, hint, required = false }) {
  const id = useId()
  return (
    <div>
      <label className={labelClass} htmlFor={id}>
        {label}
        {required && <span className="text-red-600"> *</span>}
      </label>
      <input
        id={id}
        type="time"
        step={60}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? 'true' : undefined}
        className={error ? inputErrorClass : inputClass}
      />
      <FieldError message={error} />
      {!error && hint && <p className={hintClass}>{hint}</p>}
    </div>
  )
}

/** Liste fermée : `options` = [{ value, label }]. */
export function SelectField({ label, value, onChange, options, error, hint, disabled = false }) {
  const id = useId()
  return (
    <div>
      <label className={labelClass} htmlFor={id}>
        {label}
      </label>
      <select
        id={id}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        aria-invalid={error ? 'true' : undefined}
        className={error ? inputErrorClass : inputClass}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <FieldError message={error} />
      {!error && hint && <p className={hintClass}>{hint}</p>}
    </div>
  )
}

/** Interrupteur oui/non : libellé cliquable, explication sous le libellé. */
export function ToggleField({ label, description, checked, onChange, disabled = false }) {
  const id = useId()
  return (
    <label
      htmlFor={id}
      className={`flex items-start gap-3 py-2 ${disabled ? 'opacity-50' : 'cursor-pointer'}`}
    >
      <input
        id={id}
        type="checkbox"
        checked={!!checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 h-4 w-4 rounded border-ink/25 text-signal accent-signal focus:ring-2 focus:ring-signal"
      />
      <span className="min-w-0">
        <span className="block text-sm font-medium text-ink/80">{label}</span>
        {description && <span className="block text-xs text-ink/45 mt-0.5">{description}</span>}
      </span>
    </label>
  )
}

/** Valeur pilotée par un autre module : affichée, jamais modifiable ici. */
export function ReadOnlyField({ label, value, hint }) {
  return (
    <div>
      <span className={labelClass}>{label}</span>
      <p className="px-3 py-2.5 border border-ink/10 rounded-lg text-sm bg-ink/5 text-ink/60">
        {value ?? '—'}
      </p>
      {hint && <p className={hintClass}>{hint}</p>}
    </div>
  )
}

/** Bandeau de confirmation après un enregistrement réussi. */
export function SuccessBanner({ message }) {
  if (!message) return null
  return (
    <p
      role="status"
      className="text-sm text-emerald-800 bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2.5 mb-4"
    >
      {message}
    </p>
  )
}

/** Bandeau d'échec : erreur renvoyée par le serveur, telle qu'il l'a formulée. */
export function ErrorBanner({ message }) {
  if (!message) return null
  return (
    <p
      role="alert"
      className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4"
    >
      {message}
    </p>
  )
}
/**
 * Pied de section : « Enregistrer » / « Annuler ».
 *
 * Le bouton d'enregistrement reste désactivé tant que la saisie est invalide
 * (convention CampusOps) ; on explique alors pourquoi, pour ne pas laisser
 * l'utilisateur devant un bouton grisé sans raison apparente.
 */
export function SaveBar({ dirty, valid, saving, onCancel }) {
  return (
    <div className="flex flex-wrap items-center gap-3 pt-5 mt-5 border-t border-ink/10">
      <button
        type="submit"
        disabled={!dirty || !valid || saving}
        className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors"
      >
        {saving ? 'Enregistrement…' : 'Enregistrer'}
      </button>
      <button
        type="button"
        onClick={onCancel}
        disabled={!dirty || saving}
        className="px-4 py-2.5 border border-heading/25 text-heading hover:bg-heading/5 disabled:opacity-40 disabled:cursor-not-allowed text-sm font-medium rounded-lg transition-colors"
      >
        Annuler
      </button>
      {dirty && !valid && (
        <span className="text-xs text-red-600">
          Corrigez les champs en rouge avant d’enregistrer.
        </span>
      )}
      {!dirty && !saving && (
        <span className="text-xs text-ink/40">Aucune modification à enregistrer.</span>
      )}
    </div>
  )
}
