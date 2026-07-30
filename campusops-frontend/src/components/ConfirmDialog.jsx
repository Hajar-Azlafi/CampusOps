export default function ConfirmDialog({ open, title, message, confirmLabel = 'Confirmer', danger, onConfirm, onCancel, loading }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className="relative bg-white rounded-xl shadow-xl w-full max-w-sm p-6">
        <h3 className="font-display text-lg font-semibold text-ink mb-2">{title}</h3>
        <p className="text-sm text-ink/60 mb-6">{message}</p>
        <div className="flex justify-end gap-3">
          <button onClick={onCancel} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
            Annuler
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className={`px-4 py-2 text-sm font-medium text-white rounded-lg disabled:opacity-50 transition-colors ${
              danger ? 'bg-red-600 hover:bg-red-700' : 'bg-blueprint-800 hover:bg-blueprint-700'
            }`}
          >
            {loading ? '...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}