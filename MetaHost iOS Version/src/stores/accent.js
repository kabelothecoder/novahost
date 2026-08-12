import { reactive, watch } from 'vue'
import { secStorage } from '../utils/secStorage.js'

// 8 accent colors — Crimson is first (default)
export const SWATCHES = [
  { name: 'Crimson', hex: '#FF003C', rgb: '255, 0, 60'    },
  { name: 'Cyan',    hex: '#00E5FF', rgb: '0, 229, 255'   },
  { name: 'Violet',  hex: '#7C3AED', rgb: '124, 58, 237'  },
  { name: 'Emerald', hex: '#00FF87', rgb: '0, 255, 135'   },
  { name: 'Gold',    hex: '#FFB800', rgb: '255, 184, 0'   },
  { name: 'Ice',     hex: '#A5F3FC', rgb: '165, 243, 252' },
  { name: 'Plasma',  hex: '#E879F9', rgb: '232, 121, 249' },
  { name: 'Amber',   hex: '#FB923C', rgb: '251, 146, 60'  },
]

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
const activeAccentName = savedRobotAccent || 'Amber'
const initialAccent    = SWATCHES.find(s => s.name === activeAccentName) || SWATCHES[0]

// Robot identity from license
const savedRobotName   = secStorage.getItem('ROBOT_NAME')  || 'MetaBot'
const savedAvatarUrl   = secStorage.getItem('AVATAR_URL')  || ''

// Allowed trading symbols from license
const initialAllowedSymbols = secStorage.getItem('ALLOWED_SYMBOLS', true) || []

export const accent = reactive({
  current:     initialAccent,
  videoBg:     VIDEO_BG_OPTIONS[0],
  isListening: false,
  isSpeaking:  false,
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
