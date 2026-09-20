/**
 * Coquille commune aux écrans d'authentification (connexion, mot de passe
 * oublié, réinitialisation). Le panneau gauche « blueprint » est identique
 * partout : il est factorisé ici pour éviter de le dupliquer dans chaque page.
 *
 * L'identité de l'établissement (logo, nom, slogan) y est affichée AVANT toute
 * authentification (§11) : elle vient de `GET /api/settings/branding`, endpoint
 * volontairement public, via SettingsContext.
 */

import { useSettings } from '../../context/SettingsContext'
import { BrandLogo, BrandName } from '../Brand'
import ThemeToggle from '../ThemeToggle'

function BlueprintFloorplan() {
  return (
    <svg viewBox="0 0 420 260" className="w-full max-w-sm" aria-hidden="true">
      <rect x="30" y="20" width="360" height="200" rx="6"
        className="fill-white/[0.02] stroke-blueprint-line/30" strokeWidth="1.5" />
      <rect x="30" y="100" width="360" height="40" className="fill-white/[0.03]" />
      <line x1="35" y1="120" x2="385" y2="120"
        className="stroke-blueprint-line/20" strokeWidth="1" strokeDasharray="4 4" />

      <line x1="150" y1="20" x2="150" y2="100" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="270" y1="20" x2="270" y2="100" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="150" y1="140" x2="150" y2="220" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="270" y1="140" x2="270" y2="220" className="stroke-blueprint-line/30" strokeWidth="1.5" />

      <rect x="32" y="22" width="116" height="76" rx="2"
        className="fill-signal/10 stroke-signal/60 motion-safe:animate-pulse" strokeWidth="1.5" />
      <circle cx="138" cy="32" r="3" className="fill-signal motion-safe:animate-pulse" />
      <text x="42" y="42" className="fill-signal-light font-mono" fontSize="9" letterSpacing="0.5">A-01</text>
      <text x="42" y="54" className="fill-blueprint-line/70 font-mono" fontSize="7">Libre</text>
      <path d="M80,100 A20,20 0 0 1 100,80" className="fill-none stroke-blueprint-line/25" strokeWidth="1" />

      <rect x="272" y="142" width="116" height="76" rx="2"
        className="fill-signal/10 stroke-signal/60 motion-safe:animate-pulse" strokeWidth="1.5" />
      <circle cx="378" cy="152" r="3" className="fill-signal motion-safe:animate-pulse" />
      <text x="282" y="162" className="fill-signal-light font-mono" fontSize="9" letterSpacing="0.5">C-18</text>
      <text x="282" y="174" className="fill-blueprint-line/70 font-mono" fontSize="7">Libre</text>
      <path d="M320,140 A20,20 0 0 0 340,160" className="fill-none stroke-blueprint-line/25" strokeWidth="1" />

      <g className="stroke-blueprint-line/30" strokeWidth="1" fill="none">
        <circle cx="395" cy="40" r="13" />
        <polygon points="395,29 398,41 392,41" className="fill-blueprint-line/40" />
      </g>
      <text x="395" y="14" textAnchor="middle" className="fill-blueprint-line/50 font-mono" fontSize="8">N</text>
    </svg>
  )
}

export default function AuthShell({ children }) {
  const { branding } = useSettings()
  return (
    <div className="min-h-screen flex flex-col lg:flex-row font-body">

      {/* Panneau gauche : identité de l'établissement */}
      <div className="relative lg:w-1/2 bg-gradient-to-br from-nav to-nav-deep text-white px-8 py-10 lg:px-16 lg:py-16 flex flex-col justify-between overflow-hidden">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'linear-gradient(var(--color-blueprint-line) 1px, transparent 1px), linear-gradient(90deg, var(--color-blueprint-line) 1px, transparent 1px)',
            backgroundSize: '32px 32px',
          }}
        />

        <div className="relative">
          <span className="font-mono text-[11px] tracking-[0.2em] text-signal-light uppercase">
            Système de gestion des espaces
          </span>
          <BrandLogo className="mt-4 h-14 w-auto max-w-[220px] object-contain object-left" />
          <h1 className="font-display text-4xl lg:text-5xl font-semibold mt-3 tracking-tight">
            <BrandName accentClass="text-signal-light" />
          </h1>
          <p className="text-blueprint-line/80 mt-3 max-w-sm text-sm lg:text-base">
            {branding.slogan || 'Réservez et gérez les salles, amphis et laboratoires de votre établissement en temps réel.'}
          </p>
        </div>

        <div className="relative flex justify-center py-8">
          <BlueprintFloorplan />
        </div>

        <div className="relative flex items-center gap-6 font-mono text-xs text-blueprint-line/70 border-t border-white/10 pt-5">
          <span><span className="text-signal-light font-medium">18+</span> Bâtiments</span>
          <span className="w-px h-4 bg-white/10" />
          <span><span className="text-signal-light font-medium">300+</span> Espaces</span>
          <span className="w-px h-4 bg-white/10" />
          <span>Disponibilité en temps réel</span>
        </div>
      </div>

      {/* Panneau droit : formulaire de la page courante */}
      <div className="relative lg:w-1/2 bg-paper flex items-center justify-center px-6 py-12">
        {/* Bascule clair/sombre, disponible avant authentification. */}
        <div className="absolute top-4 right-4 flex items-center gap-1.5">
          <ThemeToggle />
        </div>

        <div className="w-full max-w-sm">
          {children}

          <div className="mt-10 pt-6 border-t border-ink/10 text-center">
            <p className="text-xs text-ink/40">
              © 2026 CampusOps · Tous droits réservés
            </p>
            <p className="font-mono text-[10px] text-ink/30 mt-1 tracking-wide">
              Gestion et réservation des espaces pédagogiques
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
