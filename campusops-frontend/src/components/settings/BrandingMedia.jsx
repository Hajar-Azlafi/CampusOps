import { useRef, useState } from 'react'
import { deleteBrandingMedia, mediaSrc, uploadBrandingMedia } from '../../api/settingsApi'
import { SettingsSection, hintClass, labelClass } from './fields'

// Logo et favicon de l'établissement (§11). Le contrôle fait ici (type MIME
// annoncé + taille) n'est qu'un confort : le backend, lui, vérifie la signature
// binaire du fichier et ne fait jamais confiance à l'extension.

const MAX_OCTETS = 2 * 1024 * 1024

const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']
const FAVICON_TYPES = [...LOGO_TYPES, 'image/x-icon', 'image/vnd.microsoft.icon']

/** Message de refus local, ou `null` si le fichier peut être envoyé. */
function refuser(file, types, formats) {
  if (file.size > MAX_OCTETS) {
    const mo = (file.size / (1024 * 1024)).toFixed(1)
    return `Fichier trop volumineux (${mo} Mo). Maximum 2 Mo.`
  }
  // Un .ico peut arriver sans type MIME selon le navigateur : on laisse alors
  // le backend trancher sur la signature du fichier.
  if (file.type && !types.includes(file.type)) {
    return `Format non autorisé. Formats acceptés : ${formats}.`
  }
  return null
}

function tailleLisible(octets) {
  if (!octets) return null
  return octets < 1024 * 1024
    ? `${Math.max(1, Math.round(octets / 1024))} Ko`
    : `${(octets / (1024 * 1024)).toFixed(1)} Mo`
}

function MediaCard({ type, label, info, types, formats, previewClass, onChanged }) {
  const inputRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const src = mediaSrc(info?.url)
  const present = Boolean(info?.present && src)

  const run = (promise, fallback) => {
    setBusy(true)
    setError('')
    promise
      .then(() => onChanged())
      .catch((err) => setError(err.response?.data?.message || fallback))
      .finally(() => setBusy(false))
  }

  const onFile = (event) => {
    const file = event.target.files?.[0]
    // Réinitialisé aussitôt : choisir deux fois le même fichier doit fonctionner.
    event.target.value = ''
    if (!file) return
    const refus = refuser(file, types, formats)
    if (refus) {
      setError(refus)
      return
    }
    run(uploadBrandingMedia(type, file), 'Téléversement impossible. Réessayez.')
  }

  return (
    <div className="border border-ink/10 rounded-lg p-4">
      <span className={labelClass}>{label}</span>
      <div className="flex items-center gap-4">
        <div className="h-20 w-20 shrink-0 rounded-lg border border-ink/10 bg-ink/[0.03] grid place-items-center overflow-hidden">
          {present ? (
            <img src={src} alt={`${label} de l’établissement`} className={previewClass} />
          ) : (
            <span className="text-[11px] text-ink/40 text-center px-1">
              Aucun fichier
            </span>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm text-ink/70 truncate">
            {info?.fileName || 'Non défini'}
          </p>
          {present && (
            <p className="text-xs text-ink/45 mt-0.5">
              {[info?.contentType, tailleLisible(info?.tailleOctets)].filter(Boolean).join(' · ')}
            </p>
          )}
          <div className="flex flex-wrap gap-2 mt-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setError('')
                inputRef.current?.click()
              }}
              className="px-3 py-1.5 bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-40 text-white text-xs font-medium rounded-lg transition-colors"
            >
              {present ? 'Remplacer' : 'Téléverser'}
            </button>
            {present && (
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  run(deleteBrandingMedia(type), 'Suppression impossible. Réessayez.')
                }
                className="px-3 py-1.5 border border-red-300 text-red-700 hover:bg-red-50 disabled:opacity-40 text-xs font-medium rounded-lg transition-colors"
              >
                Supprimer
              </button>
            )}
          </div>
        </div>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={types.join(',')}
        onChange={onFile}
        className="hidden"
      />
      <p className={hintClass}>{`${formats} — 2 Mo maximum.`}</p>
      {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
      {busy && <p className="text-xs text-ink/45 mt-1.5">Envoi en cours…</p>}
    </div>
  )
}

/** Section « Identité visuelle » : aperçu, téléversement, remplacement, suppression. */
export default function BrandingMedia({ logo, favicon, onChanged }) {
  return (
    <SettingsSection
      title="Identité visuelle"
      description="Le logo apparaît dans le menu latéral et sur la page de connexion ; le favicon est l’icône de l’onglet du navigateur."
    >
      <div className="grid gap-4 md:grid-cols-2">
        <MediaCard
          type="logo"
          label="Logo"
          info={logo}
          types={LOGO_TYPES}
          formats="PNG, JPEG, WEBP ou GIF"
          previewClass="max-h-16 max-w-16 object-contain"
          onChanged={onChanged}
        />
        <MediaCard
          type="favicon"
          label="Favicon"
          info={favicon}
          types={FAVICON_TYPES}
          formats="PNG, ICO, JPEG, WEBP ou GIF"
          previewClass="max-h-8 max-w-8 object-contain"
          onChanged={onChanged}
        />
      </div>
    </SettingsSection>
  )
}
