import { useState } from 'react'

/**
 * Affichage professionnel d'un texte long dans une cellule de tableau :
 * troncature propre a une longueur fixe, texte complet en tooltip et bouton
 * "Voir plus" ouvrant une fenetre avec le contenu integral.
 */
export default function TruncatedText({ text, limit = 70, title = 'Description' }) {
  const [open, setOpen] = useState(false)

  if (!text) return <span>—</span>
  if (text.length <= limit) return <span className="whitespace-pre-line">{text}</span>

  return (
    <>
      <span title={text}>{text.slice(0, limit).trimEnd()}… </span>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-signal hover:underline text-xs font-medium whitespace-nowrap"
      >
        Voir plus
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-lg p-6">
            <h3 className="font-display text-lg font-semibold text-ink mb-3">{title}</h3>
            <p className="text-sm text-ink/70 whitespace-pre-line max-h-80 overflow-y-auto">
              {text}
            </p>
            <div className="mt-5 text-right">
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="px-4 py-2 rounded-lg text-sm font-medium bg-blueprint-800 text-white hover:bg-blueprint-700"
              >
                Fermer
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
