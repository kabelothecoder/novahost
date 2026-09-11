<template>
  <!--
    6 · COMMAND DECK — the bento one.

    A dashboard rather than a portrait: the screen answers "is my robot
    healthy, and what has it done" before it answers "what does my robot
    look like". That makes it the second data-forward layout after Signal
    Feed, and the two differ in shape — Signal Feed is one column of
    figures, this is a bento whose hero cell summarises the rest.

    Composition note. The header and the bento sit ON the backdrop, so
    glass cards there have real art to refract; everything from the
    ignition down sits on `.cc-ground`, because a full page of glass over
    one mentor portrait turns to soup (see the ground's own note in
    command.css). That split is deliberate: the top third is material,
    the rest is data.
  -->
  <div class="deck">
    <!-- ═══ 1 · EXECUTIVE HEADER ═══════════════════════════════════ -->
    <header class="deck-head">
      <button class="deck-avatar" @click="$emit('robots')">
        <img :src="avatar" alt="" class="deck-avatar-img" />
        <!-- The doc's status ring. Colour is the run state, never the
             accent — a user on the mint accent must still be able to
             tell a running robot from a stopped one. -->
        <span class="deck-avatar-ring" :class="`is-${runState}`" aria-hidden="true"></span>
        <span class="deck-avatar-dot" :class="`is-${runState}`" aria-hidden="true"></span>
        <span class="cc-sr">{{ robot.name }} · {{ runLabel }}</span>
      </button>

      <button class="deck-switch" @click="$emit('robots')">
        <span class="deck-switch-name">{{ robot.name }}</span>
        <span class="deck-switch-sub cc-mono">{{ runLabel }}</span>
      </button>

      <div class="deck-head-actions">
        <button
          class="deck-icon"
          :aria-label="unseen ? `Activity — ${unseen} new` : 'Activity'"
          @click="revealActivity"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          <span v-if="unseen" class="deck-bell-badge cc-figure">{{ unseen > 9 ? '9+' : unseen }}</span>
        </button>

        <!-- Every other layout puts the Interface Style sheet one tap
             from Home. Leaving it off here made Command Deck the only
             screen you could switch TO but not switch away from without
             detouring through Settings. -->
        <button class="deck-icon" aria-label="Interface style" @click="$emit('interface')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polygon points="12 2 2 7 12 12 22 7 12 2" />
            <polyline points="2 17 12 22 22 17" /><polyline points="2 12 12 17 22 12" />
          </svg>
        </button>
      </div>
    </header>

    <!-- ═══ 2 · KEY METRICS — the bento ════════════════════════════ -->
    <div class="deck-sec-head">
      <span class="cc-label">System</span>
      <!-- The one control on this screen with a genuine async state to
           show. Every other action here is a route change, and a
           spinner on an instant navigation is theatre. -->
      <button class="deck-refresh" :disabled="refreshing" @click="refresh">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"
             :class="{ 'is-spinning': refreshing }" aria-hidden="true">
          <path d="M21 12a9 9 0 1 1-2.64-6.36" /><polyline points="21 3 21 9 15 9" />
        </svg>
        <span>{{ refreshing ? 'Checking' : 'Refresh' }}</span>
      </button>
    </div>

    <div class="deck-bento cc-stagger">
      <SystemHealthWidget class="deck-health" />

      <div v-for="t in tiles" :key="t.k" class="deck-tile cc-card">
        <span class="deck-tile-k cc-label">{{ t.k }}</span>
        <span class="deck-tile-v cc-figure" :class="t.tone">{{ t.v }}</span>
      </div>
    </div>

    <!-- Everything below here gets an opaque base. -->
    <div class="deck-ground cc-ground">
      <!-- ═══ 3 · PRIMARY ACTION CENTRE ════════════════════════════ -->
      <IgnitionPod variant="bare" class="deck-pod" @toast="$emit('toast', $event)" />

      <div class="deck-actions">
        <button v-for="a in actions" :key="a.id" class="deck-action cc-card" @click="run(a)">
          <span class="deck-action-icon" v-html="a.icon"></span>
          <span class="deck-action-label">{{ a.label }}</span>
        </button>
      </div>

      <!-- ═══ 4 · MENTOR SIGNALS ═══════════════════════════════════ -->
      <section class="deck-block">
        <div class="deck-sec-head">
          <span class="cc-label">Mentor Signals</span>
          <button class="deck-more" @click="goSignals">Open</button>
        </div>

        <p v-if="signals.loading" class="deck-empty cc-caption">Checking for signals…</p>
        <p v-else-if="signals.error" class="deck-empty cc-caption is-error">{{ signals.error }}</p>
        <p v-else-if="!hasSignals" class="deck-empty cc-caption">
          No signals from your mentor yet. They appear here the moment one is broadcast.
        </p>

        <ul v-else class="deck-signals">
          <!--
            Read-only rows, and deliberately so. The obvious bento
            affordance here is a swipe with actions on it, but there is
            no honest action to put there: a mentor's call cannot be
            restarted or paused from a subscriber's handset, and the one
            operation that does exist — claiming — is CONSUMPTION. A
            claim from this device would take the trade away from the
            handset that was going to execute it, silently and for good.
            See services/fleetService.js.
          -->
          <li v-for="row in topSignals" :key="row.id" class="deck-signal cc-card">
            <div class="deck-signal-top">
              <span class="deck-dir" :class="dirClass(row)">{{ direction(row) }}</span>
              <span class="deck-pair">{{ row.pair || '—' }}</span>
              <span class="deck-tag" :class="isClosed(row) ? 'is-closed' : 'is-live'">
                {{ isClosed(row) ? (row.status || 'closed').toUpperCase() : 'LIVE' }}
              </span>
              <span class="deck-age cc-mono">{{ age(row.created_at) }}</span>
            </div>
            <div class="deck-levels">
              <span v-for="l in levels(row)" :key="l.k" class="deck-level">
                <span class="deck-level-k cc-label">{{ l.k }}</span>
                <span class="deck-level-v cc-figure">{{ l.v }}</span>
              </span>
            </div>
          </li>
        </ul>
      </section>

      <!-- ═══ 5 · LIVE ACTIVITY ════════════════════════════════════ -->
      <section ref="activityEl" class="deck-block">
        <div class="deck-sec-head">
          <span class="cc-label">Activity</span>
          <button class="deck-more" @click="$router.push('/terminal')">Terminal</button>
        </div>

        <p v-if="!session.events.length" class="deck-empty cc-caption">
          Nothing has happened yet this session.
        </p>
        <ul v-else class="deck-events">
          <li v-for="e in recentEvents" :key="e.id" class="deck-event">
            <span class="deck-event-chip" :class="`is-${e.kind}`">{{ chipFor(e.kind) }}</span>
            <span class="deck-event-x">{{ e.text }}</span>
            <span class="deck-event-t cc-mono">{{ clock(e.at) }}</span>
          </li>
        </ul>
      </section>

      <div v-if="widgets.length" class="deck-widgets cc-stagger">
        <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

import { theme, activeWidgets } from '../../../stores/theme.js'
import { robot, FALLBACK_AVATAR } from '../../../stores/robot.js'
import {
  session, uptimeMs, formatUptime, beatState,
  unseenEvents, markEventsSeen,
} from '../../../stores/session.js'
import { signals, hasSignals, age, direction, isClosed, entryLabel } from '../../../stores/signals.js'
import { loadRecentSignals } from '../../../services/signalService.js'
import { pollFleet } from '../../../services/fleetService.js'
import { haptic } from '../../../composables/useSpeech.js'

import WidgetSlot from '../WidgetSlot.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'
import SystemHealthWidget from '../widgets/SystemHealthWidget.vue'

const emit = defineEmits(['interface', 'robots', 'assets', 'toast'])
const router = useRouter()

const widgets = computed(() => activeWidgets.value)
const avatar  = computed(() => robot.avatarUrl || FALLBACK_AVATAR)
const unseen  = computed(() => unseenEvents.value)

/* ── Clock ───────────────────────────────────────────────────────────
   Uptime and the signal ages derive from timestamps, and `Date.now()`
   is not reactive — a computed wrapping it caches on first read and
   never moves again. The layout owns the tick; the derived values are
   plain functions called during this re-render. */
const now = ref(Date.now())
let clockTimer = null
onMounted(() => { clockTimer = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(clockTimer))

/* ── Run state ───────────────────────────────────────────────────── */
const runState = computed(() => {
  if (session.isRunning) return session.brokerConnected ? 'live' : 'warn'
  return 'idle'
})
const runLabel = computed(() => {
  if (session.isRunning) return session.brokerConnected ? 'EXECUTING' : 'NO BROKER'
  return session.brokerConnected ? 'STANDBY' : 'NOT LINKED'
})

/* ── Bento tiles ─────────────────────────────────────────────────────
   The same four figures and the same honesty rule as the Fleet Status
   widget: an em dash wherever nothing has been measured, never a zero
   dressed up as a reading. `fleetStatus` is hidden by default on this
   layout so the card version cannot show the same numbers twice. */
const tiles = computed(() => {
  void now.value
  const beat = beatState()
  return [
    { k: 'Uptime', v: session.isRunning ? formatUptime(uptimeMs()) : '—',
      tone: session.isRunning ? 'is-live' : '' },
    { k: 'Ping', v: session.pingMs == null ? '—' : `${session.pingMs}ms`,
      tone: session.pingMs != null && session.pingMs > 400 ? 'is-warn' : '' },
    { k: 'Broker', v: session.brokerConnected ? 'Linked' : 'None',
      tone: session.brokerConnected ? 'is-live' : '' },
    { k: 'Beat', v: beat === 'ok' ? 'OK' : beat === 'stale' ? 'STALE' : '—',
      tone: beat === 'ok' ? 'is-live' : beat === 'stale' ? 'is-warn' : '' },
  ]
})

/* ── Refresh ─────────────────────────────────────────────────────────
   `pollFleet` is read-only (`license-status`, not `claim-signals`) and
   guards against overlapping itself, so a double tap is harmless. The
   local flag exists purely to drive the spinner — the service does not
   expose its in-flight state. */
const refreshing = ref(false)
async function refresh() {
  if (refreshing.value) return
  refreshing.value = true
  haptic(20)
  try {
    await Promise.all([pollFleet(), loadRecentSignals()])
  } finally {
    refreshing.value = false
  }
}

/* ── Action centre ───────────────────────────────────────────────────
   Four one-tap actions, all of which exist. Icons are inline so a tile
   costs nothing beyond this file. */
const actions = [
  { id: 'scan', label: 'Scan', to: '/scanner',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><line x1="7" y1="12" x2="17" y2="12"/></svg>' },
  { id: 'assets', label: 'Assets', emit: 'assets',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>' },
  { id: 'terminal', label: 'Terminal', to: '/terminal',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>' },
  { id: 'voice', label: 'Voice', to: '/voice',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="22"/></svg>' },
]

function run(a) {
  haptic(20)
  if (a.emit) emit(a.emit)
  else router.push(a.to)
}

/* ── Signals ─────────────────────────────────────────────────────── */
const topSignals = computed(() => signals.rows.slice(0, 3))

function levels(row) {
  void now.value
  return [
    { k: 'Entry', v: entryLabel(row) },
    { k: 'SL', v: row.sl ?? '—' },
    { k: 'TP', v: row.tp ?? '—' },
  ]
}

function dirClass(row) {
  const d = direction(row)
  return d === 'BUY' ? 'is-buy' : d === 'SELL' ? 'is-sell' : ''
}

function goSignals() {
  haptic(20)
  /* Signal Feed is the layout built to show the whole feed; there is no
     dedicated route. Switching to it is the honest "see all". */
  theme.layout = 'feed'
}

/* ── Activity ────────────────────────────────────────────────────── */
const activityEl = ref(null)
const recentEvents = computed(() => session.events.slice(0, 6))

function chipFor(kind) {
  return kind === 'ok' ? 'OK' : kind === 'error' ? 'ERR' : kind === 'idle' ? 'IDLE' : 'INFO'
}

function clock(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/**
 * The badge clears only once the stream is actually on screen — a badge
 * that zeroes itself on mount tells the user nothing happened.
 *
 * `smooth` is skipped under reduced motion: it is the one scroll in this
 * layout long enough to be uncomfortable for someone who asked for calm.
 */
function revealActivity() {
  haptic(20)
  activityEl.value?.scrollIntoView({
    behavior: theme.motion === 'reduced' ? 'auto' : 'smooth',
    block: 'center',
  })
  markEventsSeen()
}

onMounted(loadRecentSignals)
</script>

<style scoped>
.deck {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: calc(var(--cc-safe-top) + 8px) 20px 0;
}

/* Screen-reader-only. The avatar's state is carried by colour alone
   otherwise, which is exactly the thing colour must never carry. */
.cc-sr {
  position: absolute;
  width: 1px; height: 1px;
  margin: -1px; padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* ═══ 1 · HEADER ═══════════════════════════════════════════════════ */
.deck-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
}

/* The two icon buttons read as one cluster, so no gap between them —
   their 44px boxes already carry the optical spacing. The negative
   margin pulls the last one's padding off the screen edge, which is
   what stops a right-aligned icon looking inset on iOS. */
.deck-head-actions {
  flex: none;
  display: flex;
  align-items: center;
  margin-right: -6px;
}

.deck-avatar {
  position: relative;
  flex: none;
  width: 44px;   /* the 44pt floor, exactly */
  height: 44px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.deck-avatar:active { transform: scale(0.94) }

.deck-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: var(--cc-card-2);
}

.deck-avatar-ring {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--cc-idle);
  transition: border-color 240ms var(--cc-ease);
}
.deck-avatar-ring.is-live { border-color: var(--cc-live) }
.deck-avatar-ring.is-warn { border-color: var(--cc-warn) }

.deck-avatar-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--cc-idle);
  /* Rings the dot in the canvas colour so it reads as a separate object
     against the portrait rather than a smudge on it. */
  box-shadow: 0 0 0 2.5px var(--cc-canvas);
}
.deck-avatar-dot.is-live {
  background: var(--cc-live);
  animation: ccPulse 2.2s var(--cc-ease) infinite;
}
.deck-avatar-dot.is-warn { background: var(--cc-warn) }

.deck-switch {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-height: 44px;
  justify-content: center;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
}
.deck-switch-name {
  max-width: 100%;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
/* ink-3 rather than the cc-mono default of ink-4: this line sits on the
   backdrop with no card behind it. */
.deck-switch-sub {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--cc-ink-3);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.55);
}
.cmd[data-scheme="light"] .deck-switch-sub { text-shadow: 0 1px 8px rgba(255, 255, 255, 0.8) }

.deck-icon {
  position: relative;
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: none;
  background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  /* transform only — `color` reads from a scheme token, and a
     transition on a token-fed property does not re-run when the token
     changes, which leaves the icon painting the previous scheme's ink
     after a Light/Dark swap. Same finding as HomeTopBar. */
  transition: transform 180ms var(--cc-spring);
}
.deck-icon:active { transform: scale(0.9) }

.deck-bell-badge {
  position: absolute;
  top: 4px;
  right: 3px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  color: var(--cc-accent-ink);
  background: var(--cc-accent);
  box-shadow: 0 0 0 2px var(--cc-canvas), var(--cc-glow-edge);
}

/* ═══ Section heads ════════════════════════════════════════════════ */
.deck-sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 20px;
}

.deck-refresh,
.deck-more {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  /* Visually an 11px text link, but a real 44x44 target.
     Sized with min-* rather than padding: padding around an 11px line
     box left these at 39px, because the box is 15px tall and not the
     20 the arithmetic assumed. The negative margin then pulls the
     overhang back out of the flow, so a 44px control still contributes
     the 20px the section head is designed around. */
  min-height: 44px;
  min-width: 44px;
  padding: 0 6px;
  margin: -12px -6px;
  border: none;
  background: none;
  font-family: var(--cc-font);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--cc-accent-text);
  cursor: pointer;
}
.deck-refresh:disabled { color: var(--cc-ink-3); cursor: default }
.deck-refresh svg.is-spinning { animation: ccSpin 900ms linear infinite }

@keyframes ccSpin { to { transform: rotate(360deg) } }

/* ═══ 2 · BENTO ════════════════════════════════════════════════════ */
.deck-bento {
  display: grid;
  /* The hero cell earns the extra width: it holds a ring, the stat
     tiles hold one figure each. */
  grid-template-columns: 1.12fr 1fr;
  gap: 10px;
}

.deck-health {
  grid-row: span 4;
  display: flex;
  flex-direction: column;
}

.deck-tile {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding: 10px 14px;
  min-height: 54px;
}
.deck-tile-k { font-size: 9px; letter-spacing: 0.14em }
.deck-tile-v {
  font-size: 16px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.deck-tile-v.is-live { color: var(--cc-live) }
.deck-tile-v.is-warn { color: var(--cc-warn) }

/* ═══ Ground ═══════════════════════════════════════════════════════ */
.deck-ground {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: calc(var(--cc-safe-bottom) + 108px);
}

.deck-pod { align-self: center; max-width: 320px }

/* ═══ 3 · ACTION CENTRE ════════════════════════════════════════════ */
.deck-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: -4px;
}

.deck-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  /* Four across on a 375pt phone is ~78pt wide; the height is what
     guarantees the 44pt target. */
  min-height: 66px;
  padding: 10px 4px;
  cursor: pointer;
  transition: transform 200ms var(--cc-spring);
}
.deck-action:active { transform: scale(0.95) }

.deck-action-icon { display: block; width: 20px; height: 20px; color: var(--cc-accent-text) }
.deck-action-icon :deep(svg) { width: 100%; height: 100% ; display: block }

.deck-action-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--cc-ink-2);
  max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ═══ Blocks ═══════════════════════════════════════════════════════ */
.deck-block { display: flex; flex-direction: column; gap: 10px }
.deck-empty { margin: 0 }
.deck-empty.is-error { color: var(--cc-warn) }

/* ═══ 4 · SIGNALS ══════════════════════════════════════════════════ */
.deck-signals { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }

.deck-signal { padding: 12px 14px; display: flex; flex-direction: column; gap: 10px }

.deck-signal-top { display: flex; align-items: center; gap: 8px; min-width: 0 }

/* Buy/sell keep their own colours, never the accent — a user on the
   crimson accent must still be able to read a BUY row as a BUY. */
.deck-dir {
  flex: none;
  padding: 3px 8px;
  border-radius: 5px;
  font-family: var(--cc-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.10em;
  color: var(--cc-ink-3);
  background: var(--cc-card-2);
}
.deck-dir.is-buy  { color: var(--cc-buy);  background: var(--cc-buy-dim) }
.deck-dir.is-sell { color: var(--cc-sell); background: var(--cc-sell-dim) }

.deck-pair {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.deck-tag {
  flex: none;
  padding: 2px 7px;
  border-radius: 4px;
  font-family: var(--cc-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--cc-ink-3);
  background: var(--cc-card-2);
}
.deck-tag.is-live { color: var(--cc-live); background: var(--cc-buy-dim) }

.deck-age { flex: none; font-size: 10px; color: var(--cc-ink-4) }

.deck-levels { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.deck-level { display: flex; flex-direction: column; gap: 3px; min-width: 0 }
.deck-level-k { font-size: 8px; letter-spacing: 0.16em }
.deck-level-v {
  font-size: 13px;
  color: var(--cc-ink-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ═══ 5 · ACTIVITY ═════════════════════════════════════════════════ */
.deck-events { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }

.deck-event { display: flex; align-items: center; gap: 9px; min-width: 0 }

.deck-event-chip {
  flex: none;
  width: 38px;
  padding: 3px 0;
  text-align: center;
  border-radius: 4px;
  font-family: var(--cc-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--cc-ink-3);
  background: var(--cc-card-2);
}
.deck-event-chip.is-ok    { color: var(--cc-live); background: var(--cc-buy-dim) }
.deck-event-chip.is-error { color: var(--cc-sell); background: var(--cc-sell-dim) }

.deck-event-x {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--cc-ink-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.deck-event-t { flex: none; font-size: 10px; color: var(--cc-ink-4) }

.deck-widgets { display: flex; flex-direction: column; gap: 14px }
</style>
