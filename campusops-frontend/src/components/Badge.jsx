const roleColors = {
  ADMIN: 'bg-signal/15 text-heading border-signal/40',
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

/** Marqueur « semestre courant » (un niveau peut en avoir plusieurs). */
export function CurrentBadge() {
  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-signal/15 text-heading border border-signal/40">
      <span className="w-1.5 h-1.5 rounded-full bg-signal" />
      Courant
    </span>
  )
}

const reservationStatusColors = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTED: 'bg-red-50 text-red-700 border-red-200',
  CANCELLED: 'bg-ink/5 text-ink/50 border-ink/15',
  COMPLETED: 'bg-blue-50 text-blue-700 border-blue-200',
}

export function ReservationStatusBadge({ status, label }) {
  const cls = reservationStatusColors[status] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

const priorityColors = {
  HIGH: 'bg-red-50 text-red-700 border-red-200',
  MEDIUM: 'bg-amber-50 text-amber-700 border-amber-200',
  LOW: 'bg-ink/5 text-ink/60 border-ink/15',
}

export function PriorityBadge({ priority, label }) {
  const cls = priorityColors[priority] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

const notificationTypeColors = {
  RESERVATION_APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  RESERVATION_REJECTED: 'bg-red-50 text-red-700 border-red-200',
  RESERVATION_CANCELLED: 'bg-ink/5 text-ink/50 border-ink/15',
  NEW_RESERVATION: 'bg-blue-50 text-blue-700 border-blue-200',
  SCHEDULE_IMPORTED: 'bg-purple-50 text-purple-700 border-purple-200',
  SYSTEM: 'bg-ink/5 text-ink/60 border-ink/15',
  WARNING: 'bg-amber-50 text-amber-700 border-amber-200',
  INFO: 'bg-blue-50 text-blue-700 border-blue-200',
}

export function NotificationTypeBadge({ type, label }) {
  const cls = notificationTypeColors[type] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

const timetableStatusColors = {
  BROUILLON: 'bg-amber-50 text-amber-700 border-amber-200',
  PUBLIE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  ARCHIVE: 'bg-ink/5 text-ink/50 border-ink/15',
}

export function TimetableStatusBadge({ statut, label }) {
  const cls = timetableStatusColors[statut] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

const auditActionColors = {
  LOGIN: 'bg-blue-50 text-blue-700 border-blue-200',
  LOGOUT: 'bg-ink/5 text-ink/50 border-ink/15',
  CREATE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  UPDATE: 'bg-amber-50 text-amber-700 border-amber-200',
  SOFT_DELETE: 'bg-red-50 text-red-700 border-red-200',
  DELETE: 'bg-red-50 text-red-700 border-red-200',
  EXCEL_IMPORT: 'bg-purple-50 text-purple-700 border-purple-200',
  RESERVATION: 'bg-blue-50 text-blue-700 border-blue-200',
  APPROVAL: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTION: 'bg-red-50 text-red-700 border-red-200',
  CANCELLATION: 'bg-ink/5 text-ink/50 border-ink/15',
  ACTIVATION: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  DEACTIVATION: 'bg-red-50 text-red-700 border-red-200',
  SYSTEM: 'bg-amber-50 text-amber-700 border-amber-200',
}

export function AuditActionBadge({ action, label }) {
  const cls = auditActionColors[action] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}