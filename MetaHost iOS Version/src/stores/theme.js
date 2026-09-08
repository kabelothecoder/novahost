/**
 * NovaHost — Command Center theme store.
 *
 * The visual half of the `accent.js` split. The design doc's own
 * "BEFORE KOTLIN" notes flagged the Android equivalent for exactly this
 * reason, and the iOS store had the same defect: `accent.js` mixed
 * visual tokens with robot identity (`robotName`, `avatarUrl`,
 * `allowedSymbols`) and broker state in one reactive object, so layout
 * and art settings would have ridden along with robot switching.
 *
 * What lives here: scheme, accent, glow, card style, layout, art mode,
 * widget arrangement. Nothing about which robot is selected.
 *
 * Prefs are device-local — no Supabase columns, no portal work. That
 * matches the Android build, where it was an explicit product decision
 * rather than a shortcut.
 */

import { reactive, computed, watch } from 'vue'
import { secStorage } from '../utils/secStorage.js'
import { SWATCHES, DEFAULT_ACCENT, swatchByName } from './palette.js'
// One-way import only: accent.js must never import this file back, or
// the two stores form a cycle and neither initialises.
import { accent } from './accent.js'

/* ── Registries ──────────────────────────────────────────────────── */

/**
 * The five layouts, in the order the picker shows them.
 * `defaultArt` is the art mode each one is drawn around in the doc —
 * art mode is stored per layout, so switching layout restores the art
 * treatment that layout was designed for rather than carrying the last
 * one across.
 */
export const LAYOUTS = [
  { id: 'classic', name: 'Classic Core',  blurb: 'Reworked · text bottom nav', defaultArt: 'avatar' },
  { id: 'focus',   name: 'Focus Engine',  blurb: 'Art forward · floating pill', defaultArt: 'bleed'  },
  { id: 'bleed',   name: 'Full-Bleed Art', blurb: 'The marketing screen',       defaultArt: 'bleed'  },
  { id: 'neon',    name: 'Neon Frame',    blurb: 'Circular art · text nav',     defaultArt: 'framed' },
  { id: 'feed',    name: 'Signal Feed',   blurb: 'Stats · live mentor signals', defaultArt: 'avatar' },
]

export const ART_MODES = [
  { id: 'avatar', name: 'Avatar',    blurb: 'Contained portrait' },
  { id: 'framed', name: 'Framed',    blurb: 'Circular, ringed'   },
  { id: 'bleed',  name: 'Full bleed', blurb: 'Fills the screen'  },
]

/**
 * Robot Hero and Ignition Pod are pinned in all five — the doc pins
 * them, and a Home with no way to start the robot is not a layout.
 */
export const WIDGETS = [
  { id: 'robotHero',    name: 'Robot Hero',           desc: 'Art, name, mentor credit',        pinned: true  },
  { id: 'ignitionPod',  name: 'Ignition Pod',         desc: 'Quotes · Start/Stop · Asset Hub', pinned: true  },
  { id: 'robotList',    name: 'Robot List',           desc: 'Licence-bound robots + connect',  pinned: false },
  { id: 'fleetStatus',  name: 'Fleet Status',         desc: 'Uptime · ping · heartbeat',       pinned: false },
  { id: 'voiceConsole', name: 'Voice Console',        desc: 'Waveform, push to talk',          pinned: false },
  { id: 'chartScanner', name: 'AI Chart Scanner',     desc: 'Screenshot dropzone',             pinned: false },
  { id: 'terminalLog',  name: 'Terminal Activity Log', desc: 'Pulse service events',           pinned: false },
]

export const REORDERABLE = WIDGETS.filter(w => !w.pinned).map(w => w.id)

export const GLOW_MODES = [
  { id: 'off',     name: 'Off',     blurb: 'Crisp edge, no bloom', value: 0    },
  { id: 'soft',    name: 'Soft',    blurb: 'Subtle halo',          value: 0.5  },
  { id: 'medium',  name: 'Medium',  blurb: 'Balanced shine',       value: 1    },
  { id: 'intense', name: 'Intense', blurb: 'Full neon blast',      value: 1.6  },
]

export const CARD_STYLES = [
  { id: 'glass', name: 'Glass',       blurb: 'Frosted, blurs the art' },
  { id: 'neon',  name: 'Neon border', blurb: 'Every edge glows'       },
  { id: 'solid', name: 'Solid',       blurb: 'Opaque panels'          },
]

export const SCHEMES = [
  { id: 'dark',   name: 'Dark'   },
  { id: 'light',  name: 'Light'  },
  { id: 'system', name: 'System' },
]

/**
 * Default arrangement per layout. Order is shared; what differs is what
 * each layout hides, and that follows how densely the doc draws it —
 * Full-Bleed Art is the marketing screen and shows nothing but the two
 * pinned widgets, Signal Feed is the dense one and shows everything.
 */
const DEFAULT_HIDDEN = {
  classic: ['terminalLog'],
  focus:   ['robotList', 'fleetStatus', 'terminalLog'],
  bleed:   ['robotList', 'fleetStatus', 'voiceConsole', 'chartScanner', 'terminalLog'],
  neon:    ['voiceConsole', 'terminalLog'],
  /* Signal Feed draws its own fleet strip and terminal section as part
     of the layout, so the widget versions of both would render the
     same numbers twice on one screen. They remain available in Arrange
     Widgets for anyone who wants the card treatment instead. */
  feed:    ['fleetStatus', 'terminalLog'],
}

/* ── Persistence ─────────────────────────────────────────────────── */

const KEY = {
  scheme:   'CC_SCHEME',
  accent:   'ACCENT_COLOR',   // shared with accent.js on purpose — one choice, one key
  mentor:   'MENTOR_ACCENT',  // written by authService from the licence
  override: 'ACCENT_OVERRIDDEN',
  glow:     'CC_GLOW',
  card:     'CC_CARD_STYLE',
  layout:   'CC_LAYOUT',
  art:      'CC_ART_MODES',
  widgets:  'CC_WIDGETS',
  motion:   'CC_MOTION',
  haptics:  'CC_HAPTICS',
}

function readEnum(key, options, fallback) {
  const saved = secStorage.getItem(key)
  return options.some(o => o.id === saved) ? saved : fallback
}

/** Seeds every layout that has no saved entry, so a layout added later
 *  picks up its own default instead of inheriting an empty object. */
function seedArt(saved) {
  const out = {}
  for (const l of LAYOUTS) {
    out[l.id] = ART_MODES.some(a => a.id === saved?.[l.id]) ? saved[l.id] : l.defaultArt
  }
  return out
}

/**
 * Rebuilt against the WIDGETS registry rather than trusted wholesale:
 * a stored arrangement from an older build can name a widget that no
 * longer exists, or be missing one that was added since. Unknown ids
 * are dropped, new ids are appended.
 */
function seedWidgets(saved) {
  const out = {}
  for (const l of LAYOUTS) {
    const s = saved?.[l.id]
    const order = Array.isArray(s?.order) ? s.order.filter(id => REORDERABLE.includes(id)) : []
    for (const id of REORDERABLE) if (!order.includes(id)) order.push(id)
    const hidden = Array.isArray(s?.hidden)
      ? s.hidden.filter(id => REORDERABLE.includes(id))
      : [...DEFAULT_HIDDEN[l.id]]
    out[l.id] = { order, hidden }
  }
  return out
}

/* ── State ───────────────────────────────────────────────────────── */

export const theme = reactive({
  scheme:     readEnum(KEY.scheme, SCHEMES,     'dark'),
  accent:     swatchByName(secStorage.getItem(KEY.accent) || DEFAULT_ACCENT),
  glowMode:   readEnum(KEY.glow,   GLOW_MODES,  'medium'),
  cardStyle:  readEnum(KEY.card,   CARD_STYLES, 'glass'),
  layout:     readEnum(KEY.layout, LAYOUTS,     'bleed'),
  art:        seedArt(secStorage.getItem(KEY.art, true)),
  widgets:    seedWidgets(secStorage.getItem(KEY.widgets, true)),

  /* 'full' | 'reduced'. An in-app override, because the OS-level
     prefers-reduced-motion setting is all-or-nothing across every app
     and some people want this one calm without changing that. The CSS
     media query still wins independently — this can only ADD calm,
     never take it away from someone who asked the OS for it. */
  motion:     secStorage.getItem(KEY.motion) === 'reduced' ? 'reduced' : 'full',

  /* The old Settings screen had a "Haptic Feedback" switch that was a
     local ref nothing ever read — it looked like a setting and did
     nothing. Kept, but wired: composables/useSpeech.js consults this
     before every vibrate. */
  haptics:    secStorage.getItem(KEY.haptics) !== 'false',

  /** The accent the licence specified, if a licence has been validated
   *  on this device. Null means the Mentor default option has nothing
   *  to restore and must be offered as unavailable rather than as a
   *  button that silently does nothing. */
  mentorAccent: secStorage.getItem(KEY.mentor),

  /** True once the user picks a colour themselves. Kept so re-validating
   *  a licence does not quietly undo their choice. */
  accentOverridden: secStorage.getItem(KEY.override) === 'true',
})

/* ── Derived ─────────────────────────────────────────────────────── */

const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)')

/** 'system' resolves through matchMedia; everything else is literal. */
export const resolvedScheme = computed(() =>
  theme.scheme === 'system'
    ? (systemDark?.matches ? 'dark' : 'light')
    : theme.scheme
)

export const glowValue = computed(() =>
  GLOW_MODES.find(g => g.id === theme.glowMode)?.value ?? 1
)

export const activeLayout = computed(() =>
  LAYOUTS.find(l => l.id === theme.layout) || LAYOUTS[0]
)

/** Art mode for whichever layout is active — the doc's contextual card. */
export const activeArtMode = computed({
  get:  () => theme.art[theme.layout] || activeLayout.value.defaultArt,
  set:  (v) => { theme.art[theme.layout] = v },
})

/** Visible reorderable widgets for the active layout, in stored order. */
export const activeWidgets = computed(() => {
  const cfg = theme.widgets[theme.layout]
  if (!cfg) return []
  return cfg.order.filter(id => !cfg.hidden.includes(id))
})

/* ── Apply to the DOM ────────────────────────────────────────────────
   Only the accent and glow become CSS custom properties. Scheme and
   card style are data attributes bound by the views onto their `.cmd`
   root, because command.css keys its overrides off that element — a
   root-level variable could not switch a whole token set at once.     */

/**
 * Repaints token changes with transitions off for one frame.
 *
 * A transition on a property fed by a custom property does not re-run
 * when that custom property changes — the element settles on the value
 * it had BEFORE the change and stays there. Switching Light -> Dark
 * left every transitioned control painting the previous scheme's ink.
 * See the matching note in command.css.
 *
 * Two rAFs, not one: the first schedules the frame that applies the new
 * values, the second runs after it has been painted. Removing the class
 * on a single rAF puts the transitions back before that paint and the
 * stale value returns.
 */
function withoutTransitions(apply) {
  const el = document.documentElement
  el.classList.add('cc-no-transition')
  apply()
  // Force a style flush so the frozen state is what the new values land on.
  void el.offsetHeight

  let done = false
  const thaw = () => {
    if (done) return
    done = true
    el.classList.remove('cc-no-transition')
  }

  // Preferred path: unfreeze after the frame carrying the new values has
  // painted.
  requestAnimationFrame(() => requestAnimationFrame(thaw))

  // rAF is throttled to zero in a hidden, occluded or backgrounded tab —
  // measured firing NOT AT ALL in an unfocused preview pane. Without this
  // fallback a scheme change made as the app backgrounds would leave every
  // transition disabled for the rest of the session.
  setTimeout(thaw, 120)
}

function applyTokens() {
  withoutTransitions(writeTokens)
}

function writeTokens() {
  const root  = document.documentElement.style
  const sw    = theme.accent
  const light = resolvedScheme.value === 'light'

  root.setProperty('--cc-accent',     sw.hex)
  root.setProperty('--cc-accent-rgb', sw.rgb)
  root.setProperty('--cc-accent-ink', sw.ink)
  // Accent-as-text needs the darker sibling on a light canvas; the raw
  // hex only clears 4.5:1 against #0E0E11.
  root.setProperty('--cc-accent-text', light ? sw.text : sw.hex)
  root.setProperty('--cc-glow', String(glowValue.value))

  // PWA status bar / theme colour follows the canvas, not the accent —
  // accent.js points it at the accent for the obsidian views, and Home
  // reclaims it while mounted.
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', light ? '#F4F7F9' : '#0E0E11')
}

watch(
  () => [theme.accent, theme.glowMode, resolvedScheme.value],
  applyTokens,
  { immediate: true }
)

// Only matters while the user is actually on 'system'.
systemDark?.addEventListener?.('change', () => {
  if (theme.scheme === 'system') applyTokens()
})

/* ── Persist ─────────────────────────────────────────────────────── */

watch(() => theme.scheme,    v => secStorage.setItem(KEY.scheme, v))
watch(() => theme.glowMode,  v => secStorage.setItem(KEY.glow,   v))
watch(() => theme.cardStyle, v => secStorage.setItem(KEY.card,   v))
watch(() => theme.layout,    v => secStorage.setItem(KEY.layout, v))
watch(() => theme.motion,    v => secStorage.setItem(KEY.motion, v))
watch(() => theme.haptics,   v => secStorage.setItem(KEY.haptics, String(v)))
watch(() => theme.accentOverridden, v => secStorage.setItem(KEY.override, String(v)))
watch(() => theme.art,     v => secStorage.setItem(KEY.art,     v), { deep: true })
watch(() => theme.widgets, v => secStorage.setItem(KEY.widgets, v), { deep: true })

/* ── Actions ─────────────────────────────────────────────────────── */

/**
 * One accent, two stores.
 *
 * `accent.current` drives glass.css's `--accent*` vars for the nine
 * views this redesign does not touch; `theme.accent` drives the
 * `--cc-accent*` vars for Home and Settings. They are separate reactive
 * objects, so without a bridge a pick made in the Interface Style sheet
 * would leave the dock and the other views on the previous colour until
 * a reload. Both directions are mirrored: the old SettingsView picker
 * still writes `accent.current` directly.
 *
 * accent.js persists ACCENT_COLOR itself, so this does not write it.
 */
export function setAccent(swatch, { asOverride = true } = {}) {
  const sw = typeof swatch === 'string' ? swatchByName(swatch) : swatch
  theme.accent   = sw
  accent.current = sw
  if (asOverride) theme.accentOverridden = true
}

/** Restores the licence's colour and clears the override, so a later
 *  mentor change propagates again. */
export function useMentorAccent() {
  if (!theme.mentorAccent) return false
  setAccent(theme.mentorAccent, { asOverride: false })
  theme.accentOverridden = false
  return true
}

export const isMentorAccent = computed(() =>
  Boolean(theme.mentorAccent) && !theme.accentOverridden
)

watch(() => accent.current, (sw) => {
  if (sw && sw.name !== theme.accent.name) theme.accent = swatchByName(sw.name)
})

export function toggleWidget(layoutId, widgetId) {
  const cfg = theme.widgets[layoutId]
  if (!cfg || !REORDERABLE.includes(widgetId)) return
  const i = cfg.hidden.indexOf(widgetId)
  if (i === -1) cfg.hidden.push(widgetId)
  else cfg.hidden.splice(i, 1)
}

export function moveWidget(layoutId, from, to) {
  const cfg = theme.widgets[layoutId]
  if (!cfg) return
  if (to < 0 || to >= cfg.order.length || from === to) return
  const [id] = cfg.order.splice(from, 1)
  cfg.order.splice(to, 0, id)
}

export function resetArrangement(layoutId) {
  theme.widgets[layoutId] = {
    order:  [...REORDERABLE],
    hidden: [...DEFAULT_HIDDEN[layoutId]],
  }
}

export { SWATCHES }
