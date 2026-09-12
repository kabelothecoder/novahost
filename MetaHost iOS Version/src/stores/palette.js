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
 *
 * ── Custom accents (2026-09-12) ──────────────────────────────────────
 * The eight presets are no longer the whole palette: the Interface Style
 * sheet now carries a spectrum picker, so an accent can be any hue. A
 * chosen colour is not a preset with a name — it is a hex — so the three
 * companion colours above have to be DERIVED rather than looked up, and
 * `deriveSwatch` below does that with the same contrast maths the preset
 * table was measured with. It is the reason `ACCENT_COLOR` now accepts a
 * `#RRGGBB` as well as a name.
 */

/* ── Colour maths ─────────────────────────────────────────────────────
   WCAG 2.1 relative luminance and contrast ratio. Small, exact, and
   deliberately not a dependency: this runs on every accent change and
   on first paint, and a colour library would be more bytes than the
   whole store. */

function clamp255(n) { return Math.max(0, Math.min(255, Math.round(n))) }

export function hexToRgbArray(hex) {
  const h = String(hex || '').replace('#', '').trim()
  const full = h.length === 3 ? h.split('').map(c => c + c).join('') : h
  if (!/^[0-9a-f]{6}$/i.test(full)) return null
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ]
}

function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map(n => clamp255(n).toString(16).padStart(2, '0')).join('').toUpperCase()
}

function luminance([r, g, b]) {
  const f = (v) => {
    v /= 255
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
}

/** WCAG contrast ratio between two hexes. 1 (identical) to 21 (b/w). */
export function contrastRatio(a, b) {
  const ra = hexToRgbArray(a), rb = hexToRgbArray(b)
  if (!ra || !rb) return 1
  const la = luminance(ra), lb = luminance(rb)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

/** HSL in 0-360 / 0-1 / 0-1. */
export function hexToHsl(hex) {
  const rgb = hexToRgbArray(hex)
  if (!rgb) return { h: 0, s: 0, l: 0 }
  const [r, g, b] = rgb.map(v => v / 255)
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const l = (max + min) / 2
  const d = max - min
  if (!d) return { h: 0, s: 0, l }
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h
  if (max === r)      h = ((g - b) / d + (g < b ? 6 : 0))
  else if (max === g) h = ((b - r) / d + 2)
  else                h = ((r - g) / d + 4)
  return { h: h * 60, s, l }
}

export function hslToHex(h, s, l) {
  h = ((h % 360) + 360) % 360
  s = Math.max(0, Math.min(1, s))
  l = Math.max(0, Math.min(1, l))
  const c = (1 - Math.abs(2 * l - 1)) * s
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = l - c / 2
  const seg = Math.floor(h / 60) % 6
  const [r, g, b] = (
    [[c, x, 0], [x, c, 0], [0, c, x], [0, x, c], [x, 0, c], [c, 0, x]][seg]
  )
  return rgbToHex((r + m) * 255, (g + m) * 255, (b + m) * 255)
}

/* The two canvases every accent has to survive. Both are literals from
   command.css rather than reads of the live tokens: this runs before
   first paint, and the tone setting only moves the DARK canvas, always
   downward in luminance — so measuring against the lightest dark canvas
   the app can show is the conservative check. */
const LIGHT_CANVAS = '#F4F7F9'
const DARK_CANVAS  = '#1C1C21'   // the lightest step of the lightest tone
const INK_DARK     = '#0B0B0D'

/**
 * Walks a hue toward a target contrast against one canvas.
 *
 * Lightness only — the hue and saturation are the user's choice and
 * moving either would hand back a colour they did not pick. `step` is
 * signed: negative darkens (for the light canvas), positive lightens
 * (for the dark one).
 */
function toContrast(hex, canvas, target, step) {
  const { h, s } = hexToHsl(hex)
  let { l } = hexToHsl(hex)
  let out = hex
  for (let i = 0; i < 40; i++) {
    if (contrastRatio(out, canvas) >= target) return out
    l = Math.max(0, Math.min(1, l + step))
    out = hslToHex(h, s, l)
    if (l <= 0 || l >= 1) break
  }
  return out
}

/**
 * Builds a full swatch — the four colours the app needs — from one hex.
 *
 * Used for custom spectrum picks. The presets below carry hand-measured
 * values instead, because they were tuned as a family and re-deriving
 * them would move colours that are already correct.
 *
 *   ink      what sits ON the accent fill. Black-ish wherever it wins,
 *            which is most of this register; white for anything genuinely
 *            dark, so a deep navy accent does not get black text on it.
 *   text     accent-as-text on the LIGHT canvas — darkened until it
 *            clears 4.5:1, since almost nothing in this register does at
 *            full lightness.
 *   textDark accent-as-text on the DARK canvas. Usually the hex itself;
 *            lightened when the user picks something too dark to read.
 *            The presets do not need this field — every one of them
 *            already clears the dark canvas — but a spectrum can reach
 *            colours that do not, and without it a deep pick turns every
 *            accent label on Home invisible.
 */
export function deriveSwatch(hex, name) {
  const rgb = hexToRgbArray(hex)
  if (!rgb) return swatchByName(DEFAULT_ACCENT)
  const clean = rgbToHex(...rgb)

  return {
    name: name || clean,
    hex: clean,
    rgb: rgb.join(', '),
    ink: contrastRatio(clean, INK_DARK) >= contrastRatio(clean, '#FFFFFF') ? INK_DARK : '#FFFFFF',
    text: toContrast(clean, LIGHT_CANVAS, 4.6, -0.03),
    textDark: toContrast(clean, DARK_CANVAS, 4.6, +0.03),
    custom: true,
  }
}

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

/**
 * Resolves a stored accent.
 *
 * Takes a preset NAME or a `#RRGGBB` — the spectrum picker stores the
 * latter under the same key, because one accent should have one storage
 * slot and adding a second would mean deciding which of two saved values
 * wins on every read.
 */
export function swatchByName(name) {
  const v = String(name || '').trim()
  if (v.startsWith('#')) {
    const rgb = hexToRgbArray(v)
    if (rgb) return deriveSwatch(v)
  }
  return SWATCHES.find(s => s.name === v)
    || SWATCHES.find(s => s.name === DEFAULT_ACCENT)
    || SWATCHES[0]
}

/**
 * The spectrum, as one dimension the UI can put on a rail.
 *
 * Hue is the obvious axis; the second one is NOT saturation or lightness
 * on its own. Every preset above sits between L .54 and .62 at S .65-.70,
 * which is not a coincidence — it is the band where a colour is vivid
 * enough to glow on a dark canvas and still dark enough to hold black
 * ink on a filled button. A raw S/L pair lets the user leave that band
 * and get an accent that fails one end or the other.
 *
 * `intensity` travels along the band instead: 0 is a pale wash, 1 a deep
 * saturated tone, and both ends stay measurable. Two rails, whole wheel,
 * nothing unreadable.
 */
export function spectrumHex(hue, intensity) {
  const t = Math.max(0, Math.min(1, Number(intensity)))
  const s = 0.32 + t * 0.53   // .32 → .85
  const l = 0.74 - t * 0.26   // .74 → .48
  return hslToHex(Number(hue) || 0, s, l)
}

/** The inverse, so opening the sheet puts the rails where the colour is. */
export function spectrumPosition(hex) {
  const { h, l } = hexToHsl(hex)
  // Lightness is the reliable half: saturation saturates (a pale colour and
  // a washed one differ more in L than in S), so the rail is read off L.
  const t = Math.max(0, Math.min(1, (0.74 - l) / 0.26))
  return { hue: Math.round(h), intensity: Math.round(t * 100) / 100 }
}
