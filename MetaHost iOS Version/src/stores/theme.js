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
import { SWATCHES, DEFAULT_ACCENT, swatchByName, deriveSwatch } from './palette.js'
// One-way import only: accent.js must never import this file back, or
// the two stores form a cycle and neither initialises.
import { accent } from './accent.js'

/* ── Registries ──────────────────────────────────────────────────── */

/**
 * The layouts, in the order the picker shows them (Home v4, 2026-10-22).
 *
 * Neon Frame, Signal Feed and Command Deck were removed. A stored id for
 * one of them fails readEnum's check and falls back to Full-Bleed, so
 * nobody lands on a blank Home. `bleed` keeps its id because it is a
 * stored preference.
 *
 * `defaultArt` is vestigial for the v4 layouts, which own their art
 * treatment, but the art map is still seeded from it.
 */
export const LAYOUTS = [
  { id: 'classic', name: 'Classic Core', blurb: 'Hero card, neon name, slide to start', defaultArt: 'portrait' },
  { id: 'focus',   name: 'Focus Engine', blurb: 'Controls orbit the robot',             defaultArt: 'bleed'    },
  { id: 'bleed',   name: 'Full-Bleed',   blurb: 'Video edge to edge, slim control bar', defaultArt: 'bleed'    },
  { id: 'core',    name: 'Command Core', blurb: 'Live HUD · auto Lite',                 defaultArt: 'portrait' },
]

export const ART_MODES = [
  { id: 'avatar',   name: 'Avatar',     blurb: 'Contained portrait' },
  { id: 'framed',   name: 'Framed',     blurb: 'Circular, ringed'   },
  /* The robot fills its own card rather than sitting as a token inside one.
     Between Framed and Full bleed in weight: the art is the card, but the
     card is still a card — so the layout keeps its structure and the widgets
     below it keep a ground to sit on. */
  { id: 'portrait', name: 'Portrait',   blurb: 'Art fills the card' },
  { id: 'bleed',    name: 'Full bleed', blurb: 'Fills the screen'   },
]

/**
 * Robot Hero and Ignition Pod are pinned in all five — the doc pins
 * them, and a Home with no way to start the robot is not a layout.
 */
export const WIDGETS = [
  { id: 'robotHero',    name: 'Robot Hero',           desc: 'Art, name, mentor credit',        pinned: true  },
  { id: 'ignitionPod',  name: 'Ignition Pod',         desc: 'Quotes · Start/Stop · Asset Hub', pinned: true  },
  { id: 'openTrades',   name: 'Open Trades',          desc: 'Live positions and floating P/L', pinned: false },
  { id: 'systemHealth', name: 'System Health',        desc: 'Readiness score across checks',   pinned: false },
  { id: 'robotList',    name: 'Robot List',           desc: 'Licence-bound robots + connect',  pinned: false },
  { id: 'fleetStatus',  name: 'Fleet Status',         desc: 'Uptime · ping · heartbeat',       pinned: false },
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

/**
 * Gradient accents — twenty ramps plus Off.
 *
 * The doc shipped six. Twenty is the ask, and the jump is what forced
 * the picker from a list of named rows to a square grid: twenty rows is
 * four screens of scrolling, twenty tiles is one glance.
 *
 * Values are rebuilt from the NovaHost swatches wherever the doc's ramp
 * had an equivalent: its palette is the one palette.js replaced, for the
 * reason recorded there. Rust keeps the doc's own values — no NovaHost
 * swatch sits in that register, and "deep and matte" is the point of it.
 *
 * `ink` is the text colour ON the ramp, and it is MEASURED across the
 * ramp rather than at its two stops: text spans the whole gradient, and
 * the middle of a two-stop ramp is not guaranteed to sit between its
 * ends once both are in linear light. Nine samples per ramp, worst case
 * wins, and the number in each comment is that worst case. Every one of
 * the twenty clears 4.5:1.
 *
 * Four were retuned or replaced during the expansion rather than shipped
 * as drawn:
 *
 *   Forest  #3FA96B->#1C5638 failed with EVERY ink — 2.28 against black,
 *           2.96 against white. Its midrange lands in the dead zone where
 *           neither works, which picking a side does not fix. Scaled to
 *           0.70 of its own luminance; white is now 5.53.
 *   Indigo  #4C6BFF->#5C9CE6 scraped 4.55 with black — a pass, but inside
 *           the rounding of the floor. Its dark stop is lifted 20% toward
 *           periwinkle; black is now 5.15.
 *   Magma   drawn as #D93A3A->#F07C2E and measured 4.32, a fail. Pulled
 *           down into the deep register instead of up into Ember's, which
 *           it would otherwise have duplicated: white is now 5.31.
 *   Moss    #6E8C3A->#2F6B44 measured 3.84 and every fix for it landed on
 *           top of Forest or Citrus. Dropped for Mono, the one achromatic
 *           ramp in the set — and the only option for someone who wants
 *           the ignition button to carry no hue at all.
 *
 * Off is first and default, so an update repaints nobody's screen.
 */
export const GRADIENTS = [
  { id: 'off',    name: 'Off',    blurb: 'Flat accent, no ramp', css: null,                                     ink: null      },
  { id: 'nebula', name: 'Nebula', blurb: 'Blue into violet',     css: 'linear-gradient(135deg,#5C9CE6,#C77BE0)', ink: '#0B0B0D' }, //  6.59
  { id: 'tide',   name: 'Tide',   blurb: 'Mint into cyan',       css: 'linear-gradient(135deg,#3FD6A8,#5AC8DE)', ink: '#0B0B0D' }, // 10.07
  { id: 'ember',  name: 'Ember',  blurb: 'Coral into amber',     css: 'linear-gradient(135deg,#E0544E,#E8A33D)', ink: '#0B0B0D' }, //  5.19
  { id: 'rust',   name: 'Rust',   blurb: 'Deep and matte',       css: 'linear-gradient(135deg,#B4472F,#5A1F16)', ink: '#FFFFFF' }, //  5.41
  { id: 'forest', name: 'Forest', blurb: 'Green, low key',       css: 'linear-gradient(135deg,#2C764B,#143C27)', ink: '#FFFFFF' }, //  5.53
  { id: 'indigo', name: 'Indigo', blurb: 'Blue into ice',        css: 'linear-gradient(135deg,#5877FF,#5C9CE6)', ink: '#0B0B0D' }, //  5.15
  { id: 'sunset', name: 'Sunset', blurb: 'Amber into rose',      css: 'linear-gradient(135deg,#F0A03C,#E0546F)', ink: '#0B0B0D' }, //  5.31
  { id: 'magma',  name: 'Magma',  blurb: 'Deep red into orange', css: 'linear-gradient(135deg,#8E1C1C,#B8471A)', ink: '#FFFFFF' }, //  5.31
  { id: 'orchid', name: 'Orchid', blurb: 'Violet into pink',     css: 'linear-gradient(135deg,#A288E3,#E56FAE)', ink: '#0B0B0D' }, //  6.59
  { id: 'lagoon', name: 'Lagoon', blurb: 'Teal into blue',       css: 'linear-gradient(135deg,#2FC4C0,#4A8FE0)', ink: '#0B0B0D' }, //  5.90
  { id: 'citrus', name: 'Citrus', blurb: 'Lime into gold',       css: 'linear-gradient(135deg,#A8D93C,#F0C63C)', ink: '#0B0B0D' }, // 11.77
  { id: 'coral',  name: 'Coral',  blurb: 'Pink into peach',      css: 'linear-gradient(135deg,#F0708A,#F5A97A)', ink: '#0B0B0D' }, //  6.92
  { id: 'arctic', name: 'Arctic', blurb: 'Ice into pale blue',   css: 'linear-gradient(135deg,#7FC4F0,#C2E4F5)', ink: '#0B0B0D' }, // 10.35
  { id: 'plum',   name: 'Plum',   blurb: 'Wine into violet',     css: 'linear-gradient(135deg,#8C2F5A,#4A1F5C)', ink: '#FFFFFF' }, //  7.87
  { id: 'copper', name: 'Copper', blurb: 'Bronze into sand',     css: 'linear-gradient(135deg,#C4763C,#E0B478)', ink: '#0B0B0D' }, //  5.62
  { id: 'storm',  name: 'Storm',  blurb: 'Slate into steel',     css: 'linear-gradient(135deg,#4A5A70,#22303F)', ink: '#FFFFFF' }, //  7.03
  { id: 'volt',   name: 'Volt',   blurb: 'Green into cyan',      css: 'linear-gradient(135deg,#4CE07A,#3FD6D6)', ink: '#0B0B0D' }, // 11.05
  { id: 'royal',  name: 'Royal',  blurb: 'Indigo into purple',   css: 'linear-gradient(135deg,#3A4FC4,#7A3FC4)', ink: '#FFFFFF' }, //  6.30
  { id: 'candy',  name: 'Candy',  blurb: 'Cyan into magenta',    css: 'linear-gradient(135deg,#4FD6E0,#E06FD6)', ink: '#0B0B0D' }, //  6.94
  { id: 'mono',   name: 'Mono',   blurb: 'Graphite, no hue',     css: 'linear-gradient(135deg,#C2C6CE,#7E8490)', ink: '#0B0B0D' }, //  5.23
]

/**
 * Background tones — the base the DARK scheme is built on.
 *
 * Light is untouched. Its canvas is a designed value from the nova.css
 * register rather than a tint of the dark one, so tinting it would
 * either break that identity or need a second set of thirteen. The
 * setting is dark-only and the picker says so rather than silently doing
 * nothing.
 *
 * Each tone carries its whole ladder because the steps are not derivable
 * from the canvas in CSS. They are GENERATED by applying the shipped
 * ladder's per-channel deltas to each base — which preserves both the
 * hue and the separation between steps — then MEASURED against the ink
 * scale: every tone holds ink 1-4 above 4.5:1 on canvas, canvas-2, card
 * and card-2. The worst case in each row's comment is that measurement,
 * and in all thirteen it is ink-4 (#8A8A94) on card-2, the tightest pair
 * in the system.
 *
 * ── The eight coloured ones (2026-09-12) ─────────────────────────────
 * Asked for as "lighter and colourful — red, orange etc". They are
 * colourful and they are not lighter, and that is the whole trick: a
 * tone is the base under EVERY screen, and raising its luminance is what
 * breaks the ink scale. Chroma is free, brightness is not. Crimson at
 * #1B0B0E reads unmistakably red against Obsidian while measuring 4.91
 * — brighter than Slate, which was the previous ceiling.
 *
 * Slate remains the lightest of the set at 4.54. Anything lighter than
 * that needs the ink scale retuned with it, which would repaint every
 * screen in the app rather than the background.
 *
 * The doc's "Ink" (#0A0C10) is deliberately absent — it is within a hair
 * of the shipped canvas, and a picker holding two entries nobody can
 * tell apart is a defect, not a choice. Obsidian is that value, first
 * and default so no existing install is repainted.
 */
export const TONES = [
  { id: 'obsidian',  name: 'Obsidian',   blurb: 'The NovaHost default',
    canvas: '#0E0E11', canvas2: '#131317', card: '#16161A', card2: '#1C1C21', recess: '#101013',
    rgb: '14, 14, 17', cardRgb: '22, 22, 26' },                              // 4.96
  { id: 'navy',      name: 'Deep navy',  blurb: 'Cool, blue-black',
    canvas: '#0C1424', canvas2: '#11192A', card: '#141C2D', card2: '#1A2234', recess: '#0E1626',
    rgb: '12, 20, 36', cardRgb: '20, 28, 45' },                              // 4.65
  { id: 'slate',     name: 'Slate',      blurb: 'Neutral, lifted',
    canvas: '#14161A', canvas2: '#191B20', card: '#1C1E23', card2: '#22242A', recess: '#16181C',
    rgb: '20, 22, 26', cardRgb: '28, 30, 35' },                              // 4.54
  { id: 'aubergine', name: 'Aubergine',  blurb: 'Warm, violet-black',
    canvas: '#150C1C', canvas2: '#1A1122', card: '#1D1425', card2: '#231A2C', recess: '#170E1E',
    rgb: '21, 12, 28', cardRgb: '29, 20, 37' },                              // 4.89
  { id: 'teal',      name: 'Teal black', blurb: 'Deep, green-black',
    canvas: '#08161A', canvas2: '#0D1B20', card: '#101E23', card2: '#16242A', recess: '#0A181C',
    rgb: '8, 22, 26',  cardRgb: '16, 30, 35' },                              // 4.65
  { id: 'crimson',   name: 'Crimson',    blurb: 'Deep, blood red',
    canvas: '#1B0B0E', canvas2: '#201014', card: '#231317', card2: '#29191E', recess: '#1D0D10',
    rgb: '27, 11, 14', cardRgb: '35, 19, 23' },                              // 4.91
  { id: 'ember',     name: 'Ember',      blurb: 'Warm, burnt orange',
    canvas: '#1B1008', canvas2: '#20150E', card: '#231811', card2: '#291E18', recess: '#1D120A',
    rgb: '27, 16, 8',  cardRgb: '35, 24, 17' },                              // 4.75
  { id: 'rust',      name: 'Rust',       blurb: 'Dry, oxidised red',
    canvas: '#19100A', canvas2: '#1E1510', card: '#211813', card2: '#271E1A', recess: '#1B120C',
    rgb: '25, 16, 10', cardRgb: '33, 24, 19' },                              // 4.77
  { id: 'forest',    name: 'Forest',     blurb: 'Dark, pine green',
    canvas: '#0A1810', canvas2: '#0F1D16', card: '#122019', card2: '#182620', recess: '#0C1A12',
    rgb: '10, 24, 16', cardRgb: '18, 32, 25' },                              // 4.60
  { id: 'ocean',     name: 'Ocean',      blurb: 'Cold, deep blue',
    canvas: '#081422', canvas2: '#0D1928', card: '#101C2B', card2: '#162232', recess: '#0A1624',
    rgb: '8, 20, 34',  cardRgb: '16, 28, 43' },                              // 4.69
  { id: 'plum',      name: 'Plum',       blurb: 'Rich, wine violet',
    canvas: '#190A16', canvas2: '#1E0F1C', card: '#21121F', card2: '#271826', recess: '#1B0C18',
    rgb: '25, 10, 22', cardRgb: '33, 18, 31' },                              // 4.94
  { id: 'bronze',    name: 'Bronze',     blurb: 'Antique, gold-brown',
    canvas: '#171208', canvas2: '#1C170E', card: '#1F1A11', card2: '#252018', recess: '#19140A',
    rgb: '23, 18, 8',  cardRgb: '31, 26, 17' },                              // 4.73
  { id: 'steel',     name: 'Steel',      blurb: 'Blue-grey, technical',
    canvas: '#101418', canvas2: '#15191E', card: '#181C21', card2: '#1E2228', recess: '#12161A',
    rgb: '16, 20, 24', cardRgb: '24, 28, 33' },                              // 4.67
]

/**
 * Typefaces and colours for the robot's display name.
 *
 * Ids only — the VALUES live in command.css, keyed off `data-name-font`
 * and `data-name-color` on the `.cmd` root, the same contract
 * CARD_STYLES and GLOW_MODES already have with `data-card`. They cannot
 * be written from here: their values reference --cc-font, --cc-ink and
 * --cc-ink-3, which are declared on `.cmd`, and a runtime token written
 * to :root cannot see them. Keeping the ids here and the values there
 * also means a preview cannot disagree with the real thing — it sets
 * the same attribute on a wrapper and puts .cc-name inside it.
 *
 * System is first and default. Three of the doc's five faces are
 * already in the app's font link and cost nothing to offer; the two
 * serifs are the only addition to the bundle.
 */
export const NAME_FONTS = [
  { id: 'system',   name: 'System',    face: 'SF Pro' },
  { id: 'oldmoney', name: 'Old money', face: 'Cormorant Garamond' },
  { id: 'bodoni',   name: 'Bodoni',    face: 'Playfair Display' },
  { id: 'grotesk',  name: 'Grotesk',   face: 'Space Grotesk' },
  { id: 'neue',     name: 'Neue',      face: 'Inter' },
  { id: 'terminal', name: 'Terminal',  face: 'JetBrains Mono' },
]

/** Named by role rather than by literal colour: the doc's White / Bone /
 *  Ink only work because the Android screen is dark-only. */
export const NAME_COLORS = [
  { id: 'default', name: 'Default', blurb: 'Follows the scheme' },
  { id: 'accent',  name: 'Accent',  blurb: 'Your accent colour' },
  { id: 'warm',    name: 'Warm',    blurb: 'Bone on dark'       },
  { id: 'muted',   name: 'Muted',   blurb: 'Recedes behind art' },
]

/** Multiplier bounds for the name — not the doc's absolute 24-72sp; see
 *  the --cc-name-scale note in command.css. 0.8 keeps Signal Feed's 22px
 *  name from dropping under 18px, 1.4 keeps Focus Engine's 46px clamp
 *  inside a 390px viewport. */
export const NAME_SCALE = { min: 0.8, max: 1.4, step: 0.1, default: 1 }

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
  /* `systemHealth` is hidden in all five of the doc's layouts on
     purpose: it arrived with Command Deck, and switching it on by
     default would silently change five screens that were designed and
     signed off without it. It is one tap away in Arrange Widgets.

     `openTrades` deliberately does NOT follow that precedent, and is
     absent from every list below so it shows everywhere including
     `bleed`. The rule systemHealth follows is "a widget added later
     should not redesign a signed-off screen", and it holds for a
     readout nobody asked for. Open positions are not that: until this
     build the product could not tell a user whether their money was in
     a trade AT ALL — `trade_logs` has no writer and no rows, and the
     executor forgets an order the moment the broker accepts it.
     Defaulting the only answer to that question to hidden would ship
     the feature switched off, and `bleed` is the default layout, so
     most users would never see it. One extra card on the art screen is
     the smaller cost. */
  classic: ['systemHealth', 'terminalLog'],
  focus:   ['systemHealth', 'robotList', 'fleetStatus', 'terminalLog'],
  bleed:   ['systemHealth', 'robotList', 'fleetStatus', 'chartScanner', 'terminalLog'],
  core:    ['systemHealth', 'robotList', 'fleetStatus', 'chartScanner', 'terminalLog'],
}

/**
 * Falling FX — the calm particle layer over the robot's video (Home v4).
 * `off` is a real choice, not an error state: the home then shows video,
 * controls and the status dot only.
 */
export const FX_STYLES = [
  { id: 'currency', name: 'Currency',      blurb: 'R, $, €, £ and ¥' },
  { id: 'coins',    name: 'Coins & notes', blurb: 'Gold coins and notes' },
  { id: 'gems',     name: 'Gold & gems',   blurb: 'Ingots and cut gems' },
  { id: 'pairs',    name: 'Pair names',    blurb: 'Symbols falling like code' },
  { id: 'mix',      name: 'Mix · all four', blurb: 'Every style, same calm density' },
  { id: 'off',      name: 'Off',           blurb: 'Video and controls only' },
]

export const FX_INTENSITIES = [
  { id: 'subtle', name: 'Subtle' },
  { id: 'normal', name: 'Normal' },
  { id: 'rich',   name: 'Rich'   },
]

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
  gradient: 'CC_GRADIENT',
  tone:     'CC_TONE',
  nameFont: 'CC_NAME_FONT',
  nameSize: 'CC_NAME_SCALE',
  nameInk:  'CC_NAME_COLOR',
  fx:       'CC_FX',
  fxLevel:  'CC_FX_INTENSITY',
}

function readEnum(key, options, fallback) {
  const saved = secStorage.getItem(key)
  return options.some(o => o.id === saved) ? saved : fallback
}

/** NaN — an absent or corrupt stored value — falls to the default
 *  rather than to `min`, so a first run gets the shipped size. */
function clampScale(n) {
  if (!Number.isFinite(n) || n <= 0) return NAME_SCALE.default
  return Math.min(NAME_SCALE.max, Math.max(NAME_SCALE.min, n))
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
    const stored = Array.isArray(s?.order) ? s.order.filter(id => REORDERABLE.includes(id)) : []
    const order  = [...stored]
    const hidden = Array.isArray(s?.hidden)
      ? s.hidden.filter(id => REORDERABLE.includes(id))
      : [...DEFAULT_HIDDEN[l.id]]

    /* A widget added to the registry AFTER this arrangement was saved
       appears in neither stored list. Appending it to `order` alone
       switched it ON for every user who had ever opened Arrange
       Widgets, while a fresh install correctly got the layout default —
       the same build showing two different Homes depending on whether
       the user had touched the screen. It takes the layout's default
       instead, and is only consulted for a genuinely stored
       arrangement (`hidden` already came from the defaults otherwise). */
    for (const id of REORDERABLE) {
      if (order.includes(id)) continue
      order.push(id)
      if (stored.length && DEFAULT_HIDDEN[l.id].includes(id) && !hidden.includes(id)) {
        hidden.push(id)
      }
    }

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

  gradient:   readEnum(KEY.gradient, GRADIENTS,    'off'),
  tone:       readEnum(KEY.tone,     TONES,        'obsidian'),
  nameFont:   readEnum(KEY.nameFont, NAME_FONTS,   'system'),
  nameColor:  readEnum(KEY.nameInk,  NAME_COLORS,  'default'),

  fx:         readEnum(KEY.fx,      FX_STYLES,      'mix'),
  fxIntensity: readEnum(KEY.fxLevel, FX_INTENSITIES, 'normal'),

  /* Clamped on read, not just on write: a stored value from a build
     with different bounds must not escape them, and Number('') is 0. */
  nameScale:  clampScale(Number(secStorage.getItem(KEY.nameSize))),

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

export const activeGradient = computed(() =>
  GRADIENTS.find(g => g.id === theme.gradient) || GRADIENTS[0]
)

export const activeTone = computed(() =>
  TONES.find(t => t.id === theme.tone) || TONES[0]
)

export const activeNameFont = computed(() =>
  NAME_FONTS.find(f => f.id === theme.nameFont) || NAME_FONTS[0]
)

export const activeNameColor = computed(() =>
  NAME_COLORS.find(c => c.id === theme.nameColor) || NAME_COLORS[0]
)

/** The tone only reaches the screen on dark — light restates its own
 *  ladder — so anything reporting the setting must say so rather than
 *  show a value that is not being used. */
export const toneApplies = computed(() => resolvedScheme.value === 'dark')

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
  /* Accent-as-text needs the darker sibling on a light canvas; the raw
     hex only clears 4.5:1 against #0E0E11.

     `textDark` is the same idea pointing the other way, and it exists for
     the spectrum picker. Every one of the eight presets is legible on the
     dark canvas as-is, so they carry no such field and fall back to the hex
     — but a spectrum reaches colours that are not, and a deep pick would
     otherwise turn every accent label on Home invisible. */
  root.setProperty('--cc-accent-text', light ? sw.text : (sw.textDark || sw.hex))
  /* Two names for one setting. --cc-glow-user is the raw multiplier;
     `.cmd` resolves it into --cc-glow so the light scheme can damp it
     without clobbering the user's choice with a literal. --cc-glow stays
     written here as well, for anything outside a `.cmd` subtree. */
  root.setProperty('--cc-glow-user', String(glowValue.value))
  root.setProperty('--cc-glow',      String(glowValue.value))

  /* Accent fill. Off restores the flat accent and its swatch ink — it
     cannot just be left unset, because these are :root properties and a
     stale value would survive the change. A gradient brings its OWN
     measured ink: the swatch's is paired with the swatch, and Rust under
     black ink is 1.54:1. */
  const grad = activeGradient.value
  root.setProperty('--cc-accent-fill',     grad.css || sw.hex)
  root.setProperty('--cc-accent-fill-ink', grad.ink || sw.ink)

  /* Background tone. Written on every scheme, not only dark: the light
     block restates the whole ladder literally and ignores these, so
     leaving them stale would repaint the wrong tone the moment the user
     switched back. */
  const tone = activeTone.value
  root.setProperty('--cc-tone',          tone.canvas)
  root.setProperty('--cc-tone-2',        tone.canvas2)
  root.setProperty('--cc-tone-card',     tone.card)
  root.setProperty('--cc-tone-card-2',   tone.card2)
  root.setProperty('--cc-tone-recess',   tone.recess)
  root.setProperty('--cc-tone-rgb',      tone.rgb)
  root.setProperty('--cc-tone-card-rgb', tone.cardRgb)

  /* Only the scale. The name's face and colour are data attributes on
     the `.cmd` root — see NAME_FONTS for why they cannot be written
     here. This one is a bare number, so it is safe. */
  root.setProperty('--cc-name-scale', String(theme.nameScale))

  /* `theme-color` is NOT written here any more.
   *
   * It was set to the canvas tone from this function and to the ACCENT hex
   * from accent.js, so the tag's value depended on which store had last
   * changed. On an installed handset that tag paints the chrome around the
   * web view, which is how a magenta robot ended up with a coloured band
   * under the app.
   *
   * App.vue is the single writer now, because the right value is the ground
   * the CURRENT ROUTE paints — and this function cannot see the route. */
}

watch(
  () => [
    theme.accent, theme.glowMode, resolvedScheme.value,
    theme.gradient, theme.tone, theme.nameScale,
  ],
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
watch(() => theme.gradient,  v => secStorage.setItem(KEY.gradient, v))
watch(() => theme.tone,      v => secStorage.setItem(KEY.tone,     v))
watch(() => theme.nameFont,  v => secStorage.setItem(KEY.nameFont, v))
watch(() => theme.nameColor, v => secStorage.setItem(KEY.nameInk,  v))
watch(() => theme.nameScale, v => secStorage.setItem(KEY.nameSize, String(v)))
watch(() => theme.fx,          v => secStorage.setItem(KEY.fx,      v))
watch(() => theme.fxIntensity, v => secStorage.setItem(KEY.fxLevel, v))
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

/**
 * A colour off the spectrum rails rather than out of the preset table.
 *
 * Persisted under the SAME key as a preset, as its hex. accent.js writes
 * `swatch.name` to ACCENT_COLOR on every change, so the derived swatch is
 * named with its own hex and `swatchByName` recognises a leading '#' and
 * re-derives it on the next launch. One accent, one storage slot — a second
 * key would mean deciding which of two saved values wins on every read, and
 * that decision has no right answer.
 */
export function setCustomAccent(hex) {
  const sw = deriveSwatch(hex)
  if (!sw) return false
  setAccent(sw)
  return true
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

/**
 * Erases every preference this store owns.
 *
 * Lives here rather than in the screen that offers it, because KEY is
 * here: a list copied into a view goes stale the first time a setting is
 * added, and the failure is silent — the erase quietly leaves something
 * behind. `secStorage.clear()` is deliberately not used; it drops the
 * install salt and DEVICE_ID with everything else, and a fresh device id
 * reads as a new device on the licence.
 *
 * The in-memory state is NOT reset. Every value here is watched and
 * re-persisted on change, so a caller that keeps running would write
 * some of these straight back; the one caller reloads the app instead.
 */
export function clearPreferences() {
  for (const k of Object.values(KEY)) secStorage.removeItem(k)
}

/** The only way the name scale should be written. A range input reports
 *  its value as a STRING, and `theme.nameScale = '1.2'` would persist a
 *  string that Number() later re-parses fine but `.toFixed()` throws on. */
export function setNameScale(v) {
  theme.nameScale = clampScale(Number(v))
}

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
