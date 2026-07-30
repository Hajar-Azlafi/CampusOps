const roleColors = {
  ADMIN: 'bg-signal/15 text-blueprint-800 border-signal/40',
  RESPONSABLE_PEDAGOGIQUE: 'bg-blue-50 text-blue-700 border-blue-200',
  ENSEIGNANT: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  RESPONSABLE_CLUB: 'bg-purple-50 text-purple-700 border-purple-200',
}

export function RoleBadge({ role, label }) {
  const cls = roleColors[role] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

export function StatusBadge({ active }) {
  return active ? (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
      Actif
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-ink/5 text-ink/50 border border-ink/15">
      <span className="w-1.5 h-1.5 rounded-full bg-ink/30" />
      Inactif
    </span>
  )
}