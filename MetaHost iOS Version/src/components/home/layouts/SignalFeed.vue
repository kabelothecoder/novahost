<template>
  <!--
    5 · SIGNAL FEED — the dense one.

    The only layout that leads with data rather than art. It draws its own
    fleet strip, trade list and terminal section; the widget versions of
    the last two are hidden by default so the same numbers do not appear
    twice.

    ── What changed ───────────────────────────────────────────────────
    The screen was drawn on a flat opaque canvas, which meant the robot
    was nowhere on it: HomeBackdrop renders a heavy blurred wash of the
    mentor's art in `avatar` mode, and `background: var(--cc-canvas)`
    painted straight over it. The ground is a graded VEIL now — the same
    canvas colour, clearing at the top where the name and the ignition
    control are and firming up over the figures — so the art is present
    without ever competing with anything small. The original note was
    right that a column of figures over full-strength art is unreadable;
    it was the wrong fix.

    "Mentor Signals" is "Robot Trades", and it is not a rename. It listed
    rows from `signals` — instructions the mentor BROADCAST, entry/SL/TP
    and nothing about what happened next. This section lists what the
    robot actually did on the account, from `broker-positions` and
    `broker-performance`: open positions with live floating P/L, then what
    it closed and for how much. A signal is a plan; a trade is money.
  -->
  <div class="feed">
    <header class="feed-head">
      <!-- The name is the switcher here, the way the chip is on the
           layouts that draw HomeTopBar. This layout has no top bar of its
           own, so without this it is the one Home you cannot change robot
           from. -->
      <button class="feed-id" @click="$emit('robots')">
        <span class="feed-name cc-name">{{ robot.name }}</span>
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
             class="feed-id-chev" aria-hidden="true">
          <polyline points="6 9 12 15 18 9" />
        </svg>
        <span v-if="robot.mentor" class="feed-mentor cc-mono">{{ robot.mentor }}</span>
      </button>

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
    <IgnitionPod class="feed-pod" />

    <!-- Fleet strip -->
    <div class="feed-stats">
      <div v-for="s in stats" :key="s.k" class="feed-stat">
        <span class="feed-stat-k cc-label">{{ s.k }}</span>
        <span class="feed-stat-v cc-figure" :class="s.tone">{{ s.v }}</span>
      </div>
    </div>

    <!-- ── Robot trades ─────────────────────────────────────────────── -->
    <section class="feed-block">
      <div class="feed-block-head">
        <span class="cc-label">Robot Trades</span>
        <span v-if="openRows.length" class="cc-chip cc-chip-accent feed-live">
          <span class="cc-dot cc-dot-live"></span>{{ floating }}
        </span>
        <button v-else-if="anyRows" class="feed-open" @click="$router.push('/trades')">All</button>
      </div>

      <!-- Four situations, four sentences. "Nothing yet" because the
           broker is not linked and "nothing yet" because the robot has
           not traded are different facts with different next steps, and
           a shared empty state would answer neither. -->
      <p v-if="noBroker" class="feed-empty cc-caption">
        No broker linked yet — connect one to see what your robot trades.
      </p>
      <p v-else-if="trades.positionsError" class="feed-empty cc-caption is-error">
        {{ trades.positionsError }}
      </p>
      <p v-else-if="loadingFirst" class="feed-empty cc-caption">Reading your account…</p>
      <p v-else-if="!anyRows" class="feed-empty cc-caption">
        {{ robot.name }} has not placed a trade on this account yet.
      </p>

      <ul v-else class="feed-trades">
        <li v-for="row in rows" :key="row.key" class="feed-trade cc-card">
          <div class="feed-trade-top">
            <span class="feed-dir" :class="sideClass(row.side)">{{ row.side || '—' }}</span>
            <span class="feed-pair">{{ row.symbol }}</span>
            <span class="feed-age cc-mono">{{ row.when }}</span>
          </div>

          <div class="feed-trade-foot">
            <span class="feed-size cc-mono">{{ row.size }}</span>
            <span v-if="row.pending" class="cc-chip feed-wait">WAITING</span>
            <span v-else class="feed-pl cc-figure" :data-tone="tone(row.net)">
              {{ money(row.net) }}
            </span>
          </div>

          <span class="feed-state cc-mono" :class="row.open ? 'is-open' : ''">
            {{ row.open ? 'OPEN' : 'CLOSED' }}
          </span>
        </li>
      </ul>

      <!-- Never silently filtered. The section is named for the robot, so
           a hand-placed trade does not belong in it — but pretending the
           account has fewer trades than it does would be worse. -->
      <p v-if="manualCount" class="feed-note cc-caption">
        {{ manualCount }} manual {{ manualCount === 1 ? 'trade' : 'trades' }} on this
        account are not shown here —
        <button class="feed-inline" @click="$router.push('/trades')">see Trades</button>.
      </p>
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
import {
  trades, money, tone, refreshHistory, startPositionsPolling, stopPositionsPolling,
} from '../../../stores/trades.js'
import WidgetSlot from '../WidgetSlot.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'toast'])

const widgets = computed(() => activeWidgets.value)
const recentEvents = computed(() => session.events.slice(0, 4))

/* Uptime is derived from `startedAt`, so it needs a clock to re-render
   against — without one the figure freezes at mount and the trade ages
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

/* ── Robot trades ────────────────────────────────────────────────────
   `ours` is the broker row's magic-number match against this robot's own
   magic (77 + six digits of an ea_id hash), so it is the only reliable
   way to tell a trade the robot placed from one the user opened by hand
   in MetaTrader. A section called Robot Trades that lists manual trades
   would be lying about which of them the mentor's product is
   responsible for.

   `ours !== false` rather than `ours === true`: a broker branch that does
   not report a magic number at all leaves the field undefined, and
   dropping every row on a broker that cannot answer the question would
   show an empty list on a busy account. */
const isOurs = (t) => t.ours !== false

const openRows = computed(() => trades.positions.filter(isOurs))

const manualCount = computed(() =>
  trades.positions.filter(t => t.ours === false).length +
  trades.closed.filter(t => t.ours === false).length
)

/** Open positions first, then the most recent closed — six rows at most.
 *  This is a glance; /trades is the screen. */
const rows = computed(() => {
  void now.value
  const open = openRows.value.map((p, i) => ({
    key: p.id || `o${i}${p.symbol}`,
    open: true,
    pending: Boolean(p.pending),
    side: p.side,
    symbol: p.symbol || p.base || '—',
    size: p.volume != null ? `${p.volume} lots` : '—',
    net: p.net_profit,
    when: age(p.open_time),
  }))

  const closed = trades.closed.filter(isOurs).slice(0, Math.max(0, 6 - open.length)).map((t, i) => ({
    key: t.id || `c${i}${t.symbol}${t.close_time}`,
    open: false,
    pending: false,
    side: t.side,
    symbol: t.symbol || t.base || '—',
    size: t.volume != null ? `${t.volume} lots` : '—',
    net: t.net_profit,
    when: age(t.close_time),
  }))

  return [...open, ...closed].slice(0, 6)
})

const anyRows = computed(() => rows.value.length > 0)

/** Floating P/L across the robot's open positions, for the header chip. */
const floating = computed(() => {
  const net = openRows.value.reduce((sum, p) => {
    const v = Number(p.net_profit)
    return Number.isFinite(v) ? sum + v : sum
  }, 0)
  return money(net)
})

/* `NO_ACCOUNT_LINKED` is an answer, not a failure, and it wants a
   different sentence from a broker that could not be reached. The store
   keeps the message rather than the code, so this checks the session's
   own broker flag — which the Terminal screen and the fleet poll both
   maintain — instead of parsing prose. */
const noBroker = computed(() => !session.brokerConnected && !trades.positions.length)

const loadingFirst = computed(() =>
  (trades.positionsLoading || trades.historyLoading) && !anyRows.value
)

function sideClass(side) {
  const s = String(side || '').toUpperCase()
  return s === 'BUY' ? 'is-buy' : s === 'SELL' ? 'is-sell' : ''
}

/** Compact relative age — "4m", "3h", "2d". Longer than that and the
 *  exact figure stops mattering at a glance. */
function age(ts) {
  if (!ts) return '—'
  const then = new Date(ts).getTime()
  if (Number.isNaN(then)) return '—'
  const mins = Math.max(0, Math.round((now.value - then) / 60000))
  if (mins < 1) return 'now'
  if (mins < 60) return `${mins}m`
  const hrs = Math.round(mins / 60)
  if (hrs < 24) return `${hrs}h`
  return `${Math.round(hrs / 24)}d`
}

function clock(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/* Positions are polled (reference-counted in the store, so this and the
   Open Trades widget cost one request between them). History is NOT:
   MetaCopier documents it as lagging live trading by up to three minutes,
   so a fast poll cannot make it fresher. */
onMounted(() => {
  startPositionsPolling()
  refreshHistory({ limit: 20 })
})
onUnmounted(stopPositionsPolling)
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
  /*
    A VEIL, not a lid.

    This was `background: var(--cc-canvas)` — fully opaque, painted over
    the top of HomeBackdrop's blurred wash of the mentor's art, so the one
    layout that needed the robot's presence most was the only one where it
    was invisible.

    It is GRADED rather than flat, because the screen is not uniformly
    dense. The top band — robot name, ignition control — is where the art
    belongs and where nothing small is set, so it clears to 0.42 — the
    only things over it are the robot name at display size and the
    ignition control, which is opaque. The stops were placed by measuring
    where the first small type actually lands: the fleet strip draws its
    own opaque card, and the first `cc-label` sits below 34%, by which
    point the veil is at 0.84 and every figure under it is on effectively
    solid ground.

    The rgb fallback matters. --cc-tone-rgb is written by theme.js at
    runtime; a first paint before the store initialises would otherwise
    get an invalid colour and fall back to transparent, which is the
    unreadable screen this whole note is about.
  */
  background: linear-gradient(
    180deg,
    rgba(var(--cc-tone-rgb, 14, 14, 17), 0.42) 0%,
    rgba(var(--cc-tone-rgb, 14, 14, 17), 0.84) 34%,
    rgba(var(--cc-tone-rgb, 14, 14, 17), 0.90) 100%
  );
}
.cmd[data-scheme="light"] .feed {
  background: linear-gradient(
    180deg,
    rgba(244, 247, 249, 0.70) 0%,
    rgba(244, 247, 249, 0.90) 30%,
    rgba(244, 247, 249, 0.94) 100%
  );
}

/* ── Header ──────────────────────────────────────────────────────── */
.feed-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.feed-id {
  display: grid;
  grid-template-columns: auto auto;
  align-items: center;
  justify-content: start;
  column-gap: 6px;
  min-width: 0;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.feed-id:active { transform: scale(0.98) }
.feed-name {
  margin: 0;
  font-size: calc(22px * var(--cc-name-scale));
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--cc-name-color);
  overflow-wrap: anywhere;
}
.feed-id-chev { color: var(--cc-ink-4); flex: none }
.feed-mentor {
  grid-column: 1 / -1;
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
.feed-empty.is-error { color: var(--cc-sell-text) }
.feed-note { margin: 0 }
.feed-inline {
  border: none; background: none; padding: 0;
  font: inherit; color: var(--cc-accent-text); cursor: pointer;
  text-decoration: underline;
}

/* ── Trade rows ──────────────────────────────────────────────────── */
.feed-trades { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }

.feed-trade {
  position: relative;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.feed-trade-top { display: flex; align-items: center; gap: 9px; min-width: 0 }

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
/* The age column stops short of the OPEN/CLOSED mark in the corner. */
.feed-age { flex: none; padding-right: 52px; font-size: 10px; color: var(--cc-ink-4) }

.feed-trade-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.feed-size { font-size: 11px; color: var(--cc-ink-4) }
.feed-wait { height: 20px; padding: 0 8px; font-size: 9px; letter-spacing: 0.12em }

.feed-pl { font-size: 15px; font-weight: 700 }
.feed-pl[data-tone="up"]   { color: var(--cc-buy) }
.feed-pl[data-tone="down"] { color: var(--cc-sell) }
.feed-pl[data-tone="flat"] { color: var(--cc-ink-3) }

.feed-state {
  position: absolute;
  top: 12px;
  right: 14px;
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--cc-ink-5);
}
.feed-state.is-open { color: var(--cc-accent-text) }

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
