/**
 * NovaHost — live session store.
 *
 * The run-state half of the `accent.js` split, and the fix for a defect
 * the design doc flagged on Android and that is equally true here:
 *
 *   > Home state must move to a ViewModel. `isRunning`, `isConnecting`
 *   > and the TTS instance are local `remember` today. A layout switch
 *   > remounts Home and loses a running bot's UI state.
 *
 * On iOS `isRunning` was a `ref` inside HomeView.vue. Home is about to
 * become a host that swaps between five layout components, so anything
 * held in the view would be destroyed on every layout change — the user
 * would start the robot, change layout, and be told it was stopped.
 * Run state lives here so it outlives the view.
 *
 * `brokerConnected` / `brokerBalance` are accessors over `accent.js`
 * rather than copies, because TerminalView.vue (out of scope) writes
 * them there. Same reasoning as robot.js — one source of truth.
 */

import { reactive, computed } from 'vue'
import { accent } from './accent.js'

export const session = reactive({
  /* ── Run state — survives a layout remount ─────────────────────── */
  isRunning: false,
  isConnecting: false,
  /** Epoch ms the robot last started; null while stopped. Uptime is
   *  derived from this rather than counted, so it stays correct across
   *  a remount and across the app being backgrounded. */
  startedAt: null,

  /* ── Façade over accent.js ─────────────────────────────────────── */
  get brokerConnected()  { return accent.brokerConnected },
  set brokerConnected(v) { accent.brokerConnected = v },
  get relinkRequired()   { return accent.relinkRequired },
  set relinkRequired(v)  { accent.relinkRequired = v },

  get brokerBalance()  { return accent.brokerBalance },
  set brokerBalance(v) { accent.brokerBalance = v },

  /** The balance pipe must carry its currency — a bare figure is not
   *  meaningful when accounts are denominated in USD, ZAR or cents. */
  brokerCurrency: 'USD',
  brokerServer: null,
  brokerLogin: null,

  /* ── Fleet telemetry — populated by services/fleetService.js ───── */
  /** Round-trip of the last edge call, in ms. Null until measured. */
  pingMs: null,
  /** Epoch ms of the last successful heartbeat write. */
  lastBeatAt: null,
  /** Devices seen against this licence, and what the licence allows. */
  devicesOnline: 0,
  devicesAllowed: 0,

  /* ── Terminal events — the doc's Terminal Activity Log widget ───── */
  events: [],

  /** Epoch ms the user last looked at the activity stream. Lives here
   *  rather than in a layout for the same reason `isRunning` does: the
   *  Command Deck's bell badge would reset to zero on every layout
   *  change if the view owned it. Seeded to mount time so the badge
   *  starts empty instead of counting a session's backlog. */
  seenEventsAt: Date.now(),
})

/* ── Derived ─────────────────────────────────────────────────────── */

/**
 * Plain functions, NOT computeds.
 *
 * `Date.now()` is not reactive, so a computed wrapping it caches on first
 * read and only ever re-evaluates when one of its *reactive* deps
 * changes — here, `startedAt`. Uptime therefore froze at the value it
 * had the moment the widget first rendered and never moved again;
 * measured 0m00 still reading 0m00 three and a half seconds later.
 *
 * Callers own the clock instead: the widgets keep a `now` ref ticking on
 * an interval and call these during that re-render.
 */
export function uptimeMs() {
  return session.startedAt ? Date.now() - session.startedAt : 0
}

/** "14h22" / "8m41" / "—", matching the doc's compact figures. */
export function formatUptime(ms) {
  if (!ms || ms < 0) return '—'
  const totalMin = Math.floor(ms / 60000)
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  if (h > 0) return `${h}h${String(m).padStart(2, '0')}`
  const s = Math.floor(ms / 1000) % 60
  return `${m}m${String(s).padStart(2, '0')}`
}

/**
 * Heartbeat health. A beat older than 90s reads as stale rather than
 * OK — the poll runs on 30s, so two missed beats is a real gap and not
 * a slow network.
 */
export function beatState() {
  if (!session.lastBeatAt) return 'none'
  return Date.now() - session.lastBeatAt < 90_000 ? 'ok' : 'stale'
}

/**
 * Events the user has not looked at yet — the Command Deck's bell badge.
 *
 * A genuine `computed`, unlike the two above: it compares two pieces of
 * REACTIVE state (`events` and `seenEventsAt`) and never reads the clock
 * at evaluation time, so nothing here can cache against a frozen
 * `Date.now()`. `pushEvent` stamps `at` when the event happens, which is
 * what makes that possible.
 */
export const unseenEvents = computed(() =>
  session.events.reduce((n, e) => (e.at > session.seenEventsAt ? n + 1 : n), 0),
)

/* ── Actions ─────────────────────────────────────────────────────── */

export function startSession() {
  session.isRunning = true
  session.startedAt = Date.now()
}

export function stopSession() {
  session.isRunning = false
  session.startedAt = null
}

/** Newest first, capped — the log widget shows a handful and this is
 *  in memory for the life of the session, not a history. */
export function pushEvent(text, kind = 'info') {
  session.events.unshift({
    id: `${Date.now()}-${session.events.length}`,
    at: Date.now(),
    text,
    kind,
  })
  if (session.events.length > 60) session.events.length = 60
}

/** Clears the bell badge. Called when the activity stream is actually
 *  revealed, not when the layout mounts — a badge that clears itself
 *  before anyone reads it is worse than no badge. */
export function markEventsSeen() {
  session.seenEventsAt = Date.now()
}
