/**
 * Barre « Page X sur Y » avec Précédent / Suivant.
 *
 * Reprend à l'identique le bloc qui était dupliqué dans les huit écrans
 * paginés (mêmes classes, mêmes libellés) : le changement est structurel, pas
 * visuel. À utiliser avec le hook `usePagination`, qui lit le nombre
 * d'éléments par page dans les paramètres (Module 11, §10).
 */
export default function Pagination({ page, totalPages, onChange }) {
  const bouton =
    'px-3 py-1.5 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg ' +
    'hover:bg-ink/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors'

  return (
    <div className="flex items-center justify-between mt-4">
      <p className="text-sm text-ink/50">Page {page} sur {totalPages}</p>
      <div className="flex gap-2">
        <button
          onClick={() => onChange(Math.max(1, page - 1))}
          disabled={page === 1}
          className={bouton}
        >
          Précédent
        </button>
        <button
          onClick={() => onChange(Math.min(totalPages, page + 1))}
          disabled={page === totalPages}
          className={bouton}
        >
          Suivant
        </button>
      </div>
    </div>
  )
}
