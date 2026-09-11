<template>
  <!--
    5 · SIGNAL FEED — the dense one.

    The only layout that leads with data rather than art, so its
    backdrop stays a wash (art mode defaults to `avatar`) and the
    screen is a column of figures. It draws its own fleet strip and
    terminal section; the widget versions of both are hidden by default
    so the same numbers do not appear twice.
  -->
  <div class="feed">
    <header class="feed-head">
      <div class="feed-id">
        <h1 class="feed-name cc-name">{{ robot.name }}</h1>
        <span v-if="robot.mentor" class="feed-mentor cc-mono">{{ robot.mentor }}</span>
      </div>
      <button class="feed-cog" aria-label="Interface style" @click="$emit('interface')">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polygon points="12 2 2 7 12 12 22 7 12 2" />
          <polyline points="2 17 12 22 22 17" /><polyline points="2 12 12 17 22 12" />
        </svg>
      </button>
    </header>

    <!--
      The doc places START at the bottom of this layout. That works
      against its own labelled bottom nav; against THIS app's floating
      dock it does not — the feed is taller than a screen, so a
      bottom-anchored control spends most of its life underneath the
      dock. Moved above the feed, where the primary action is reachable
      without scrolling and never collides.
    -->
    <IgnitionPod class="feed-pod" @assets="$emit('assets')" @toast="$emit('toast', $event)" />

    <!-- Fleet strip -->
    <div class="feed-stats">
      <div v-for="s in stats" :key="s.k" class="feed-stat">
        <span class="feed-stat-k cc-label">{{ s.k }}</span>
        <span class="feed-stat-v cc-figure" :class="s.tone">{{ s.v }}</span>
      </div>
    </div>

    <!-- Mentor signals -->
    <section class="feed-block">
      <div class="feed-block-head">
        <span class="cc-label">Mentor Signals</span>
        <span v-if="hasSignals" class="cc-chip cc-chip-accent feed-live">
          <span class="cc-dot cc-dot-live"></span>Live
        </span>
      </div>

      <p v-if="signals.loading" class="feed-empty cc-caption">Loading…</p>
      <p v-else-if="signals.error" class="feed-empty cc-caption is-error">{{ signals.error }}</p>
      <p v-else-if="!hasSignals" class="feed-empty cc-caption">No signals from your mentor yet.</p>

      <ul v-else class="feed-signals">
        <li v-for="row in signals.rows" :key="row.id" class="feed-signal cc-card">
          <div class="feed-signal-top">
            <span class="feed-dir" :class="dirClass(row)">{{ direction(row) }}</span>
            <span class="feed-pair">{{ row.pair || '—' }}</span>
            <span class="feed-age cc-mono">{{ age(row.created_at) }}</span>
          </div>

          <div class="feed-levels">
            <span class="feed-level">
              <span class="feed-level-k cc-label">Entry</span>
              <span class="feed-level-v cc-figure">{{ entryLabel(row) }}</span>
            </span>
            <span class="feed-level">
              <span class="feed-level-k cc-label">SL</span>
              <span class="feed-level-v cc-figure">{{ row.sl ?? '—' }}</span>
            </span>
            <span class="feed-level">
              <span class="feed-level-k cc-label">TP</span>
              <span class="feed-level-v cc-figure">{{ row.tp ?? '—' }}</span>
            </span>
          </div>

          <span v-if="isClosed(row)" class="feed-closed cc-mono">{{ (row.status || '').toUpperCase() }}</span>
        </li>
      </ul>
    </section>

    <!-- Terminal -->
    <section class="feed-block">
      <div class="feed-block-head">
        <span class="cc-label">Terminal</span>
        <button class="feed-open" @click="$router.push('/terminal')">Open</button>
      </div>
      <p v-if="!session.events.length" class="feed-empty cc-caption">No events yet this session.</p>
      <ul v-else class="feed-log">
        <li v-for="e in recentEvents" :key="e.id" class="feed-log-row">
          <span class="feed-log-t cc-figure">{{ clock(e.at) }}</span>
          <span class="feed-log-x">{{ e.text }}</span>
        </li>
      </ul>
    </section>

    <div v-if="widgets.length" class="feed-widgets cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { activeWidgets } from '../../../stores/theme.js'
import { robot } from '../../../stores/robot.js'
import { session, uptimeMs, formatUptime, beatState } from '../../../stores/session.js'
import { signals, hasSignals, age, direction, isClosed, entryLabel } from '../../../stores/signals.js'
import { loadRecentSignals } from '../../../services/signalService.js'
import WidgetSlot from '../WidgetSlot.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'assets', 'toast'])

const widgets = computed(() => activeWidgets.value)
const recentEvents = computed(() => session.events.slice(0, 4))

/* Uptime is derived from `startedAt`, so it needs a clock to re-render
   against — without one the figure freezes at mount and the signal ages
   below it stop moving too. */
const now = ref(Date.now())
let clockTimer = null
onMounted(() => { clockTimer = setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(clockTimer))

/* Same set and the same honesty rule as the Fleet Status widget — an em
   dash where nothing has been measured, never a zero dressed up as a
   reading. See that widget for why BOTS is BROKER here. */
const stats = computed(() => {
  void now.value
  return [
    { k: 'Uptime', v: session.isRunning ? formatUptime(uptimeMs()) : '—', tone: session.isRunning ? 'is-live' : '' },
    { k: 'Ping',   v: session.pingMs == null ? '—' : `${session.pingMs}ms`, tone: '' },
    { k: 'Broker', v: session.brokerConnected ? 'Linked' : 'None', tone: session.brokerConnected ? 'is-live' : '' },
    { k: 'Beat',   v: beatState() === 'ok' ? 'OK' : beatState() === 'stale' ? 'STALE' : '—',
      tone: beatState() === 'ok' ? 'is-live' : beatState() === 'stale' ? 'is-warn' : '' },
  ]
})

function dirClass(row) {
  const d = direction(row)
  return d === 'BUY' ? 'is-buy' : d === 'SELL' ? 'is-sell' : ''
}

function clock(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(loadRecentSignals)
</script>

<style scoped>
.feed {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: calc(var(--cc-safe-top) + 14px) 20px calc(var(--cc-safe-bottom) + 108px);
  /* Data layout: an opaque ground under the whole screen, because a
     column of figures over mentor art is unreadable at any scrim. */
  background: var(--cc-canvas);
}

/* ── Header ──────────────────────────────────────────────────────── */
.feed-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.feed-id { min-width: 0 }
.feed-name {
  margin: 0;
  font-size: calc(22px * var(--cc-name-scale));
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--cc-name-color);
  overflow-wrap: anywhere;
}
.feed-mentor {
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--cc-accent-text);
}
.feed-cog {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: none;
  background: none;
  color: var(--cc-ink-3);
  cursor: pointer;
}

/* ── Fleet strip ─────────────────────────────────────────────────── */
.feed-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-card);
  background: var(--cc-card);
}
.feed-stat { display: flex; flex-direction: column; gap: 4px; min-width: 0 }
.feed-stat-k { font-size: 9px; letter-spacing: 0.14em }
.feed-stat-v {
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.feed-stat-v.is-live { color: var(--cc-live) }
.feed-stat-v.is-warn { color: var(--cc-warn) }

/* ── Blocks ──────────────────────────────────────────────────────── */
.feed-block { display: flex; flex-direction: column; gap: 10px }
.feed-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.feed-live { height: 22px; padding: 0 9px; font-size: 10px }
.feed-open {
  border: none; background: none; padding: 0;
  font-family: var(--cc-font); font-size: 11px; font-weight: 600;
  letter-spacing: 0.06em; color: var(--cc-accent-text); cursor: pointer;
}
.feed-empty { margin: 0 }
.feed-empty.is-error { color: var(--cc-warn) }

/* ── Signal rows ─────────────────────────────────────────────────── */
.feed-signals { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }

.feed-signal {
  position: relative;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feed-signal-top { display: flex; align-items: center; gap: 9px; min-width: 0 }

/* Buy/sell are their own colours, never the accent — a user on the
   crimson accent must still be able to tell a BUY row from a SELL. */
.feed-dir {
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
.feed-dir.is-buy  { color: var(--cc-buy);  background: var(--cc-buy-dim) }
.feed-dir.is-sell { color: var(--cc-sell); background: var(--cc-sell-dim) }

.feed-pair {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.feed-age { flex: none; font-size: 10px; color: var(--cc-ink-4) }

.feed-levels { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.feed-level { display: flex; flex-direction: column; gap: 3px; min-width: 0 }
.feed-level-k { font-size: 8px; letter-spacing: 0.16em }
.feed-level-v {
  font-size: 13px;
  color: var(--cc-ink-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.feed-closed {
  align-self: flex-start;
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--cc-ink-4);
}

/* ── Terminal ────────────────────────────────────────────────────── */
.feed-log { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px }
.feed-log-row { display: flex; align-items: baseline; gap: 9px; min-width: 0 }
.feed-log-t { flex: none; font-size: 10px; color: var(--cc-ink-4) }
.feed-log-x {
  flex: 1; min-width: 0;
  font-size: 12px; color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.feed-widgets { display: flex; flex-direction: column; gap: 14px }

.feed-pod { align-self: center; max-width: 320px }
</style>
