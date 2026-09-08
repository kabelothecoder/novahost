/**
 * NovaHost — the accent palette, single source of truth.
 *
 * Lives apart from both stores because `accent.js` (glass.css, 9 views)
 * and `theme.js` (command.css, Home + Settings) both need it and neither
 * should own it.
 *
 * ── Why the values changed (2026-09-07) ──────────────────────────────
 * The original eight were pure neon — #00E5FF, #00FF87, #FF003C. Neon
 * blows out through a frosted material: the blur spreads the hue across
 * the whole surface and anything sitting on it loses its edge. Every
 * value was pulled into the register the Claude Design doc uses, which
 * was drawn for exactly this kind of surface.
 *
 * The eight NAMES are unchanged on purpose. `ACCENT_COLOR` persists by
 * name, so retuning the values retunes a saved choice in place instead
 * of orphaning it — dropping to the doc's five would have reset anyone
 * on Cyan, Emerald, Ice or Plasma.
 *
 * ── The three colours each swatch carries ────────────────────────────
 *   hex   the accent itself: fills, glows, borders, and accent-as-text
 *         on the dark canvas (#0E0E11).
 *   ink   text placed ON an accent fill. All eight land between L .22
 *         and L .52, so black-ish ink beats white on every one of them
 *         — white would fail AA at the ignition button's 14px bold.
 *   text  accent-as-text on the LIGHT canvas (#F4F7F9). The hex itself
 *         cannot do this job: #5C9CE6 on #F4F7F9 is 2.9:1. Each of
 *         these darker siblings clears 4.5:1.
 *
 * Verified against #F4F7F9 (light) and #0E0E11 (dark); ratios in the
 * comments are the computed WCAG values, lowest first.
 */

export const SWATCHES = [
  // name        accent      on light bg   contrast: light / dark
  { name: 'Crimson', hex: '#E0544E', rgb: '224, 84, 78',   ink: '#0B0B0D', text: '#B8433D' }, // 4.86 / 5.04
  { name: 'Cyan',    hex: '#5AC8DE', rgb: '90, 200, 222',  ink: '#0B0B0D', text: '#196E82' }, // 5.29 / 9.77
  { name: 'Violet',  hex: '#A288E3', rgb: '162, 136, 227', ink: '#0B0B0D', text: '#6B4FB0' }, // 5.65 / 6.51
  { name: 'Emerald', hex: '#3FD6A8', rgb: '63, 214, 168',  ink: '#0B0B0D', text: '#14795A' }, // 4.87 / 10.4
  { name: 'Gold',    hex: '#E8A33D', rgb: '232, 163, 61',  ink: '#0B0B0D', text: '#8A5D12' }, // 5.14 / 8.84
  { name: 'Ice',     hex: '#5C9CE6', rgb: '92, 156, 230',  ink: '#0B0B0D', text: '#2C6FB8' }, // 4.72 / 6.68
  { name: 'Plasma',  hex: '#C77BE0', rgb: '199, 123, 224', ink: '#0B0B0D', text: '#8340A0' }, // 5.90 / 6.67
  { name: 'Amber',   hex: '#DD8049', rgb: '221, 128, 73',  ink: '#0B0B0D', text: '#96501F' }, // 5.42 / 6.60
]

/** Ice is the doc's own primary (#5C9CE6) and the value nova.css uses. */
export const DEFAULT_ACCENT = 'Amber'

export function swatchByName(name) {
  return SWATCHES.find(s => s.name === name) || SWATCHES.find(s => s.name === DEFAULT_ACCENT) || SWATCHES[0]
}
