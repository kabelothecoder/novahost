import { reactive, watch } from 'vue'
import { secStorage } from '../utils/secStorage.js'
import { SWATCHES, DEFAULT_ACCENT, swatchByName } from './palette.js'

// The palette moved to ./palette.js so `theme.js` (command.css) and this
// store (glass.css) cannot drift apart. Re-exported because nine views
// and SettingsView import SWATCHES from here — that contract is kept.
export { SWATCHES }

export const VIDEO_BG_OPTIONS = [
  { id: 'mesh',      name: 'Nebula Mesh',   type: 'canvas' },
  { id: 'grid',      name: 'Cyber Grid',    type: 'canvas' },
  { id: 'particles', name: 'Particles',     type: 'canvas' },
  { id: 'waves',     name: 'Oceanic Waves', type: 'canvas' },
  { id: 'plasma',    name: 'Plasma Field',  type: 'canvas' },
  { id: 'matrix',    name: 'Data Matrix',   type: 'canvas' },
  { id: 'vortex',    name: 'Vortex',        type: 'canvas' },
  { id: 'aurora',    name: 'Aurora',        type: 'canvas' },
]

// ── Identity bootstrap from secStorage ─────────────────────────────────────
// Priority: Robot license accent → user manual pick (which is also stored via secStorage now) → default Amber
const savedRobotAccent = secStorage.getItem('ACCENT_COLOR')
const initialAccent    = swatchByName(savedRobotAccent || DEFAULT_ACCENT)

// Robot identity from license
const savedRobotName   = secStorage.getItem('ROBOT_NAME')  || 'MetaBot'
const savedAvatarUrl   = secStorage.getItem('AVATAR_URL')  || ''

// Allowed trading symbols from license
const initialAllowedSymbols = secStorage.getItem('ALLOWED_SYMBOLS', true) || []

export const accent = reactive({
  current:     initialAccent,
  videoBg:     VIDEO_BG_OPTIONS[0],
  micVolume:   0,
  // ── Robot identity (synced from license) ──────────────────
  robotName:   savedRobotName,
  avatarUrl:   savedAvatarUrl,
  isHighProb:  false,
  allowedSymbols: initialAllowedSymbols,
  // ── Broker Connection (Cloud Bridge) ─────────────
  brokerConnected: false,
  brokerBalance:   0,
})

// ── Sync CSS custom properties + persist whenever accent changes ──────────────
watch(
  () => accent.current,
  (swatch) => {
    const root = document.documentElement.style
    root.setProperty('--accent',      swatch.hex)
    root.setProperty('--accent-rgb',  swatch.rgb)
    root.setProperty('--accent-dim',  `rgba(${swatch.rgb}, 0.12)`)
    root.setProperty('--accent-glow', `rgba(${swatch.rgb}, 0.45)`)
    root.setProperty('--accent-neon',
      `0 0 20px rgba(${swatch.rgb}, 0.7), 0 0 40px rgba(${swatch.rgb}, 0.3), 0 0 80px rgba(${swatch.rgb}, 0.1)`
    )
    // Persist choice
    secStorage.setItem('ACCENT_COLOR', swatch.name)

    // Sync Meta Theme Color for iOS Status Bar & PWA
    const metaTheme = document.querySelector('meta[name="theme-color"]')
    if (metaTheme) metaTheme.setAttribute('content', swatch.hex)
  },
  { immediate: true }
)

// ── Sync robot identity to secStorage whenever it changes ──────────────────
watch(
  () => accent.robotName,
  (name) => { secStorage.setItem('ROBOT_NAME', name) }
)

watch(
  () => accent.avatarUrl,
  (url) => { secStorage.setItem('AVATAR_URL', url) }
)

watch(
  () => accent.allowedSymbols,
  (symbols) => { secStorage.setItem('ALLOWED_SYMBOLS', symbols) },
  { deep: true }
)
