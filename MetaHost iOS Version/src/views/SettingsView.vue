<template>
  <div
    class="cmd settings"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <header class="st-head">
      <h1 class="cc-title">Settings</h1>
      <span class="cc-caption">{{ robot.name }}</span>
    </header>

    <div class="st-body">
      <!-- ── TOOLS ──────────────────────────────────────────────────── -->
      <span class="cc-label st-section">Tools</span>
      <div class="st-group">
        <button v-for="t in tools" :key="t.to" class="st-row cc-card" @click="go(t.to)">
          <span class="st-icon" v-html="t.icon"></span>
          <span class="st-copy">
            <span class="st-name">{{ t.name }}</span>
            <span class="cc-caption">{{ t.desc }}</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── APPEARANCE ─────────────────────────────────────────────── -->
      <span class="cc-label st-section">Appearance</span>
      <div class="st-group">
        <button class="st-row cc-card" @click="sheetOpen = true">
          <span class="st-icon" v-html="ICON_LAYERS"></span>
          <span class="st-copy">
            <span class="st-name">Interface Style</span>
            <span class="cc-caption">{{ styleSummary }}</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row cc-card" @click="go('/arrange-widgets')">
          <span class="st-icon" v-html="ICON_GRID"></span>
          <span class="st-copy">
            <span class="st-name">Arrange Widgets</span>
            <span class="cc-caption">{{ arrangeSummary }}</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>
      </div>

      <!-- ── ADVANCED ───────────────────────────────────────────────── -->
      <span class="cc-label st-section">Advanced</span>
      <div class="st-group">
        <div class="st-row cc-card">
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
        <div class="st-row cc-card">
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

        <!-- Credentials. Kept here rather than dropped: the doc's
             Settings has no place for them, but they are the only way
             to connect MetaAPI and there is no other screen that
             collects them. -->
        <div class="st-row st-row-col cc-card">
          <span class="st-name">MetaAPI</span>
          <label class="st-field">
            <span class="cc-label">Token</span>
            <input
              type="password"
              class="cc-recess st-input"
              v-model="apiKeys.metaapi"
              placeholder="sk-…"
              autocomplete="off"
              spellcheck="false"
              @blur="saveKey('METAAPI_TOKEN', apiKeys.metaapi)"
            />
          </label>
          <label class="st-field">
            <span class="cc-label">Account ID</span>
            <input
              type="text"
              class="cc-recess st-input"
              v-model="apiKeys.accountId"
              placeholder="Account ID"
              autocomplete="off"
              spellcheck="false"
              @blur="saveKey('METAAPI_ACCOUNT_ID', apiKeys.accountId)"
            />
          </label>
        </div>

        <div class="st-row st-row-col cc-card">
          <span class="st-name">ElevenLabs</span>
          <label class="st-field">
            <span class="cc-label">API key</span>
            <input
              type="password"
              class="cc-recess st-input"
              v-model="apiKeys.elevenlabs"
              placeholder="Optional — the robot falls back to system speech"
              autocomplete="off"
              spellcheck="false"
              @blur="saveKey('ELEVENLABS_API_KEY', apiKeys.elevenlabs)"
            />
          </label>
        </div>
      </div>

      <!-- ── ABOUT ──────────────────────────────────────────────────── -->
      <span class="cc-label st-section">About</span>
      <div class="st-group">
        <button class="st-row cc-card" @click="go('/support')">
          <span class="st-icon" v-html="ICON_HELP"></span>
          <span class="st-copy">
            <span class="st-name">Support</span>
            <span class="cc-caption">Device moves, licence help</span>
          </span>
          <span class="st-chev" v-html="CHEV"></span>
        </button>

        <button class="st-row cc-card st-danger" @click="signOut">
          <span class="st-copy">
            <span class="st-name">Sign out</span>
            <span class="cc-caption">Clears this device's licence and session</span>
          </span>
        </button>
      </div>

      <p class="cc-caption st-version">NovaHost · {{ version }}</p>
    </div>

    <InterfaceStyleSheet v-if="sheetOpen" @close="sheetOpen = false" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  theme, resolvedScheme, activeLayout, REORDERABLE, GLOW_MODES,
} from '../stores/theme.js'
import { robot } from '../stores/robot.js'
import { logout } from '../services/authService.js'
import { haptic } from '../composables/useSpeech.js'
import InterfaceStyleSheet from '../components/settings/InterfaceStyleSheet.vue'

const router = useRouter()
const sheetOpen = ref(false)
/* A literal, not a build define: this project has no version metadata
   wired into the build (package.json is still 0.0.0), and a define
   pointing at that would print "NovaHost · 0.0.0". */
const version = 'v6'

const CHEV = `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>`
const ICON_LAYERS = `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>`
const ICON_GRID = `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>`
const ICON_HELP = `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.6-3 4"/><line x1="12" y1="17" x2="12" y2="17"/></svg>`

const tools = [
  {
    to: '/scanner', name: 'Chart Scanner', desc: 'AI chart analysis & auto trade',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8V5a2 2 0 0 1 2-2h3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M21 16v3a2 2 0 0 1-2 2h-3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/><polyline points="7 14 10 11 13 14 17 9"/></svg>`,
  },
  {
    to: '/pairs', name: 'Trading Quotes', desc: 'Live pairs and symbol config',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
  },
  {
    to: '/terminal', name: 'Terminal', desc: 'Broker link and service events',
    icon: `<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`,
  },
]

const styleSummary = computed(() => {
  const glow = GLOW_MODES.find(g => g.id === theme.glowMode)?.name || 'Medium'
  return `${activeLayout.value.name} · ${theme.accent.name} · ${glow} glow`
})

const arrangeSummary = computed(() => {
  const cfg = theme.widgets[theme.layout]
  if (!cfg) return ''
  return `${REORDERABLE.length - cfg.hidden.length} shown · ${cfg.hidden.length} hidden`
})

/* ── Notifications ────────────────────────────────────────────────── */
const notifSupported = typeof Notification !== 'undefined'
const notifPermission = ref(notifSupported ? Notification.permission : 'unsupported')

const notifCaption = computed(() => {
  switch (notifPermission.value) {
    case 'granted':     return 'Signal and trade alerts are on'
    case 'denied':      return 'Blocked — turn on in your device settings'
    case 'unsupported': return 'Not supported on this device'
    default:            return 'Get alerted when a signal arrives'
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

/* ── Credentials ──────────────────────────────────────────────────── */
const apiKeys = ref({
  metaapi:    localStorage.getItem('METAAPI_TOKEN') || '',
  accountId:  localStorage.getItem('METAAPI_ACCOUNT_ID') || '',
  elevenlabs: localStorage.getItem('ELEVENLABS_API_KEY') || '',
})

/* Writes an empty value too, so clearing a field actually clears the
   credential. The previous version skipped falsy values, which made a
   wrong key impossible to remove from the UI. */
function saveKey(key, val) {
  const v = (val || '').trim()
  if (v) localStorage.setItem(key, v)
  else localStorage.removeItem(key)
}

function go(to) { haptic(20); router.push(to) }

function signOut() {
  logout()
  router.replace({ name: 'unlock' })
}
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
  padding: calc(var(--cc-safe-top) + 18px) 20px 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.st-body {
  padding: 12px 16px calc(var(--cc-safe-bottom) + 120px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.st-section { margin: 14px 4px 4px }
.st-group { display: flex; flex-direction: column; gap: 8px }

.st-row {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  padding: 13px 15px;
  text-align: left;
  cursor: pointer;
}
.st-row-col {
  flex-direction: column;
  align-items: stretch;
  gap: 11px;
  cursor: default;
}

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
  font-size: 14px; font-weight: 600; color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.st-chev { flex: none; color: var(--cc-ink-5); display: grid; place-items: center }

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

/* ── Fields ──────────────────────────────────────────────────────── */
.st-field { display: flex; flex-direction: column; gap: 6px }
.st-input {
  width: 100%;
  padding: 11px 13px;
  /* 16px floor is enforced globally in command.css: anything smaller
     makes iOS Safari zoom the viewport on focus and never zoom back. */
  font-family: var(--cc-mono);
}

.st-danger .st-name { color: var(--cc-sell) }

.st-version {
  margin: 18px 0 0;
  text-align: center;
  color: var(--cc-ink-4);
}
</style>
