import { useId, useState } from 'react'

/**
 * Composants de visualisation en SVG pur (aucune dependance externe). Ils sont
 * generiques : ils consomment des listes d'elements { key, label, value } ou des
 * valeurs simples et restent stylises via la palette Tailwind du projet.
 *
 * Couleurs : la palette pointe vers les variables --chart-N definies dans
 * src/index.css, qui sont eclaircies sous `html.dark`. Les graphiques suivent
 * donc le theme sans logique conditionnelle ici.
 */

export const CHART_PALETTE = [
  'var(--chart-1)', // bleu marine
  'var(--chart-2)', // bleu
  'var(--chart-3)', // emeraude
  'var(--chart-4)', // ambre
  'var(--chart-5)', // violet
  'var(--chart-6)', // rouge
  'var(--chart-7)', // bleu clair
  'var(--chart-8)',
  'var(--chart-9)',
  'var(--chart-10)',
]

/** Seuils d'occupation : rouge >= 75 %, ambre >= 50 %, vert en dessous. */
function occupancyColor(pct) {
  return pct >= 75 ? 'var(--chart-6)' : pct >= 50 ? 'var(--chart-4)' : 'var(--chart-3)'
}

/**
 * Ordre de couleurs propre aux diagrammes circulaires : la palette generique
 * commence par trois bleus voisins, ce qui rendait des parts adjacentes du
 * donut indistinguables. On alterne donc les familles de teintes.
 */
const DONUT_PALETTE = [
  'var(--chart-2)', // bleu
  'var(--chart-4)', // ambre
  'var(--chart-3)', // emeraude
  'var(--chart-5)', // violet
  'var(--chart-6)', // rouge
  'var(--chart-8)', // cyan
  'var(--chart-10)', // rose
  'var(--chart-9)', // turquoise
  'var(--chart-1)', // bleu marine
  'var(--chart-7)', // bleu clair
]

/**
 * Classes de grille de la legende. Les chaines sont ecrites en entier : Tailwind
 * scanne le source, une classe construite dynamiquement ne serait pas generee.
 */
const LEGEND_GRID = {
  1: 'grid-cols-1',
  2: 'grid-cols-1 sm:grid-cols-2',
  3: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3',
  4: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-4',
}

function formatValue(value) {
  return typeof value === 'number' ? value.toLocaleString('fr-FR') : value
}

/** Carte de statistique simple avec libelle, valeur et indice optionnel. */
export function StatCard({ label, value, hint, tone = 'default', icon: Icon }) {
  const tones = {
    default: 'bg-surface border-ink/10 text-ink',
    blue: 'bg-signal/10 border-signal/30 text-heading',
    emerald: 'bg-emerald-50 border-emerald-200 text-emerald-700',
    amber: 'bg-amber-50 border-amber-200 text-amber-700',
    red: 'bg-red-50 border-red-200 text-red-700',
    muted: 'bg-ink/5 border-ink/10 text-ink/60',
    purple: 'bg-purple-50 border-purple-200 text-purple-700',
  }
  return (
    <div className={`border rounded-xl p-4 ${tones[tone] ?? tones.default}`}>
      <div className="flex items-start justify-between">
        <p className="text-2xl font-display font-semibold leading-tight">{formatValue(value)}</p>
        {Icon ? <Icon className="w-5 h-5 opacity-40 shrink-0" /> : null}
      </div>
      <p className="text-xs mt-1 opacity-70">{label}</p>
      {hint ? <p className="text-[11px] mt-0.5 opacity-50">{hint}</p> : null}
    </div>
  )
}

/** Histogramme vertical avec info-bulle au survol. */
export function BarChart({ data = [], color = CHART_PALETTE[1], height = 200, unit = '' }) {
  const [hover, setHover] = useState(null)
  if (!data.length) return <EmptyChart />
  const max = Math.max(...data.map((d) => d.value), 1)
  const barGap = 8
  const chartH = height - 28

  return (
    <div className="relative w-full min-w-0">
      {hover !== null && (
        <div className="absolute -top-1 left-1/2 -translate-x-1/2 z-10 px-2 py-1 rounded-md bg-inverse text-white text-[11px] whitespace-nowrap shadow-lg pointer-events-none max-w-full truncate">
          {data[hover].label} · {formatValue(data[hover].value)}{unit}
        </div>
      )}
      <div className="flex items-end gap-2" style={{ height }}>
        {data.map((d, i) => {
          const h = Math.max((d.value / max) * chartH, d.value > 0 ? 3 : 0)
          return (
            <div
              key={d.key ?? i}
              className="flex-1 flex flex-col items-center justify-end min-w-0"
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
            >
              <div
                className="w-full rounded-t-md transition-all"
                style={{
                  height: h,
                  backgroundColor: color,
                  opacity: hover === null || hover === i ? 1 : 0.45,
                  marginBottom: barGap,
                }}
              />
              <span className="text-[10px] text-ink/50 truncate w-full text-center">{d.label}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function EmptyChart({ label = 'Aucune donnée disponible' }) {
  return (
    <div className="h-40 flex items-center justify-center text-sm text-ink/40 text-center px-4">
      {label}
    </div>
  )
}

/** Courbe d'evolution (aire + ligne) avec points interactifs. */
export function LineChart({ data = [], color = CHART_PALETTE[0], height = 200, unit = '' }) {
  const [hover, setHover] = useState(null)
  // La couleur peut etre une variable CSS : l'identifiant du degrade ne peut
  // donc plus en etre derive, useId garantit son unicite dans le document.
  const gradientId = `area-${useId().replace(/:/g, '')}`
  if (!data.length) return <EmptyChart />

  const width = 640
  const padX = 32
  const padY = 20
  const max = Math.max(...data.map((d) => d.value), 1)
  const innerW = width - padX * 2
  const innerH = height - padY * 2
  const stepX = data.length > 1 ? innerW / (data.length - 1) : 0
  const points = data.map((d, i) => {
    const x = padX + i * stepX
    const y = padY + innerH - (d.value / max) * innerH
    return { ...d, x, y }
  })
  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const areaPath =
    `M${points[0].x},${padY + innerH} ` +
    points.map((p) => `L${p.x},${p.y}`).join(' ') +
    ` L${points[points.length - 1].x},${padY + innerH} Z`

  return (
    <div className="relative w-full">
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ height }}>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.25" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={areaPath} fill={`url(#${gradientId})`} />
        <path d={linePath} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
        {points.map((p, i) => (
          <g key={p.key ?? i}>
            <circle
              cx={p.x}
              cy={p.y}
              r={hover === i ? 5 : 3}
              fill="var(--color-surface)"
              stroke={color}
              strokeWidth="2"
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
              style={{ cursor: 'pointer' }}
            />
            {i % Math.ceil(data.length / 8 || 1) === 0 && (
              <text x={p.x} y={height - 4} textAnchor="middle" className="fill-ink/45" fontSize="9">
                {p.label}
              </text>
            )}
          </g>
        ))}
        {hover !== null && (
          <g>
            <rect
              x={Math.min(Math.max(points[hover].x - 40, 2), width - 82)}
              y={Math.max(points[hover].y - 32, 2)}
              width="80"
              height="22"
              rx="4"
              fill="var(--color-inverse)"
            />
            <text
              x={Math.min(Math.max(points[hover].x, 42), width - 42)}
              y={Math.max(points[hover].y - 17, 17)}
              textAnchor="middle"
              fill="white"
              fontSize="10"
            >
              {points[hover].label} · {formatValue(points[hover].value)}{unit}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}

/**
 * Diagramme circulaire (donut) avec legende laterale et survol des segments.
 *
 * `legendCols` repartit la legende sur plusieurs colonnes quand la carte est
 * large : les libelles s'affichent alors en entier. Ils ne sont jamais tronques,
 * ils passent a la ligne — un libelle coupe (« Sall... ») n'apprend rien.
 */
export function DonutChart({ data = [], size = 180, unit = '', legendCols = 1, showPercent = false }) {
  const [hover, setHover] = useState(null)
  const items = data.filter((d) => d.value > 0)
  const total = items.reduce((sum, d) => sum + d.value, 0)
  if (!total) return <EmptyChart />

  const radius = size / 2
  const stroke = size * 0.18
  // Marge pour l'epaississement au survol (+4) afin de rester dans le viewBox.
  const r = radius - (stroke + 4) / 2
  const circumference = 2 * Math.PI * r
  let offset = 0

  const segments = items.map((d, i) => {
    const fraction = d.value / total
    const seg = {
      ...d,
      // Couleur explicite de l'element si fournie (ex. couleur configuree d'un
      // type de seance), sinon repli sur la palette du donut.
      color: d.color || DONUT_PALETTE[i % DONUT_PALETTE.length],
      dash: fraction * circumference,
      offset,
      fraction,
    }
    offset += fraction * circumference
    return seg
  })

  return (
    <div className="flex flex-col sm:flex-row items-start gap-5 w-full min-w-0">
      {/* Le donut est fluide : il retrecit avec la Card sans jamais en sortir. */}
      <div
        className="relative shrink-0 w-full sm:w-auto"
        style={{ maxWidth: size, aspectRatio: '1 / 1' }}
      >
        <svg viewBox={`0 0 ${size} ${size}`} className="w-full h-full" style={{ maxHeight: size }}>
          <g transform={`rotate(-90 ${radius} ${radius})`}>
            {segments.map((s, i) => (
              <circle
                key={s.key ?? i}
                cx={radius}
                cy={radius}
                r={r}
                fill="none"
                stroke={s.color}
                strokeWidth={hover === i ? stroke + 4 : stroke}
                strokeDasharray={`${s.dash} ${circumference - s.dash}`}
                strokeDashoffset={-s.offset}
                opacity={hover === null || hover === i ? 1 : 0.4}
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
                style={{ cursor: 'pointer', transition: 'all .15s' }}
              />
            ))}
          </g>
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          {hover === null ? (
            <>
              <span className="text-xl font-display font-semibold text-ink">{formatValue(total)}</span>
              <span className="text-[10px] text-ink/50">Total</span>
            </>
          ) : (
            <>
              <span className="text-lg font-display font-semibold text-ink">
                {Math.round(segments[hover].fraction * 100)}%
              </span>
              <span className="text-[10px] text-ink/50">{formatValue(segments[hover].value)}{unit}</span>
            </>
          )}
        </div>
      </div>
      <ul
        className={`flex-1 min-w-0 w-full grid gap-x-6 gap-y-2 ${LEGEND_GRID[legendCols] ?? LEGEND_GRID[1]}`}
      >
        {segments.map((s, i) => (
          <li
            key={s.key ?? i}
            className="flex items-start justify-between text-sm gap-3 cursor-pointer min-w-0"
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover(null)}
          >
            <span className="flex items-start gap-2 min-w-0">
              <span
                className="w-2.5 h-2.5 rounded-sm shrink-0 mt-[5px]"
                style={{ backgroundColor: s.color }}
              />
              <span className="text-ink/70 leading-snug break-words">{s.label}</span>
            </span>
            <span className="shrink-0 text-right tabular-nums">
              <span className="text-ink font-medium">{formatValue(s.value)}{unit}</span>
              {showPercent && (
                <span className="text-ink/45 ml-1.5">{Math.round(s.fraction * 100)} %</span>
              )}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Classement en barres horizontales (top N).
 *
 * `scaleMax` impose l'echelle des barres : deux classements qui se lisent
 * ensemble (les plus / les moins utilises) doivent partager la meme reference,
 * sinon « 3 utilisations » remplit toute la barre et se lit comme un maximum.
 * `hint` porte le detail de la ligne (localisation, decomposition du total).
 */
export function HBarList({ data = [], color = CHART_PALETTE[2], unit = '', scaleMax, emptyLabel }) {
  if (!data.length) return <EmptyChart label={emptyLabel} />
  const max = Math.max(scaleMax ?? 0, ...data.map((d) => d.value), 1)
  return (
    <ul className="space-y-3">
      {data.map((d, i) => {
        const width = (d.value / max) * 100
        return (
          <li key={d.key ?? i}>
            <div className="flex items-start justify-between text-sm gap-3 mb-1">
              <span className="text-ink/70 leading-snug min-w-0">{d.label}</span>
              <span className="text-ink font-medium tabular-nums shrink-0">
                {formatValue(d.value)}{unit}
              </span>
            </div>
            <div className="h-2 rounded-full bg-ink/8 overflow-hidden">
              <div
                className="h-full rounded-full transition-all"
                // Une valeur non nulle garde un filet visible meme a 1 % de l'echelle.
                style={{
                  width: d.value > 0 ? `max(${width}%, 3px)` : 0,
                  backgroundColor: color,
                }}
              />
            </div>
            {d.hint ? <p className="text-[11px] text-ink/45 mt-1">{d.hint}</p> : null}
          </li>
        )
      })}
    </ul>
  )
}

/** Jauge lineaire de taux d'occupation (0-100 %). */
export function OccupancyBar({ label, hint, rate = 0 }) {
  const pct = Math.max(0, Math.min(100, rate))
  const color = occupancyColor(pct)
  return (
    <div>
      <div className="flex items-center justify-between text-sm mb-1">
        <span className="text-ink/70 truncate pr-2">{label}</span>
        <span className="text-ink font-medium tabular-nums shrink-0">{pct.toFixed(1)} %</span>
      </div>
      <div className="h-2.5 rounded-full bg-ink/8 overflow-hidden">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${pct}%`, backgroundColor: color }}
        />
      </div>
      {hint ? <p className="text-[11px] text-ink/45 mt-0.5">{hint}</p> : null}
    </div>
  )
}

/** Anneau de progression unique (taux d'occupation global). */
export function GaugeRing({ rate = 0, size = 150, label }) {
  const pct = Math.max(0, Math.min(100, rate))
  const radius = size / 2
  const stroke = size * 0.13
  const r = radius - stroke / 2
  const circumference = 2 * Math.PI * r
  const color = occupancyColor(pct)
  return (
    <div className="relative inline-flex" style={{ width: size, height: size }}>
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size}>
        <circle
          cx={radius}
          cy={radius}
          r={r}
          fill="none"
          className="stroke-ink/10"
          strokeWidth={stroke}
        />
        <g transform={`rotate(-90 ${radius} ${radius})`}>
          <circle
            cx={radius}
            cy={radius}
            r={r}
            fill="none"
            stroke={color}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${(pct / 100) * circumference} ${circumference}`}
          />
        </g>
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-display font-semibold text-ink">{pct.toFixed(1)}%</span>
        {label ? <span className="text-[11px] text-ink/50 mt-0.5">{label}</span> : null}
      </div>
    </div>
  )
}
