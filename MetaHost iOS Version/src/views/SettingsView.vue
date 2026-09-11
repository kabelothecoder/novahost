<template>
  <!--
    Settings, rebuilt to the Claude Design doc "NovaHost Settings v2".

    The doc's spec, in its own words: one card per group with hairline
    dividers instead of a bordered card inside another bordered card;
    every row carrying its current VALUE so the hub reads at a glance;
    sentence case and plain language; and the destructive action moved
    off the scroll onto its own screen.

    What is deliberately NOT ported: the doc turns each choice into a
    routed sub-screen with a sticky live preview. On iOS those choices
    already live in the Interface Style sheet, which Home opens too, and
    two editors for one setting drift. The hub is the readable index; a
    tap opens the sheet on the tab that owns that row.
  -->
  <div
    class="cmd settings"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
    :data-name-font="theme.nameFont"
    :data-name-color="theme.nameColor"
  >
    <header class="st-head">
      <h1 class="cc-title">Settings</h1>
      <p class="st-sub">Every look and layout for NovaHost, in one place.</p>
    </header>

    <div class="st-body">
      <!-- ── APPEARANCE ─────────────────────────────────────────────── -->
      <span class="cc-label st-section">Appearance</span>
      <div class="st-panel cc-card">
        <button class="st-row" @click="openSheet('colours')">
          <span class="st-copy">
            <span class="st-name">Accent colour</span>
            <span class="cc-caption">Buttons and active states</span>
          </span>
          <span class="st-value">{{ theme.accent.name }}</span>
          <span class="st-dot" :style="{ background: theme.accent.hex }"></span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('colours')">
          <span class="st-copy">
            <span class="st-name">Gradient accent</span>
            <span class="cc-caption">{{ activeGradient.blurb }}</span>
          </span>
          <span class="st-value">{{ activeGradient.name }}</span>
          <span class="st-dot" :style="{ background: activeGradient.css || theme.accent.hex }"></span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('colours')">
          <span class="st-copy">
            <span class="st-name">Background</span>
            <!-- Says what it is doing rather than showing a tone that is
                 not on screen: light restates its own ladder and ignores
                 the setting entirely. -->
            <span class="cc-caption">{{ toneApplies ? 'Base tone behind every screen' : 'Dark scheme only — not in use' }}</span>
          </span>
          <span class="st-value">{{ activeTone.name }}</span>
          <!-- Two steps of the tone, not just its canvas. A near-black
               dot on a near-black card reads as an empty ring; the split
               is what tells Obsidian from Deep navy at a glance. -->
          <span class="st-dot" :style="{ background: `linear-gradient(135deg, ${activeTone.canvas} 50%, ${activeTone.card2} 50%)` }"></span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('colours')">
          <span class="st-copy">
            <span class="st-name">Glow</span>
            <span class="cc-caption">Halo around lit elements</span>
          </span>
          <span class="st-value">{{ activeGlow.name }}</span>
          <span class="st-dot st-dot-glow" :style="{ background: theme.accent.hex }"></span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('colours')">
          <span class="st-copy">
            <span class="st-name">Card style</span>
            <span class="cc-caption">{{ activeCard.blurb }}</span>
          </span>
          <span class="st-value">{{ activeCard.name }}</span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── HOME SCREEN ────────────────────────────────────────────── -->
      <span class="cc-label st-section">Home screen</span>
      <div class="st-panel cc-card">
        <button class="st-row" @click="openSheet('layouts')">
          <span class="st-copy">
            <span class="st-name">Layout</span>
            <span class="cc-caption">Six arrangements for Home</span>
          </span>
          <span class="st-value">{{ activeLayout.name }}</span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('layouts')">
          <span class="st-copy">
            <span class="st-name">Robot art</span>
            <span class="cc-caption">Saved per {{ activeLayout.name }}</span>
          </span>
          <span class="st-value">{{ activeArt.name }}</span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="go('/arrange-widgets')">
          <span class="st-copy">
            <span class="st-name">Widgets</span>
            <span class="cc-caption">Reorder or hide cards</span>
          </span>
          <span class="st-value">{{ arrangeSummary }}</span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row" @click="openSheet('type')">
          <span class="st-copy">
            <span class="st-name">Bot name &amp; type</span>
            <span class="cc-caption">Typeface, size and colour</span>
          </span>
          <span class="st-value">{{ activeNameFont.name }}</span>
          <span class="st-dot st-dot-name"></span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── DISPLAY ────────────────────────────────────────────────── -->
      <span class="cc-label st-section">Display</span>
      <div class="st-panel cc-card">
        <button class="st-row" @click="openSheet('modes')">
          <span class="st-copy">
            <span class="st-name">Colour scheme</span>
            <span class="cc-caption">Dark, light, or follow the device</span>
          </span>
          <span class="st-value">{{ schemeSummary }}</span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <div class="st-row is-static">
          <span class="st-copy">
            <span class="st-name">Reduce motion</span>
            <span class="cc-caption">Calms backdrop and entrances</span>
          </span>
          <button
            class="st-switch"
            :class="{ 'is-on': theme.motion === 'reduced' }"
            role="switch"
            :aria-checked="theme.motion === 'reduced'"
            aria-label="Reduce motion"
            @click="theme.motion = theme.motion === 'reduced' ? 'full' : 'reduced'"
          ><span class="st-knob"></span></button>
        </div>

        <div class="st-row is-static">
          <span class="st-copy">
            <span class="st-name">Haptic feedback</span>
            <span class="cc-caption">Vibrate on actions</span>
          </span>
          <button
            class="st-switch"
            :class="{ 'is-on': theme.haptics }"
            role="switch"
            :aria-checked="theme.haptics"
            aria-label="Haptic feedback"
            @click="theme.haptics = !theme.haptics"
          ><span class="st-knob"></span></button>
        </div>

        <!-- Reports the browser's real permission state instead of a
             switch that pretends to own it: once notifications are
             denied, only the OS can grant them back and a toggle here
             would be a lie. -->
        <div class="st-row is-static">
          <span class="st-copy">
            <span class="st-name">Notifications</span>
            <span class="cc-caption">{{ notifCaption }}</span>
          </span>
          <button
            v-if="notifPermission === 'default'"
            class="st-mini"
            @click="askNotifications"
          >Enable</button>
          <span v-else class="cc-chip" :class="{ 'cc-chip-accent': notifPermission === 'granted' }">
            {{ notifPermission === 'granted' ? 'On' : 'Blocked' }}
          </span>
        </div>
      </div>

      <!-- ── TOOLS ──────────────────────────────────────────────────── -->
      <span class="cc-label st-section">Tools</span>
      <div class="st-panel cc-card">
        <button v-for="t in tools" :key="t.to" class="st-row" @click="go(t.to)">
          <span class="st-icon" v-html="t.icon"></span>
          <span class="st-copy">
            <span class="st-name">{{ t.name }}</span>
            <span class="cc-caption">{{ t.desc }}</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── CONNECTIONS ────────────────────────────────────────────────
           The "API credentials" disclosure is gone entirely.

           It held three fields. Two were MetaAPI — a bridge this product
           stopped using when it moved to MetaCopier, which is reached
           server-side with a key the device never sees — so the screen was
           inviting users to paste a live third-party credential into a
           field nothing would ever read. The third was an ElevenLabs key,
           which ships as `VITE_ELEVENLABS_API_KEY` and does not need a
           per-user entry; voice falls back to system speech without it.

           That left a "Connections" section whose only content was a
           credential form for credentials the app does not use. The real
           connection a user has to make is their broker, and that has its
           own screen. -->
      <span class="cc-label st-section">Trading</span>
      <div class="st-panel cc-card">

        <!-- ── Auto-execute ───────────────────────────────────────────
             The only control in this app that spends money on its own, so
             it says what it will do in the caption rather than relying on
             the name, and it reports the server's answer instead of
             assuming the flip worked. The server refuses to enable a
             licence with no broker attached, and warns (without refusing)
             when no symbol has a lot size — both arrive as `note`. -->
        <div class="st-row is-static">
          <span class="st-copy">
            <span class="st-name">Auto-execute signals</span>
            <span class="cc-caption">{{ autoExecCaption }}</span>
          </span>
          <button
            class="st-switch"
            :class="{ 'is-on': autoExec, 'is-busy': autoExecBusy }"
            role="switch"
            :aria-checked="autoExec"
            :disabled="autoExecBusy || autoExec === null"
            aria-label="Auto-execute signals"
            @click="toggleAutoExec"
          ><span class="st-knob"></span></button>
        </div>
        <p v-if="autoExecNote" class="st-note cc-caption" :class="{ 'is-bad': autoExecBad }">
          {{ autoExecNote }}
        </p>

        <button class="st-row" @click="go('/trades')">
          <span class="st-copy">
            <span class="st-name">Trades</span>
            <span class="cc-caption">Open positions, chart and performance</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
        <button class="st-row" @click="go('/terminal')">
          <span class="st-copy">
            <span class="st-name">MetaTrader terminal</span>
            <span class="cc-caption">Broker link and service events</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── ABOUT ──────────────────────────────────────────────────── -->
      <span class="cc-label st-section">About</span>
      <div class="st-panel cc-card">
        <button class="st-row" @click="go('/support')">
          <span class="st-copy">
            <span class="st-name">Help &amp; support</span>
            <span class="cc-caption">Device moves, licence help</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <!-- The doc's danger screen. It gets a route rather than a red
             button mid-scroll, so the list of what is erased can be read
             before anything is armed. -->
        <button class="st-row st-danger" @click="go('/sign-out')">
          <span class="st-copy">
            <span class="st-name">Sign out of this device</span>
            <span class="cc-caption">Clears this device's licence</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <p class="st-version cc-mono">NOVAHOST {{ version }} · CHANGES SAVE INSTANTLY</p>
    </div>

    <InterfaceStyleSheet v-if="sheetOpen" :tab="sheetTab" @close="sheetOpen = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { loadLinkState, setAutoExecute } from '../services/brokerService.js'
import {
  theme, resolvedScheme, activeLayout, activeArtMode, activeGradient, activeTone,
  activeNameFont, toneApplies, REORDERABLE, GLOW_MODES, CARD_STYLES, SCHEMES, ART_MODES,
} from '../stores/theme.js'
import { haptic } from '../composables/useSpeech.js'
import InterfaceStyleSheet from '../components/settings/InterfaceStyleSheet.vue'

const router = useRouter()
const sheetOpen = ref(false)
const sheetTab = ref('layouts')
/* A literal, not a build define: this project has no version metadata
   wired into the build (package.json is still 0.0.0), and a define
   pointing at that would print "NOVAHOST 0.0.0". */
const version = 'V6'

const CHEV = `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>`

/* ── Auto-execute ─────────────────────────────────────────────────────
   The opt-in for server-side execution. Until this switch existed nothing
   anywhere wrote `licenses.auto_execute`, so `auto-execute-signals` — cron,
   trigger and all — was a no-op for every licence. That is what an iOS user
   was missing: this app does not claim signals itself, so with the switch
   off it receives no trades at all.

   `null` is a real third state and not a default: it means the licence has
   not answered yet, or could not be reached. Rendering "off" for unknown
   would tell someone their robot is idle when it may be trading. */
const autoExec = ref(null)
const autoExecBusy = ref(false)
const autoExecNote = ref('')
const autoExecBad = ref(false)
const brokerLinked = ref(null)

const autoExecCaption = computed(() => {
  if (autoExec.value === null) return 'Checking your licence…'
  return autoExec.value
    ? 'The server places your mentor’s trades even when this app is closed'
    : 'Trades only arrive while this app is open and awake'
})

async function refreshAutoExec() {
  const state = await loadLinkState()
  brokerLinked.value = state.linked
  /* `linked: null` is "we could not ask", not "disconnected" — leave the
     switch unknown rather than drawing a confident off. */
  autoExec.value = state.linked === null ? null : Boolean(state.autoExecute)
  if (state.linked === null) {
    autoExecNote.value = 'Could not reach your licence just now.'
    autoExecBad.value = true
  }
}

async function toggleAutoExec() {
  if (autoExecBusy.value || autoExec.value === null) return

  const next = !autoExec.value
  autoExecBusy.value = true
  autoExecNote.value = ''
  autoExecBad.value = false
  haptic()

  const res = await setAutoExecute(next)

  if (res.ok) {
    /* The server's answer, not `next` — they agree today, and a switch that
       trades money should not be the one place that assumes they always will. */
    autoExec.value = res.autoExecute
    if (res.warning) {
      autoExecNote.value = res.warning
      autoExecBad.value = false
    }
  } else {
    autoExecNote.value = res.message || 'Could not change that setting.'
    autoExecBad.value = true
  }

  autoExecBusy.value = false
}

onMounted(refreshAutoExec)

const tools = [
  {
    to: '/scanner', name: 'Chart scanner', desc: 'AI chart analysis',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8V5a2 2 0 0 1 2-2h3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M21 16v3a2 2 0 0 1-2 2h-3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/><polyline points="7 14 10 11 13 14 17 9"/></svg>`,
  },
  {
    to: '/pairs', name: 'Trading quotes', desc: 'Live pairs and symbol config',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
  },
  {
    to: '/terminal', name: 'Terminal', desc: 'Broker link and service events',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`,
  },
]

const activeGlow = computed(() => GLOW_MODES.find(g => g.id === theme.glowMode) || GLOW_MODES[2])
const activeCard = computed(() => CARD_STYLES.find(c => c.id === theme.cardStyle) || CARD_STYLES[0])
const activeArt  = computed(() => ART_MODES.find(a => a.id === activeArtMode.value) || ART_MODES[0])

const schemeSummary = computed(() => {
  if (theme.scheme === 'system') return `System · ${resolvedScheme.value === 'dark' ? 'Dark' : 'Light'}`
  return SCHEMES.find(s => s.id === theme.scheme)?.name || 'Dark'
})

const arrangeSummary = computed(() => {
  const cfg = theme.widgets[theme.layout]
  if (!cfg) return ''
  return `${REORDERABLE.length - cfg.hidden.length} of ${REORDERABLE.length}`
})

/* ── Notifications ────────────────────────────────────────────────── */
const notifSupported = typeof Notification !== 'undefined'
const notifPermission = ref(notifSupported ? Notification.permission : 'unsupported')

const notifCaption = computed(() => {
  switch (notifPermission.value) {
    case 'granted':     return 'Signal and trade alerts are on'
    case 'denied':      return 'Blocked — turn on in your device settings'
    case 'unsupported': return 'Not supported on this device'
    default:            return 'Alerts when a signal arrives'
  }
})

async function askNotifications() {
  if (!notifSupported) return
  try {
    notifPermission.value = await Notification.requestPermission()
  } catch (err) {
    console.warn('Notification permission:', err)
  }
}

/* ── Legacy credential purge ──────────────────────────────────────────
   The API credentials disclosure is gone, but any key a user typed into it
   is still sitting in localStorage. Dropping the inputs without dropping
   the values would leave live third-party credentials on the device with no
   UI left to clear them. Runs on every Settings open, which is cheap and
   means an install that never revisits the screen still gets cleaned the
   first time it does. */
for (const k of ['METAAPI_TOKEN', 'METAAPI_ACCOUNT_ID']) localStorage.removeItem(k)

function openSheet(tab) {
  sheetTab.value = tab
  sheetOpen.value = true
  haptic(20)
}

function go(to) { haptic(20); router.push(to) }
</script>

<style scoped>
.settings {
  width: 100%;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

.st-head {
  padding: calc(var(--cc-safe-top) + 18px) 20px 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.st-sub {
  margin: 0;
  font-size: 13.5px;
  line-height: 19px;
  color: var(--cc-ink-4);
}

.st-body {
  padding: 14px 16px calc(var(--cc-safe-bottom) + 120px);
  display: flex;
  flex-direction: column;
}

.st-section { margin: 20px 6px 9px }
.st-section:first-of-type { margin-top: 8px }

/* The doc's headline change: ONE panel per group, with hairline
   dividers between rows — not a bordered card inside another bordered
   card. `overflow: hidden` is what lets a row's press state reach the
   panel's rounded corners without escaping them. */
.st-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--cc-r-card-lg);
}

.st-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: none;
  background: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 160ms var(--cc-ease);
}
/* A divider between rows, never after the last one — a trailing rule
   sitting on the panel's bottom edge reads as a seam in the card. */
.st-row + .st-row,
.st-row + .st-disclose {
  border-top: 1px solid var(--cc-border-soft);
}
.st-row:active { background: var(--cc-accent-dim) }
.st-row.is-static { cursor: default }
.st-row.is-static:active { background: none }

.st-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 34px; height: 34px;
  border-radius: var(--cc-r-tile);
  color: var(--cc-accent-text);
  background: var(--cc-accent-dim);
}

.st-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.st-name {
  font-size: 15px; font-weight: 500; color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* The current value, on the right of the row. This is what makes the
   hub readable in one glance instead of a list of destinations. */
.st-value {
  flex: none;
  max-width: 42%;
  font-size: 13px;
  color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* Chevrons and dividers are the only things allowed on ink-5: it is
   3.7:1 and fails AA for anything text-shaped. */
.st-chev {
  flex: none;
  color: var(--cc-ink-5);
  display: grid; place-items: center;
  transition: transform 220ms var(--cc-ease);
}
.st-chev.is-open { transform: rotate(90deg) }

.st-dot {
  flex: none;
  width: 20px; height: 20px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px var(--cc-glass-rim);
}
/* The glow row's dot wears the setting it describes, so the four steps
   are distinguishable without opening anything. */
.st-dot-glow { box-shadow: inset 0 0 0 1px var(--cc-glass-rim), var(--cc-glow-edge) }
/* The name row's dot is painted by the same token the name itself uses,
   so it cannot disagree with what Home renders. */
.st-dot-name { background: var(--cc-name-color) }

/* ── Switch ──────────────────────────────────────────────────────── */
.st-switch {
  flex: none;
  position: relative;
  width: 46px; height: 28px;
  padding: 0;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-chip);
  background: var(--cc-track);
  cursor: pointer;
}
.st-switch.is-on {
  background: var(--cc-accent);
  border-color: var(--cc-accent);
  box-shadow: var(--cc-glow-edge);
}
/* In flight, or the licence has not answered yet. Dimmed rather than hidden:
   the control still says what state it believes it is in, it just refuses a
   second press while the first is unresolved. */
.st-switch:disabled,
.st-switch.is-busy {
  opacity: 0.45;
  cursor: default;
}
/* Sits under the row it belongs to, inside the same panel. Warnings read as
   ordinary caption text; only a refusal is coloured, because colouring the
   common case trains people to ignore it. */
.st-note {
  margin: -2px 0 0;
  padding: 0 16px 12px;
}
/* `--cc-warn`, not a literal. It is the one attention colour in this system
   that has been measured in both schemes (dark #E8A33D at 7.87–8.94, light
   darkened to #8A5410 to clear 4.5 on all four surfaces). A hand-picked red
   here would repeat exactly the fault that comment records: a hue chosen on
   dark and carried to light unmeasured. */
.st-note.is-bad { color: var(--cc-warn); }
.st-knob {
  position: absolute;
  top: 2px; left: 2px;
  width: 22px; height: 22px;
  border-radius: 50%;
  background: var(--cc-ink);
  /* transform only — a background/colour transition here reads from a
     scheme token and would keep the old scheme's value after a swap. */
  transition: transform 200ms var(--cc-spring);
}
.st-switch.is-on .st-knob {
  transform: translateX(18px);
  background: var(--cc-accent-ink);
}

.st-mini {
  flex: none;
  padding: 7px 14px;
  border: none;
  border-radius: var(--cc-r-chip);
  background: var(--cc-accent);
  color: var(--cc-accent-ink);
  font-family: var(--cc-font);
  font-size: 12px; font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
}

/* ── Credentials disclosure ──────────────────────────────────────── */
.st-disclose {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
  animation: stOpen 240ms var(--cc-ease) both;
}
@keyframes stOpen { from { opacity: 0; transform: translateY(-6px) } to { opacity: 1; transform: none } }
.cmd[data-motion="reduced"] .st-disclose { animation: none }

.st-field { display: flex; flex-direction: column; gap: 6px }
.st-input {
  width: 100%;
  padding: 11px 13px;
  /* 16px floor is enforced globally in command.css: anything smaller
     makes iOS Safari zoom the viewport on focus and never zoom back. */
  font-family: var(--cc-mono);
}
.st-note { margin: 0 }

/* sell-text, not sell: the row tints to sell-dim while pressed, and
   the raw hue measures 4.11:1 on that tint at the darkest tone. */
.st-danger .st-name { color: var(--cc-sell-text) }
.st-danger:active { background: var(--cc-sell-dim) }

.st-version {
  margin: 22px 0 0;
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--cc-ink-4);
}
</style>
